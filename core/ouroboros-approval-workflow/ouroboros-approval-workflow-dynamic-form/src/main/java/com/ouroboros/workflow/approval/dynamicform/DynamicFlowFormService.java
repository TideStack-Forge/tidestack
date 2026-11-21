package com.ouroboros.workflow.approval.dynamicform;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataOperationIdentityProviders;
import com.ouroboros.data.record.Record;
import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.dynamicform.service.DynamicFormDataService;
import com.ouroboros.dynamicform.service.DynamicFormDefineService;
import com.ouroboros.dynamicform.schema.DynamicFormSchemaService;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.uischema.schema.CommonContainer;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.uischema.schema.FormNode;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.dynamicform.service.DynamicFormWorkflowRecordService;

@Service
public class DynamicFlowFormService implements FlowFormService {

  @Autowired
  DynamicFormDefineService dynamicFormDefineService;

  @Autowired
  DynamicFormDataService dynamicFormDataService;

  @Autowired
  DynamicFormSchemaService dynamicFormFieldSchemaBuilder;

  @Autowired
  DynamicFormWorkflowRecordService dynamicFormWorkflowRecordService;

  @Override
  public String getType() {
    return "default";
  }

  @Override
  public Try<SchemaNode> getInitialFormSchema(String formKey, List<NodeFormFieldPermission> permissions) {
    // 流程此时还没有发起，我们得从原来的定义中获取权限
    Optional<DynamicForm> dynamicForm = dynamicFormDefineService.getDynamicFormByName(formKey);
    if (!dynamicForm.isPresent()) {
      return Try.failure(new PlatformException("未找到表单定义"));
    }

    FormNode amisNode = dynamicFormFieldSchemaBuilder.buildFormSchema(dynamicForm.get());
    CommonContainer container = new CommonContainer();
    container.setType("container");
    container.setProp("className", "mb-10");

    // 配置表单各项权限
    if (permissions != null && amisNode.getBody() != null) {
      List<FormItemNode> finalBody = amisNode.getBody().stream()
          .filter(x -> x instanceof FormItemNode)
          .map(x -> (FormItemNode) x)
          .map(item -> {
            NodeFormFieldPermission permission = permissions.stream()
                .filter(y -> y.getName().equals(item.getName()))
                .findFirst()
                .orElse(null);

            if (permission == null || permission.isVisible() == null || !permission.isVisible()) {
              return null;
            }

            item.setRequired(permission.isRequired());

            if (!permission.isWritable()) {
              item.setDisabled(true);
            }

            return item;
          })
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
      container.setBody(finalBody);
    }

    return Try.success(container);
  }

  @Override
  public Try<String> submitInitialForm(String instanceId, String formKey, String userId,
                                       List<NodeFormFieldPermission> permissions, Map<String, Object> formData) {
    Map<String, Object> data;
    if (permissions != null) {
      List<String> errorList = permissions.stream()
          .filter(permission -> permission.isRequired() != null && permission.isRequired())
          .map(NodeFormFieldPermission::getName)
          .filter(name -> !formData.containsKey(name))
          .collect(Collectors.toList());
      if (!errorList.isEmpty()) {
        return Try.failure(new PlatformException("表单数据不完整: " + String.join(",", errorList)));
      }

      data = permissions.stream()
          .filter(permission -> permission.isWritable() != null && permission.isWritable())
          .map(NodeFormFieldPermission::getName)
          .collect(HashMap::new, (m, name) -> m.put(name, formData.get(name)), HashMap::putAll);
    } else {
      data = new HashMap<>();
    }

    return Try.of(() -> {
      DataModel model = dynamicFormDataService.getWritableDataModel(formKey);
      Try<Record> insertResult = DataOperationIdentityProviders.withCurrentOperator(operator(userId), () -> model.insert(data));
      if (!insertResult.isSuccess()) {
        throw new PlatformException("插入数据失败: " + formKey + "，原因：" + failureMessage(insertResult.getCause()),
            insertResult.getCause());
      }

      Record inserted = insertResult.get();
      String formId = String.valueOf(inserted.get("id"));
      if (dynamicFormWorkflowRecordService != null) {
        dynamicFormWorkflowRecordService.recordInitialForm(instanceId, formKey, formId, userId, inserted);
      }
      return formId;
    });
  }

