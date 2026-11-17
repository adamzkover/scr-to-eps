package no.ntnu.folk.adamzk.scr;
import java.util.Objects;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.utilities.Utilities;
import org.springframework.stereotype.Component;

import no.ntnu.folk.adamzk.eps.EpsProfiles;
import no.ntnu.folk.adamzk.terminology.TerminologyService;

@Component
public class ScrProcedureConverter {

    public Procedure convert(Procedure scrProcedure) {

        Procedure epsProcedure = new Procedure();
        epsProcedure.getMeta().addProfile(EpsProfiles.PROCEDURE_PROFILE);

        // The recordedDate extension has a corresponding Extension in EPS.
        Extension scrRecordedDateExtension = scrProcedure.getExtensionByUrl(ScrProfiles.EXT_PROCEDURE_RECORDED_DATE);
        if (scrRecordedDateExtension != null) {
            epsProcedure.addExtension()
                    .setUrl(EpsProfiles.EXT_PROCEDURE_RECORDED_DATE)
                    .setValue(scrRecordedDateExtension.getValue());
        }

        // identifier can be transferred unchanged
        epsProcedure.setIdentifier(scrProcedure.getIdentifier());

        // instantiatesCanonical can be transferred unchanged
        epsProcedure.setInstantiatesCanonical(scrProcedure.getInstantiatesCanonical());

        // instantiatesUri can be transferred unchanged
        epsProcedure.setInstantiatesUri(scrProcedure.getInstantiatesUri());

        // basedOn can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setBasedOn(scrProcedure.getBasedOn());

        // partOf can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setPartOf(scrProcedure.getPartOf());

        // status can be transferred unchanged
        epsProcedure.setStatus(scrProcedure.getStatus());

        // statusReason can be transferred unchanged
        epsProcedure.setStatusReason(scrProcedure.getStatusReason());

        // category needs terminology mapping
        // category contains one fixed Coding: http://nhn.no/kj/fhir/CodeSystem/SubcategoriesOfCriticalInformation|6 (Behandling)
        Coding scrProcedureCategory = scrProcedure.getCategory().getCoding().stream()
                .filter(coding -> TerminologyService.SCR_SUBCATEGORIES_OF_CRITICAL_INFORMATION_SYSTEM.equals(coding.getSystem())
                        && "6".equals(coding.getCode()))
                .findFirst().orElse(null);
        if (scrProcedureCategory != null) {
            epsProcedure.getCategory().addCoding()
                    .setSystem(TerminologyService.SNOMED_CT_SYSTEM)
                    .setCode("71388002")
                    .setDisplay("Procedure");
        }

        // code needs terminology mapping
        epsProcedure.setCode(new CodeableConcept().setCoding(
                scrProcedure.getCode().getCoding().stream()
                        .map(coding -> TerminologyService.translate(coding.getSystem(), coding.getCode()))
                        .filter(Objects::nonNull)
                        .toList()
        ));

        // subject can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setSubject(scrProcedure.getSubject());

        // encounter can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setEncounter(scrProcedure.getEncounter());

        // performed can be transferred unchanged
        // TODO EPS requires this optional SCR element
        epsProcedure.setPerformed(scrProcedure.getPerformed());

        // recorder can be transferred unchanged
        // TODO Handle reference targets
        // epsProcedure.setRecorder(scrProcedure.getRecorder());

        // asserter can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setAsserter(scrProcedure.getAsserter());

        // performer can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setPerformer(scrProcedure.getPerformer());

        // location can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setLocation(scrProcedure.getLocation());

        // reasonCode can be transferred unchanged
        epsProcedure.setReasonCode(scrProcedure.getReasonCode());

        // reasonReference can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setReasonReference(scrProcedure.getReasonReference());

        // bodySite needs terminology mapping
        // Neither the SCR profile nor the example indicate the source terminology
        epsProcedure.setBodySite(scrProcedure.getBodySite());

        // outcome can be transferred unchanged
        epsProcedure.setOutcome(scrProcedure.getOutcome());

        // report can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setReport(scrProcedure.getReport());

        // complication can be transferred unchanged
        epsProcedure.setComplication(scrProcedure.getComplication());

        // complicationDetail can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setComplicationDetail(scrProcedure.getComplicationDetail());

        // followUp can be transferred unchanged
        epsProcedure.setFollowUp(scrProcedure.getFollowUp());

        // note can be transferred unchanged
        epsProcedure.setNote(scrProcedure.getNote());

        // focalDevice can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setFocalDevice(scrProcedure.getFocalDevice());

        // usedReference can be transferred unchanged
        // TODO Handle reference targets
        epsProcedure.setUsedReference(scrProcedure.getUsedReference());

        // usedCode can be transferred unchanged
        epsProcedure.setUsedCode(scrProcedure.getUsedCode());

        generateNarrative(epsProcedure);

        return epsProcedure;

    }

