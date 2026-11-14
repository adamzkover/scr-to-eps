package no.ntnu.folk.adamzk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SyntPopPatientConverter {

    private static final String PATIENT_PROFILE = "http://hl7.eu/fhir/eps/StructureDefinition/patient-eu-eps";
    private static final String NORWEGIAN_NIN_SYSTEM = "urn:oid:2.16.578.1.12.4.1.4.1";
    private static final Pattern FHIR_ID = Pattern.compile("[A-Za-z0-9.-]{1,64}");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FhirHelper fhirHelper;

    public SyntPopPatientConverter(FhirHelper fhirHelper) {
        this.fhirHelper = fhirHelper;
    }

    public Path convert(Path inputFile, int personIndex, Path targetDirectory) throws IOException {
        if (!Files.isRegularFile(inputFile)) {
            throw new IllegalArgumentException("SyntPop input file does not exist: " + inputFile);
        }
        if (personIndex < 0) {
            throw new IllegalArgumentException("Patient index must be zero or greater: " + personIndex);
        }
        if (Files.exists(targetDirectory) && !Files.isDirectory(targetDirectory)) {
            throw new IllegalArgumentException("Target path is not a directory: " + targetDirectory);
        }

        JsonNode root = objectMapper.readTree(inputFile.toFile());
        JsonNode data = getDataObject(root);
        JsonNode persons = data.path("persons");
        if (!persons.isArray()) {
            throw new IllegalArgumentException("SyntPop data does not contain a persons array.");
        }
        if (personIndex >= persons.size()) {
            throw new IllegalArgumentException("Patient index " + personIndex
                    + " is outside the persons array (size " + persons.size() + ").");
        }

        JsonNode person = persons.get(personIndex);
        String name = requiredText(person, "name", personIndex);
        String nin = requiredText(person, "nin", personIndex);
        String birthDateValue = requiredText(person, "birthDate", personIndex);

        Path normalizedTarget = targetDirectory.toAbsolutePath().normalize();
        Path targetName = normalizedTarget.getFileName();
        if (targetName == null || !FHIR_ID.matcher(targetName.toString()).matches()) {
            throw new IllegalArgumentException("Target directory name must be a valid FHIR id: " + targetDirectory);
        }

        Patient patient = new Patient();
        patient.setId(targetName.toString());
        patient.getMeta().addProfile(PATIENT_PROFILE);
        patient.addIdentifier().setSystem(NORWEGIAN_NIN_SYSTEM).setValue(nin);
        patient.addName().setText(name);
        patient.setBirthDateElement(new DateType(parseBirthDate(birthDateValue).toString()));
        patient.setGender(parseGender(person.path("gender").asText("")));
        patient.getText()
                .setStatus(NarrativeStatus.GENERATED)
                .setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\">Patient "
                        + escapeHtml(name) + "</div>");

        Path outputFile = targetDirectory.resolve("Patient.json");
        fhirHelper.writeResource(patient, outputFile);
        return outputFile;
    }

    private JsonNode getDataObject(JsonNode root) {
        if (root.isArray()) {
            if (root.size() != 1) {
                throw new IllegalArgumentException("Expected one SyntPop data object in the input array.");
            }
            return root.get(0);
        }
        if (root.isObject()) {
            return root;
        }
        throw new IllegalArgumentException("SyntPop input must be a JSON object or a single-item array.");
    }

    private String requiredText(JsonNode person, String field, int personIndex) {
        JsonNode value = person.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("SyntPop person at index " + personIndex
                    + " is missing a non-empty " + field + " value.");
        }
        return value.asText();
    }

    private LocalDate parseBirthDate(String value) {
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException dateTimeException) {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException dateException) {
                dateException.addSuppressed(dateTimeException);
                throw new IllegalArgumentException("Invalid SyntPop birthDate: " + value, dateException);
            }
        }
    }

    private AdministrativeGender parseGender(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "male" -> AdministrativeGender.MALE;
            case "female" -> AdministrativeGender.FEMALE;
            case "other" -> AdministrativeGender.OTHER;
            default -> AdministrativeGender.UNKNOWN;
        };
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
