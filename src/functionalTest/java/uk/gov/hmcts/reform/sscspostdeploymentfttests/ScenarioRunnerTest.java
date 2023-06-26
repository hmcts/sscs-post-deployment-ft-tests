package uk.gov.hmcts.reform.sscspostdeploymentfttests;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.Headers;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionEvaluationLogger;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.StopWatch;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.TestRequestType;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.TestScenario;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.CredentialRequest;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.UserInfo;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.taskretriever.TaskRetrieverEnum;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.preparers.Preparer;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.CcdCaseCreator;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.MessageInjector;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.RestMessageService;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.RoleAssignmentService;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.TaskManagementService;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.taskretriever.CamundaTaskRetrieverService;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.taskretriever.TaskMgmApiRetrieverService;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.CaseIdUtil;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.DeserializeValuesUtil;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.JsonUtil;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.Logger;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapSerializer;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.StringResourceLoader;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.verifiers.TaskDataVerifier;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.verifiers.Verifier;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertFalse;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider.AUTHORIZATION;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider.SERVICE_AUTHORIZATION;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.CaseIdUtil.addAssignedCaseId;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.CLEANUP_USERS_FINISHED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.CLEANUP_USERS_RUNNING;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_BEFORE_COMPLETED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_BEFORE_FOUND;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_DISABLED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_ENABLED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_FINISHED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_ROLE_ASSIGNMENT_COMPLETED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_ROLE_ASSIGNMENT_FOUND;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_RUNNING;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_RUNNING_TIME;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_START;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_SUCCESSFUL;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_UPDATE_CASE_COMPLETED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_UPDATE_CASE_FOUND;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExpander.ENVIRONMENT_PROPERTIES;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor.extractOrDefault;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor.extractOrThrow;

@Slf4j
public class ScenarioRunnerTest extends SpringBootFunctionalBaseTest {

    @Autowired
    protected MessageInjector messageInjector;
    @Autowired
    protected TaskDataVerifier taskDataVerifier;
    @Autowired
    protected AuthorizationHeadersProvider authorizationHeadersProvider;
    @Autowired
    private CamundaTaskRetrieverService camundaTaskRetrievableService;
    @Autowired
    private TaskMgmApiRetrieverService taskMgmApiRetrievableService;
    @Autowired
    private RoleAssignmentService roleAssignmentService;
    @Autowired
    private TaskManagementService taskManagementService;
    @Autowired
    private Environment environment;
    @Autowired
    private DeserializeValuesUtil deserializeValuesUtil;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private List<Verifier> verifiers;
    @Autowired
    private List<Preparer> preparers;
    @Autowired
    private CcdCaseCreator ccdCaseCreator;
    @Autowired
    private RestMessageService restMessageService;
    @Value("${wa-post-deployment-test.environment}")
    protected String postDeploymentTestEnvironment;

    @Before
    public void setUp() {
        MapSerializer.setObjectMapper(objectMapper);
        JsonUtil.setObjectMapper(objectMapper);
    }

    @Test
    public void scenarios_should_behave_as_specified() throws Exception {

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();


        loadPropertiesIntoMapValueExpander();

        for (Preparer preparer : preparers) {
            preparer.prepare();
        }

        assertFalse("Verifiers configured successfully", verifiers.isEmpty());

        URL path = getClass().getClassLoader().getResource("scenarios");
        File[] directories = new File(path.toURI()).listFiles(File::isDirectory);
        Objects.requireNonNull(directories, "No directories found under 'scenarios'");

        Exception testException = null;
        try {
            for (File directory : directories) {
                runAllScenariosFor(directory.getName());
            }
        } catch (Exception ex) {
            testException = ex;
        } finally {
            Logger.say(CLEANUP_USERS_RUNNING);
            authorizationHeadersProvider.cleanupTestUsers();
            Logger.say(CLEANUP_USERS_FINISHED);

            if (testException != null) {
                throw testException;
            }
        }

        stopWatch.stop();
        Logger.say(SCENARIO_RUNNING_TIME, stopWatch.getTotalTimeSeconds());
    }

