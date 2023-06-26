package uk.gov.hmcts.reform.sscspostdeploymentfttests.services;

import io.restassured.http.Header;
import io.restassured.http.Headers;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.CredentialRequest;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.UserInfo;

import java.io.IOException;

public interface AuthorizationHeaders {
    Headers getWaSystemUserAuthorization();

    Headers getWaUserAuthorization(CredentialRequest request) throws IOException;

    UserInfo getUserInfo(String userToken);

    void cleanupTestUsers();

    Header getUserAuthorizationHeader(String credentials);
}
