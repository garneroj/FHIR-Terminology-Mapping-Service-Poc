package com.myhospital;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TerminologyIT {

    private static final String SERVER_URL = "http://localhost:8080/fhir";
    private static final String EXPECTED_LOINC_CODE = "883-9";

    @Test
    public void testTerminologyTranslationFlow() {


        //--- New client.
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient(SERVER_URL);

        //--- Server up & running ?
        try {
            CapabilityStatement capability = client.capabilities().ofType(CapabilityStatement.class)
                                .execute();
            assertNotNull(capability);
            System.out.println("✅ Successful connection: " + capability.getSoftware().getName());
        } catch (Exception e) {
            fail("❌ FATAL: Unable to connect to: localhost:8080. Please check.");
        }

        //--- GET for translation
        //--- http://localhost:8080/fhir/ConceptMap/$translate?system=http://my-hospital.com/internal-lab-codes&code=HOSP-123&target=http://loinc.org
        System.out.println("🔄 Running...");
        Parameters response = null;
        try {
             response = client
                .operation      ()
                .onType         (org.hl7.fhir.r4.model.ConceptMap.class)
                .named          ("$translate")
                .withParameter  (Parameters.class, "system", new StringType("http://my-hospital.com/internal-lab-codes"))
                .andParameter   ("code", new StringType("HOSP-123"))
                .andParameter   ("target", new StringType("http://loinc.org"))

                .useHttpGet         ()

                .returnResourceType (Parameters.class)
                .execute            ();

        } catch (Exception e) {
            fail("❌ Failed to $translate: " + e.getMessage ()) ;
        }

        assertNotNull(response);
        assertTrue(response.getParameterBool("result"), "Server should had responded true.");

        //{ "resourceType": "Parameters", "parameter": [{"name": "result", ... "message", "match"...

        var parameterComponent = Optional.ofNullable(response.getParameter())
                                    .orElse(List.of())
                                    .stream()
                .filter(param -> param.getName().equals("match"))
                .findFirst();

        var actualCode = parameterComponent
                .flatMap(match -> match.getPart().stream()
                                       .filter(p -> "concept".equals(p.getName()))
                                       .findFirst()
                        )
                .map(concept -> concept.getValue())
                .filter(val -> val instanceof Coding)
                .map(val -> (Coding) val)
                .map(Coding::getCode)
                .orElse("");


        System.out.println("   Expected: " + EXPECTED_LOINC_CODE);
        System.out.println("   Actual: " + actualCode);

        assertEquals(EXPECTED_LOINC_CODE, actualCode);
        System.out.println("✅ Successful translation.");

    }
}
