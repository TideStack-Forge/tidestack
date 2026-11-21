package com.ouroboros.workflow.approval.external.runtime.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;

import io.vavr.control.Try;

class ExternalBindingIdentityConflictTest {

  @Test
  void instanceBindingConflictDoesNotReturnBindingFromAnotherMapping() {
    ExternalInstanceBinding existing = instance("local-1", 1);
    ExternalInstanceBinding requested = instance("local-1", 2);
    ExternalInstanceBindingService service = new ExternalInstanceService(dataModel(existing));

    Try<ExternalInstanceBinding> result = service.create(requested);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
  }

  @Test
  void taskBindingDuplicateReusesBindingWithAnotherLocalProjection() {
    ExternalTaskBinding existing = task("local-task-1", "local-instance-1");
    ExternalTaskBinding requested = task("local-task-2", "local-instance-1");
    ExternalTaskBindingService service = new ExternalTaskService(dataModel(existing));

    Try<ExternalTaskBinding> result = service.create(requested);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isSameAs(existing);
  }

  @Test
  void taskBindingDuplicateIgnoresMutableSnapshotMetadata() {
    ExternalTaskBinding existing = task("local-task-1", "local-instance-1");
    ExternalTaskBinding requested = task("local-task-2", "local-instance-1");
    requested.setNodeId("new-node");
    requested.setAssignee("new-user");
    ExternalTaskBindingService service = new ExternalTaskService(dataModel(existing));

    Try<ExternalTaskBinding> result = service.create(requested);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isSameAs(existing);
  }

  @Test
  void resultBindingConflictDoesNotReturnBindingFromAnotherBusinessRecord() {
    ExternalResultBinding existing = result("tracking-1", "record-1");
    ExternalResultBinding requested = result("tracking-2", "record-2");
    ExternalResultBindingServiceImpl service = new ExternalResultService(dataModel(existing));

    Try<ExternalResultBinding> result = service.create(requested);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
  }

  @Test
  void approvalCaseRoundIsPartOfInstanceAndResultIdentity() {
    ExternalInstanceBinding existingInstance = instance("local-1", 1);
    existingInstance.setApprovalCaseId("case-1");
    existingInstance.setSubmissionRound(1);
    ExternalInstanceBinding nextRoundInstance = instance("local-1", 1);
    nextRoundInstance.setApprovalCaseId("case-1");
    nextRoundInstance.setSubmissionRound(2);
    Try<ExternalInstanceBinding> instanceResult =
        new ExternalInstanceService(dataModel(existingInstance)).create(nextRoundInstance);

    ExternalResultBinding existingResult = result("tracking-1", "record-1");
    existingResult.setApprovalCaseId("case-1");
    existingResult.setSubmissionRound(1);
    ExternalResultBinding nextRoundResult = result("tracking-1", "record-1");
    nextRoundResult.setApprovalCaseId("case-1");
    nextRoundResult.setSubmissionRound(2);
    Try<ExternalResultBinding> result =
        new ExternalResultService(dataModel(existingResult)).create(nextRoundResult);

    assertThat(instanceResult.isFailure()).isTrue();
    assertThat(instanceResult.getCause().getMessage())
        .contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage())
        .contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
  }

  @Test
  void exactIdentityConflictIsIdempotentForEachBindingKind() {
    ExternalInstanceBinding instance = instance("local-1", 1);
    ExternalTaskBinding task = task("task-1", "local-1");
    ExternalResultBinding result = result("tracking-1", "record-1");

    assertThat(new ExternalInstanceService(dataModel(instance)).create(instance).get()).isSameAs(instance);
    assertThat(new ExternalTaskService(dataModel(task)).create(task).get()).isSameAs(task);
    assertThat(new ExternalResultService(dataModel(result)).create(result).get()).isSameAs(result);
  }

  private <M> TypedDataModel<String, M> dataModel(M existing) {
    TypedDataModel<String, M> dataModel = mock(TypedDataModel.class);
    when(dataModel.insert(any())).thenReturn(Try.failure(new UniqueConstraintViolationException("duplicate")));
    when(dataModel.query(anyMap())).thenReturn(Try.success(List.of(existing)));
    return dataModel;
  }

  private ExternalInstanceBinding instance(String localId, int version) {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId(localId);
    binding.setScope("scope");
    binding.setFlowKey("flow");
    binding.setMappingId("mapping");
    binding.setMappingVersion(version);
    binding.setAdapterId("adapter");
    binding.setConnectionId("connection");
    binding.setExternalInstanceId("external-instance");
    binding.setExternalDefinitionId("definition");
    binding.setBusinessRecordType("record");
    binding.setBusinessRecordId("record-1");
    binding.setCallbackFlow("callback");
    return binding;
  }

  private ExternalTaskBinding task(String localTaskId, String localInstanceId) {
    ExternalTaskBinding binding = new ExternalTaskBinding();
    binding.setLocalTaskId(localTaskId);
    binding.setLocalInstanceId(localInstanceId);
    binding.setScope("scope");
    binding.setMappingId("mapping");
    binding.setMappingVersion(1);
    binding.setAdapterId("adapter");
    binding.setConnectionId("connection");
    binding.setExternalTaskId("external-task");
    binding.setNodeId("node");
    binding.setAssignee("user");
    return binding;
  }

  private ExternalResultBinding result(String trackingId, String recordId) {
    ExternalResultBinding binding = new ExternalResultBinding();
    binding.setTrackingId(trackingId);
    binding.setScope("scope");
    binding.setFlowKey("flow");
    binding.setMappingId("mapping");
    binding.setMappingVersion(1);
    binding.setAdapterId("adapter");
    binding.setConnectionId("connection");
    binding.setBusinessRecordType("record");
    binding.setBusinessRecordId(recordId);
    binding.setExternalInstanceId("external-instance");
    binding.setIdempotencyKey("idempotency");
    binding.setExternalDefinitionId("definition");
    binding.setStatus(ExternalResultBinding.STATUS_RUNNING);
    return binding;
  }

  private static final class ExternalInstanceService extends ExternalInstanceBindingService {
    private final TypedDataModel<String, ExternalInstanceBinding> dataModel;
    private ExternalInstanceService(TypedDataModel<String, ExternalInstanceBinding> dataModel) {
      this.dataModel = dataModel;
    }
    @Override
    protected TypedDataModel<String, ExternalInstanceBinding> getDataModel() {
      return dataModel;
    }
  }

  private static final class ExternalTaskService extends ExternalTaskBindingService {
    private final TypedDataModel<String, ExternalTaskBinding> dataModel;
    private ExternalTaskService(TypedDataModel<String, ExternalTaskBinding> dataModel) {
      this.dataModel = dataModel;
    }
    @Override
    protected TypedDataModel<String, ExternalTaskBinding> getDataModel() {
      return dataModel;
    }
  }

  private static final class ExternalResultService extends ExternalResultBindingServiceImpl {
    private final TypedDataModel<String, ExternalResultBinding> dataModel;
    private ExternalResultService(TypedDataModel<String, ExternalResultBinding> dataModel) {
      this.dataModel = dataModel;
    }
    @Override
    protected TypedDataModel<String, ExternalResultBinding> getDataModel() {
      return dataModel;
    }
  }
}
