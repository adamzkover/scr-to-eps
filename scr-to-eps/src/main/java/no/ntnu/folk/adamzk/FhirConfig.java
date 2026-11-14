package no.ntnu.folk.adamzk;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;

@Component
public class FhirConfig {

    @Bean
    public FhirContext getFhirContext() {
        return FhirContext.forR4();
    }

}
