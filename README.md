# sscs-post-deployment-ft-tests

[![Build Status](https://travis-ci.org/hmcts/sscs-post-deployment-ft-tests.svg?branch=master)](https://travis-ci.org/hmcts/sscs-post-deployment-ft-tests)

## Purpose c
This repository contains a set of functional tests which are designed to run periodically or after a helm deployment as a post deployment job to ensure regression.

## What does this app do?

We have used the repository from the Work Allocation (WA) Task Management team as a base and adapted it for SSCS onboarding.
See: https://github.com/hmcts/wa-task-configuration-template

Tests effectively treat the WA Task Management backend as a black box where an event message it sent and then we await the outcome and perform checks.

#### Tests will typically:
* Extract data from a test specification file
* Set up users with Idam and Role Assignment Services
* Create and update CCD Case(s)
* Send a CCD Event Message to the WA Task Management backend
* Retrieve and verify Task and Tast Role data from WA Task Management using API requests
* Optionally perform actions on Tasks like Claim/Assign/Complete

## Requirements
* These tests require all relevant dependencies running either locally or in an environment.
* For SSCS it is preferred to spin up a Preview environment with WA dependencies.
This will take care of all the required config and not consume significant resources on your machine.
Refer to the Readme file here: https://github.com/hmcts/sscs-tribunals-case-api/blob/master/README.md

## When merging to master:
* This is a non prod repo so it will not be deployed to production environments.
* Please ensure all checks are run and code has been reviewed.

## Nightly Builds
The pipeline has been configured to run in the nightly builds.
This is specified on the `Jenkinsfile_nightly` file and the schedule is set there e.g: `pipelineTriggers([cron('0 * * * *')])`

## Publishing as ACR task
Any commit or merge into master will automatically trigger an Azure ACR task. This task has been manually
created using `./bin/deploy-acr-task.sh`. The task is defined in `acr-build-task.yaml`.

Note: the deployment script relies on a GitHub token (https://help.github.com/en/articles/creating-a-personal-access-token-for-the-command-line) defined in `infra-vault-prod`, secret `hmcts-github-apikey`. The token is for setting up a webhook so Azure will be notified when a merge or commit happens. Make sure you are a repo admin and select token scope of: `admin:repo_hook  Full control of repository hooks`

More info on ACR tasks can be read here: https://docs.microsoft.com/en-us/azure/container-registry/container-registry-tasks-overview

## Running functional tests

A bash script has been provided to run tests against a remote environment such as Preview (recommended) or AAT.

#### To run against Preview:
```shell
./run-ft-preview.sh <pr-number>
```
#### To run against another HMCTS lower environment e.g. aat | demo
```shell
./run-ft-lower-env.sh <environment>
```

#### To run functional against a local environment
```bash
./gradlew functional
```
#### You can also target a specific scenario:
```bash
./gradlew functional --tests ScenarioRunnerTest --info -Dscenario=SSCS-10005-review-fta-time-extension-request
```

#### Or multiple scenarios:
```bash
./gradlew functional --tests ScenarioRunnerTest --info -Dscenario=SSCS-10005
```

## Test Scenario Specification
When adding a new test scenario you will typically need to provide a new specification in json format in:
```
./resources/scenarios/sscs
```



## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
