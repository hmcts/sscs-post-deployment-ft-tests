package uk.gov.hmcts.reform.sscspostdeploymentfttests;


import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import io.restassured.http.Headers;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionEvaluationLogger;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.test.context.junit.jupiter.SpringExtension;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider.AUTHORIZATION;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider.SERVICE_AUTHORIZATION;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.CaseIdUtil.addAssignedCaseId;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_BEFORE_COMPLETED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_BEFORE_FOUND;
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
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SUMMARY_SCENARIOS_RAN;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExpander.ENVIRONMENT_PROPERTIES;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor.extractOrDefault;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor.extractOrThrow;

@Slf4j
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Application.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ScenarioRunnerTest extends SpringBootFunctionalBaseTest {

    protected MessageInjector messageInjector;
    protected TaskDataVerifier taskDataVerifier;
    protected AuthorizationHeadersProvider authorizationHeadersProvider;
    private final CamundaTaskRetrieverService camundaTaskRetrievableService;
    private final TaskMgmApiRetrieverService taskMgmApiRetrievableService;
    private final RoleAssignmentService roleAssignmentService;
    private final TaskManagementService taskManagementService;
    private final Environment environment;
    private final DeserializeValuesUtil deserializeValuesUtil;
    private final ObjectMapper objectMapper;
    private final List<Verifier> verifiers;
    private final List<Preparer> preparers;
    private final CcdCaseCreator ccdCaseCreator;
    private final RestMessageService restMessageService;
    @Value("${wa-post-deployment-test.environment}")
    protected String postDeploymentTestEnvironment;
    private final ArrayList<String> failedScenarios = new ArrayList<>();
    private final ArrayList<String> passedScenarios = new ArrayList<>();
    private StopWatch stopWatch;
    @Value("${scenarioRunner.retryCount}")
    private int retryCount;

    @Autowired
    public ScenarioRunnerTest(
        MessageInjector messageInjector,
        TaskDataVerifier taskDataVerifier,
        AuthorizationHeadersProvider authorizationHeadersProvider,
        CamundaTaskRetrieverService camundaTaskRetrievableService,
        TaskMgmApiRetrieverService taskMgmApiRetrievableService,
        RoleAssignmentService roleAssignmentService,
        TaskManagementService taskManagementService,
        Environment environment,
        DeserializeValuesUtil deserializeValuesUtil,
        ObjectMapper objectMapper,
        List<Verifier> verifiers,
        List<Preparer> preparers,
        CcdCaseCreator ccdCaseCreator,
        RestMessageService restMessageService
    ) {
        this.messageInjector = messageInjector;
        this.taskDataVerifier = taskDataVerifier;
        this.authorizationHeadersProvider = authorizationHeadersProvider;
        this.camundaTaskRetrievableService = camundaTaskRetrievableService;
        this.taskMgmApiRetrievableService = taskMgmApiRetrievableService;
        this.roleAssignmentService = roleAssignmentService;
        this.taskManagementService = taskManagementService;
        this.environment = environment;
        this.deserializeValuesUtil = deserializeValuesUtil;
        this.objectMapper = objectMapper;
        this.verifiers = verifiers;
        this.preparers = preparers;
        this.ccdCaseCreator = ccdCaseCreator;
        this.restMessageService = restMessageService;
    }

    @BeforeAll
    public void beforeAll() throws Exception {
        stopWatch = new StopWatch();
        stopWatch.start();
        loadPropertiesIntoMapValueExpander();

        for (Preparer preparer : preparers) {
            preparer.prepare();
        }

        Assertions.assertFalse(verifiers.isEmpty(), "Verifiers configured successfully");
        MapSerializer.setObjectMapper(objectMapper);
        JsonUtil.setObjectMapper(objectMapper);
    }

    @AfterAll
    public void tearDown() {
        if (!failedScenarios.isEmpty()) {
            StringBuilder sb = new StringBuilder("Failed scenarios:\n=========================================");
            failedScenarios.forEach(scenario -> sb.append("\n").append(scenario));
            throw new RuntimeException(sb.toString());
        }

        stopWatch.stop();
        Logger.say(SCENARIO_RUNNING_TIME, stopWatch.getTotalTimeSeconds());
        StringBuilder sb = new StringBuilder();
        passedScenarios.forEach(scenario -> sb.append("\n").append(scenario));
        Logger.say(SUMMARY_SCENARIOS_RAN, sb.toString());
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("ctscScenarios")
    public void ctsc_scenarios_should_behave_as_specified(String scenarioSource) throws Exception {
        Assumptions.assumeTrue(scenarioSource != null, "Skipping CTSC scenarios");
        runScenarioBySource(scenarioSource, retryCount);
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("judgeScenarios")
    @Disabled("No judge scenarios yet")
    public void judge_scenarios_should_behave_as_specified(String scenarioSource) throws Exception {
        Assumptions.assumeTrue(scenarioSource != null, "Skipping Judge scenarios");
        runScenarioBySource(scenarioSource, retryCount);
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("legalOfficerScenarios")
    @Disabled("No legal officer scenarios yet")
    public void lo_scenarios_should_behave_as_specified(String scenarioSource) throws Exception {
        Assumptions.assumeTrue(scenarioSource != null, "Skipping Legal Officer scenarios");
        runScenarioBySource(scenarioSource, retryCount);
    }

    static Stream<Arguments> ctscScenarios()  {
        return caseTypeScenarios("CTSC");
    }

    static Stream<Arguments> judgeScenarios() {
        return caseTypeScenarios("Judge");
    }

    static Stream<Arguments> legalOfficerScenarios() {
        return caseTypeScenarios("LegalOfficer");
    }

    static Stream<Arguments> caseTypeScenarios(String scenarioFolder) {
        String enabledUserRoles = System.getProperty("enabledUserRoles");
        List<String> enabledUserRoleList;
        if (enabledUserRoles == null || enabledUserRoles.isBlank()) {
            log.info("No roles specified, running all user roles");
            enabledUserRoleList = new ArrayList<>(List.of("CTSC", "Judge", "LegalOfficer"));
        } else {
            enabledUserRoleList = Arrays.stream(enabledUserRoles.split(","))
                .map(String::trim)
                .toList();
        }

        if (!enabledUserRoleList.contains(scenarioFolder)) {
            log.info("{} user role is disabled", scenarioFolder);
            return Stream.of(Arguments.of(Named.of(scenarioFolder + " is disabled", null)));
        }

        String scenarioPattern = System.getProperty("scenario");
        if (scenarioPattern == null) {
            scenarioPattern = "*.json";
        } else {
            scenarioPattern = "*" + scenarioPattern + "*.json";
        }

        Collection<String> scenarioSources;
        try {
            scenarioSources = StringResourceLoader
                .load("/scenarios/sscs/" + scenarioFolder + "/" + scenarioPattern)
                .values();
        } catch (IOException exception) {
            log.info("No scenarios found at {}", scenarioFolder);
            return Stream.of(Arguments.of(Named.of(scenarioFolder + " is empty", null)));
        }

        Logger.say(SCENARIO_START, scenarioSources.size() + " SSCS");

        return scenarioSources.stream().map(scenarioSource -> {
            String displayName;
            try {
                Map<String, Object> scenarioValues = MapSerializer.deserialize(scenarioSource);
                displayName = scenarioFolder + "-" + extractOrDefault(scenarioValues, "description", "Unnamed scenario");
            } catch (IOException e) {
                displayName = "Unnamed " + scenarioFolder + " scenario";
            }

            return Arguments.of(Named.of(displayName, scenarioSource));
        });
    }

    private void runScenarioBySource(String scenarioSource, int retryCount) throws Exception {
        String description = "";
        for (int i = 0; i < retryCount; i++) {
            try {
                Map<String, Object> scenarioValues = deserializeValuesUtil
                    .deserializeStringWithExpandedValues(scenarioSource, emptyMap());

                description = extractOrDefault(scenarioValues, "description", "Unnamed scenario");
                Boolean scenarioEnabled;
                try {
                    scenarioEnabled = extractOrDefault(scenarioValues, "enabled", true);
                } catch (ClassCastException e) {
                    scenarioEnabled = Boolean.parseBoolean(extractOrDefault(scenarioValues, "enabled", "true"));
                }

                Assumptions.assumeTrue(scenarioEnabled, "ℹ️ SCENARIO: " + description + " **disabled**");
                Logger.say(SCENARIO_ENABLED, description);

                Map<String, Object> beforeClauseValues = extractOrDefault(scenarioValues, "before", null);
                Map<String, Object> testClauseValues = Objects.requireNonNull(
                    MapValueExtractor.extract(scenarioValues, "test"));
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

                failedScenarios.removeIf(description::equals);
                passedScenarios.add(description);
                Logger.say(SCENARIO_FINISHED);
                break; // Exit the retry loop if successful or disabled
            } catch (Error | FeignException | NullPointerException | ConditionTimeoutException | JsonParseException e) {
                log.error("Scenario {} failed with error {}", description, e.getMessage());
                if (!failedScenarios.contains(description)) {
                    failedScenarios.add(description);
                }
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
        processScenario(scenario.getTestClauseValues(), scenario);
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

            verifyMessages(expectationValue, expectedMessages, expectationCaseIds.getFirst());

            removeInvalidMessages(expectationCaseIds.getFirst());
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
                    expectationCaseIds.getFirst(),
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

        log.info("Checking Invalid Messages for caseId: {}", expectationCaseId);
        String actualMessageResponse = restMessageService.getCaseMessages(expectationCaseId);

        Map<String, Object> actualResponse = MapSerializer.deserialize(actualMessageResponse);

        List<Map<String, Object>> messagesSent = new ArrayList<>(Objects.requireNonNull(
            MapValueExtractor.extract(actualResponse, "caseEventMessages")));

        messagesSent.forEach(messageData -> {
            String state = MapValueExtractor.extract(messageData, "State");
            log.info("State: {}", state);
            if ("UNPROCESSABLE".equals(state)) {
                String messageId = MapValueExtractor.extract(messageData, "MessageId");
                String caseId = MapValueExtractor.extract(messageData, "CaseId");
                log.info("Found UNPROCESSABLE messageId: {} caseId:{}", messageId, caseId);
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
