#!/bin/bash

if [ -z "$1" ]; then
    echo "❌ Error: Missing environment parameter."
    echo "Usage: $0 <pr-number>"
    echo "Example: $0 4888"
    echo ""
    exit 1
fi

export TRIBUNALS_PR="$1"
export SSCS_VAULT_NAME=sscs-aat
export WA_VAULT_NAME=wa-aat
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

loadSecret "WA_IDAM_CLIENT_ID" ${WA_VAULT_NAME} "wa-idam-client-id"
loadSecret "WA_IDAM_CLIENT_SECRET" ${WA_VAULT_NAME} "wa-idam-client-secret"
loadSecret "WA_SYSTEM_USERNAME" ${WA_VAULT_NAME} "wa-system-username"
loadSecret "WA_SYSTEM_PASSWORD" ${WA_VAULT_NAME} "wa-system-password"
loadSecret "S2S_SECRET_TASK_MANAGEMENT_API" ${WA_VAULT_NAME} "s2s-secret-task-management-api"

loadSecret "AZURE_SERVICE_BUS_CONNECTION_STRING" ${SSCS_VAULT_NAME} "sscs-servicebus-connection-string-tf"
loadSecret "SYSTEMUPDATE_USERNAME" ${SSCS_VAULT_NAME} "idam-sscs-systemupdate-user"
loadSecret "SYSTEMUPDATE_PASSWORD" ${SSCS_VAULT_NAME} "idam-sscs-systemupdate-password"

./gradlew functional --tests ScenarioRunnerTest --info
