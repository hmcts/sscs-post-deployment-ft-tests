package uk.gov.hmcts.reform.sscspostdeploymentfttests;

import groovy.util.logging.Slf4j;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.Logger;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapSerializer;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.StringResourceLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.LoggerMessage.SCENARIO_START;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor.extractOrDefault;

@Slf4j
public class ScenarioSources {

    private ScenarioSources() {
        // Needs a private constructor
    }

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ScenarioSources.class);

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
                displayName = scenarioFolder + "-"
                    + extractOrDefault(scenarioValues, "description", "Unnamed scenario");
            } catch (IOException e) {
                displayName = "Unnamed " + scenarioFolder + " scenario";
            }

            return Arguments.of(Named.of(displayName, scenarioSource));
        });
    }
}
