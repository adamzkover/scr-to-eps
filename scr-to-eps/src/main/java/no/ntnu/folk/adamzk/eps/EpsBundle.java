package no.ntnu.folk.adamzk.eps;

import static org.joox.JOOX.$;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.joox.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import no.ntnu.folk.adamzk.scr.M1Parser;
import no.ntnu.folk.adamzk.scr.ScrAllergyIntoleranceConverter;
import no.ntnu.folk.adamzk.scr.ScrCriticalMedicalConditionConverter;
import no.ntnu.folk.adamzk.scr.ScrProfiles;
import no.ntnu.folk.adamzk.terminology.TerminologyService;

public final class EpsBundle {

    private static final Logger logger = LoggerFactory.getLogger(EpsBundle.class);

    private final FhirContext fhirContext;
    private final DocumentBuilderFactory documentBuilderFactory;

    private final M1Parser m1Parser;
    private final ScrAllergyIntoleranceConverter scrAllergyIntoleranceConverter;
    private final ScrCriticalMedicalConditionConverter scrCriticalMedicalConditionConverter;

    private Bundle bundle;

    private EpsBundle(FhirContext fhirContext, DocumentBuilderFactory documentBuilderFactory,
        M1Parser m1Parser, ScrAllergyIntoleranceConverter scrAllergyIntoleranceConverter,
        ScrCriticalMedicalConditionConverter scrCriticalMedicalConditionConverter) {
        this.fhirContext = Objects.requireNonNull(fhirContext, "fhirContext");
        this.documentBuilderFactory = Objects.requireNonNull(documentBuilderFactory, "documentBuilderFactory");
        this.m1Parser = Objects.requireNonNull(m1Parser, "m1Parser");
        this.scrAllergyIntoleranceConverter = Objects.requireNonNull(scrAllergyIntoleranceConverter, "scrAllergyIntoleranceConverter");
        this.scrCriticalMedicalConditionConverter = Objects.requireNonNull(scrCriticalMedicalConditionConverter, "scrCriticalMedicalConditionConverter");
    }

    public static EpsBundle newInstance(
            FhirContext fhirContext, DocumentBuilderFactory documentBuilderFactory, M1Parser m1Parser,
            ScrAllergyIntoleranceConverter scrAllergyIntoleranceConverter,
            ScrCriticalMedicalConditionConverter scrCriticalMedicalConditionConverter) {
        return new EpsBundle(fhirContext, documentBuilderFactory, m1Parser,
            scrAllergyIntoleranceConverter,
            scrCriticalMedicalConditionConverter);
    }

    public Bundle toResource() {
        return bundle;
    }

    public EpsBundle createBundle() {

        Date createdAt = Date.from(Instant.now());
        String bundleId = UUID.randomUUID().toString();

        bundle = new Bundle();
        bundle = new Bundle()
                .setType(Bundle.BundleType.DOCUMENT)
                .setTimestamp(createdAt)
                .setIdentifier(new Identifier()
                        .setSystem("urn:ietf:rfc:3986")
                        .setValue("urn:uuid:" + bundleId));

        bundle.setId(bundleId);
        bundle.getMeta().addProfile(EpsProfiles.BUNDLE_PROFILE);

        return this;

    }

    public EpsBundle withComposition(Composition composition) {

        bundle.addEntry()
                .setFullUrl("urn:uuid:" + composition.getId())
                .setResource(composition);

        return this;

    }

    public EpsBundle withPatient(Path directory) {
        Path patientFile = directory.resolve("Patient.json");
        if (!Files.isRegularFile(patientFile)) {
            throw new IllegalArgumentException("Patient file does not exist: " + patientFile);
        }

        IBaseResource parsedResource;
        try {
            parsedResource = parseJsonResource(patientFile).getFirst();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read patient file " + patientFile, e);
        }
        if (!(parsedResource instanceof Patient patient)) {
            throw new DataFormatException("Patient file does not contain a Patient resource: " + patientFile);
        }

        String patientId = UUID.randomUUID().toString();
        patient.setId(patientId);
        bundle.addEntry()
                .setFullUrl("urn:uuid:" + patientId)
                .setResource(patient);

        Composition composition = (Composition) bundle.getEntryFirstRep().getResource();
        composition.getSubject().setReference("urn:uuid:" + patientId);

        return this;
    }

    public EpsBundle populateFrom(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("The provided path is not a directory: " + directory);
        }
        Composition composition = (Composition) bundle.getEntryFirstRep().getResource();

