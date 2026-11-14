package no.ntnu.folk.adamzk;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;

/**
 * Generic FHIR helper methods.
 */
@Component
public class FhirHelper {

    private static final Logger logger = LoggerFactory.getLogger(FhirHelper.class);

    private FhirContext fhirContext;

    public FhirHelper(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    /**
     * Writes the Bundle as pretty-printed FHIR JSON under the specified output directory.
     *
     * @param resource the FHIR resource to write
     * @param outputFile the file to write the JSON to
     */
    public void writeResource(IBaseResource resource, Path outputFile) {

        try {

            Files.createDirectories(outputFile.getParent());
            String json = fhirContext
                    .newJsonParser()
                    .setPrettyPrint(true)
                    .encodeResourceToString(resource);
            Files.writeString(outputFile, json);

            logger.info("Wrote resource to {}", outputFile);

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write resource to " + outputFile, e);
        }

    }

}
