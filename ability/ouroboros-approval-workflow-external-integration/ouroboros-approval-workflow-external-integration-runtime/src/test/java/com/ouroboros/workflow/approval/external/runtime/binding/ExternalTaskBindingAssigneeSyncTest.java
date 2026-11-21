package com.ouroboros.workflow.approval.external.runtime.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.TypedDataModel;

import io.vavr.control.Try;

/**
 * 待办聚合按外部权威快照同步处理人：外部改派后绑定必须跟上，否则以绑定处理人为权威的
 * 动作授权会把新处理人拒之门外（F-01 授权与 F-02 绑定刷新的耦合）。
 */
class ExternalTaskBindingAssigneeSyncTest {

  @Test
  void syncsAssigneeWhenSnapshotDiffers() {
    ExternalTaskBinding existing = task("local-task-1", "u-old", ExternalTaskBinding.STATUS_CREATED);
    TypedDataModel<String, ExternalTaskBinding> dataModel = dataModel(existing);
    when(dataModel.update(anyMap(), anyMap())).thenReturn(Try.success(1L));

    Try<Boolean> result = new ExternalTaskService(dataModel).syncAssignee("local-task-1", "u-new");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isTrue();
    verify(dataModel).update(anyMap(), argThat(data -> "u-new".equals(data.get("assignee"))));
  }

  @Test
  void skipsWriteWhenAssigneeIsUnchanged() {
    ExternalTaskBinding existing = task("local-task-1", "u-new", ExternalTaskBinding.STATUS_CREATED);
    TypedDataModel<String, ExternalTaskBinding> dataModel = dataModel(existing);

    Try<Boolean> result = new ExternalTaskService(dataModel).syncAssignee("local-task-1", "u-new");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isFalse();
    verify(dataModel, never()).update(anyMap(), anyMap());
  }

  @Test
  void skipsWriteForTerminalBinding() {
    ExternalTaskBinding existing = task("local-task-1", "u-old", ExternalTaskBinding.STATUS_APPROVED);
    TypedDataModel<String, ExternalTaskBinding> dataModel = dataModel(existing);

    Try<Boolean> result = new ExternalTaskService(dataModel).syncAssignee("local-task-1", "u-new");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isFalse();
    verify(dataModel, never()).update(anyMap(), anyMap());
  }

  @Test
  void rejectsBlankAssignee() {
    ExternalTaskBinding existing = task("local-task-1", "u-old", ExternalTaskBinding.STATUS_CREATED);
    TypedDataModel<String, ExternalTaskBinding> dataModel = dataModel(existing);

    Try<Boolean> result = new ExternalTaskService(dataModel).syncAssignee("local-task-1", " ");

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("外部任务处理人为空");
    verify(dataModel, never()).update(anyMap(), anyMap());
  }

  @Test
  void failsWhenBindingIsMissing() {
    TypedDataModel<String, ExternalTaskBinding> dataModel = emptyDataModel();

    Try<Boolean> result = new ExternalTaskService(dataModel).syncAssignee("local-task-1", "u-new");

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("外部任务绑定不存在");
  }

  /** {@code findByLocalId} 走基类 {@code getByPrimaryKey}：需要同时提供字段元数据与按主键读取。 */
  private TypedDataModel<String, ExternalTaskBinding> dataModel(ExternalTaskBinding existing) {
    TypedDataModel<String, ExternalTaskBinding> dataModel = emptyDataModel();
    when(dataModel.get(eq(existing.getLocalTaskId()), anyMap())).thenReturn(Try.success(existing));
    return dataModel;
  }

  private TypedDataModel<String, ExternalTaskBinding> emptyDataModel() {
    TypedDataModel<String, ExternalTaskBinding> dataModel = mock(TaskDataModel.class);
    DataModel metadata = mock(DataModel.class);
    when(metadata.getFields()).thenReturn(List.of());
    when(dataModel.getDataModel()).thenReturn(metadata);
    when(dataModel.get(any(), anyMap())).thenReturn(Try.success(null));
    return dataModel;
  }

  private ExternalTaskBinding task(String localTaskId, String assignee, String status) {
    ExternalTaskBinding binding = new ExternalTaskBinding();
    binding.setLocalTaskId(localTaskId);
    binding.setLocalInstanceId("local-instance-1");
    binding.setScope("scope");
    binding.setMappingId("mapping");
    binding.setMappingVersion(1);
    binding.setAdapterId("adapter");
    binding.setConnectionId("connection");
    binding.setExternalTaskId("external-task");
    binding.setNodeId("node");
    binding.setAssignee(assignee);
    binding.setStatus(status);
    return binding;
  }

  interface TaskDataModel extends TypedDataModel<String, ExternalTaskBinding> {
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
}
