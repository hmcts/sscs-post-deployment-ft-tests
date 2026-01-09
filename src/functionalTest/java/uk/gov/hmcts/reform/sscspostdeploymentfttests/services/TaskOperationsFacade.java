package uk.gov.hmcts.reform.sscspostdeploymentfttests.services;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider.AUTHORIZATION;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider.SERVICE_AUTHORIZATION;

import io.restassured.http.Headers;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.TestScenario;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.CredentialRequest;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.UserInfo;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.taskretriever.CamundaTaskRetrieverService;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.services.taskretriever.TaskMgmApiRetrieverService;

@Component
public class TaskOperationsFacade {
    protected AuthorizationHeadersProvider authHeadersProvider;
    private final CamundaTaskRetrieverService camundaTaskRetrieverService;
    private final TaskMgmApiRetrieverService taskMgmApiRetrieverService;
    private final TaskManagementService taskManagementService;
    private final RoleAssignmentService roleAssignmentService;

    public TaskOperationsFacade(AuthorizationHeadersProvider authHeadersProvider, CamundaTaskRetrieverService camundaTaskRetrieverService,
                                TaskMgmApiRetrieverService taskMgmApiRetrieverService,
                                TaskManagementService taskManagementService, RoleAssignmentService roleAssignmentService) {
        this.authHeadersProvider = authHeadersProvider;
        this.camundaTaskRetrieverService = camundaTaskRetrieverService;
        this.taskMgmApiRetrieverService = taskMgmApiRetrieverService;
        this.taskManagementService = taskManagementService;
        this.roleAssignmentService = roleAssignmentService;
    }

    public void retrieveByCamunda(Map<String, Object> clauseValues, TestScenario scenario, String caseId,
                                  Headers authorizationHeaders) {
        camundaTaskRetrieverService.retrieveTask(clauseValues, scenario, caseId, authorizationHeaders);
    }

    public void retrieveByTaskMgm(Map<String, Object> clauseValues, TestScenario scenario, List<String> caseIds,
                                  Headers authorizationHeaders) {
        taskMgmApiRetrieverService.retrieveTask(clauseValues, scenario, caseIds, authorizationHeaders);
    }

    public void claimTask(TestScenario scenario, Headers authorizationHeaders, UserInfo userInfo) {
        taskManagementService.claimTask(scenario, authorizationHeaders, userInfo);
    }

    public void assignTask(TestScenario scenario, Headers authorizationHeaders, UserInfo assignee) {
        taskManagementService.assignTask(scenario, authorizationHeaders, assignee);
    }

    public void completeTask(TestScenario scenario, Headers authorizationHeaders, UserInfo userInfo) {
        taskManagementService.completeTask(scenario, authorizationHeaders, userInfo);
    }

    public void processRoleAssignment(Map<String, Object> postRoleAssignmentClauseValues, TestScenario scenario)
        throws IOException {
        Map<String, Object> postRoleAssignmentValues = scenario.getPostRoleAssignmentClauseValues();

        CredentialRequest credentialRequest = authHeadersProvider.extractCredentialRequest(
            postRoleAssignmentValues, "credentials");

        Headers requestAuthorizationHeaders = authHeadersProvider.getWaUserAuthorization(credentialRequest);

        String userToken = requestAuthorizationHeaders.getValue(AUTHORIZATION);
        String serviceToken = requestAuthorizationHeaders.getValue(SERVICE_AUTHORIZATION);
        UserInfo userInfo = authHeadersProvider.getUserInfo(userToken);

        roleAssignmentService.processRoleAssignments(
            scenario,
            postRoleAssignmentClauseValues, userToken, serviceToken, userInfo);
    }
}
