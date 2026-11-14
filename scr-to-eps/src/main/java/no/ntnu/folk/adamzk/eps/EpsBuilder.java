package no.ntnu.folk.adamzk.eps;

import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import no.ntnu.folk.adamzk.FhirHelper;
import no.ntnu.folk.adamzk.scr.M1Parser;
import no.ntnu.folk.adamzk.scr.ScrAllergyIntoleranceConverter;
import no.ntnu.folk.adamzk.scr.ScrCriticalMedicalConditionConverter;

@Component
public class EpsBuilder {

    private static final Logger logger = LoggerFactory.getLogger(EpsBuilder.class);

    private static final String INPUT_DIRECTORY = "input-scr";
    private static final String OUTPUT_DIRECTORY = "output-eps";
    private static final String OUTPUT_FILE = "eps.json";

    private final FhirContext fhirContext;
    private final FhirHelper fhirHelper;
    private final DocumentBuilderFactory documentBuilderFactory;

    private final M1Parser m1Parser;
    private final ScrAllergyIntoleranceConverter scrAllergyIntoleranceConverter;
    private final ScrCriticalMedicalConditionConverter scrCriticalMedicalConditionConverter;

    public EpsBuilder(
            FhirContext fhirContext,
            FhirHelper fhirHelper,
            DocumentBuilderFactory documentBuilderFactory,
            M1Parser m1Parser,
            ScrAllergyIntoleranceConverter scrAllergyIntoleranceConverter,
            ScrCriticalMedicalConditionConverter scrCriticalMedicalConditionConverter) {
        this.fhirContext = fhirContext;
        this.fhirHelper = fhirHelper;
        this.documentBuilderFactory = documentBuilderFactory;
        this.m1Parser = m1Parser;
        this.scrAllergyIntoleranceConverter = scrAllergyIntoleranceConverter;
        this.scrCriticalMedicalConditionConverter = scrCriticalMedicalConditionConverter;
    }

    public void buildEps(Path directory) {

        logger.info("Building EPS for directory: {}", directory);

        Bundle bundle = EpsBundle
                .newInstance(fhirContext, documentBuilderFactory,
                        m1Parser, scrAllergyIntoleranceConverter, scrCriticalMedicalConditionConverter)
                .createBundle()
                .withComposition(EpsComposition
                        .newInstance()
                        .createComposition()
                        .addEmptySections()
                        .toResource())
                .withPatient(directory)
                .populateFrom(directory.resolve(INPUT_DIRECTORY))
                .toResource();

        Path outputDirectory = directory.resolve(OUTPUT_DIRECTORY);
        fhirHelper.writeResource(bundle, outputDirectory.resolve(OUTPUT_FILE));

    }

}
