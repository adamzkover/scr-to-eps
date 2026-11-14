java -jar $FHIR_VALIDATOR_JAR -version 4.0.1 \
  -ig implementation-guides/ips-2.0.0 \
  -ig implementation-guides/ihe-pharm-mpd-1.0.0-comment2 \
  -ig implementation-guides/eps-1.0.0 \
  -ig implementation-guides/eu-ext-1.3.0 \
  -ig implementation-guides/eu-base-core-2.0.0 \
  sample-data/patient-1/output-eps/eps.json
