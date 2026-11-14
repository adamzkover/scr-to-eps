package no.ntnu.folk.adamzk.scr;
import static org.joox.JOOX.$;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Dosage.DosageDoseAndRateComponent;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Timing;
import org.hl7.fhir.r4.model.Timing.EventTiming;
import org.hl7.fhir.utilities.Utilities;
import org.joox.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import no.ntnu.folk.adamzk.eps.EpsProfiles;
import no.ntnu.folk.adamzk.terminology.TerminologyService;

@Component
public final class M1Parser {

    private static final Logger logger = LoggerFactory.getLogger(M1Parser.class);

    public List<Resource> parse(Match m1Document) {

        Medication medication = new Medication();
        medication.setId(UUID.randomUUID().toString());
        medication.getMeta().addProfile(EpsProfiles.MEDICATION_PROFILE);

        Match legemiddelpakning = findLegemiddelpakning(m1Document);
        if (!legemiddelpakning.isEmpty()) {
            parseMedicationPackage(legemiddelpakning, medication);
        }
        generateNarrative(medication);

        MedicationStatement medicationStatement = new MedicationStatement();
        medicationStatement.setId(UUID.randomUUID().toString());

        medicationStatement.getMeta().addProfile(EpsProfiles.MEDICATION_STATEMENT_PROFILE);
        medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
        medicationStatement.setMedication(new Reference("urn:uuid:" + medication.getId()));
        medicationStatement.setEffective(new DateTimeType(new Date()));

        generateDosage(medicationStatement, m1Document);
        generateNarrative(medicationStatement, medication);

        return List.of(medicationStatement, medication);

    }

    private Match findLegemiddelpakning(Match document) {
        return document.xpath("//*[local-name()='Forskrivning']/*[local-name()='Legemiddelpakning']").first();
    }

    private void parseMedicationPackage(Match packageElement, Medication medication) {
        Match atc = findChildElement(packageElement, "Atc");
        if (!atc.isEmpty()) {
            CodeableConcept classification = new CodeableConcept();
            classification.addCoding()
                    .setSystem(TerminologyService.ATC_SYSTEM)
                    .setCode(atc.attr("V").trim());
            medication.addExtension()
                    .setUrl(EpsProfiles.EXT_MEDICATION_CLASSIFICATION)
                    .setValue(classification);
        }

        Match nameFormStrength = findChildElement(packageElement, "NavnFormStyrke");
        if (!nameFormStrength.isEmpty()) {
            medication.addExtension()
                    .setUrl(EpsProfiles.EXT_MEDICATION_PRODUCT_NAME)
                    .setValue(new StringType(nameFormStrength.text().trim()));
        }

        Match dosageForm = findChildElement(packageElement, "LegemiddelformKort");
        if (!dosageForm.isEmpty()) {
            CodeableConcept form = new CodeableConcept();
            form.addCoding(TerminologyService.translate(dosageForm.attr("S"), dosageForm.attr("V")));
            medication.setForm(form);
        }

        Match productNumber = findChildElement(packageElement, "Varenr");
        if (!productNumber.isEmpty()) {
            String value = productNumber.text().trim();
            if (!value.isEmpty()) {
                medication.addIdentifier()
                        .setSystem("https://farmalogg.no/varenummer")
                        .setValue(value);
                medication.getCode().addCoding(TerminologyService.translate("https://farmalogg.no/varenummer", value));
            }
        }
    }

    private Match findChildElement(Match parent, String localName) {
        return parent.xpath("./*[local-name()='" + localName + "']");
    }

