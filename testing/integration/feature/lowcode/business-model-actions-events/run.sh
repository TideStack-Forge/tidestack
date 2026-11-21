#!/usr/bin/env bash
set -euo pipefail

scenario_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$scenario_dir/../../../../.." && pwd)"
cd "$repo_root"

if ! java -version 2>&1 | rg -q 'version "21\.'; then
  if [[ -x /usr/libexec/java_home ]]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
    export PATH="$JAVA_HOME/bin:$PATH"
  else
    printf '%s\n' 'Java 21 is required; configure JAVA_HOME and PATH before running this scenario.' >&2
    exit 1
  fi
fi

if [[ "${1:-}" == "--deployment" ]]; then
  shift
  deployment_dir="$repo_root/tmp/testing-evidence/business-model-actions-events/deployment"
  mkdir -p "$deployment_dir/classes"
  mvn -q -pl platform/ouroboros-mothership compile dependency:build-classpath \
    -Dmdep.outputFile="$deployment_dir/classpath"
  deployment_classpath="$repo_root/platform/ouroboros-mothership/target/classes:$(cat "$deployment_dir/classpath")"
  javac -cp "$deployment_classpath" -d "$deployment_dir/classes" \
    "$scenario_dir/fixtures/BehaviorAcceptanceApplication.java"
  exec java -cp "$deployment_dir/classes:$deployment_classpath" \
    com.ouroboros.acceptance.BehaviorAcceptanceApplication "$@"
fi

if [[ "${1:-}" == "--performance" ]]; then
  performance_dir="$repo_root/tmp/testing-evidence/business-model-actions-events/performance"
  mkdir -p "$performance_dir"
  mvn -q -pl core/ouroboros-business-model/ouroboros-business-model-runtime -am test \
    -Dsurefire.failIfNoSpecifiedTests=false -Dtest=BusinessActionInputPerformanceTest \
    -Douroboros.behavior.performance=true -Douroboros.behavior.performance.output="$performance_dir/input.csv"
  mvn -q -pl core/ouroboros-business-model/ouroboros-business-model-runtime -am test \
    -Dsurefire.failIfNoSpecifiedTests=false \
    '-Dtest=BusinessBehaviorJourneyIntegrationTest#measureDefaultFactStorageWithBoundedChunksAndSubscriptionFanout' \
    -Douroboros.behavior.performance=true -Douroboros.behavior.performance.output="$performance_dir/mutations.csv"
  exit 0
fi

mvn -q -pl core/ouroboros-business-model/ouroboros-business-model-runtime,core/ouroboros-business-model/ouroboros-business-model-dev,core/ouroboros-approval-workflow/ouroboros-approval-workflow-runtime,core/ouroboros-webapi-model/ouroboros-webapi-runtime,core/ouroboros-webapi-model/ouroboros-webapi-model-dev,ability/ouroboros-notification/ouroboros-notification-runtime -am test \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=BusinessEventConsumerIdentityResolverTest,BusinessBehaviorMappingPlanTest,BusinessBehaviorMappingUpgradeTest,BusinessEventListenerConfigurationTest,BusinessEventListenerInputPlanTest,WorkflowBusinessNotificationOwnershipTest,WorkflowBusinessFactProjectionIntegrationTest,WorkflowNotificationSubscriptionsTest,BusinessEventNotificationListenerTest,ReliableNotificationDispatchIntegrationTest,WorkflowNotificationDeliveryIntegrationTest,NotificationBusinessEventConfigurationTest,BusinessModelRuntimeAccessTest,SpringContextUtilTest,BusinessActionMutationEventEmitterTest,AccessControlDataModelPluginCompatibilityTest,BusinessActionHttpInputPlanTest,BusinessActionInputTest,BusinessActionMethodBindingTest,BusinessActionInputJourneyTest,ApprovalProcessCallbackListenerTest,ApprovalProcessCallbackOutboxDeliveryTest,FlowEventOutboxStorageIntegrationTest,BusinessActionAnnotationCatalogTest,BusinessActionDescriptionControllerTest,BusinessActionExecutionAuditModelTest,BusinessActionWebApiAdapterTest,BusinessBehaviorJourneyIntegrationTest,BusinessBehaviorTypeGeneratorTest,BusinessEventAdaptersTest,BusinessEventAnnotationCatalogTest,BusinessEventOccurrenceTest,BusinessEventOperationsAuthoritiesTest,BusinessEventOperationsControllerTest,BusinessEventOutboxIntegrationTest,BusinessEventRuntimeConfigurationTest,BusinessEventSpringAdapterTest,BusinessModelBehaviorContractTest,BusinessModelControllerTest,BusinessModelExportPublicationTest,BusinessModelGeneratedOpenAPISupportTest,BusinessModelSchemaSupportChildMasterRelationTest,BusinessObjectReferenceSchemasTest,BusinessRelationParametersTest,BusinessActionPreconditionPlanTest,BusinessTransactionResourcesTest,ChildCollectionSyncTransactionIntegrationTest,DataModelBusinessActionExecutionAuditRecorderTest,DataModelWriteGuardTest,DefaultBusinessActionRuntimeTest,DefaultBusinessEventPortTest,DefaultBusinessEventSubscriptionRuntimeTest,DefaultBusinessModelEditorSchemaGeneratorTest,EnhancedDataModelProxyDeleteRoutingTest,FlowEventOutboxServiceTest,FlowEventRetryJobTest,FlowModelRevisionTest,JsonSchemasTest,LogicFlowTemplateProjectionIntegrationContractTest,OuroborosContextRefreshLifecycleTest,RelationValidatorTest,SpElRestrictedPredicateTest,SqlDataAdapterRowLockTest,SubmitTogetherRelationValidatorTest,WebAPIModelContractTest

npm --prefix frontends/web/ouroboros-sdk run test:unit
node "$scenario_dir/verify-generated-client.mjs"
