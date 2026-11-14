package no.ntnu.folk.adamzk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ca.uhn.fhir.context.FhirContext;

class SyntPopPatientConverterTest {

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    @TempDir
    Path temporaryDirectory;

    @Test
    void convertsSyntPopPersonToEpsPatient() throws Exception {
        Path input = writeInput("""
                [{
                  "persons": [{
                    "nin": "23707015357",
                    "name": "Ask <Ebeger & Co>",
                    "gender": "Male",
                    "birthDate": "1970-05-23T00:00:00+00:00"
                  }],
                  "totalCount": 1
                }]
                """);
        Path target = temporaryDirectory.resolve("patient-1");

        Path output = converter().convert(input, 0, target);
        Patient patient = (Patient) FHIR_CONTEXT.newJsonParser().parseResource(Files.readString(output));

        assertEquals("patient-1", patient.getIdElement().getIdPart());
        assertEquals("23707015357", patient.getIdentifierFirstRep().getValue());
        assertEquals("Ask <Ebeger & Co>", patient.getNameFirstRep().getText());
        assertEquals("1970-05-23", patient.getBirthDateElement().getValueAsString());
        assertEquals(AdministrativeGender.MALE, patient.getGender());
        assertEquals("http://hl7.eu/fhir/eps/StructureDefinition/patient-eu-eps",
                patient.getMeta().getProfile().get(0).getValue());
        assertEquals(true, patient.getText().getDivAsString().contains("Ask &lt;Ebeger &amp; Co&gt;"));
    }

    @Test
    void rejectsIndexOutsidePersonsArray() throws Exception {
        Path input = writeInput("""
                [{"persons":[{"nin":"23707015357","name":"Ask Ebeger",
                "birthDate":"1970-05-23T00:00:00+00:00"}]}]
                """);

        assertThrows(IllegalArgumentException.class,
                () -> converter().convert(input, 1, temporaryDirectory.resolve("patient-1")));
    }

    private SyntPopPatientConverter converter() {
        return new SyntPopPatientConverter(new FhirHelper(FHIR_CONTEXT));
    }

    private Path writeInput(String json) throws Exception {
        Path input = temporaryDirectory.resolve("syntpop.json");
        Files.writeString(input, json);
        return input;
    }
}
