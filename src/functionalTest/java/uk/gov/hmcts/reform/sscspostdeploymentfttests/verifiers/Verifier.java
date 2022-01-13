package uk.gov.hmcts.reform.sscspostdeploymentfttests.verifiers;

import java.util.Map;

public interface Verifier {

    void verify(
        Map<String, Object> scenario,
        Map<String, Object> expectedResponse,
        Map<String, Object> actualResponse
    );
}
