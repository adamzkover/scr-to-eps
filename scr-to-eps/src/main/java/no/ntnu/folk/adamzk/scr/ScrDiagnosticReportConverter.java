package no.ntnu.folk.adamzk.scr;

import org.hl7.fhir.r4.model.DiagnosticReport;

import no.ntnu.folk.adamzk.eps.EpsProfiles;

public class ScrDiagnosticReportConverter {

    public DiagnosticReport convert(DiagnosticReport scrDiagnosticReport) {

        DiagnosticReport epsDiagnosticReport = new DiagnosticReport();
        epsDiagnosticReport.getMeta().addProfile(EpsProfiles.DIAGNOSTIC_REPORT_PROFILE);

        return epsDiagnosticReport;

    }

}
