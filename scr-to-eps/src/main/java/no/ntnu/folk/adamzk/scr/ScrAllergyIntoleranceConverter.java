package no.ntnu.folk.adamzk.scr;

import java.util.List;
import java.util.Objects;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.AllergyIntolerance.AllergyIntoleranceReactionComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.utilities.Utilities;
import org.springframework.stereotype.Component;

import no.ntnu.folk.adamzk.eps.EpsProfiles;
import no.ntnu.folk.adamzk.terminology.TerminologyService;

/**
 * Converts SCR AllergyIntolerance resources to the EPS profile.
 */
@Component
public class ScrAllergyIntoleranceConverter {

    public AllergyIntolerance parse(AllergyIntolerance scrAllergyIntolerance) {

        AllergyIntolerance epsAllergyIntolerance = new AllergyIntolerance();
        epsAllergyIntolerance.getMeta().addProfile(EpsProfiles.EPS_ALLERGY_INTOLERANCE_PROFILE);

        // Identifiers can be transferred unchanged
        epsAllergyIntolerance.setIdentifier(List.copyOf(scrAllergyIntolerance.getIdentifier()));

        // clinicalStatus can be transferred unchanged
        epsAllergyIntolerance.setClinicalStatus(scrAllergyIntolerance.getClinicalStatus());

        // verificationStatus can be transferred unchanged
        epsAllergyIntolerance.setVerificationStatus(scrAllergyIntolerance.getVerificationStatus());

        // type can be transferred unchanged
        epsAllergyIntolerance.setType(scrAllergyIntolerance.getType());

        // category can be transferred unchanged
        epsAllergyIntolerance.setCategory(scrAllergyIntolerance.getCategory());

        // criticality can be transferred unchanged
        epsAllergyIntolerance.setCriticality(scrAllergyIntolerance.getCriticality());

        // code has to be mapped to international codes
        epsAllergyIntolerance.setCode(new CodeableConcept().setCoding(
                scrAllergyIntolerance.getCode().getCoding().stream()
                        .map(coding -> TerminologyService.translate(coding.getSystem(), coding.getCode()))
                        .filter(Objects::nonNull)
                        .toList()
        ));

        // TODO code.extension:inactiveIngredient

        // patient is handled elsewhere

        // encounter can be transferred unchanged
        // TODO Handle reference target
        epsAllergyIntolerance.setEncounter(scrAllergyIntolerance.getEncounter());

        // onset can be transferred unchanged
        epsAllergyIntolerance.setOnset(scrAllergyIntolerance.getOnset());

        // recordedDate can be transferred unchanged
        epsAllergyIntolerance.setRecordedDate(scrAllergyIntolerance.getRecordedDate());

        // recorder can be transferred unchanged
        // TODO Handle reference target
        // epsAllergyIntolerance.setRecorder(scrAllergyIntolerance.getRecorder());

        // asserter can be transferred unchanged
        // TODO Handle reference target
        epsAllergyIntolerance.setAsserter(scrAllergyIntolerance.getAsserter());

        // lastOccurrence can be transferred unchanged
        epsAllergyIntolerance.setLastOccurrence(scrAllergyIntolerance.getLastOccurrence());

        // note can be transferred unchanged
        // The text should be translated to English
        epsAllergyIntolerance.setNote(scrAllergyIntolerance.getNote());

        // reaction is a complex element
        if (scrAllergyIntolerance.hasReaction()) {
            // SCR has exactly 1 reaction
            AllergyIntoleranceReactionComponent scrReaction = scrAllergyIntolerance.getReactionFirstRep();
            AllergyIntoleranceReactionComponent epsReaction = epsAllergyIntolerance.addReaction();
            // reaction.substance can be transferred unchanged
            epsReaction.setSubstance(scrReaction.getSubstance());
            // reaction.manifestation has to be translated to international codes
            // SCR has exactly 1 manifestation
            CodeableConcept scrManifestation = scrReaction.getManifestationFirstRep();
            CodeableConcept epsManifestation = epsReaction.addManifestation();
            scrManifestation.getCoding().stream()
                    .map(coding -> TerminologyService.translate(coding.getSystem(), coding.getCode()))
                    .filter(Objects::nonNull)
                    .forEach(epsManifestation::addCoding);
            // reaction.description can be transferred unchanged
            epsReaction.setDescription(scrReaction.getDescription());
            // reaction.onset can be transferred unchanged
            epsReaction.setOnset(scrReaction.getOnset());
            // reaction.severity can be transferred unchanged
            epsReaction.setSeverity(scrReaction.getSeverity());
            // reaction.exposureRoute can be transferred unchanged
            epsReaction.setExposureRoute(scrReaction.getExposureRoute());
            // reaction.note can be transferred unchanged
            // The text should be translated to English
            epsReaction.setNote(scrReaction.getNote());
        }

        generateNarrative(epsAllergyIntolerance);

        return epsAllergyIntolerance;

    }

