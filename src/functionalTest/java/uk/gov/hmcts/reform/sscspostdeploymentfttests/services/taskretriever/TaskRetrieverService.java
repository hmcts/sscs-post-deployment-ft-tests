package uk.gov.hmcts.reform.sscspostdeploymentfttests.services.taskretriever;

import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.TestScenario;

import java.util.Map;

public interface TaskRetrieverService {

    void retrieveTask(Map<String, Object> clauseValues, TestScenario scenario);
}
