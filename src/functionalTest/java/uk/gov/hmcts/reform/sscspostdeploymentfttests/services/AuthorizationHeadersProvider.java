package uk.gov.hmcts.reform.sscspostdeploymentfttests.services;

import io.restassured.http.Header;
import io.restassured.http.Headers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.clients.IdamWebApi;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.CredentialRequest;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.UserInfo;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Service
public class AuthorizationHeadersProvider  implements AuthorizationHeaders {

    public static final String AUTHORIZATION = "Authorization";
    public static final String SERVICE_AUTHORIZATION = "ServiceAuthorization";

    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    private final Map<String, UserInfo> userInfo = new ConcurrentHashMap<>();
    @Value("${idam.redirectUrl}")
    protected String idamRedirectUrl;
    @Value("${idam.scope}")
    protected String userScope;
    @Value("${spring.security.oauth2.client.registration.oidc.client-id}")
    protected String idamClientId;
    @Value("${spring.security.oauth2.client.registration.oidc.client-secret}")
    protected String idamClientSecret;

    @Autowired
    private IdamWebApi idamWebApi;

    @Autowired
    private AuthTokenGenerator serviceAuthTokenGenerator;

    @Override
    public Headers getWaSystemUserAuthorization() {
        return new Headers(
            getUserAuthorizationHeader(
                "WaSystemUser",
                System.getenv("WA_SYSTEM_USERNAME"),
                System.getenv("WA_SYSTEM_PASSWORD")
            ),
            getServiceAuthorizationHeader()
        );
    }

    @Override
    public Headers getWaUserAuthorization(CredentialRequest request) throws IOException {
        if ("WaSystemUser".equals(request.getCredentialsKey())) {
            return getWaSystemUserAuthorization();
        } else {
            return new Headers(
                getUserAuthorizationHeader(request.getCredentialsKey()),
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
    }

    @Override
    public Header getUserAuthorizationHeader(String credentials) {
        switch (credentials) {
            case "citizen":
                return getUserAuthorization(credentials, "CITIZEN_USERNAME", "CITIZEN_PASSWORD");
            case "caseworker":
                return getUserAuthorization(credentials, "CASEWORKER_USERNAME", "CASEWORKER_PASSWORD");
            case "clerk":
                return getUserAuthorization(credentials, "CLERK_USERNAME", "CLERK_PASSWORD");
            case "judge":
                return getUserAuthorization(credentials, "JUDGE_USERNAME", "JUDGE_PASSWORD");
            case "superuser":
                return getUserAuthorization(credentials, "SUPERUSER_USERNAME", "SUPERUSER_PASSWORD");
            case "systemupdate":
                return getUserAuthorization(credentials, "SYSTEMUPDATE_USERNAME", "SYSTEMUPDATE_PASSWORD");
            case "CTSC-Administrator":
                return getUserAuthorization(credentials, "CTSC_ADMINISTRATOR_USERNAME", "CTSC_ADMINISTRATOR_PASSWORD");
            case "Regional-Centre-Admin":
                return getUserAuthorization(credentials, "REGIONAL_CENTRE_ADMIN_USERNAME", "REGIONAL_CENTRE_ADMIN_PASSWORD");
            case "Tribunal-Member-1":
                return getUserAuthorization(credentials, "TRIBUNAL_MEMBER_1_USERNAME", "TRIBUNAL_MEMBER_1_PASSWORD");
            case "Tribunal-Member-2":
                return getUserAuthorization(credentials, "TRIBUNAL_MEMBER_2_USERNAME", "TRIBUNAL_MEMBER_2_PASSWORD");
            case "Tribunal-Member-3":
                return getUserAuthorization(credentials, "TRIBUNAL_MEMBER_3_USERNAME", "TRIBUNAL_MEMBER_3_PASSWORD");
            case "appraiser-1":
                return getUserAuthorization(credentials, "APPRAISER_1_USERNAME", "APPRAISER_1_PASSWORD");
            case "appraiser-2":
                return getUserAuthorization(credentials, "APPRAISER_2_USERNAME", "APPRAISER_2_PASSWORD");
            default:
                throw new IllegalStateException("Credentials implementation for '" + credentials + "' not found");
        }
    }

    private Header getUserAuthorizationHeader(String key, String username, String password) {

        MultiValueMap<String, String> body = createIdamRequest(username, password);

        String accessToken = tokens.computeIfAbsent(
            key,
            user -> "Bearer " + idamWebApi.token(body).getAccessToken()
        );
        return new Header(AUTHORIZATION, accessToken);
    }

    public Header getUserAuthorization(String key, String username, String password) {
        return getUserAuthorizationHeader(key, System.getenv(username), System.getenv(password));
    }

    private Header getServiceAuthorizationHeader() {
        String serviceToken = tokens.computeIfAbsent(
            SERVICE_AUTHORIZATION,
            user -> serviceAuthTokenGenerator.generate()
        );

        return new Header(SERVICE_AUTHORIZATION, serviceToken);
    }

    private MultiValueMap<String, String> createIdamRequest(String username, String password) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("redirect_uri", idamRedirectUrl);
        body.add("client_id", idamClientId);
        body.add("client_secret", idamClientSecret);
        body.add("username", username);
        body.add("password", password);
        body.add("scope", userScope);

        return body;
    }
}
