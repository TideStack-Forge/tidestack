package com.ouroboros.workflow.approval.dynamicform;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.data.dsl.query.Query;
import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.dynamicform.model.FormField;
import com.ouroboros.dynamicform.service.DynamicFormDefineService;
import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.FlowFormProvider;
import com.ouroboros.workflow.approval.dynamicform.expressionvariables.ExpressionVariableBuilder;
import com.ouroboros.workflow.approval.model.ExpressionVariable;
import com.ouroboros.workflow.approval.model.FlowForm;
import com.ouroboros.workflow.approval.model.FlowFormField;

@Component
public class DynamicFormProvider implements FlowFormProvider {

  @Autowired
  private DynamicFormDefineService dynamicFormDefineService;

  @Override
  public String getType() {
    return "default";
  }

  @Override
  public String getTitle() { return "动态表单"; }

  private Map<String, Object> buildWhere(String keyword) {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("status", "已部署");
    if (StringUtils.isNotEmpty(keyword)) {
      where.put("name", Map.of("$like", keyword));
    }
    return where;
  }

  private FlowForm mapFlowForm(DynamicForm dynamicForm) {
    if (dynamicForm == null) {
      return null;
    }
    var formFields = dynamicForm.getFields() == null ? Collections.<FormField>emptyList() : dynamicForm.getFields();
    // 获取表单字段
    var fields = formFields
        .stream()
        .map(field -> {
          var flowFormField = new FlowFormField();
          flowFormField.setName(field.getName());
          flowFormField.setLabel(field.getLabel());
          flowFormField.setType(field.getType());
          return flowFormField;
        })
        .collect(Collectors.toList());
    var form = new FlowForm();
    form.setName(dynamicForm.getName());
    form.setTitle(dynamicForm.getTitle());
    form.setFields(fields);
    form.setExpressionVariables(buildExpressionVariables(formFields));
    return form;
  }

  @Override
  public List<FlowForm> search(String keyword) {
    var where = buildWhere(keyword);
    var query = Query.from("DynamicForm")
        .select(List.of("name", "title"))
        .where(where)
        .build();
    var items = dynamicFormDefineService.search(query);
    return items.stream().map(this::mapFlowForm).collect(Collectors.toList());
  }

  @Override
  public Tuple2<Long, List<FlowForm>> search(String keyword, Integer page, Integer pageSize) {
    var where = buildWhere(keyword);
    var total = dynamicFormDefineService.getTotal(where);
    if (total == 0) {
      return Tuple.of(total, Collections.emptyList());
    }
    var query = Query.from("DynamicForm")
        .select(List.of("name", "title"))
        .where(where)
        .clause("OFFSET", (page - 1) * pageSize)
        .clause("LIMIT", pageSize)
        .build();
    var items = dynamicFormDefineService.search(query);
    return Tuple.of(total, items.stream().map(this::mapFlowForm).collect(Collectors.toList()));
  }

  @Override
  public Optional<FlowForm> get(String key) {
    return dynamicFormDefineService
        .getDynamicFormByName(key)
        .map(this::mapFlowForm);
  }

  /**
   * 构建表达式变量列表
   *
   * @param fields
   * @return
   */
  private List<ExpressionVariable> buildExpressionVariables(List<FormField> fields) {
    if (fields == null || fields.isEmpty()) {
      return Collections.emptyList();
    }
    var builders = SPI.getReversedServiceStream(ExpressionVariableBuilder.class).collect(Collectors.toList());
    return fields.stream().map(field ->
        builders.stream()
            .filter(builder -> builder.support(field))
            .map(builder -> builder.build(field))
            .findFirst()
            .orElse(null)
    ).filter(Objects::nonNull).collect(Collectors.toList());
  }
}
