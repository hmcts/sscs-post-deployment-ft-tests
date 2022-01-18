package uk.gov.hmcts.reform.sscspostdeploymentfttests;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.Headers;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.TestScenario;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.taskretriever.TaskRetrieverEnum;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.preparers.Preparer;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AzureMessageInjector;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.CcdCaseCreator;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.taskretriever.CamundaTaskRetrieverService;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.taskretriever.TaskMgmApiRetrieverService;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.DeserializeValuesUtil;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.Logger;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapSerializer;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.StringResourceLoader;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.verifiers.TaskDataVerifier;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.verifiers.Verifier;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertFalse;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_BEFORE_COMPLETED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_BEFORE_FOUND;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_DISABLED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_ENABLED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_FINISHED;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_RUNNING;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_START;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_SUCCESSFUL;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExpander.ENVIRONMENT_PROPERTIES;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor.extractOrDefault;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor.extractOrThrow;

@Slf4j
public class ScenarioRunnerTest extends SpringBootFunctionalBaseTest {

    @Autowired
    protected AzureMessageInjector azureMessageInjector;
    @Autowired
    protected TaskDataVerifier taskDataVerifier;
    @Autowired
    protected AuthorizationHeadersProvider authorizationHeadersProvider;
    @Autowired
    private CamundaTaskRetrieverService camundaTaskRetrievableService;
    @Autowired
    private TaskMgmApiRetrieverService taskMgmApiRetrievableService;
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

    @Before
    public void setUp() {
        MapSerializer.setObjectMapper(objectMapper);
    }

    @Test
    public void scenarios_should_behave_as_specified() throws IOException, URISyntaxException {

        loadPropertiesIntoMapValueExpander();

        for (Preparer preparer : preparers) {
            preparer.prepare();
        }

        assertFalse("Verifiers configured successfully", verifiers.isEmpty());

        URL path = getClass().getClassLoader().getResource("scenarios");
        File[] directories = new File(path.toURI()).listFiles(File::isDirectory);
        Objects.requireNonNull(directories, "No directories found under 'scenarios'");

        for (File directory : directories) {
            runAllScenariosFor(directory.getName());
        }

    }

    private void runAllScenariosFor(String directoryName) throws IOException {
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
                continue;
            } else {
                Logger.say(SCENARIO_ENABLED, description);

                Map<String, Object> beforeClauseValues = extractOrDefault(scenarioValues, "before", null);
                Map<String, Object> testClauseValues = extractOrThrow(scenarioValues, "test");


                String scenarioJurisdiction = extractOrThrow(scenarioValues, "jurisdiction");
                String caseType = extractOrThrow(scenarioValues, "caseType");

                TestScenario scenario = new TestScenario(
                    scenarioValues,
                    scenarioSource,
                    scenarioJurisdiction,
                    caseType,
                    beforeClauseValues,
                    testClauseValues
                );
                createBaseCcdCase(scenario);

                if (scenario.getBeforeClauseValues() != null) {
                    Logger.say(SCENARIO_BEFORE_FOUND);
                    //If before was found process with before values
                    processBeforeClauseScenario(scenario);
                    Logger.say(SCENARIO_BEFORE_COMPLETED);

                }
                Logger.say(SCENARIO_RUNNING);

                processTestClauseScenario(scenario);

                Logger.say(SCENARIO_SUCCESSFUL, description);
                Logger.say(SCENARIO_FINISHED);
            }

        }
    }

    private void processBeforeClauseScenario(TestScenario scenario) throws IOException {
        processScenario(scenario.getBeforeClauseValues(), scenario);
    }

    private void processTestClauseScenario(TestScenario scenario) throws IOException {
        processScenario(scenario.getTestClauseValues(), scenario);
    }

    private void createBaseCcdCase(TestScenario scenario) throws IOException {
        Map<String, Object> scenarioValues = scenario.getScenarioMapValues();
        String requestCredentials = extractOrThrow(scenarioValues, "required.credentials");

        Headers requestAuthorizationHeaders = getAuthorizationHeaders(requestCredentials);

        scenario.setRequestAuthorizationHeaders(requestAuthorizationHeaders);


        String caseId = ccdCaseCreator.createCase(
            scenarioValues,
            scenario.getJurisdiction(),
            requestAuthorizationHeaders
        );

        scenario.setCaseId(caseId);
    }

    private void processScenario(Map<String, Object> values, TestScenario scenario) throws IOException {

        azureMessageInjector.injectMessage(
            values,
            scenario.getCaseId(),
            scenario.getJurisdiction(),
            scenario.getRequestAuthorizationHeaders()
        );

        String taskRetrieverOption = MapValueExtractor.extract(
            values,
            "options.taskRetrievalApi"
        );

        String expectationCredentials = extractOrThrow(values, "expectation.credentials");
        Headers expectationAuthorizationHeaders = getAuthorizationHeaders(expectationCredentials);
        scenario.setExpectationAuthorizationHeaders(expectationAuthorizationHeaders);

        if (TaskRetrieverEnum.CAMUNDA_API.getId().equals(taskRetrieverOption)) {
            camundaTaskRetrievableService.retrieveTask(
                values,
                scenario
            );
        } else {
            taskMgmApiRetrievableService.retrieveTask(
                values,
                scenario
            );
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

    private Headers getAuthorizationHeaders(String credentials) {
        switch (credentials) {
            case "WaSystemUser":
                return authorizationHeadersProvider.getWaSystemUserAuthorization();
            case "SSCSSystemUpdateUser":
                return authorizationHeadersProvider.getSscsSystemUserAuthorization();
            case "SSCSCaseWorker":
                return authorizationHeadersProvider.getSscsCaseWorkerAuthorization();
            default:
                throw new IllegalStateException("Credentials implementation for '" + credentials + "' not found");
        }

    }
}
