# SCR to EPS Converter

Tools for creating an EPS from SCR data.

## The EPS converter

The Spring Boot application Maven project in `scr-to-eps` converts SCR data to a FHIR EPS resource.

Test data is in the `sample-data` directory.
Each test patient is in a separate subdirectory.

Each patient directory has:

* A `Patient.json` file for the Patient resource with demographic information.
* Clinical data in files in the `input-scr` directory. These files can be in FHIR format or custom national formats. The application processes each file and adds its data to the EPS composition created for the patient.
    * JSON files are parsed as FHIR resources. The resources are routed to converters based on resource type and profile.
    * XML files are parsed as XML documents and routed to converters based on the root element and the namespace.
* An `output-eps` directory where the application writes the generated EPS Bundle.

To build the EPS for a patient:

```sh
cd scr-to-eps
mvn spring-boot:run -Dspring-boot.run.arguments="../sample-data/patient-1"
```

## Convert a SyntPop person to an EPS Patient

Synthetic person data was generated using SyntPop from Norsk helsenett. The original data is in `sample-data/input-syntpop/syntpop-persons.json`.

The `syntpop` Maven profile enables a separate command-line runner for converting from the source format to FHIR Patient resources.
The runner expects the source file, the person index, and the target directory as input parameters.
The person index is zero-based, and the runner writes `Patient.json` in the target directory (replacing an existing file with that name).

```sh
cd scr-to-eps
mvn spring-boot:run -Psyntpop \
  -Dspring-boot.run.arguments="--syntpop.input=../sample-data/input-syntpop/syntpop-persons.json --syntpop.index=0 --syntpop.target=../sample-data/patient-1"
```

## Validation of EPS

The `validate.sh` script shows how to use the [FHIR Valudator](https://hl7.org/fhir/validator/) from HL7 to validate the EPS.
The `FHIR_VALIDATOR_JAR` environment variable should be set to the location of the validator JAR file.
The EPS implementation guide is specified in the `-ig` parameter with a fixed version.
The validator downloads the IG and its dependencies and installs them to the local FHIR package cache.

## Validation of SCR

Profiles are available on Simplifier: <https://simplifier.net/kjernejournalr4>.

The PPS IG is at <https://hl7norway.github.io/NILAR/DiagnosticReportIG/CurrentBuild/index.html>.
