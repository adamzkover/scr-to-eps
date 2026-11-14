package no.ntnu.folk.adamzk.scr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

import no.ntnu.folk.adamzk.eps.EpsProfiles;
import no.ntnu.folk.adamzk.terminology.TerminologyService;

import static org.joox.JOOX.$;

class M1ParserTest {

    @Test
    void includesAtcCodeInMedicationNarrative() throws Exception {
        Medication medication = parseMedication("<Atc V=\"A01AA01\"/>");
        assertTrue(medication.getText().getDivAsString().contains("ATC code: A01AA01"));
    }

    @Test
    void missingClassificationProducesUnknownInNarrative() throws Exception {
        Medication medication = parseMedication("");
        assertTrue(medication.getText().getDivAsString().contains("ATC code: Unknown"));
        assertTrue(medication.getText().getDivAsString().contains("SNOMED CT code: Unknown"));
    }

    @Test
    void includesDrugSnomedCodeInMedicationNarrative() throws Exception {
        List<Resource> resources = parseResources("<Varenr>090031</Varenr>");
        MedicationStatement statement = (MedicationStatement) resources.get(0);
        Medication medication = (Medication) resources.get(1);
        String paragraph = "<p>SNOMED CT code: 322827001 "
                + "(Naratriptan (as naratriptan hydrochloride) 2.5 mg oral tablet)</p>";
        assertTrue(medication.getText().getDivAsString().contains(paragraph));
        assertTrue(statement.getText().getDivAsString().contains(paragraph));
    }

    @Test
    void missingDrugSnomedCodingOrCodeProducesUnknown() {
        Medication medication = new Medication();
        medication.getForm().addCoding().setSystem(TerminologyService.SNOMED_CT_SYSTEM).setCode("421026006");
        assertEquals("Unknown", M1Parser.getSnomedCtCode(medication));

        medication.getCode().addCoding().setSystem("other-system").setCode("other");
        medication.getCode().addCoding().setCode("no-system");
        medication.getCode().addCoding().setSystem(TerminologyService.SNOMED_CT_SYSTEM);
        assertEquals("Unknown", M1Parser.getSnomedCtCode(medication));

        medication.getCode().addCoding().setSystem(TerminologyService.SNOMED_CT_SYSTEM).setCode("322827001");
        assertEquals("322827001", M1Parser.getSnomedCtCode(medication));
    }

    @Test
    void includesSnomedDisplayOnlyWhenNotBlank() {
        Medication medication = new Medication();
        var coding = medication.getCode().addCoding()
                .setSystem(TerminologyService.SNOMED_CT_SYSTEM).setCode("322827001");
        coding.setDisplay(" ");
        assertEquals("322827001", M1Parser.getSnomedCtCode(medication));
        coding.setDisplay("Drug & product");
        assertEquals("322827001 (Drug & product)", M1Parser.getSnomedCtCode(medication));
    }

    @Test
    void missingAtcCodingOrCodeProducesUnknown() {
        Medication medication = new Medication();
        CodeableConcept concept = new CodeableConcept();
        concept.addCoding().setSystem("other-system").setCode("other");
        concept.addCoding().setCode("no-system");
        medication.addExtension().setUrl(EpsProfiles.EXT_MEDICATION_CLASSIFICATION).setValue(concept);
        assertEquals("Unknown", M1Parser.getAtcCode(medication));

        concept.addCoding().setSystem(TerminologyService.ATC_SYSTEM);
        assertEquals("Unknown", M1Parser.getAtcCode(medication));
        concept.addCoding().setSystem(TerminologyService.ATC_SYSTEM).setCode("B02AB01").setDisplay("ATC display");
        assertEquals("B02AB01", M1Parser.getAtcCode(medication));
    }

    @Test
    void missingExtensionValueProducesUnknown() {
        Medication medication = new Medication();
        medication.addExtension().setUrl(EpsProfiles.EXT_MEDICATION_CLASSIFICATION);
        assertEquals("Unknown", M1Parser.getAtcCode(medication));
    }

    @Test
    void wrongExtensionValueTypeFailsExplicitly() {
        Medication medication = new Medication();
        medication.addExtension().setUrl(EpsProfiles.EXT_MEDICATION_CLASSIFICATION)
                .setValue(new StringType("not a classification"));
        assertThrows(IllegalStateException.class, () -> M1Parser.getAtcCode(medication));
    }

    private Medication parseMedication(String packageContent) throws Exception {
        return (Medication) parseResources(packageContent).get(1);
    }

    private List<Resource> parseResources(String packageContent) throws Exception {
        String xml = "<Resept><Forskrivning><Legemiddelpakning>" + packageContent
                + "</Legemiddelpakning></Forskrivning></Resept>";
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return new M1Parser().parse($(document));
    }
}
