package com.ouroboros.dynamicform.provider;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.dynamicform.service.DynamicFormDefineService;
import com.ouroboros.dynamicform.schema.DynamicFormFieldSchemaBuilder;
import com.ouroboros.uimodel.SimpleUIModel;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uimodel.UIModelProvider;
import com.ouroboros.uischema.schema.PageNode;

@Component
public class DynamicFormUIModelProvider {

  @Autowired
  private DynamicFormDefineService dynamicFormDefineService;

  @Autowired
  private DynamicFormFieldSchemaBuilder dynamicFormFieldSchemaBuilder;

  public DynamicFormUIModelProvider() {
    Proxy.provider = this;
  }

  public Optional<UIModel> getUiModel(String path) {
    if (path.startsWith("/dynamic-form/form/")) {
      var formName = path.substring("/dynamic-form/form/".length());
      var form = dynamicFormDefineService.getDynamicFormByName(formName);
      return form.map(guardFormUiModelBuilder(this::buildFormUiModel, "已部署"));
    }
    if (path.startsWith("/dynamic-form/form-page/")) {
      var formName = path.substring("/dynamic-form/form-page/".length());
      var form = dynamicFormDefineService.getDynamicFormByName(formName);
      return form.map(guardFormUiModelBuilder(this::buildFormPageUiModel, "已部署"));
    }
    if (path.startsWith("/dynamic-form/table/")) {
      var formName = path.substring("/dynamic-form/table/".length());
      var form = dynamicFormDefineService.getDynamicFormByName(formName);
      return form.map(guardFormUiModelBuilder(this::buildCrudTableUiModel, "已部署", "已归档"));
    }
    if (path.startsWith("/dynamic-form/table-page/")) {
      var formName = path.substring("/dynamic-form/table-page/".length());
      var form = dynamicFormDefineService.getDynamicFormByName(formName);
      return form.map(guardFormUiModelBuilder(this::buildCrudTablePageUiModel, "已部署", "已归档"));
    }
    return Optional.empty();
  }

  public Map<String, UIModel> getUiModelMap() {
    var statusList = Arrays.asList("已部署", "已归档");
    var where = Collections.singletonMap("status", statusList);
    var query = Collections.singletonMap("where", (Object) where);
    var formList = dynamicFormDefineService.search(query);
    var formModelStream = formList.stream()
        .map(this::buildFormUiModel);
    var tableModelStream = formList.stream()
        .map(this::buildCrudTableUiModel);
    var formPageModelStream = formList.stream()
        .map(this::buildFormPageUiModel);
    var tablePageModelStream = formList.stream()
        .map(this::buildCrudTablePageUiModel);
    return Stream.of(formModelStream, tableModelStream, formPageModelStream, tablePageModelStream)
        .flatMap(Function.identity())
        .collect(Collectors.toMap(
            UIModel::getPath,
            Function.identity(),
            (v1, v2) -> v2
        ));
  }

  private Function<DynamicForm, UIModel> guardFormUiModelBuilder(Function<DynamicForm, UIModel> builder, String... statuses) {
    return form -> {
      var statusList = Arrays.asList(statuses);
      if (statusList.contains(form.getStatus())) {
        return builder.apply(form);
      }
      return null;
    };
  }

  private UIModel buildFormUiModel(DynamicForm form) {
    var uiModel = new SimpleUIModel();
    var formSchema = dynamicFormFieldSchemaBuilder.buildFormSchema(form);

    uiModel.setTitle(form.getTitle());
    uiModel.setPath("/dynamic-form/form/" + form.getName());
    uiModel.setType("amis");
    uiModel.setExtraProp("schema", formSchema);

    return uiModel;
  }

  private UIModel buildFormPageUiModel(DynamicForm form) {
    var uiModel = new SimpleUIModel();
    var formSchema = dynamicFormFieldSchemaBuilder.buildFormSchema(form);
    var pageSchema = new PageNode();

    pageSchema.setTitle(form.getTitle());
    pageSchema.setBody(formSchema);

    uiModel.setTitle(form.getTitle());
    uiModel.setPath("/dynamic-form/form-page/" + form.getName());
    uiModel.setType("amis");
    uiModel.setExtraProp("schema", pageSchema);

    return uiModel;
  }

  private UIModel buildCrudTableUiModel(DynamicForm form) {
    var uiModel = new SimpleUIModel();
    var crudTable = dynamicFormFieldSchemaBuilder.buildCrudTableSchema(form);

    uiModel.setTitle(form.getTitle());
    uiModel.setPath("/dynamic-form/table/" + form.getName());
    uiModel.setType("amis");
    uiModel.setExtraProp("schema", crudTable);

    return uiModel;
  }

  private UIModel buildCrudTablePageUiModel(DynamicForm form) {
    var uiModel = new SimpleUIModel();
    var crudTable = dynamicFormFieldSchemaBuilder.buildCrudTableSchema(form);
    var pageSchema = new PageNode();

    pageSchema.setTitle(form.getTitle());
    pageSchema.setBody(crudTable);

    uiModel.setTitle(form.getTitle());
    uiModel.setPath("/dynamic-form/table-page/" + form.getName());
    uiModel.setType("amis");
    uiModel.setExtraProp("schema", pageSchema);

    return uiModel;
  }

  @Priority(200)
  public static class Proxy implements UIModelProvider {
    private static DynamicFormUIModelProvider provider;

    @Override
    public Optional<UIModel> getUIModel(String path) {
      return Optional.ofNullable(provider).flatMap(map -> map.getUiModel(path));
    }

    @Override
    public Map<String, UIModel> getUIModelMap() {
      return Optional.ofNullable(provider).map(DynamicFormUIModelProvider::getUiModelMap).orElse(Collections.emptyMap());
    }
  }
}
