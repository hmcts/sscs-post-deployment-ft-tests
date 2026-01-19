package uk.gov.hmcts.reform.sscspostdeploymentfttests;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(SerenityJUnit5Extension.class)
@SpringBootTest
@ActiveProfiles("functional")
public abstract class SpringBootFunctionalBaseTest {

    public static final int DEFAULT_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_POLL_INTERVAL_SECONDS = 4;
    @Autowired
    protected ObjectMapper objectMapper;

}
