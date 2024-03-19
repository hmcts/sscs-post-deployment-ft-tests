package uk.gov.hmcts.reform.sscspostdeploymentfttests.services;

import io.restassured.http.Header;
import io.restassured.http.Headers;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.clients.IdamWebApi;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.CredentialRequest;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.RoleCode;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.UserInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Service
public class AuthorizationHeadersProvider  implements AuthorizationHeaders {

    public static final String AUTHORIZATION = "Authorization";
    public static final String SERVICE_AUTHORIZATION = "ServiceAuthorization";
    private static final String WA_USER_PASSWORD = "System01";

    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    private final Map<String, UserInfo> userInfo = new ConcurrentHashMap<>();
    private final Map<String, String> testUserAccounts = new ConcurrentHashMap<>();
    @Value("${idam.redirectUrl}")
    protected String idamRedirectUrl;
    @Value("${idam.scope}")
    protected String userScope;
    @Value("${spring.security.oauth2.client.registration.oidc.client-id}")
    protected String idamClientId;
    @Value("${spring.security.oauth2.client.registration.oidc.client-secret}")
    protected String idamClientSecret;
    @Value("${idam.test.userCleanupEnabled:false}")
    private boolean testUserDeletionEnabled;

    @Autowired
    private IdamWebApi idamWebApi;

    @Autowired
    private AuthTokenGenerator serviceAuthTokenGenerator;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Override
    public Headers getWaSystemUserAuthorization() {
        return getSystemUserAuthorization("WaSystemUser", "WA_SYSTEM_USERNAME", "WA_SYSTEM_PASSWORD");
    }

    @Override
    public Headers getSscsSystemUserAuthorization() {
        return getSystemUserAuthorization("SscsSystemUser", "SYSTEMUPDATE_USERNAME", "SYSTEMUPDATE_PASSWORD");
    }

    public Headers getSystemUserAuthorization(String key, String envUsername, String envPassword) {
        return new Headers(
            getUserAuthorizationHeader(
                key,
                System.getenv(envUsername),
                System.getenv(envPassword)
            ),
            getServiceAuthorizationHeader()
        );
    }

    @Override
    public Headers getWaUserAuthorization(CredentialRequest request) throws IOException {
        if ("WaSystemUser".equals(request.getCredentialsKey())) {
            return getSystemUserAuthorization("WaSystemUser", "WA_SYSTEM_USERNAME", "WA_SYSTEM_PASSWORD");
        } else if ("systemupdate".equals(request.getCredentialsKey())) {
            return getSystemUserAuthorization("SscsSystemUser", "SYSTEMUPDATE_USERNAME", "SYSTEMUPDATE_PASSWORD");
        } else {

            String userEmail = findOrGenerateUserAccount(request.getCredentialsKey(), request.isGranularPermission());

            return new Headers(
                getUserAuthorizationHeader(request.getCredentialsKey(), userEmail),
                getServiceAuthorizationHeader()
            );
        }
    }

    @Override
    public UserInfo getUserInfo(String userToken) {
        return userInfo.computeIfAbsent(
            userToken,
            user -> idamWebApi.userInfo(userToken)
        );
    }

    @Override
    public void cleanupTestUsers() {
        testUserAccounts.entrySet().forEach(entry -> {
                                                Headers headers =  new Headers(
                                                    getUserAuthorizationHeader(entry.getKey(), entry.getValue()),
                                                    getServiceAuthorizationHeader()
                                                );
                                                UserInfo userInfo = getUserInfo(headers.getValue(AUTHORIZATION));
                                                roleAssignmentService.clearAllRoleAssignments(headers, userInfo);
                                                deleteAccount(entry.getValue());
                                            }
        );
    }

    private void deleteAccount(String username) {
        if (testUserDeletionEnabled) {
            log.info("Deleting test account '{}'", username);
            idamWebApi.deleteTestUser(username);
        } else {
            log.info("Test User deletion feature flag was not enabled, user '{}' was not deleted", username);
        }
    }