    private void generateNarrative(Procedure epsProcedure) {
        StringBuilder narrative = new StringBuilder("<div xmlns=\"http://www.w3.org/1999/xhtml\">");
        if (epsProcedure.hasStatus()) {
            appendParagraph(narrative, "Status", formatCode(epsProcedure.getStatus().toCode()));
        }
        appendCodeableConcept(narrative, "Category", epsProcedure.getCategory());
        appendCodeableConcept(narrative, "Procedure", epsProcedure.getCode());
        if (epsProcedure.hasPerformed()) {
            appendParagraph(narrative, "Performed", formatType(epsProcedure.getPerformed()));
        }

        Extension recordedDate = epsProcedure.getExtensionByUrl(EpsProfiles.EXT_PROCEDURE_RECORDED_DATE);
        if (recordedDate != null && recordedDate.hasValue()) {
            appendParagraph(narrative, "Recorded date", formatType(recordedDate.getValue()));
        }
        for (var note : epsProcedure.getNote()) {
            appendParagraph(narrative, "Note", note.getText());
        }

        narrative.append("</div>");
        epsProcedure.getText()
                .setStatus(NarrativeStatus.GENERATED)
                .setDivAsString(narrative.toString());
    }

    private void appendCodeableConcept(StringBuilder narrative, String label, CodeableConcept concept) {
        if (concept == null || (!concept.hasText() && !concept.hasCoding())) {
            return;
        }

        String codes = concept.getCoding().stream()
                .map(this::formatCoding)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
        String text = concept.hasText() ? concept.getText().trim() : null;
        String value = text == null || text.isBlank()
                ? (codes.isBlank() ? null : codes)
                : (codes.isBlank() ? text : text + " (" + codes + ")");
        appendParagraph(narrative, label, value);
    }

    private String formatCoding(Coding coding) {
        String code = coding.hasCode() ? coding.getCode() : null;
        String display = coding.hasDisplay() ? coding.getDisplay().trim() : null;
        if (display == null || display.isBlank()) {
            display = code == null ? null : humanize(code);
        }
        if (display == null || display.isBlank()) {
            return code == null || code.isBlank() ? null : "(" + code + ")";
        }
        return code == null || code.isBlank() ? display : display + " (" + code + ")";
    }

    private String formatCode(String code) {
        String display = humanize(code);
        return display == null ? null : display + " (" + code + ")";
    }

    private String humanize(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String readable = code.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ');
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    private String formatType(Type value) {
        if (value instanceof PrimitiveType<?> primitive) {
            return primitive.getValueAsString();
        }
        if (value instanceof Quantity quantity) {
            String amount = quantity.hasValue() ? quantity.getValue().stripTrailingZeros().toPlainString() : null;
            String unit = quantity.hasUnit() ? quantity.getUnit() : null;
            return joinValues(amount, unit);
        }
        if (value instanceof Period period) {
            return joinValues(
                    period.hasStart() ? period.getStartElement().getValueAsString() : null,
                    period.hasEnd() ? "to " + period.getEndElement().getValueAsString() : null);
        }
        if (value instanceof Range range) {
            return joinValues(
                    range.hasLow() ? formatType(range.getLow()) : null,
                    range.hasHigh() ? "to " + formatType(range.getHigh()) : null);
        }
        return value.fhirType();
    }

    private String joinValues(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null || second.isBlank() ? null : second;
        }
        return second == null || second.isBlank() ? first : first + "; " + second;
    }

    private void appendParagraph(StringBuilder narrative, String label, String value) {
        if (value != null && !value.isBlank()) {
            narrative.append("<p><strong>")
                    .append(Utilities.escapeXml(label))
                    .append(":</strong> ")
                    .append(Utilities.escapeXml(value))
                    .append("</p>");
        }
    }

}
