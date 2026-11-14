package no.ntnu.folk.adamzk.eps;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;

public final class EpsComposition {

    private static final String LOINC_SYSTEM = "http://loinc.org";
    private static final String EMPTY_REASON_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/list-empty-reason";

    private Composition composition;

    public static EpsComposition newInstance() {
        return new EpsComposition();
    }

    public Composition toResource() {
        return composition;
    }

    public EpsComposition createComposition() {

        Date createdAt = Date.from(Instant.now());
        String compositionId = UUID.randomUUID().toString();

        composition = new Composition();
        composition.setId(compositionId);
        composition.getMeta().addProfile(EpsProfiles.COMPOSITION_PROFILE);
        composition.getText().setStatus(NarrativeStatus.GENERATED).setDivAsString("EPS Composition");

        composition.getIdentifier()
                .setSystem("urn:ietf:rfc:3986")
                .setValue("urn:uuid:" + compositionId);

        composition.getType().addCoding()
                .setSystem(LOINC_SYSTEM)
                .setCode("60591-5")
                .setDisplay("Patient summary Document");

        composition.setStatus(Composition.CompositionStatus.FINAL)
                .setDate(createdAt)
                .setTitle("European Patient Summary");

        composition.addAuthor().setDisplay("SCR-to-EPS");

        return this;

    }

    public EpsComposition addEmptySections() {

        Stream.of(EpsSection.values())
                .sorted(Comparator.comparingInt(section -> section.getSequenceNumber()))
                .map(EpsComposition::createEmptySection)
                .forEach(composition::addSection);

        return this;

    }

    private static Composition.SectionComponent createEmptySection(EpsSection section) {

        Narrative narrative = new Narrative();
        narrative.setStatus(NarrativeStatus.GENERATED);
        narrative.setDivAsString(
                "<div xmlns=\"http://www.w3.org/1999/xhtml\">This section is empty.</div>");

        return new Composition.SectionComponent()
                .setTitle(section.getTitle())
                .setCode(new CodeableConcept().addCoding(new Coding()
                        .setSystem(LOINC_SYSTEM)
                        .setCode(section.getCode())
                        .setDisplay(section.getCodeTerm())))
                .setText(narrative)
                .setEmptyReason(new CodeableConcept().addCoding(new Coding()
                        .setSystem(EMPTY_REASON_SYSTEM)
                        .setCode("notstarted")
                        .setDisplay("Not Started")));

    }

}