    private void generateNarrative(AllergyIntolerance epsAllergyIntolerance) {
        StringBuilder narrative = new StringBuilder("<div xmlns=\"http://www.w3.org/1999/xhtml\">");
        appendCodeableConcept(narrative, "Clinical status", epsAllergyIntolerance.getClinicalStatus());
        appendCodeableConcept(narrative, "Verification status", epsAllergyIntolerance.getVerificationStatus());

        if (epsAllergyIntolerance.hasType()) {
            appendCode(narrative, "Type", epsAllergyIntolerance.getType().toCode());
        }
        if (epsAllergyIntolerance.hasCategory()) {
            String categories = epsAllergyIntolerance.getCategory().stream()
                    .map(Enumeration::getValue)
                    .map(category -> category.toCode())
                    .map(this::formatCode)
                    .collect(java.util.stream.Collectors.joining(", "));
            appendParagraph(narrative, "Category", categories);
        }
        if (epsAllergyIntolerance.hasCriticality()) {
            appendCode(narrative, "Criticality", epsAllergyIntolerance.getCriticality().toCode());
        }

        appendCodeableConcept(narrative, "Allergen", epsAllergyIntolerance.getCode());
        if (epsAllergyIntolerance.hasOnset()) {
            appendParagraph(narrative, "Onset", formatType(epsAllergyIntolerance.getOnset()));
        }

        for (AllergyIntoleranceReactionComponent reaction : epsAllergyIntolerance.getReaction()) {
            String description = reaction.hasDescription() ? reaction.getDescription() : null;
            String substance = formatCodeableConcept(reaction.getSubstance());
            appendParagraph(narrative, "Reaction", joinValues(description, substance));
            for (CodeableConcept manifestation : reaction.getManifestation()) {
                appendCodeableConcept(narrative, "Manifestation", manifestation);
            }
            if (reaction.hasOnset()) {
                appendParagraph(narrative, "Reaction onset",
                        new DateTimeType(reaction.getOnset()).getValueAsString());
            }
            if (reaction.hasSeverity()) {
                appendCode(narrative, "Reaction severity", reaction.getSeverity().toCode());
            }
            appendCodeableConcept(narrative, "Exposure route", reaction.getExposureRoute());
        }

        narrative.append("</div>");
        epsAllergyIntolerance.getText()
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
                .map(Coding::getCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
        if (text != null && !text.isBlank()) {
            return codes.isEmpty() ? text : text + " (" + codes + ")";
        }

        return concept.getCoding().stream()
                .map(this::formatCoding)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String formatCoding(Coding coding) {
        String code = coding.hasCode() ? coding.getCode() : null;
        String display = coding.hasDisplay() ? coding.getDisplay().trim() : null;
        if (display == null || display.isBlank()) {
            display = code == null ? null : humanize(code);
        }
        return formatValue(display, code);
    }

    private void appendCode(StringBuilder narrative, String label, String code) {
        appendParagraph(narrative, label, formatCode(code));
    }

    private String formatCode(String code) {
        return formatValue(humanize(code), code);
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

}
