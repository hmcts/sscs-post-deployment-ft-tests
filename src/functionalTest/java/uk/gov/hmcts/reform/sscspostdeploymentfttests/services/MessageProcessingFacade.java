package uk.gov.hmcts.reform.sscspostdeploymentfttests.services;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.TestScenario;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.idam.UserInfo;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapSerializer;
import uk.gov.hmcts.reform.sscspostdeploymentfttests.util.MapValueExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class MessageProcessingFacade {
    private final MessageInjector messageInjector;
    private final RestMessageService restMessageService;

    public MessageProcessingFacade(MessageInjector messageInjector, RestMessageService restMessageService) {
        this.messageInjector = messageInjector;
        this.restMessageService = restMessageService;
    }

    public void injectMessage(Map<String, Object> clauseValues,
                              TestScenario scenario,
                              UserInfo userInfo) throws IOException {
        messageInjector.injectMessage(clauseValues, scenario, userInfo);
    }

    public String getCaseMessages(String caseId) {
        return restMessageService.getCaseMessages(caseId);
    }

    public void deleteMessage(String messageId, String caseId) {
        restMessageService.deleteMessage(messageId, caseId);
    }

    @SneakyThrows
    public void removeInvalidMessages(String expectationCaseId) {

        log.info("Checking Invalid Messages for caseId: {}", expectationCaseId);
        String actualMessageResponse = getCaseMessages(expectationCaseId);

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
                deleteMessage(messageId, caseId);
            }
        });
    }
}
