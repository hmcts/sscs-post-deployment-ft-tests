package uk.gov.hmcts.reform.sscspostdeploymentfttests.services.taskretriever;

import io.restassured.http.Headers;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionEvaluationLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.SpringBootFunctionalBaseTest;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.TestScenario;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.CamundaService;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.DeserializeValuesUtil;

import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@Component
@Slf4j
public class CamundaTaskRetrieverService implements TaskRetrieverService {

    @Autowired
    private CamundaService camundaService;
    @Autowired
    private DeserializeValuesUtil deserializeValuesUtil;

    @Override
    public void retrieveTask(Map<String, Object> clauseValues, TestScenario scenario, String caseId,
                             Headers authorizationHeaders) {

        Map<String, Object> deserializedClauseValues = deserializeValuesUtil.expandMapValues(clauseValues, emptyMap());

        await()
            .ignoreException(AssertionError.class)
            .conditionEvaluationListener(new ConditionEvaluationLogger(log::info))
            .pollInterval(SpringBootFunctionalBaseTest.DEFAULT_POLL_INTERVAL_SECONDS, SECONDS)
            .atMost(SpringBootFunctionalBaseTest.DEFAULT_TIMEOUT_SECONDS, SECONDS)
            .until(
                () -> {
                    camundaService.searchByCaseIdJurisdictionAndCaseType(deserializedClauseValues, scenario, caseId,
                                                                         authorizationHeaders);
                    // TODO: Do we want to verify responses for camunda?
                    return true;
                });

    }
}
