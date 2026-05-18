package uk.gov.hmcts.reform.probatepostdeploymentfttests.services;

import io.restassured.http.Headers;
import uk.gov.hmcts.reform.probatepostdeploymentfttests.domain.entities.idam.CredentialRequest;
import uk.gov.hmcts.reform.probatepostdeploymentfttests.domain.entities.idam.UserInfo;

import java.io.IOException;

public interface AuthorizationHeaders {
    Headers getWaSystemUserAuthorization();

    Headers getProbateSystemUserAuthorization();

    Headers getWaUserAuthorization(CredentialRequest request) throws IOException;

    UserInfo getUserInfo(String userToken);

    void cleanupTestUsers();
}
