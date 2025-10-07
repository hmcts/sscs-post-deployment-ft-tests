#!/bin/bash

# Use this script if you would like to run your sscs-post-deployment-ft-tests against a lower environment except preview.
# Note the tests rely on the correct DMN files being deployed in the target env.
#
# In most cases it is preferred to use the script: ./run-ft-preview.sh


# The following envs could also be added if needed: ithc perftest
ALLOWED_ENVS=("aat" "demo")

if [ -z "$1" ]; then
    echo "❌ Error: Missing environment parameter."
    echo "Usage: $0 <environment>"
    echo "Allowed environments are: ${ALLOWED_ENVS[*]}"
    echo ""
    exit 1
fi

case "$1" in
    # Allowed values (case-sensitive)
    aat|demo)
        ENVIRONMENT=$1
        echo "✅ Valid environment selected: ${ENVIRONMENT}"
        ;;
    *)
        echo "❌ Error: Invalid environment specified: '$1'."
        echo "Allowed environments are: ${ALLOWED_ENVS[*]}"
        exit 1
        ;;
esac


export FT_ENVIRONMENT="$1"
export WA_POST_DEPLOYMENT_TEST_FT_ENVIRONMENT=${FT_ENVIRONMENT}
export AZURE_SERVICE_BUS_TOPIC_NAME=ccd-case-events
export AZURE_SERVICE_BUS_MESSAGE_AUTHOR=sscs-${FT_ENVIRONMENT}
export SSCS_VAULT_NAME=sscs-${FT_ENVIRONMENT}
export WA_VAULT_NAME=wa-${FT_ENVIRONMENT}
export AZURE_SERVICE_BUS_SUBSCRIPTION_NAME=ccd-case-events-ft
export S2S_NAME_TASK_MANAGEMENT_API=wa_task_management_api
export DM_STORE_URL=http://dm-store-${FT_ENVIRONMENT}.service.core-compute-${FT_ENVIRONMENT}.internal
export IDAM_URL=https://idam-api.${FT_ENVIRONMENT}.platform.hmcts.net
export S2S_URL=http://rpe-service-auth-provider-${FT_ENVIRONMENT}.service.core-compute-${FT_ENVIRONMENT}.internal
export OPEN_ID_IDAM_URL=https://idam-web-public.${FT_ENVIRONMENT}.platform.hmcts.net
export DOCUMENT_STORE_URL=http://dm-store-${FT_ENVIRONMENT}.service.core-compute-${FT_ENVIRONMENT}.internal
export CCD_URL=http://ccd-data-store-api-${FT_ENVIRONMENT}.service.core-compute-${FT_ENVIRONMENT}.internal
export WA_TASK_MANAGEMENT_API_URL=http://wa-task-management-api-${FT_ENVIRONMENT}.service.core-compute-${FT_ENVIRONMENT}.internal
export WA_TASK_MONITOR_URL=http://wa-task-monitor-${FT_ENVIRONMENT}.service.core-compute-${FT_ENVIRONMENT}.service.core-compute-${FT_ENVIRONMENT}.internal
export CAMUNDA_URL=http://camunda-api-${FT_ENVIRONMENT}.service.core-compute-${FT_ENVIRONMENT}.internal/engine-rest
export ROLE_ASSIGNMENT_URL=http://am-role-assignment-service-${FT_ENVIRONMENT}.service.core-compute-${FT_ENVIRONMENT}.internal
export WA_CASE_EVENT_HANDLER_URL=http://wa-case-event-handler-${FT_ENVIRONMENT}.service.core-compute-${FT_ENVIRONMENT}.internal

# Set environment variable from Azure secret vault
# Parameters: <FT_ENVIRONMENT variable name> <Vault Name> <Secret Name>
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
