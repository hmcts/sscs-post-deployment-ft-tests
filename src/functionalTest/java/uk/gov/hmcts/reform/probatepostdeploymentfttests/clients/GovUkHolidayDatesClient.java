package uk.gov.hmcts.reform.probatepostdeploymentfttests.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import uk.gov.hmcts.reform.probatepostdeploymentfttests.domain.entities.gov.UkHolidayDates;

@FeignClient(
    name = "GovUkHolidayDatesClient",
    url = "${govUk.url}"
)
public interface GovUkHolidayDatesClient {
    @GetMapping(value = "/bank-holidays.json", produces = MediaType.APPLICATION_JSON_VALUE)
    UkHolidayDates getHolidayDates();
}
