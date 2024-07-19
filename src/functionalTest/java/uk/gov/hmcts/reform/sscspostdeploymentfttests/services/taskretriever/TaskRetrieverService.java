package uk.gov.hmcts.reform.sscspostdeploymentfttests.services.taskretriever;

import io.restassured.http.Headers;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.TestScenario;

import java.util.Map;

public interface TaskRetrieverService {

    void retrieveTask(Map<String, Object> clauseValues,
                      TestScenario scenario,
                      String caseId,
                      Headers authorizationHeaders);
}