    private void generateDosage(MedicationStatement medicationStatement, Match m1Document) {

        logger.debug("Processing dosage information");

        Match forskrivning = m1Document.find("Forskrivning");
        Match dosVeiledEnkel = forskrivning.find("DosVeiledEnkel");
        if (!dosVeiledEnkel.isEmpty()) {
            // The text should be translated to English
            medicationStatement.addDosage()
                    .setText(dosVeiledEnkel.text().trim());
        }

        Match dosering = forskrivning.find("Dosering");
        dosering.forEach(doseringInstance -> {
            Match doseFastTidspunkt = $(doseringInstance).find("DoseFastTidspunkt");
            doseFastTidspunkt.forEach(doseFastTidspunktInstance -> {
                Dosage currentDosage = medicationStatement.addDosage();
                Match starttidspunkt = $(doseringInstance).find("Starttidspunkt");
                Match sluttidspunkt = $(doseringInstance).find("Sluttidspunkt");
                if (!starttidspunkt.isEmpty() || !sluttidspunkt.isEmpty()) {
                    Timing timing = currentDosage.getTiming();
                    Period boundsPeriod = new Period();
                    if (!starttidspunkt.isEmpty()) {
                        boundsPeriod.setStartElement(new DateTimeType(starttidspunkt.attr("V").trim()));
                    }
                    if (!sluttidspunkt.isEmpty()) {
                        boundsPeriod.setEndElement(new DateTimeType(sluttidspunkt.attr("V").trim()));
                    }
                    timing.getRepeat().setBounds(boundsPeriod);
                }

                // Mengde and Intervall to doseAndRate
                Match mengde = $(doseringInstance).find("Mengde");
                Match intervall = $(doseringInstance).find("Intervall");
                if (!mengde.isEmpty() || !intervall.isEmpty()) {
                    DosageDoseAndRateComponent currentDoseAndRate = currentDosage.addDoseAndRate();
                    if (!mengde.isEmpty()) {
                        String amount = mengde.attr("V").trim();
                        String unit = mengde.attr("U").trim();
                        SimpleQuantity dose = new SimpleQuantity();
                        if (!amount.isEmpty()) {
                            dose.setValue(new BigDecimal(amount));
                        }
                        if (!unit.isEmpty()) {
                            String translatedUnit = TerminologyService.translateUnit("7480", unit);
                            if (translatedUnit != null) {
                                dose.setUnit(translatedUnit);
                                dose.setSystem(TerminologyService.UCUM_SYSTEM);
                                dose.setCode(translatedUnit);
                            } else {
                                dose.setUnit(unit);
                            }
                        }
                        currentDoseAndRate.setDose(dose);
                    }
                    if (!intervall.isEmpty()) {
                        String intervalValue = intervall.attr("V").trim();
                        String intervalUnit = intervall.attr("U").trim();
                        SimpleQuantity rate = new SimpleQuantity();
                        if (!intervalValue.isEmpty()) {
                            rate.setValue(new BigDecimal(intervalValue));
                        }
                        if (!intervalUnit.isEmpty()) {
                            String translatedUnit = TerminologyService.translateUnit("9088", intervalUnit);
                            if (translatedUnit != null) {
                                rate.setUnit(translatedUnit);
                                rate.setSystem(TerminologyService.UCUM_SYSTEM);
                                rate.setCode(translatedUnit);
                            } else {
                                rate.setUnit(intervalUnit);
                            }
                        }
                        currentDoseAndRate.setRate(rate);
                    }
                }

                // Tidsomrade
                Match tidsomrade = $(doseFastTidspunktInstance).find("Tidsomrade");
                if (!tidsomrade.isEmpty()) {
                    String value = tidsomrade.attr("V").trim();
                    Coding whenConding = TerminologyService.translate("8325", value);
                    if (whenConding != null) {
                        Timing timing = currentDosage.getTiming();
                        timing.getRepeat().addWhen(EventTiming.valueOf(whenConding.getCode()));
                    }
                }

            });

        });
    }

    private void generateNarrative(MedicationStatement medicationStatement, Medication medication) {
        StringBuilder narrative = new StringBuilder();
        narrative.append("<div xmlns=\"http://www.w3.org/1999/xhtml\">")
                .append("<p>Medication '")
                .append(medication.getExtensionString(EpsProfiles.EXT_MEDICATION_PRODUCT_NAME))
                .append("'</p>")
                .append("<p>ATC code: ")
                .append(Utilities.escapeXml(getAtcCode(medication)))
                .append("</p>")
                .append("<p>SNOMED CT code: ")
                .append(Utilities.escapeXml(getSnomedCtCode(medication)))
                .append("</p>")
                .append("</div>");
        medicationStatement.getText()
                .setStatus(NarrativeStatus.GENERATED)
                .setDivAsString(narrative.toString());
    }

    private void generateNarrative(Medication medication) {
        StringBuilder narrative = new StringBuilder();
        narrative.append("<div xmlns=\"http://www.w3.org/1999/xhtml\">")
                .append("<p>Medication '")
                .append(medication.getExtensionString(EpsProfiles.EXT_MEDICATION_PRODUCT_NAME))
                .append("'</p>")
                .append("<p>ATC code: ")
                .append(Utilities.escapeXml(getAtcCode(medication)))
                .append("</p>")
                .append("<p>SNOMED CT code: ")
                .append(Utilities.escapeXml(getSnomedCtCode(medication)))
                .append("</p>")
                .append("</div>");
        medication.getText()
                .setStatus(NarrativeStatus.GENERATED)
                .setDivAsString(narrative.toString());
    }

    static String getAtcCode(Medication medication) {
        Extension classification = medication.getExtensionByUrl(EpsProfiles.EXT_MEDICATION_CLASSIFICATION);
        if (classification == null || !classification.hasValue()) {
            return "Unknown";
        }
        if (!(classification.getValue() instanceof CodeableConcept concept)) {
            throw new IllegalStateException("Medication classification extension must contain a CodeableConcept.");
        }
        return getCode(concept, TerminologyService.ATC_SYSTEM, false);
    }

    static String getSnomedCtCode(Medication medication) {
        return medication.hasCode() ? getCode(medication.getCode(), TerminologyService.SNOMED_CT_SYSTEM, true) : "Unknown";
    }

    private static String getCode(CodeableConcept concept, String system, boolean withDisplay) {
        return concept.getCoding().stream()
                .filter(coding -> system.equals(coding.getSystem()))
                .filter(coding -> coding.hasCode() && !coding.getCode().isBlank())
                .map(coding -> withDisplay && coding.hasDisplay() && !coding.getDisplay().isBlank()
                        ? coding.getCode() + " (" + coding.getDisplay() + ")"
                        : coding.getCode())
                .findFirst()
                .orElse("Unknown");
    }

}