    private Header getServiceAuthorizationHeader() {
        String serviceToken = tokens.computeIfAbsent(
            SERVICE_AUTHORIZATION,
            user -> serviceAuthTokenGenerator.generate()
        );

        return new Header(SERVICE_AUTHORIZATION, serviceToken);
    }

    private Header getUserAuthorizationHeader(String key, String username) {
        return getUserAuthorizationHeader(key, username, WA_USER_PASSWORD);
    }

    private Header getUserAuthorizationHeader(String key, String username, String password) {

        MultiValueMap<String, String> body = createIdamRequest(username, password);

        String accessToken = tokens.computeIfAbsent(
            key,
            user -> "Bearer " + idamWebApi.token(body).getAccessToken()
        );
        return new Header(AUTHORIZATION, accessToken);
    }


    private MultiValueMap<String, String> createIdamRequest(String username, String password) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", idamClientId);
        body.add("client_secret", idamClientSecret);
        body.add("username", username);
        body.add("password", password);
        body.add("scope", userScope);
        return body;
    }

    private String findOrGenerateUserAccount(String credentialsKey, boolean granularPermission) throws IOException {
        return testUserAccounts.computeIfAbsent(
            credentialsKey,
            user -> generateUserAccount(credentialsKey, granularPermission)
        );
    }

    private String generateUserAccount(String credentialsKey, boolean granularPermission) {
        String emailPrefix = granularPermission ? "wa-ft" : "sscs-";
        String userEmail = emailPrefix + UUID.randomUUID() + "@fake.hmcts.net";

        List<RoleCode> requiredRoles = new ArrayList<>(List.of(
            new RoleCode("caseworker"),
            new RoleCode("caseworker-sscs")
        ));

        log.info("Attempting to create a new test account {}", userEmail);

        Map<String, Object> body = requestBody(userEmail, requiredRoles);

        idamWebApi.createTestUser(body);

        log.info("Test account created successfully");

        List<String> roleAssignments = findRoleAssignmentRequirements(credentialsKey);
        log.info("Assigning role assignments {}", roleAssignments);

        for (String r : roleAssignments) {
            assignRoleAssignment(userEmail, credentialsKey, r);
        }

        return userEmail;
    }

    @NotNull
    private Map<String, Object> requestBody(String userEmail, List<RoleCode> requiredRoles) {
        RoleCode userGroup = new RoleCode("caseworker");

        Map<String, Object> body = new ConcurrentHashMap<>();
        body.put("email", userEmail);
        body.put("password", WA_USER_PASSWORD);
        body.put("forename", "SSCS");
        body.put("surname", "Functional");
        body.put("roles", requiredRoles);
        body.put("userGroup", userGroup);
        return body;
    }

    private List<String> findRoleAssignmentRequirements(String credentialsKey) {
        List<String> roleAssignments = new ArrayList<>();
        switch (credentialsKey) {
            case "superuser":
            case "systemupdate":
                break;
            case "caseworker":
                roleAssignments.add("tribunal-caseworker");
                break;
            case "judge":
                roleAssignments.add("judge");
                break;
            case "CTSC-Administrator":
                roleAssignments.add("ctsc");
                break;
            case "Regional-Centre-Admin":
                roleAssignments.add("regional-centre-admin");
                break;
            default:
                throw new IllegalStateException("Credentials implementation for '" + credentialsKey + "' not found");
        }
        return roleAssignments;
    }

    private void assignRoleAssignment(String userEmail, String credentialsKey, String roleName) {
        Headers authenticationHeaders = new Headers(
            getUserAuthorizationHeader(credentialsKey, userEmail),
            getServiceAuthorizationHeader()
        );
        UserInfo userInfo = getUserInfo(authenticationHeaders.getValue(AUTHORIZATION));
        roleAssignmentService.setupRoleAssignment(authenticationHeaders, userInfo, roleName);
    }
}
