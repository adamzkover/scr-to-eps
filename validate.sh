java -jar $FHIR_VALIDATOR_JAR -version 4.0.1 \
  -ig 'hl7.fhir.eu.eps#1.0.0-ballot' \
  sample-data/patient-1/output-eps/eps.json
