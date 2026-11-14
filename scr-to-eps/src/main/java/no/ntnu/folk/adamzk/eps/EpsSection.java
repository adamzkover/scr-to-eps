package no.ntnu.folk.adamzk.eps;

public enum EpsSection {

    PROBLEM_LIST(1, "11450-4", "Problem List", "Problem list - Reported"),
    ALLERGIES(2, "48765-2", "Allergies", "Allergies and adverse reactions Document"),
    MEDICATIONS(3, "10160-0", "Medications", "History of Medication use Narrative"),
    IMMUNIZATIONS(4, "11369-6", "Immunizations", "History of Immunization note"),
    RESULTS(5, "30954-2", "Results", "Relevant diagnostic tests/laboratory data note"),
    PROCEDURES(6, "47519-4", "Procedures", "History of Procedures Document"),
    MEDICAL_DEVICES(7, "46264-8", "Medical Devices", "History of medical device use");

    private final int sequenceNumber;
    private final String code;
    private final String title;
    private final String codeTerm;

    EpsSection(int sequenceNumber, String code, String title, String codeTerm) {
        this.sequenceNumber = sequenceNumber;
        this.code = code;
        this.title = title;
        this.codeTerm = codeTerm;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getCodeTerm() {
        return codeTerm;
    }

}