    private void runAllScenariosFor(String directoryName) throws Exception {
        String scenarioPattern = System.getProperty("scenario");
        if (scenarioPattern == null) {
            scenarioPattern = "*.json";
        } else {
            scenarioPattern = "*" + scenarioPattern + "*.json";
        }

        Collection<String> scenarioSources =
            StringResourceLoader
                .load("/scenarios/" + directoryName + "/" + scenarioPattern)
                .values();

        Logger.say(SCENARIO_START, scenarioSources.size() + " " + directoryName.toUpperCase(Locale.ROOT));

        for (String scenarioSource : scenarioSources) {

            Map<String, Object> scenarioValues = deserializeValuesUtil
                .deserializeStringWithExpandedValues(scenarioSource, emptyMap());

            String description = extractOrDefault(scenarioValues, "description", "Unnamed scenario");

            Boolean scenarioEnabled = extractOrDefault(scenarioValues, "enabled", true);

            if (!scenarioEnabled) {
                Logger.say(SCENARIO_DISABLED, description);
            } else {
                Logger.say(SCENARIO_ENABLED, description);

                Map<String, Object> beforeClauseValues = extractOrDefault(scenarioValues, "before", null);
                List<Map<String, Object>> testClauseValues = new ArrayList<>(Objects.requireNonNull(
                    MapValueExtractor.extract(scenarioValues, "tests")));
                Map<String, Object> postRoleAssignmentClauseValues = extractOrDefault(scenarioValues,
                    "postRoleAssignments", null);
                Map<String, Object> updateCaseClauseValues = extractOrDefault(scenarioValues, "updateCase", null);

                String scenarioJurisdiction = extractOrThrow(scenarioValues, "jurisdiction");
                String caseType = extractOrThrow(scenarioValues, "caseType");

                TestScenario scenario = new TestScenario(
                    scenarioValues,
                    scenarioSource,
                    scenarioJurisdiction,
                    caseType,
                    beforeClauseValues,
                    testClauseValues,
                    postRoleAssignmentClauseValues,
                    updateCaseClauseValues
                );
                createBaseCcdCase(scenario);

                addSearchParameters(scenario, scenarioValues);

                if (scenario.getBeforeClauseValues() != null) {
                    Logger.say(SCENARIO_BEFORE_FOUND);
                    //If before was found process with before values
                    processBeforeClauseScenario(scenario);
                    Logger.say(SCENARIO_BEFORE_COMPLETED);

                }

                if (scenario.getPostRoleAssignmentClauseValues() != null) {
                    Logger.say(SCENARIO_ROLE_ASSIGNMENT_FOUND);
                    processRoleAssignment(postRoleAssignmentClauseValues, scenario);
                    Logger.say(SCENARIO_ROLE_ASSIGNMENT_COMPLETED);
                }

                if (scenario.getUpdateCaseClauseValues() != null) {
                    Logger.say(SCENARIO_UPDATE_CASE_FOUND);
                    updateBaseCcdCase(scenario);
                    Logger.say(SCENARIO_UPDATE_CASE_COMPLETED);
                }

                Logger.say(SCENARIO_RUNNING);
                processTestClauseScenario(scenario);
                Logger.say(SCENARIO_SUCCESSFUL, description);

                Logger.say(SCENARIO_FINISHED);
            }
        }
    }

    private void processRoleAssignment(Map<String, Object> postRoleAssignmentClauseValues, TestScenario scenario)
        throws IOException {
        Map<String, Object> postRoleAssignmentValues = scenario.getPostRoleAssignmentClauseValues();

        CredentialRequest credentialRequest = extractCredentialRequest(postRoleAssignmentValues, "credentials");
        Headers requestAuthorizationHeaders = authorizationHeadersProvider.getWaUserAuthorization(credentialRequest);

        String userToken = requestAuthorizationHeaders.getValue(AUTHORIZATION);
        String serviceToken = requestAuthorizationHeaders.getValue(SERVICE_AUTHORIZATION);
        UserInfo userInfo = authorizationHeadersProvider.getUserInfo(userToken);

        roleAssignmentService.processRoleAssignments(
            scenario,
            postRoleAssignmentClauseValues, userToken, serviceToken, userInfo);
    }

    private void processBeforeClauseScenario(TestScenario scenario) throws Exception {
        processScenario(scenario.getBeforeClauseValues(), scenario);
    }

    private void processTestClauseScenario(TestScenario scenario) throws Exception {
        for (Map<String, Object> testClause : scenario.getTestClauseValues()) {
            processScenario(testClause, scenario);
        }
    }

