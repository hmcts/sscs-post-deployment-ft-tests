package uk.gov.hmcts.reform.sscspostdeploymentfttests.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.Headers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.documents.Document;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.UserInfo;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.preparers.DocumentManagementFiles;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapMerger;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapSerializer;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExpander;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.StringResourceLoader;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyMap;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.documents.DocumentNames.NOTICE_OF_APPEAL_PDF;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider.AUTHORIZATION;
import static uk.gov.hmcts.reform.sscspostdeploymentfttests.services.AuthorizationHeadersProvider.SERVICE_AUTHORIZATION;

@Service
public class CcdCaseCreator {
    @Autowired
    protected CoreCaseDataApi coreCaseDataApi;
    @Autowired
    protected AuthorizationHeadersProvider authorizationHeadersProvider;
    @Autowired
    private MapValueExpander mapValueExpander;
    @Autowired
    private DocumentManagementFiles documentManagementFiles;

    public String createCase(Map<String, Object> scenario,
                             String jurisdiction,
                             Headers authorizationHeaders) throws IOException {

        final String caseType = MapValueExtractor.extractOrThrow(scenario, "caseType");

        Map<String, String> ccdTemplatesByFilename =
            StringResourceLoader.load(
                "/templates/" + jurisdiction.toLowerCase(Locale.ENGLISH) + "/ccd/*.json"
            );

        Map<String, Object> caseData = getCaseData(
            MapValueExtractor.extract(scenario, "required.ccd"),
            ccdTemplatesByFilename
        );

        String eventId = MapValueExtractor.extractOrThrow(scenario, "required.ccd.eventId");

        String caseId = createInitialStartEventAndSubmit(
            eventId,
            jurisdiction,
            caseType,
            caseData,
            authorizationHeaders
        );

        return caseId;
    }

    private String createInitialStartEventAndSubmit(String eventId,
                                                    String jurisdiction,
                                                    String caseType,
                                                    Map<String, Object> caseData,
                                                    Headers authorizationHeaders) {

        String userToken = authorizationHeaders.getValue(AUTHORIZATION);
        String serviceToken = authorizationHeaders.getValue(SERVICE_AUTHORIZATION);
        UserInfo userInfo = authorizationHeadersProvider.getUserInfo(userToken);

        //Fire start event
        StartEventResponse startCase = coreCaseDataApi.startForCaseworker(
            userToken,
            serviceToken,
            userInfo.getUid(),
            jurisdiction,
            caseType,
            eventId
        );

        CaseDataContent caseDataContent = CaseDataContent.builder()
            .eventToken(startCase.getToken())
            .event(Event.builder()
                       .id(startCase.getEventId())
                       .summary("summary")
                       .description("description")
                       .build())
            .data(caseData)
            .build();

        //Fire submit event
        CaseDetails caseDetails = coreCaseDataApi.submitForCaseworker(
            userToken,
            serviceToken,
            userInfo.getUid(),
            jurisdiction,
            caseType,
            true,
            caseDataContent
        );

        System.out.println("Created case [" + caseDetails.getId() + "]");

        return caseDetails.getId().toString();
    }


    private CaseDetails fireStartAndSubmitEventsFor(String caseId,
                                                    String eventId,
                                                    String jurisdiction,
                                                    String caseType,
                                                    Map<String, Object> caseData,
                                                    Headers authorizationHeaders) {


        String userToken = authorizationHeaders.getValue(AUTHORIZATION);
        String serviceToken = authorizationHeaders.getValue(SERVICE_AUTHORIZATION);
        UserInfo userInfo = authorizationHeadersProvider.getUserInfo(userToken);

        Objects.requireNonNull(caseId, "caseId cannot be null when submitting an event");
        Objects.requireNonNull(userInfo.getUid(), "User Id cannot be null when submitting an event");

        //Fire start event
        StartEventResponse startEventResponse = coreCaseDataApi.startEventForCaseWorker(
            userToken,
            serviceToken,
            userInfo.getUid(),
            jurisdiction,
            caseType,
            caseId,
            eventId
        );

        CaseDataContent submitCaseDataContent = CaseDataContent.builder()
            .eventToken(startEventResponse.getToken())
            .event(Event.builder()
                       .id(startEventResponse.getEventId())
                       .summary("summary")
                       .description("description")
                       .build())
            .data(caseData)
            .build();


        //Fire submit event
        CaseDetails submitEventResponse = coreCaseDataApi.submitEventForCaseWorker(
            userToken,
            serviceToken,
            userInfo.getUid(),
            jurisdiction,
            caseType,
            caseId,
            true,
            submitCaseDataContent
        );

        System.out.println("Event [" + eventId + "] completed for case with Id [" + submitEventResponse.getId() + "]");

        return submitEventResponse;
    }


    private Map<String, Object> getCaseData(
        Map<String, Object> input,
        Map<String, String> templatesByFilename
    ) throws IOException {

        Map<String, Object> caseData = buildCaseData(
            MapValueExtractor.extract(input, "caseData"),
            templatesByFilename
        );

        return caseData;
    }

    private Map<String, Object> buildCaseData(
        Map<String, Object> caseDataInput,
        Map<String, String> templatesByFilename
    ) throws IOException {

        String templateFilename = MapValueExtractor.extract(caseDataInput, "template");

        String template = templatesByFilename.get(templateFilename);

        //TODO: Abstract this as teams might use different docs
        Document noticeOfAppealDocument = documentManagementFiles.getDocument(NOTICE_OF_APPEAL_PDF);
        template = template.replace("\"{$NOTICE_OF_DECISION_DOCUMENT}\"", toJsonString(noticeOfAppealDocument));

        Map<String, Object> caseData = deserializeWithExpandedValues(template);
        Map<String, Object> caseDataReplacements = MapValueExtractor.extract(caseDataInput, "replacements");
        if (caseDataReplacements != null) {
            MapMerger.merge(caseData, caseDataReplacements);
        }

        return caseData;
    }

    private Map<String, Object> deserializeWithExpandedValues(String source) throws IOException {
        Map<String, Object> data = MapSerializer.deserialize(source);
        mapValueExpander.expandValues(data, emptyMap());
        return data;
    }

    private String toJsonString(Object object) {
        String json = null;

        try {
            json = new ObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return json;
    }
}
