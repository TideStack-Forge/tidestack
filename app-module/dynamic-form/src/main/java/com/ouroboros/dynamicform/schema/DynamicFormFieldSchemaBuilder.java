package com.ouroboros.dynamicform.schema;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.dynamicform.model.FormField;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.uischema.schema.*;

@Component
public class DynamicFormFieldSchemaBuilder implements DynamicFormSchemaService {
  private final FieldTypeCatalog fieldTypeCatalog;

  public DynamicFormFieldSchemaBuilder(FieldTypeCatalog fieldTypeCatalog) {
    this.fieldTypeCatalog = fieldTypeCatalog;
  }

  public CRUDTableNode buildCrudTableSchema(DynamicForm form) {
    var compiledFields = compileFields(form.getFields());
    var crudTable = new CRUDTableNode();
    var exportNode = buildExportActionNode(form);
    var importNode = buildImportActionNode(form.getName());
    var addNode = buildAddActionNode(form, compiledFields);
    var columns = new ArrayList<CrudTableColumnNode>();
    var idColumn = new CrudTableColumnNode();
    var operationColumn = buildOperationColumn(form, compiledFields);
    var filterForm = buildFilterForm(form);

    addNode.setProp("align", "right");

    crudTable.setApi("/api/dynamic-form/" + form.getName() + "/data");
    crudTable.setTitle(form.getTitle());
    crudTable.setHeaderToolbar(Arrays.asList(exportNode, importNode, "reload", addNode));
    crudTable.setFilter(filterForm);

    idColumn.setName("id");
    idColumn.setLabel("ID");

    columns.add(idColumn);
    columns.addAll(form.getFields().stream()
        .map(field -> buildCrudColumn(field, compiledFields.get(field.getName())))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList()));
    columns.add(operationColumn);
    crudTable.setColumns(columns);
    return crudTable;
  }

  public FormNode buildFormSchema(DynamicForm form, String... fieldNames) {
    var selectedFieldNames = Arrays.asList(fieldNames);
    var fields = fieldNames.length == 0
        ? form.getFields()
        : form.getFields().stream()
            .filter(field -> selectedFieldNames.contains(field.getName()))
            .collect(Collectors.toList());
    return buildFormSchema(form, fields, compileFields(fields));
  }

  private FormNode buildFormSchema(DynamicForm form, List<FormField> fields, Map<String, CompiledFieldType> compiledFields) {
    var formSchema = new FormNode();
    var api = new ApiConfig();
    formSchema.setApi(api);
    formSchema.setTitle(form.getTitle());
    formSchema.setBody((Collection) fields.stream()
        .map(field -> buildInputFormItem(field, compiledFields.get(field.getName())))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList()));

    api.setMethod("post");
    api.setUrl("/api/dynamic-form/" + form.getName() + "/data");
    api.setSuccessMessage("表单保存成功");

    return formSchema;
  }

  public FormNode buildFilterForm(DynamicForm form) {
    var filterFields = form.getFields().stream()
        .filter(FormField::getFilterable)
        .map(field -> {
          var fieldNode = new FormItemNode();
          fieldNode.setType("input-text");
          fieldNode.setName(field.getName());
          fieldNode.setLabel(field.getLabel());
          return fieldNode;
        })
        .collect(Collectors.toList());
    if (filterFields.isEmpty()) {
      return null;
    }
    var filterForm = new FormNode();
    var actions = new ArrayList<ActionNode>();
    var submitAction = new ActionNode();
    var resetAction = new ActionNode();

    filterForm.setBody((Collection) filterFields);
    filterForm.setActions((List) actions);

    actions.add(resetAction);
    actions.add(submitAction);

    submitAction.setActionType("submit");
    submitAction.setLabel("搜索");
    submitAction.setLevel("primary");

    resetAction.setActionType("clear-and-submit");
    resetAction.setLabel("重置");

    return filterForm;
  }

  public Object buildExportActionNode(DynamicForm form) {
    var exportActionNode = new SchemaNode();
    exportActionNode.setType("export-excel");
    exportActionNode.setProp("label", "导出Excel");
    exportActionNode.setProp("icon", "fa fa-file-excel");
    exportActionNode.setProp("filename", form.getTitle() + "导出数据-${ DATETOSTR(NOW(), 'YYYY-MM-DD_HH_MM_SS') }-共${total}条");
    exportActionNode.setProp("api", "/api/dynamic-form/" + form.getName() + "/data?page=1&perPage=${total}");
    exportActionNode.setProp("exportColumns", form.getFields().stream()
        .map(field -> {
          var column = new HashMap<String, Object>();
          column.put("name", field.getName());
          column.put("label", field.getLabel());
          return column;
        })
        .collect(Collectors.toList()));
    return exportActionNode;
  }

  public ActionNode buildImportActionNode(String formName) {
    var importActionNode = new ActionNode();
    var importDialog = new SchemaNode();
    var importForm = new FormNode();
    var importFormItem = new FormItemNode();

    importActionNode.setLabel("导入数据");
    importActionNode.setActionType("dialog");
    importActionNode.setProp("dialog", importDialog);

    importDialog.setType("dialog");
    importDialog.setProp("body", importForm);
    importDialog.setProp("title", "导入数据");
    importForm.setApi("/api/dynamic-form/" + formName + "/data/import");
    importForm.setBody(importFormItem);

    importFormItem.setType("input-excel");
    importFormItem.setLabel("导入数据");
    importFormItem.setName("items");

    return importActionNode;
  }

  public ActionNode buildAddActionNode(DynamicForm form) {
    return buildAddActionNode(form, compileFields(form.getFields()));
  }

  private ActionNode buildAddActionNode(DynamicForm form, Map<String, CompiledFieldType> compiledFields) {
    var addAction = new ActionNode();
    var addDialog = new SchemaNode();
    var addForm = buildFormSchema(form, form.getFields(), compiledFields);

    addAction.setActionType("dialog");
    addAction.setLabel("添加数据");
    addAction.setLevel("primary");
    addAction.setProp("dialog", addDialog);

    addDialog.setType("dialog");
    addDialog.setProp("title", "添加数据");
    addDialog.setProp("size", "md");
    addDialog.setProp("body", addForm);
    addDialog.setProp("data", Collections.emptyMap());

    return addAction;
  }

  public CrudTableColumnNode buildOperationColumn(DynamicForm form) {
    return buildOperationColumn(form, compileFields(form.getFields()));
  }

  private CrudTableColumnNode buildOperationColumn(DynamicForm form, Map<String, CompiledFieldType> compiledFields) {
    var operationColumn = new CrudTableColumnNode();
    var editButton = new ActionNode();
    var deleteButton = new ActionNode();
    var deleteApi = new ApiConfig();
    var editDialog = new SchemaNode();
    var editForm = buildFormSchema(form, form.getFields(), compiledFields);
    var editFormInitApi = new ApiConfig();

    operationColumn.setType("operation");
    operationColumn.setLabel("操作");
    operationColumn.setProp("buttons", Arrays.asList(editButton, deleteButton));

    editButton.setLabel("编辑");
    editButton.setActionType("dialog");
    editButton.setProp("dialog", editDialog);

    editDialog.setType("dialog");
    editDialog.setProp("body", editForm);
    editDialog.setProp("title", "编辑" + form.getTitle());

    if (editForm.getApi() instanceof ApiConfig formApi) {
      formApi.setMethod("put");
      formApi.setUrl("/api/dynamic-form/" + form.getName() + "/data/${id}");
    }
    editForm.setInitApi(editFormInitApi);

    editFormInitApi.setMethod("get");
    editFormInitApi.setUrl("/api/dynamic-form/" + form.getName() + "/data/${id}");

    deleteButton.setLabel("删除");
    deleteButton.setLevel("link");
    deleteButton.setClassName("text-danger");
    deleteButton.setActionType("ajax");
    deleteButton.setConfirmText("您确认要删除?");
    deleteButton.setProp("api", deleteApi);

    deleteApi.setMethod("delete");
    deleteApi.setUrl("/api/dynamic-form/" + form.getName() + "/data/${id}");

    return operationColumn;
  }

  private Optional<FormItemNode> buildInputFormItem(FormField field, CompiledFieldType compiledField) {
    return Optional.ofNullable(compiledField)
        .map(CompiledFieldType::toInputFormItem)
        .map(this::copyFormItem)
        .map(item -> applyFieldSettings(item, field));
  }

  private Optional<CrudTableColumnNode> buildCrudColumn(FormField field, CompiledFieldType compiledField) {
    return Optional.ofNullable(compiledField)
        .map(CompiledFieldType::toDisplayFormItem)
        .map(this::copyFormItem)
        .map(CrudTableColumnNode::new)
        .map(column -> {
          column.setName(field.getName());
          column.setLabel(field.getLabel());
          return column;
        });
  }

  private Optional<CompiledFieldType> compileField(FormField field) {
    var params = field.getParams() == null ? Collections.<String, Object>emptyMap() : field.getParams();
    return fieldTypeCatalog.compile(FieldTypeCatalogContext.runtime(), field.getType(), params)
        .toJavaOptional();
  }

  private Map<String, CompiledFieldType> compileFields(List<FormField> fields) {
    return fields.stream()
        .map(field -> {
          var compiledField = compileField(field);
          if (!compiledField.isPresent()) {
            return null;
          }
          return new AbstractMap.SimpleEntry<String, CompiledFieldType>(field.getName(), compiledField.get());
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (v1, v2) -> v2,
            LinkedHashMap::new));
  }

  private FormItemNode applyFieldSettings(FormItemNode item, FormField field) {
    item.setName(field.getName());
    item.setLabel(field.getLabel());
    item.setRequired(field.getRequired());
    return item;
  }

  private FormItemNode copyFormItem(FormItemNode item) {
    var copy = new FormItemNode();
    copy.putProps(new LinkedHashMap<String, Object>(item.getProps()));
    return copy;
  }
}