        if (composition == null) {
            throw new IllegalStateException("A Composition must be added before populating the EPS bundle.");
        }

        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(this::addResource);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read input directory " + directory, e);
        }
        return this;
    }

    private void addResource(Path file) {
        logger.info("Processing file: {}", file.getFileName());
        try {
            List<IBaseResource> parsedResources = switch (getFileExtension(file)) {
                case "json" -> parseJsonResource(file);
                case "xml" -> parseXmlResource(file);
                default -> {
                    logger.warn("Unsupported file extension: {}", file.getFileName());
                    yield null;
                }
            };
            if (parsedResources == null) {
                return;
            }
            for (IBaseResource parsedResource : parsedResources) {
                if (!(parsedResource instanceof Resource resource)) {
                    throw new DataFormatException("Parsed resource is not an R4 resource");
                }
                addResourceToBundle(resource);
            }
        } catch (DataFormatException | ConfigurationException | IOException
                | ParserConfigurationException | SAXException e) {
            logger.error("Failed to parse resource from file: {}", file.getFileName(), e);
        }
    }

    private void addResourceToBundle(Resource resource) {
        String resourceId = resource.getIdElement().getIdPart();
        if (!isUuid(resourceId)) {
            resourceId = UUID.randomUUID().toString();
            resource.setId(resourceId);
        }

        Bundle.BundleEntryComponent entry = bundle.addEntry()
                .setFullUrl("urn:uuid:" + resourceId)
                .setResource(resource);

        if (resource instanceof AllergyIntolerance allergyIntolerance) {
            allergyIntolerance.setPatient(getPatientReference());
            Composition.SectionComponent allergiesSection = getCompositionSection(EpsSection.ALLERGIES.getCode());
            allergiesSection.addEntry(new Reference(entry.getFullUrl()));
            appendResourceText(allergiesSection, allergyIntolerance);
            allergiesSection.setEmptyReason(null);
        } else if (resource instanceof Condition condition) {
            condition.setSubject(getPatientReference());
            Composition.SectionComponent criticalMedicalSection = getCompositionSection(EpsSection.PROBLEM_LIST.getCode());
            criticalMedicalSection.addEntry(new Reference(entry.getFullUrl()));
            appendResourceText(criticalMedicalSection, condition);
            criticalMedicalSection.setEmptyReason(null);
        } else if (resource instanceof MedicationStatement medicationStatement) {
            medicationStatement.setSubject(getPatientReference());
            Composition.SectionComponent medicationsSection = getCompositionSection(EpsSection.MEDICATIONS.getCode());
            medicationsSection.addEntry(new Reference(entry.getFullUrl()));
            appendResourceText(medicationsSection, medicationStatement);
            medicationsSection.setEmptyReason(null);
        }
    }

    private Composition.SectionComponent getCompositionSection(String sectionCode) {
        return bundle.getEntry().stream()
                .map(bundleEntry -> bundleEntry.getResource())
                .filter(Composition.class::isInstance)
                .map(Composition.class::cast)
                .flatMap(composition -> composition.getSection().stream())
                .filter(section -> section.hasCode() && section.getCode().getCoding().stream()
                        .anyMatch(coding -> TerminologyService.LOINC_SYSTEM.equals(coding.getSystem())
                                && sectionCode.equals(coding.getCode())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "A Medications section must exist before adding a MedicationStatement."));
    }

    private void appendResourceText(Composition.SectionComponent section, DomainResource resource) {
        if (!resource.hasText() || !resource.getText().hasDiv()) {
            if (section.hasEmptyReason()) {
                section.setText(null);
            }
            return;
        }

        String resourceText = resource.getText().getDivAsString();
        if (section.hasEmptyReason()) {
            section.getText().setDivAsString(resourceText);
            return;
        }

        Narrative sectionText = section.getText();
        String sectionDiv = sectionText.getDivAsString();
        int closingTag = sectionDiv.lastIndexOf("</div>");
        if (closingTag < 0) {
            throw new IllegalStateException("The section narrative must contain a closing div tag.");
        }
        sectionText.setDivAsString(
                sectionDiv.substring(0, closingTag) + resourceText + sectionDiv.substring(closingTag));
    }

    private List<IBaseResource> parseJsonResource(Path file) throws IOException {

        List<IBaseResource> resources = new ArrayList<>();

        IBaseResource resource = fhirContext.newJsonParser().parseResource(Files.readString(file));
        if (resource instanceof Bundle bundleResource) {
            // TODO Handle Bundles
        } else if (resource instanceof Patient patientResource) {
            resources.add(patientResource);
        } else if (resource instanceof AllergyIntolerance allergyIntoleranceResource) {
            if (allergyIntoleranceResource.getMeta().hasProfile(ScrProfiles.ALLERGY_INTOLERANCE_PROFILE)) {
                AllergyIntolerance epsAllergyIntolerance = scrAllergyIntoleranceConverter.parse(allergyIntoleranceResource);
                resources.add(epsAllergyIntolerance);
            }
        } else if (resource instanceof Condition conditionResource) {
            if (conditionResource.getMeta().hasProfile(ScrProfiles.CRITICAL_MEDICAL_CONDITION_PROFILE)) {
                Condition epsCondition = scrCriticalMedicalConditionConverter.parse(conditionResource);
                resources.add(epsCondition);
            }
        }

        return resources;

    }

    private List<IBaseResource> parseXmlResource(Path file)
            throws ParserConfigurationException, IOException, SAXException {
        Document document = documentBuilderFactory.newDocumentBuilder().parse(file.toFile());
        Match root = $(document).xpath("/*");
        String namespace = root.namespaceURI();
        if ("Resept".equals(root.tag())
                && namespace != null
                && namespace.startsWith("http://www.kith.no/xmlstds/eresept/m1/")) {
            return List.copyOf(m1Parser.parse($(document)));
        }

        throw new IOException("Unsupported XML root element: " + root.tag());
    }

    private boolean isUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Reference getPatientReference() {
        return bundle.getEntry().stream()
                .map(bundleEntry -> bundleEntry.getResource())
                .filter(Patient.class::isInstance)
                .map(Patient.class::cast)
                .findFirst()
                .map(patient -> new Reference("urn:uuid:" + patient.getId()))
                .orElseThrow(() -> new IllegalStateException(
                        "A Patient must be added before linking a MedicationStatement."));
    }

    private String getFileExtension(Path file) {
        String fileName = file.getFileName().toString();
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
    }

}
