#!/bin/bash

export TRIBUNALS_PR="$1"
export AZURE_SERVICE_BUS_TOPIC_NAME=ccd-case-events
export AZURE_SERVICE_BUS_MESSAGE_AUTHOR=sscs-aat
export AZURE_SERVICE_BUS_SUBSCRIPTION_NAME=ccd-case-events-ft
export S2S_NAME_TASK_MANAGEMENT_API=wa_task_management_api
export DM_STORE_URL=http://dm-store-aat.service.core-compute-aat.internal
export IDAM_URL=https://idam-api.aat.platform.hmcts.net
export S2S_URL=http://rpe-service-auth-provider-aat.service.core-compute-aat.internal
export OPEN_ID_IDAM_URL=https://idam-web-public.aat.platform.hmcts.net
export DOCUMENT_STORE_URL=http://dm-store-aat.service.core-compute-aat.internal
export WA_POST_DEPLOYMENT_TEST_ENVIRONMENT=preview
export CCD_URL=https://ccd-data-store-api-sscs-tribunals-api-pr-${TRIBUNALS_PR}.preview.platform.hmcts.net
export WA_TASK_MANAGEMENT_API_URL=https://wa-task-management-api-sscs-tribunals-api-pr-${TRIBUNALS_PR}.preview.platform.hmcts.net
export WA_TASK_MONITOR_URL=https://wa-task-monitor-sscs-tribunals-api-pr-${TRIBUNALS_PR}.preview.platform.hmcts.net
export CAMUNDA_URL=https://camunda-sscs-tribunals-api-pr-${TRIBUNALS_PR}.preview.platform.hmcts.net/engine-rest
export ROLE_ASSIGNMENT_URL=https://am-role-assignment-service-sscs-tribunals-api-pr-${TRIBUNALS_PR}.preview.platform.hmcts.net
export WA_CASE_EVENT_HANDLER_URL=https://wa-case-event-handler-sscs-tribunals-api-pr-${TRIBUNALS_PR}.preview.platform.hmcts.net

# Set environment variable from Azure secret vault
# Parameters: <Environment variable name> <Vault Name> <Secret Name>
loadSecret () {
  export "$1"="$(az keyvault secret show --name "$3" --vault-name $2 --query "value" | sed -e 's/^"//' -e 's/"$//')"
}

echo "Fetching secrets..."

loadSecret "WA_IDAM_CLIENT_ID" "wa-aat" "wa-idam-client-id"
loadSecret "WA_IDAM_CLIENT_SECRET" "wa-aat" "wa-idam-client-secret"
loadSecret "WA_SYSTEM_USERNAME" "wa-aat" "wa-system-username"
loadSecret "WA_SYSTEM_PASSWORD" "wa-aat" "wa-system-password"
loadSecret "S2S_SECRET_TASK_MANAGEMENT_API" "wa-aat" "s2s-secret-task-management-api"

loadSecret "AZURE_SERVICE_BUS_CONNECTION_STRING" "sscs-aat" "sscs-servicebus-connection-string-tf"
loadSecret "SYSTEMUPDATE_USERNAME" "sscs-aat" "idam-sscs-systemupdate-user"
loadSecret "SYSTEMUPDATE_PASSWORD" "sscs-aat" "idam-sscs-systemupdate-password"

./gradlew functional --tests ScenarioRunnerTest --info
