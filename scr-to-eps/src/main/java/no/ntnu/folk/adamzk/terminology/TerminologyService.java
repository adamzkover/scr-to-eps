package no.ntnu.folk.adamzk.terminology;
import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.r4.model.Coding;
/**
 * This class represents terminology services.
 */
public class TerminologyService {

    public static final String ATC_SYSTEM = "http://www.whocc.no/atc";

    public static final String HL7_EVENT_TIMING_SYSTEM = "http://hl7.org/fhir/event-timing";

    public static final String ICD_10_SYSTEM = "http://hl7.org/fhir/sid/icd-10";

    public static final String LOINC_SYSTEM = "http://loinc.org";

    public static final String UCUM_SYSTEM = "http://unitsofmeasure.org";

    public static final String SNOMED_CT_SYSTEM = "http://snomed.info/sct";

    public static final String SCR_ABS_LIST_SYSTEM = "http://nhn.no/kj/fhir/CodeSystem/CriticalMedicalConditionAbsoluteList";

    public static final String SCR_ICD_10_SYSTEM = "http://nhn.no/kj/fhir/CodeSystem/ICD-10";

    private static Map<String, Coding> terminologyMap = new HashMap<>();

    private static Map<String, String> unitMap = new HashMap<>();

    static {

        // SCR Absolute Lis
        terminologyMap.put("http://nhn.no/kj/fhir/CodeSystem/CriticalMedicalConditionAbsoluteList|1",
                new Coding().setSystem(SNOMED_CT_SYSTEM)
                .setCode("19346006")
                .setDisplay("Marfan's syndrome")
        );

        // ATC codes in AllergyIntolerance
        terminologyMap.put("http://nhn.no/kj/fhir/CodeSystem/ATC|J01AA10",
                new Coding().setSystem(ATC_SYSTEM)
                .setCode("J01AA10")
                .setDisplay("Penimepicycline")
        );
        // Manifestation in AllergyIntolerance
        terminologyMap.put("http://nhn.no/kj/fhir/CodeSystem/TypeOfReaction|1",
                new Coding().setSystem(SNOMED_CT_SYSTEM)
                .setCode("39579001")
                .setDisplay("Anaphylaxis")
        );

        // Medication
        terminologyMap.put("https://farmalogg.no/varenummer|090031",
                new Coding().setSystem(SNOMED_CT_SYSTEM)
                .setCode("322827001")
                .setDisplay("Naratriptan (as naratriptan hydrochloride) 2.5 mg oral tablet"));
        terminologyMap.put("2.16.578.1.12.4.1.1.7448|53",
                new Coding().setSystem(SNOMED_CT_SYSTEM)
                .setCode("421026006")
                .setDisplay("Oral tablet")
        );

        // Dosage in MedicationStatement
        terminologyMap.put("8325|1",
                new Coding().setSystem(HL7_EVENT_TIMING_SYSTEM)
                .setCode("MORN")
        );
        terminologyMap.put("8325|5",
                new Coding().setSystem(HL7_EVENT_TIMING_SYSTEM)
                .setCode("EVE")
        );

        unitMap.put("7480|tablett", "{tbl}");
        unitMap.put("9088|Døgn", "d");

    }

    public static Coding translate(String system, String code) {
        return terminologyMap.get(system + "|" + code);
    }


    public static String translateUnit(String system, String unit) {
        return unitMap.get(system + "|" + unit);
    }

}
