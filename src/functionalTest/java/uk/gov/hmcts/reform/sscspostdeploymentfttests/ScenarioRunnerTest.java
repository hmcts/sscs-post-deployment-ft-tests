package uk.gov.hmcts.reform.sscspostdeploymentfttests;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import io.restassured.http.Headers;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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
import uk.gov.hmcts.reform.sscspostdeploymentfttests.preparers.Preparer;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.CcdCaseCreator;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.MessageProcessingFacade;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.TaskOperationsFacade;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.VerifierService;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.CaseIdUtil;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.DeserializeValuesUtil;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.JsonUtil;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.Logger;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapSerializer;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.verifiers.Verifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyMap;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider.AUTHORIZATION;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.CaseIdUtil.addAssignedCaseId;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_BEFORE_COMPLETED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_BEFORE_FOUND;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_ENABLED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_FINISHED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_ROLE_ASSIGNMENT_COMPLETED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_ROLE_ASSIGNMENT_FOUND;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_RUNNING;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_RUNNING_TIME;
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

    protected AuthorizationHeadersProvider authorizationHeadersProvider;
    private final MessageProcessingFacade messageFacade;
    private final TaskOperationsFacade taskFacade;
    private final VerifierService verifierService;
    private final Environment environment;
    private final DeserializeValuesUtil deserializeValuesUtil;
    private final ObjectMapper objectMapper;
    private final List<Verifier> verifiers;
    private final List<Preparer> preparers;
    private final CcdCaseCreator ccdCaseCreator;

    @Value("${wa-post-deployment-test.environment}")
    protected String postDeploymentTestEnvironment;

    private final ArrayList<String> failedScenarios = new ArrayList<>();
    private final ArrayList<String> passedScenarios = new ArrayList<>();
    private StopWatch stopWatch;

    @Value("${scenarioRunner.retryCount}")
    private int retryCount;

    @Autowired
    public ScenarioRunnerTest(
        AuthorizationHeadersProvider authorizationHeadersProvider,
        Environment environment,
        DeserializeValuesUtil deserializeValuesUtil,
        ObjectMapper objectMapper,
        List<Verifier> verifiers,
        List<Preparer> preparers,
        CcdCaseCreator ccdCaseCreator,
        MessageProcessingFacade messageFacade,
        TaskOperationsFacade taskFacade,
        VerifierService verifierService
    ) {
        this.authorizationHeadersProvider = authorizationHeadersProvider;
        this.environment = environment;
        this.deserializeValuesUtil = deserializeValuesUtil;
        this.objectMapper = objectMapper;
        this.verifiers = verifiers;
        this.preparers = preparers;
        this.ccdCaseCreator = ccdCaseCreator;
        this.messageFacade = messageFacade;
        this.taskFacade = taskFacade;
        this.verifierService = verifierService;
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
    @MethodSource("uk.gov.hmcts.reform.sscspostdeploymentfttests.ScenarioSources#ctscScenarios")
    public void ctsc_scenarios_should_behave_as_specified(String scenarioSource) throws Exception {
        Assumptions.assumeTrue(scenarioSource != null, "Skipping CTSC scenarios");
        runScenarioBySource(scenarioSource, retryCount);
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("uk.gov.hmcts.reform.sscspostdeploymentfttests.ScenarioSources#judgeScenarios")
    //@Disabled("No judge scenarios yet")
    public void judge_scenarios_should_behave_as_specified(String scenarioSource) throws Exception {
        Assumptions.assumeTrue(scenarioSource != null, "Skipping Judge scenarios");
        runScenarioBySource(scenarioSource, retryCount);
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("uk.gov.hmcts.reform.sscspostdeploymentfttests.ScenarioSources#legalOfficerScenarios")
    //@Disabled("No legal officer scenarios yet")
    public void lo_scenarios_should_behave_as_specified(String scenarioSource) throws Exception {
        Assumptions.assumeTrue(scenarioSource != null, "Skipping Legal Officer scenarios");
        runScenarioBySource(scenarioSource, retryCount);
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
                    taskFacade.processRoleAssignment(postRoleAssignmentClauseValues, scenario);
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

    private void processBeforeClauseScenario(TestScenario scenario) throws Exception {
        processScenario(scenario.getBeforeClauseValues(), scenario);
    }

    private void processTestClauseScenario(TestScenario scenario) throws Exception {
        processScenario(scenario.getTestClauseValues(), scenario);
    }

    private void createBaseCcdCase(TestScenario scenario) throws IOException {
        Map<String, Object> scenarioValues = scenario.getScenarioMapValues();

        CredentialRequest credentialRequest = authorizationHeadersProvider.extractCredentialRequest(
            scenarioValues, "required.credentials");

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

        CredentialRequest credentialRequest = authorizationHeadersProvider.extractCredentialRequest(
            scenarioValues, "updateCase.credentials");
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

            verifierService.verifyTasks(scenario, taskRetrieverOption, expectationValue,
                                        expectedTasks, expectationCaseIds);
            verifierService.verifyMessages(verifiers, expectationValue, expectedMessages,
                                           expectationCaseIds.getFirst());
            messageFacade.removeInvalidMessages(expectationCaseIds.getFirst());
        }
    }

    private void processTestRequest(Map<String, Object> values, TestScenario scenario) throws Exception {

        Map<String, Object> request = extractOrThrow(values, "request");

        TestRequestType requestType = TestRequestType.valueOf(MapValueExtractor.extractOrDefault(
            request, "type", "MESSAGE"));

        CredentialRequest credentialRequest = authorizationHeadersProvider.extractCredentialRequest(
            request, "credentials");
        Headers requestAuthorizationHeaders = authorizationHeadersProvider.getWaUserAuthorization(credentialRequest);

        String userToken = requestAuthorizationHeaders.getValue(AUTHORIZATION);
        UserInfo userInfo = authorizationHeadersProvider.getUserInfo(userToken);

        log.info("{} request", requestType);
        switch (requestType) {
            case MESSAGE:
                messageFacade.injectMessage(
                    request,
                    scenario,
                    userInfo
                );
                break;
            case CLAIM:
                taskFacade.claimTask(scenario, requestAuthorizationHeaders, userInfo);
                break;
            case ASSIGN:
                UserInfo assignee = authorizationHeadersProvider.getAssigneeInfo(request);
                taskFacade.assignTask(scenario, requestAuthorizationHeaders, assignee);
                break;
            case COMPLETE:
                taskFacade.completeTask(scenario, requestAuthorizationHeaders, userInfo);
                break;
            default:
                throw new Exception("Invalid request type [" + requestType + "]");
        }
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
