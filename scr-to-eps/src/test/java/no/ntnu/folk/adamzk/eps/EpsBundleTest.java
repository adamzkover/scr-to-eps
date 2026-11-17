package no.ntnu.folk.adamzk.eps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ca.uhn.fhir.context.FhirContext;
import no.ntnu.folk.adamzk.scr.M1Parser;
import no.ntnu.folk.adamzk.scr.ScrAllergyIntoleranceConverter;
import no.ntnu.folk.adamzk.scr.ScrCriticalMedicalConditionConverter;
import no.ntnu.folk.adamzk.scr.ScrProfiles;
import no.ntnu.folk.adamzk.scr.ScrProcedureConverter;

class EpsBundleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void appendsConditionNarrativeToProblemListSection() throws Exception {
        FhirContext fhirContext = FhirContext.forR4();
        Path inputDirectory = Files.createDirectories(temporaryDirectory.resolve("input-scr"));

        Patient patient = new Patient();
        patient.setId("patient-1");
        writeResource(fhirContext, patient, temporaryDirectory.resolve("Patient.json"));

        Condition condition = new Condition();
        condition.setId(UUID.randomUUID().toString());
        condition.getMeta().addProfile(ScrProfiles.CRITICAL_MEDICAL_CONDITION_PROFILE);
        condition.getCode().setText("Example condition");
        condition.getText()
                .setStatus(NarrativeStatus.GENERATED)
                .setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\">Old source narrative</div>");
        condition.addNote().setText("Follow-up & monitoring");
        writeResource(fhirContext, condition, inputDirectory.resolve("condition.json"));

        Bundle bundle = EpsBundle.newInstance(
                fhirContext,
                DocumentBuilderFactory.newInstance(),
                new M1Parser(),
                new ScrAllergyIntoleranceConverter(),
                new ScrCriticalMedicalConditionConverter(),
                new ScrProcedureConverter())
                .createBundle()
                .withComposition(EpsComposition.newInstance().createComposition().addEmptySections().toResource())
                .withPatient(temporaryDirectory)
                .populateFrom(inputDirectory)
                .toResource();

        Composition composition = (Composition) bundle.getEntryFirstRep().getResource();
        Composition.SectionComponent problemList = composition.getSection().stream()
                .filter(section -> section.getCode().getCodingFirstRep().getCode().equals(EpsSection.PROBLEM_LIST.getCode()))
                .findFirst()
                .orElseThrow();

        assertFalse(problemList.hasEmptyReason());
        assertEquals(NarrativeStatus.GENERATED, problemList.getText().getStatus());
        assertTrue(problemList.getText().getDivAsString().contains("Example condition"));
        assertTrue(problemList.getText().getDivAsString().contains("Follow-up &amp; monitoring"));
        assertFalse(problemList.getText().getDivAsString().contains("Old source narrative"));
    }

    private void writeResource(FhirContext fhirContext, org.hl7.fhir.r4.model.Resource resource, Path path)
            throws Exception {
        Files.writeString(path, fhirContext.newJsonParser().encodeResourceToString(resource));
    }
}