    private void createBaseCcdCase(TestScenario scenario) throws IOException {
        Map<String, Object> scenarioValues = scenario.getScenarioMapValues();

        CredentialRequest credentialRequest = extractCredentialRequest(scenarioValues, "required.credentials");
        Headers requestAuthorizationHeaders = authorizationHeadersProvider.getWaUserAuthorization(credentialRequest);

        List<Map<String, Object>> ccdCaseToCreate = new ArrayList<>(Objects.requireNonNull(
            MapValueExtractor.extract(scenarioValues, "required.ccd")));

        ccdCaseToCreate.forEach(caseValues -> {
            try {
                String caseId = ccdCaseCreator.createCase(
                    caseValues,
                    scenario.getJurisdiction(),
                    scenario.getCaseType(),
                    requestAuthorizationHeaders
                );
                addAssignedCaseId(caseValues, caseId, scenario);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void updateBaseCcdCase(TestScenario scenario) throws IOException {
        Map<String, Object> scenarioValues = scenario.getScenarioMapValues();

        CredentialRequest credentialRequest = extractCredentialRequest(scenarioValues, "updateCase.credentials");
        Headers requestAuthorizationHeaders = authorizationHeadersProvider.getWaUserAuthorization(credentialRequest);

        List<Map<String, Object>> ccdCaseToUpdate = new ArrayList<>(Objects.requireNonNull(
            MapValueExtractor.extract(scenarioValues, "updateCase.ccd")));

        ccdCaseToUpdate.forEach(caseValues -> {
            try {
                String caseId = CaseIdUtil.extractAssignedCaseIdOrDefault(caseValues, scenario);
                ccdCaseCreator.updateCase(
                    caseId,
                    caseValues,
                    scenario.getJurisdiction(),
                    scenario.getCaseType(),
                    requestAuthorizationHeaders
                );
                addAssignedCaseId(caseValues, caseId, scenario);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void processScenario(Map<String, Object> values,
                                 TestScenario scenario) throws Exception {
        processTestRequest(values, scenario);

        String taskRetrieverOption = MapValueExtractor.extract(
            scenario.getScenarioMapValues(),
            "options.taskRetrievalApi"
        );

        List<Map<String, Object>> expectations = new ArrayList<>(Objects.requireNonNull(
            MapValueExtractor.extract(values, "expectations")));

        for (Map<String, Object> expectationValue : expectations) {
            int expectedTasks = extractOrDefault(
                expectationValue, "numberOfTasksAvailable", 0);
            int expectedMessages = extractOrDefault(
                expectationValue, "numberOfMessagesToCheck", 0);
            List<String> expectationCaseIds = CaseIdUtil.extractAllAssignedCaseIdOrDefault(expectationValue, scenario);

            verifyTasks(scenario, taskRetrieverOption, expectationValue, expectedTasks, expectationCaseIds);

            verifyMessages(expectationValue, expectedMessages, expectationCaseIds.get(0));

            removeInvalidMessages(expectationCaseIds.get(0));
        }
    }

    private void processTestRequest(Map<String, Object> values, TestScenario scenario) throws Exception {

        Map<String, Object> request = extractOrThrow(values, "request");

        TestRequestType requestType = TestRequestType.valueOf(MapValueExtractor.extractOrDefault(
            request, "type", "MESSAGE"));

        CredentialRequest credentialRequest = extractCredentialRequest(request, "credentials");
        Headers requestAuthorizationHeaders = authorizationHeadersProvider.getWaUserAuthorization(credentialRequest);

        String userToken = requestAuthorizationHeaders.getValue(AUTHORIZATION);
        UserInfo userInfo = authorizationHeadersProvider.getUserInfo(userToken);

        log.info("{} request", requestType);
        switch (requestType) {
            case MESSAGE:
                messageInjector.injectMessage(
                    request,
                    scenario,
                    userInfo
                );
                break;
            case CLAIM:
                taskManagementService.claimTask(scenario, requestAuthorizationHeaders, userInfo);
                break;
            case ASSIGN:
                UserInfo assignee = getAssigneeInfo(request);
                taskManagementService.assignTask(scenario, requestAuthorizationHeaders, assignee);
                break;
            case COMPLETE:
                taskManagementService.completeTask(scenario, requestAuthorizationHeaders, userInfo);
                break;
            default:
                throw new Exception("Invalid request type [" + requestType + "]");
        }
    }

    private void verifyMessages(Map<String, Object> expectationValue, int expectedMessages, String expectationCaseId) {
        if (expectedMessages > 0) {
            await()
                .ignoreException(AssertionError.class)
                .conditionEvaluationListener(new ConditionEvaluationLogger(log::info))
                .pollInterval(DEFAULT_POLL_INTERVAL_SECONDS, SECONDS)
                .atMost(DEFAULT_TIMEOUT_SECONDS, SECONDS)
                .until(
                    () -> {
                        String actualMessageResponse = restMessageService.getCaseMessages(expectationCaseId);

                        String expectedMessageResponse = buildMessageExpectationResponseBody(
                            expectationValue,
                            Map.of("caseId", expectationCaseId)
                        );

                        Map<String, Object> actualResponse = MapSerializer.deserialize(actualMessageResponse);
                        Map<String, Object> expectedResponse = MapSerializer.deserialize(expectedMessageResponse);

                        verifiers.forEach(verifier ->
                            verifier.verify(
                                expectationValue,
                                expectedResponse,
                                actualResponse
                            )
                        );

                        return true;
                    });
        }
    }

    private void verifyTasks(TestScenario scenario, String taskRetrieverOption, Map<String, Object> expectationValue,
                             int expectedTasks, List<String> expectationCaseIds) throws IOException {
        if (expectedTasks > 0) {
            CredentialRequest credentialRequest = extractCredentialRequest(expectationValue, "credentials");
            Headers expectationAuthorizationHeaders =
                authorizationHeadersProvider.getWaUserAuthorization(credentialRequest);

            if (TaskRetrieverEnum.CAMUNDA_API.getId().equals(taskRetrieverOption)) {
                camundaTaskRetrievableService.retrieveTask(
                    expectationValue,
                    scenario,
                    expectationCaseIds.get(0),
                    expectationAuthorizationHeaders
                );
            } else {
                taskMgmApiRetrievableService.retrieveTask(
                    expectationValue,
                    scenario,
                    expectationCaseIds,
                    expectationAuthorizationHeaders
                );
            }
        }
    }

    private UserInfo getAssigneeInfo(Map<String, Object> request) throws IOException {
        CredentialRequest credentialRequest = extractCredentialRequest(request, "input.assignee.credentials");
        Headers requestAuthorizationHeaders = authorizationHeadersProvider.getWaUserAuthorization(credentialRequest);

        String userToken = requestAuthorizationHeaders.getValue(AUTHORIZATION);
        return authorizationHeadersProvider.getUserInfo(userToken);
    }

    private CredentialRequest extractCredentialRequest(Map<String, Object> map, String path) {
        String credentialsKey = extractOrThrow(map, path + ".key");
        boolean granularPermission = extractOrDefault(map, path + ".granularPermission", false);

        return new CredentialRequest(credentialsKey, granularPermission);
    }

    @SneakyThrows
    private void removeInvalidMessages(String expectationCaseId) {

        log.info("Checking Invalid Messages for caseId: " + expectationCaseId);
        String actualMessageResponse = restMessageService.getCaseMessages(expectationCaseId);

        Map<String, Object> actualResponse = MapSerializer.deserialize(actualMessageResponse);

        List<Map<String, Object>> messagesSent = new ArrayList<>(Objects.requireNonNull(
            MapValueExtractor.extract(actualResponse, "caseEventMessages")));

        messagesSent.forEach(messageData -> {
            String state = MapValueExtractor.extract(messageData, "State");
            log.info("State: " + state);
            if ("UNPROCESSABLE".equals(state)) {
                String messageId = MapValueExtractor.extract(messageData, "MessageId");
                String caseId = MapValueExtractor.extract(messageData, "CaseId");
                log.info("Found UNPROCESSABLE messageId: " + messageId + " caseId:" + caseId);
                restMessageService.deleteMessage(messageId, caseId);
            }
        });
    }

    private String buildMessageExpectationResponseBody(Map<String, Object> clauseValues,
                                                       Map<String, String> additionalValues)
        throws IOException {

        Map<String, Object> scenario = deserializeValuesUtil.expandMapValues(clauseValues, additionalValues);
        Map<String, Object> roleData = MapValueExtractor.extract(scenario, "messageData");
        return MapSerializer.serialize(roleData);
    }


    private void loadPropertiesIntoMapValueExpander() {

        MutablePropertySources propertySources = ((AbstractEnvironment) environment).getPropertySources();
        StreamSupport
            .stream(propertySources.spliterator(), false)
            .filter(EnumerablePropertySource.class::isInstance)
            .map(propertySource -> ((EnumerablePropertySource) propertySource).getPropertyNames())
            .flatMap(Arrays::stream)
            .forEach(name -> ENVIRONMENT_PROPERTIES.setProperty(name, environment.getProperty(name)));
    }

    private void addSearchParameters(TestScenario scenario, Map<String, Object> scenarioValues) {

        List<Map<String, Object>> searchParameterObjects = new ArrayList<>();
        searchParameterObjects = extractOrDefault(scenarioValues, "searchParameters", searchParameterObjects);
        searchParameterObjects.forEach(scenario::addSearchMap);

    }
}