  @Override
  public Try<SchemaNode> getTaskFormSchema(String instanceId, String taskId, String formKey, String formId,
                                           List<NodeFormFieldPermission> permissions, List<String> actions) {
    Optional<DynamicForm> dynamicForm = dynamicFormDefineService.getDynamicFormByName(formKey);
    if (!dynamicForm.isPresent()) {
      return Try.failure(new PlatformException("未找到表单定义"));
    }

    Optional<DataModel> dynamicFormDataModel = dynamicFormDataService.getDataModel(formKey);
    if (!dynamicFormDataModel.isPresent()) {
      return Try.failure(new PlatformException("未找到表单定义"));
    }

    Try<Record> recordResult = dynamicFormDataModel.get().get(formId);
    if (!recordResult.isSuccess()) {
      Throwable cause = recordResult.getCause();
      return Try.failure(cause instanceof Exception ? (Exception) cause : new PlatformException("查询表单数据失败", cause));
    }
    Record record = recordResult.get();

    FormNode amisNode = dynamicFormFieldSchemaBuilder.buildFormSchema(dynamicForm.get());
    CommonContainer container = new CommonContainer();
    container.setProp("title", "");
    container.setProp("id", "form");
    container.setType("form");

    // 配置表单各项权限与数据
    if (permissions != null && amisNode.getBody() != null) {
      List<FormItemNode> finalBody = amisNode.getBody().stream()
          .filter(x -> x instanceof FormItemNode)
          .map(x -> (FormItemNode) x)
          .map(item -> {
            NodeFormFieldPermission permission = permissions.stream()
                .filter(y -> y.getName().equals(item.getName()))
                .findFirst()
                .orElse(null);

            if (permission == null || permission.isVisible() == null || !permission.isVisible()) {
              return null;
            }

            item.setRequired(permission.isRequired());

            if (record != null) {
              item.setValue(record.get(item.getName()));
            }

            if (!permission.isWritable()) {
              item.setDisabled(true);
            }

            return item;
          })
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
      container.setBody(finalBody);

      // 同意按钮必须存在
      actions.add("approve");
      List<Map<String, String>> actionButtons = actions.stream()
          .map(action -> Collections.singletonMap("$ref", action + "Button"))
          .collect(Collectors.toList());
      container.setProp("actions", actionButtons);
    }

    return Try.success(container);
  }

  @Override
  public Try<Void> submitTaskForm(String instanceId, String taskId, String formKey, String formId, String userId,
                                  List<NodeFormFieldPermission> permissions, Map<String, Object> formData) {
    if (permissions == null) {
      return Try.success(null);
    }

    List<String> errorList = permissions.stream()
        .filter(permission -> permission.isRequired() != null && permission.isRequired())
        .filter(permission -> !formData.containsKey(permission.getName()))
        .map(NodeFormFieldPermission::getLabel)
        .collect(Collectors.toList());
    if (!errorList.isEmpty()) {
      return Try.failure(new PlatformException("表单数据不完整: " + String.join(",", errorList)));
    }

    Map<String, Object> where = new HashMap<>(Collections.singletonMap("id", formId));

    Map<String, Object> data = permissions.stream()
        .filter(permission -> permission.isWritable() != null && permission.isWritable())
        .map(NodeFormFieldPermission::getName)
        .filter(formData::containsKey)
        .collect(HashMap::new, (map, key) -> map.put(key, formData.get(key)), HashMap::putAll);
    Set<String> submittedFieldNames = new LinkedHashSet<>(data.keySet());

    return Try.of(() -> {
      DataModel model = dynamicFormDataService.getWritableDataModel(formKey);

      Try<Record> beforeResult = model.get(formId);
      if (!beforeResult.isSuccess()) {
        throw new PlatformException("查询表单数据失败", beforeResult.getCause());
      }

      Try<Long> updateResult = DataOperationIdentityProviders.withCurrentOperator(operator(userId), () -> model.update(where, data));
      if (!updateResult.isSuccess()) {
        throw new PlatformException("更新表单数据失败，原因：" + failureMessage(updateResult.getCause()),
            updateResult.getCause());
      }

      if (dynamicFormWorkflowRecordService != null) {
        Try<Record> afterResult = model.get(formId);
        if (!afterResult.isSuccess()) {
          throw new PlatformException("查询更新后的表单数据失败", afterResult.getCause());
        }
        dynamicFormWorkflowRecordService.recordTaskFormUpdate(
            instanceId,
            taskId,
            formKey,
            formId,
            userId,
            beforeResult.get(),
            afterResult.get(),
            submittedFieldNames);
      }

      return null;
    });
  }

  private String operator(String userId) {
    return Optional.ofNullable(userId)
        .filter(id -> !id.isBlank())
        .orElse(DataOperationIdentityProviders.SYSTEM_OPERATOR);
  }

  private String failureMessage(Throwable cause) {
    if (cause == null) {
      return "未知错误";
    }
    return Optional.ofNullable(cause.getMessage())
        .filter(message -> !message.isBlank())
        .orElseGet(() -> cause.getClass().getSimpleName());
  }
}
