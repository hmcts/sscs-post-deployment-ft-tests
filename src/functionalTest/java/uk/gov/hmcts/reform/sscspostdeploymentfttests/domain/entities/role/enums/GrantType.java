package uk.gov.hmcts.reform.sscspostdeploymentfttests.domain.entities.role.enums;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum GrantType {
    BASIC, SPECIFIC, STANDARD, CHALLENGED, EXCLUDED, @JsonEnumDefaultValue UNKNOWN
}
