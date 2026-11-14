package no.ntnu.folk.adamzk.scr;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.utilities.Utilities;
import org.springframework.stereotype.Component;

import no.ntnu.folk.adamzk.eps.EpsProfiles;
import no.ntnu.folk.adamzk.terminology.TerminologyService;

/**
 * Converts SCR Condition resources to the EPS profile.
 */
@Component
public class ScrCriticalMedicalConditionConverter {

    public Condition parse(Condition scrCondition) {

        Condition epsCondition = new Condition();
        epsCondition.getMeta().addProfile(EpsProfiles.CONDITION_PROFILE);

        // Identifiers can be transferred unchanged
        epsCondition.setIdentifier(scrCondition.getIdentifier());

        // clinicalStatus can be transferred unchanged
        epsCondition.setClinicalStatus(scrCondition.getClinicalStatus());

        // verificationStatus can be transferred unchanged
        epsCondition.setVerificationStatus(scrCondition.getVerificationStatus());

        // category is expected to be code 5 from 7651
        // Code problem-list-item from http://terminology.hl7.org/CodeSystem/condition-category should be added
        epsCondition.setCategory(scrCondition.getCategory());
        epsCondition.addCategory()
            .addCoding()
            .setSystem("http://terminology.hl7.org/CodeSystem/condition-category")
            .setCode("problem-list-item");

        // severity can be transferred unchanged
        epsCondition.setSeverity(scrCondition.getSeverity());

        // code needs terminology mapping
        if (scrCondition.getCode().hasText()) {
            epsCondition.getCode().setText(scrCondition.getCode().getText());
        } else {
            CodeableConcept epsCode = new CodeableConcept();
            for (var coding : scrCondition.getCode().getCoding()) {
                // For ICD-10 codes, set the standard system and transfer the code
                if (TerminologyService.SCR_ICD_10_SYSTEM.equals(coding.getSystem())) {
                    epsCode.addCoding()
                        .setSystem(TerminologyService.ICD_10_SYSTEM)
                        .setCode(coding.getCode());
                } else if (TerminologyService.SCR_ABS_LIST_SYSTEM.equals(coding.getSystem())) {
                    epsCode.addCoding(TerminologyService.translate(coding.getSystem(), coding.getCode()));
                }
            }
            epsCondition.setCode(epsCode);
        }

        // bodySite can be transferred unchanged
        epsCondition.setBodySite(scrCondition.getBodySite());

        // subject (Patient) is handled elsewhere

        // encounter can be transferred unchanged
        // TODO Handle reference target
        epsCondition.setEncounter(scrCondition.getEncounter());

        // onset can be transferred unchanged
        epsCondition.setOnset(scrCondition.getOnset());

        // abatement can be transferred unchanged
        epsCondition.setAbatement(scrCondition.getAbatement());

        // recordedDate can be transferred unchanged
        epsCondition.setRecordedDate(scrCondition.getRecordedDate());

        // recorder can be transferred unchanged
        // TODO Handle reference target
        // epsCondition.setRecorder(scrCondition.getRecorder());

        // asserter can be transferred unchanged
        // TODO Handle reference target
        epsCondition.setAsserter(scrCondition.getAsserter());

        // stage can be transferred unchanged
        epsCondition.setStage(scrCondition.getStage());

        // evidence needs terminology mapping
        // TODO
        for (var evidence : scrCondition.getEvidence()) {
            var epsEvidence = epsCondition.addEvidence();
            epsEvidence.setCode(evidence.getCode());
            epsEvidence.setDetail(evidence.getDetail());
        }

        // note can be transferred unchanged
        epsCondition.setNote(scrCondition.getNote());

        generateNarrative(epsCondition);

        return epsCondition;

    }

    private void generateNarrative(Condition condition) {
        StringBuilder narrative = new StringBuilder("<div xmlns=\"http://www.w3.org/1999/xhtml\">");
        appendCodeableConcept(narrative, "Clinical status", condition.getClinicalStatus());
        appendCodeableConcept(narrative, "Verification status", condition.getVerificationStatus());

        for (CodeableConcept category : condition.getCategory()) {
            appendCodeableConcept(narrative, "Category", category);
        }
        appendCodeableConcept(narrative, "Severity", condition.getSeverity());
        appendCodeableConcept(narrative, "Condition", condition.getCode());

        for (CodeableConcept bodySite : condition.getBodySite()) {
            appendCodeableConcept(narrative, "Body site", bodySite);
        }
        if (condition.hasOnset()) {
            appendParagraph(narrative, "Onset", formatType(condition.getOnset()));
        }
        if (condition.hasAbatement()) {
            appendParagraph(narrative, "Abatement", formatType(condition.getAbatement()));
        }
        if (condition.hasRecordedDate()) {
            appendParagraph(narrative, "Recorded date", condition.getRecordedDateElement().getValueAsString());
        }

        for (var stage : condition.getStage()) {
            appendCodeableConcept(narrative, "Stage summary", stage.getSummary());
            appendCodeableConcept(narrative, "Stage type", stage.getType());
            for (Reference assessment : stage.getAssessment()) {
                appendReference(narrative, "Stage assessment", assessment);
            }
        }
        for (var evidence : condition.getEvidence()) {
            for (CodeableConcept code : evidence.getCode()) {
                appendCodeableConcept(narrative, "Evidence", code);
            }
            for (var detail : evidence.getDetail()) {
                appendReference(narrative, "Evidence detail", detail);
            }
        }
        for (var note : condition.getNote()) {
            appendParagraph(narrative, "Note", note.getText());
        }

        narrative.append("</div>");
        condition.getText()
                .setStatus(NarrativeStatus.GENERATED)
                .setDivAsString(narrative.toString());
    }

    private void appendCodeableConcept(StringBuilder narrative, String label, CodeableConcept concept) {
        String value = formatCodeableConcept(concept);
        if (value != null) {
            appendParagraph(narrative, label, value);
        }
    }

    private String formatCodeableConcept(CodeableConcept concept) {
        if (concept == null || (!concept.hasText() && !concept.hasCoding())) {
            return null;
        }

        String text = concept.hasText() ? concept.getText().trim() : null;
        String codes = concept.getCoding().stream()
                .map(this::formatCoding)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
        if (text != null && !text.isBlank()) {
            return codes.isEmpty() ? text : text + " (" + codes + ")";
        }
        return codes.isEmpty() ? null : codes;
    }

    private String formatCoding(Coding coding) {
        String code = coding.hasCode() ? coding.getCode() : null;
        String display = coding.hasDisplay() ? coding.getDisplay().trim() : null;
        if (display == null || display.isBlank()) {
            display = code == null ? null : humanize(code);
        }
        return formatValue(display, code);
    }

    private String formatValue(String display, String code) {
        if (display == null || display.isBlank()) {
            return code == null || code.isBlank() ? null : "(" + code + ")";
        }
        if (code == null || code.isBlank()) {
            return display;
        }
        return display + " (" + code + ")";
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

    private void appendReference(StringBuilder narrative, String label, Reference reference) {
        String value = reference.hasDisplay() ? reference.getDisplay()
                : reference.hasReference() ? reference.getReference() : null;
        appendParagraph(narrative, label, value);
    }

}
