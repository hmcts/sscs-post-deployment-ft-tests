package uk.gov.hmcts.reform.sscspostdeploymentfttests.services;

import groovy.util.logging.Slf4j;
import io.restassured.http.Headers;
import org.awaitility.core.ConditionEvaluationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.TestScenario;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.CredentialRequest;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.taskretriever.TaskRetrieverEnum;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.DeserializeValuesUtil;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapSerializer;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.verifiers.TaskDataVerifier;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.verifiers.Verifier;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.SpringBootFunctionalBaseTest.DEFAULT_POLL_INTERVAL_SECONDS;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.SpringBootFunctionalBaseTest.DEFAULT_TIMEOUT_SECONDS;

@Slf4j
@Service
public class VerifierService {
    private static final Logger log = LoggerFactory.getLogger(VerifierService.class);
    protected AuthorizationHeadersProvider authorizationHeadersProvider;
    protected TaskDataVerifier taskDataVerifier;
    private final DeserializeValuesUtil deserializeValues;
    private final MessageProcessingFacade messageProcessingFacade;
    private final TaskOperationsFacade taskOperationsFacade;

    public VerifierService(AuthorizationHeadersProvider authorizationHeadersProvider, TaskDataVerifier taskDataVerifier,
                           DeserializeValuesUtil deserializeValues, MessageProcessingFacade messageProcessingFacade,
                           TaskOperationsFacade taskOperationsFacade) {
        this.authorizationHeadersProvider = authorizationHeadersProvider;
        this.taskDataVerifier = taskDataVerifier;
        this.deserializeValues = deserializeValues;
        this.messageProcessingFacade = messageProcessingFacade;
        this.taskOperationsFacade = taskOperationsFacade;
    }

    public void verifyMessages(List<Verifier> verifiers, Map<String, Object> expectationValue, int expectedMessages,
                               String expectationCaseId) {
        if (expectedMessages > 0) {
            await()
                .ignoreException(AssertionError.class)
                .conditionEvaluationListener(new ConditionEvaluationLogger(log::info))
                .pollInterval(DEFAULT_POLL_INTERVAL_SECONDS, SECONDS)
                .atMost(DEFAULT_TIMEOUT_SECONDS, SECONDS)
                .until(
                    () -> {
                        String actualMessageResponse = messageProcessingFacade.getCaseMessages(expectationCaseId);

                        String expectedMessageResponse = buildMessageExpectationResponseBody(
                            expectationValue,
                            Map.of("caseId", expectationCaseId)
                        );

                        Map<String, Object> actualResponse = MapSerializer.deserialize(actualMessageResponse);
                        Map<String, Object> expectedResponse = MapSerializer.deserialize(expectedMessageResponse);

                        verifiers.forEach(verifier -> verifier.verify(
                            expectationValue,
                            expectedResponse,
                            actualResponse));

                        return true;
                    });
        }
    }

    private String buildMessageExpectationResponseBody(Map<String, Object> clauseValues,
                                                       Map<String, String> additionalValues)
        throws IOException {
        Map<String, Object> scenario = deserializeValues.expandMapValues(clauseValues, additionalValues);
        Map<String, Object> roleData = MapValueExtractor.extract(scenario, "messageData");
        return MapSerializer.serialize(roleData);
    }

    public void verifyTasks(TestScenario scenario, String taskRetrieverOption, Map<String, Object> expectationValue,
                             int expectedTasks, List<String> expectationCaseIds) throws IOException {
        if (expectedTasks > 0) {
            CredentialRequest credentialRequest = authorizationHeadersProvider.extractCredentialRequest(
                expectationValue, "credentials");

            Headers expectationAuthorizationHeaders =
                authorizationHeadersProvider.getWaUserAuthorization(credentialRequest);

            if (TaskRetrieverEnum.CAMUNDA_API.getId().equals(taskRetrieverOption)) {
                taskOperationsFacade.retrieveByCamunda(
                    expectationValue,
                    scenario,
                    expectationCaseIds.getFirst(),
                    expectationAuthorizationHeaders
                );
            } else {
                taskOperationsFacade.retrieveByTaskMgm(
                    expectationValue,
                    scenario,
                    expectationCaseIds,
                    expectationAuthorizationHeaders
                );
            }
        }
    }
}
