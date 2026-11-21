package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;

/** Persistence metadata contract for external-integration fact tables. */
class ExternalIntegrationPersistenceMetadataTest {

  @Test
  void factModelsDeclareRequiredUniqueConstraints() {
    Map<String, DataModelMeta> metas = TypedModelMetaHelper.scanModelMate(
        "com.ouroboros.workflow.approval.external.runtime");

    assertUnique(metas, "ExternalEventInbox", "uk_awf_ext_inbox_event",
        "adapterId", "connectionId", "eventId");
    assertUnique(metas, "ExternalResultBinding", "uk_awf_ext_result_external_instance",
        "externalInstanceRouteKey");
    assertUnique(metas, "ExternalResultBinding", "uk_awf_ext_result_idempotency",
        "idempotencyRouteKey");
    assertUnique(metas, "ExternalResultBinding", "uk_awf_ext_result_running_record",
        "businessRunningRouteKey");
    assertUnique(metas, "ExternalInstanceBinding", "uk_awf_ext_instance_external",
        "externalInstanceRouteKey");
    assertUnique(metas, "ExternalTaskBinding", "uk_awf_ext_task_external",
        "externalTaskRouteKey");
    assertUnique(metas, "ExternalPendingAssignment", "uk_awf_ext_pending_assignment",
        "assignmentRouteKey");
    assertUnique(metas, "WorkflowEngineMapping", "uk_awf_engine_mapping_active_route",
        "activeRouteKey");
    assertUnique(metas, "ExternalStartFact", "uk_awf_ext_start_business",
        "businessStartRouteKey");
    assertUnique(metas, "ExternalStartFact", "uk_awf_ext_start_idempotency",
        "idempotencyStartRouteKey");
    assertUnique(metas, "ExternalActionFact", "uk_awf_ext_action_active",
        "activeRouteKey");
    assertUnique(metas, "ExternalActionFact", "uk_awf_ext_action_idempotency",
        "idempotencyRouteKey");

    assertFieldSize(metas, "ExternalResultBinding", "externalInstanceRouteKey", 96);
    assertFieldSize(metas, "ExternalResultBinding", "idempotencyRouteKey", 96);
    assertFieldSize(metas, "ExternalResultBinding", "businessRunningRouteKey", 96);
    assertFieldSize(metas, "ExternalInstanceBinding", "externalInstanceRouteKey", 96);
    assertFieldSize(metas, "ExternalTaskBinding", "externalTaskRouteKey", 96);
    assertFieldSize(metas, "ExternalPendingAssignment", "assignmentRouteKey", 96);
    assertFieldSize(metas, "WorkflowEngineMapping", "activeRouteKey", 96);
    assertFieldSize(metas, "ExternalStartFact", "businessStartRouteKey", 96);
    assertFieldSize(metas, "ExternalStartFact", "idempotencyStartRouteKey", 96);
    assertFieldSize(metas, "ExternalActionFact", "activeRouteKey", 96);
    assertFieldSize(metas, "ExternalActionFact", "idempotencyRouteKey", 96);
  }

  private void assertUnique(
      Map<String, DataModelMeta> metas,
      String modelName,
      String constraintName,
      String... fields) {
    DataModelMeta meta = metas.get(modelName);
    assertThat(meta).as(modelName).isNotNull();
    assertThat(meta.getUniqueConstraints())
        .anySatisfy(constraint -> {
          assertThat(constraint.getName()).isEqualTo(constraintName);
          assertThat(constraint.getFields()).containsExactly(fields);
        });
  }

  private void assertFieldSize(
      Map<String, DataModelMeta> metas,
      String modelName,
      String fieldName,
      int expectedSize) {
    DataModelMeta meta = metas.get(modelName);
    assertThat(meta).as(modelName).isNotNull();
    assertThat(meta.getFields())
        .filteredOn(field -> fieldName.equals(field.getName()))
        .singleElement()
        .satisfies(field -> assertThat(field.getSize()).isEqualTo(expectedSize));
  }
}
