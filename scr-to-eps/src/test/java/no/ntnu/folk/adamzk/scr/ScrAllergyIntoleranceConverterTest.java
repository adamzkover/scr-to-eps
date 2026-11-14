package no.ntnu.folk.adamzk.scr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.junit.jupiter.api.Test;

class ScrAllergyIntoleranceConverterTest {

    @Test
    void generatesNarrativeFromStructuredAllergyData() {
        AllergyIntolerance source = new AllergyIntolerance();
        CodeableConcept clinicalStatus = new CodeableConcept();
        clinicalStatus.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical")
                .setCode("active");
        source.setClinicalStatus(clinicalStatus);
        CodeableConcept verificationStatus = new CodeableConcept();
        verificationStatus.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/allergyintolerance-verification")
                .setCode("confirmed");
        source.setVerificationStatus(verificationStatus);
        source.setType(AllergyIntolerance.AllergyIntoleranceType.ALLERGY);
        source.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION);
        source.setCriticality(AllergyIntolerance.AllergyIntoleranceCriticality.HIGH);
        source.getCode().addCoding()
                .setSystem("http://nhn.no/kj/fhir/CodeSystem/ATC")
                .setCode("J01AA10");
        source.setOnset(new DateTimeType("2020-05-04"));
        source.addReaction()
                .setDescription("Hives & itching")
                .addManifestation().addCoding()
                .setSystem("http://nhn.no/kj/fhir/CodeSystem/TypeOfReaction")
                .setCode("1");

        AllergyIntolerance converted = new ScrAllergyIntoleranceConverter().parse(source);

        assertEquals(NarrativeStatus.GENERATED, converted.getText().getStatus());
        String narrative = converted.getText().getDivAsString();
        assertTrue(narrative.contains("Clinical status:</strong> Active (active)"), narrative);
        assertTrue(narrative.contains("Verification status:</strong> Confirmed (confirmed)"));
        assertTrue(narrative.contains("Type:</strong> Allergy (allergy)"));
        assertTrue(narrative.contains("Category:</strong> Medication (medication)"));
        assertTrue(narrative.contains("Criticality:</strong> High (high)"));
        assertTrue(narrative.contains("Allergen:</strong> Penimepicycline (J01AA10)"));
        assertTrue(narrative.contains("Onset:</strong> 2020-05-04"));
        assertTrue(narrative.contains("Reaction:</strong> Hives &amp; itching"));
        assertTrue(narrative.contains("Manifestation:</strong> Anaphylaxis (39579001)"));
    }
}
