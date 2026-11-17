package no.ntnu.folk.adamzk.scr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import no.ntnu.folk.adamzk.terminology.TerminologyService;

class ScrProcedureConverterTest {

    @Test
    void generatesNarrativeFromTransferredProcedureData() {
        Procedure source = new Procedure();
        source.setStatus(Procedure.ProcedureStatus.INPROGRESS);
        source.getCategory().addCoding()
                .setSystem(TerminologyService.SCR_SUBCATEGORIES_OF_CRITICAL_INFORMATION_SYSTEM)
                .setCode("6")
                .setDisplay("Behandling");
        source.getCode().addCoding()
                .setSystem(TerminologyService.SCR_PROCEDURE_CODE_SYSTEM)
                .setCode("6")
                .setDisplay("LAR (Legemiddelassistert rehabilitering)");
        source.setPerformed(new DateTimeType("2021-03-09T00:00:00+01:00"));
        source.addExtension()
                .setUrl(ScrProfiles.EXT_PROCEDURE_RECORDED_DATE)
                .setValue(new DateTimeType("2021-03-14T12:43:58+01:00"));
        source.addExtension()
                .setUrl("http://example.org/untransferred")
                .setValue(new DateTimeType("2021-03-14"));
        source.setRecorder(new Reference("PractitionerRole/recorderRef"));
        source.addNote().setText("Daglig dose fra apotek");

        Procedure converted = new ScrProcedureConverter().convert(source);

        assertEquals(NarrativeStatus.GENERATED, converted.getText().getStatus());
        String narrative = converted.getText().getDivAsString();
        assertTrue(narrative.contains("Status:</strong> In progress (in-progress)"), narrative);
        assertTrue(narrative.contains("Category:</strong> Procedure (71388002)"), narrative);
        assertTrue(narrative.contains("Procedure:</strong> Drug addiction therapy (266707007)"), narrative);
        assertTrue(narrative.contains("Performed:</strong> 2021-03-09T00:00:00+01:00"), narrative);
        assertTrue(narrative.contains("Recorded date:</strong> 2021-03-14T12:43:58+01:00"), narrative);
        assertTrue(narrative.contains("Daglig dose fra apotek"), narrative);
        assertFalse(narrative.contains("recorderRef"), narrative);
        assertFalse(narrative.contains("2021-03-14</p>"), narrative);
    }
}
