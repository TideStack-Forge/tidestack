package com.ouroboros.businessmodel.dev.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.vavr.control.Try;

import com.github.promeg.pinyinhelper.Pinyin;
import com.ouroboros.businessmodel.BusinessFieldView;
import com.ouroboros.businessmodel.BusinessRelationView;
import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.util.Strings;
import com.ouroboros.util.excel.ExcelReader;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.TreeSelectOption;
import com.ouroboros.web.WebApiResult;


@RestController
@RequestMapping("/api/dev/{appName}/business-model-helper")
public class BusinessModelHelperController {

  private FieldTypeCatalog fieldTypeCatalog;

  @Autowired
  BusinessModelDevService businessModelDevService;

  @Autowired
  com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService approvalFieldSourceService;

  public BusinessModelHelperController(FieldTypeCatalog fieldTypeCatalog) {
    this.fieldTypeCatalog = fieldTypeCatalog;
  }

  @PostMapping("/{fullName}/field-input-schema")
  public WebApiResult<FormItemNode> getFieldSchema(@PathVariable("appName") String appName, @PathVariable("fullName") String fullName, @RequestBody BusinessModelFieldMeta field) {
    var type = field.getType();
    if (type == null) {
      return WebApiResult.error("字段类型不能为空");
    }
    return WebApiResult.of(error -> WebApiResult.error(error.getMessage()), buildFieldSchema(appName, field, true));
  }

  @PostMapping("/{fullName}/field-display-schema")
  public WebApiResult<FormItemNode> getFieldDisplaySchema(@PathVariable("appName") String appName, @PathVariable("fullName") String fullName, @RequestBody BusinessModelFieldMeta field) {
    var type = field.getType();
    if (type == null) {
      return WebApiResult.error("字段类型不能为空");
    }
    return WebApiResult.of(error -> WebApiResult.error(error.getMessage()), buildFieldSchema(appName, field, false));
  }

  private Try<FormItemNode> buildFieldSchema(String appName, BusinessModelFieldMeta field, boolean input) {
    var params = Optional.ofNullable(field.getExtraProps()).orElseGet(Collections::emptyMap);
    return Try.of(() -> fieldTypeCatalog.compile(FieldTypeCatalogContext.devApp(appName), field.getType(), params))
        .flatMap(result -> result.fold(
            Try::failure,
            compiledField -> toFormItem(compiledField, input)
        ));
  }

  private Try<FormItemNode> toFormItem(CompiledFieldType compiledField, boolean input) {
    var formItem = input ? compiledField.toInputFormItem() : compiledField.toDisplayFormItem();
    return formItem != null ? Try.success(formItem) : Try.failure(new IllegalStateException("字段类型不存在"));
  }

  @GetMapping("/{fullName}/model-meta")
  public WebApiResult<Map<String, Object>> getModelMeta(@PathVariable("appName") String appName, @PathVariable("fullName") String fullName) {
    var model = businessModelDevService.getBusinessModel(fullName);
    if (!model.isPresent()) {
      return WebApiResult.success(Collections.emptyMap());
    }
    return WebApiResult.success(buildModelMeta(model.get()));
  }

  @GetMapping("/approval-workflow-fields")
  public WebApiResult<Map<String, Object>> getApprovalWorkflowFields(
      @PathVariable("appName") String appName,
      @RequestParam(value = "formType", required = false) String formType,
      @RequestParam(value = "formKey", required = false) String formKey,
      @RequestParam(value = "definitionId", required = false) String definitionId) {
    return WebApiResult.success(approvalFieldSourceService.source(appName, formType, formKey, definitionId));
  }

  private Map<String, Object> buildModelMeta(BusinessModel model) {
    var data = new LinkedHashMap<String, Object>();

    var modelData = new LinkedHashMap<String, Object>();
    modelData.put("name", model.getFullName());
    modelData.put("label", model.getTitle());
    data.put("model", modelData);

    var fields = model.getFields().stream()
        .map(this::buildModelMetaField)
        .collect(Collectors.toList());
    data.put("fields", fields);

    var relations = model.getRelations().stream()
        .map(this::buildModelMetaRelation)
        .collect(Collectors.toList());
    data.put("relations", relations);

    return data;
  }

  private Map<String, Object> buildModelMetaField(BusinessFieldView field) {
    return approvalFieldSourceService.buildModelMetaField(field);
  }

  private Map<String, Object> buildModelMetaRelation(BusinessRelationView relation) {
    var result = new LinkedHashMap<String, Object>();
    result.put("name", relation.getName());
    result.put("label", Optional.ofNullable(relation.getTitle()).orElse(relation.getName()));
    result.put("type", relation.getType());

    var relatedModel = relation.getRelatedModel();
    if (relatedModel != null) {
      result.put("targetModel", relatedModel.getFullName());
      result.put("targetModelLabel", relatedModel.getTitle());
    } else {
      result.put("targetModel", relation.getRelatedModelName());
    }

    return result;
  }

  @GetMapping("/{fullName}/exportable-fields")
  public ItemsWebApiResult<TreeSelectOption> buildExportableFields(@PathVariable("fullName") String fullName) {
    var model = businessModelDevService.getBusinessModel(fullName);
    if (!model.isPresent()) {
      return ItemsWebApiResult.ok(Collections.emptyList());
    }
    return ItemsWebApiResult.success(buildExportableFields(model.get()));
  }

  private List<TreeSelectOption> buildExportableFields(BusinessModel model) {
    var fields = model.getFields();
    var relations = model.getRelations();
    var basicAuditFields = getBasicAuditFields(model);
    var exportableFields = new ArrayList<TreeSelectOption>();
    fields.forEach(field -> {
      var node = buildFieldNode(field);
      exportableFields.add(node);
    });
    exportableFields.addAll(basicAuditFields);
    relations.forEach(relation -> {
      var node = buildRelationNode(relation);
      if (node != null) {
        exportableFields.add(node);
      }
    });
    return exportableFields;
  }

  private List<TreeSelectOption> getBasicAuditFields(BusinessModel model) {
    var isEnabledBasicAudit = model.getExtraPropDeep(Boolean.class, "features.isEnabledAuditFields").orElse(false);
    if (!isEnabledBasicAudit) {
      return Collections.emptyList();
    }
    var auditFields = new ArrayList<TreeSelectOption>();
    var createdAt = new TreeSelectOption();
    createdAt.setLabel("创建时间");
    createdAt.setValue("createdAt");
    createdAt.addExtraProp("fieldType", "DataModelField");
    createdAt.addExtraProp("fieldName", "createdAt");
    createdAt.addExtraProp("fieldLabel", "创建时间");
    auditFields.add(createdAt);
    var createdBy = new TreeSelectOption();
    createdBy.setLabel("创建人");
    createdBy.setValue("createdBy");
    createdBy.addExtraProp("fieldType", "DataModelField");
    createdBy.addExtraProp("fieldName", "createdBy");
    createdBy.addExtraProp("fieldLabel", "创建人");
    auditFields.add(createdBy);
    var updatedAt = new TreeSelectOption();
    updatedAt.setLabel("更新时间");
    updatedAt.setValue("updatedAt");
    updatedAt.addExtraProp("fieldType", "DataModelField");
    updatedAt.addExtraProp("fieldName", "updatedAt");
    updatedAt.addExtraProp("fieldLabel", "更新时间");
    auditFields.add(updatedAt);
    var updatedBy = new TreeSelectOption();
    updatedBy.setLabel("更新人");
    updatedBy.setValue("updatedBy");
    updatedBy.addExtraProp("fieldType", "DataModelField");
    updatedBy.addExtraProp("fieldName", "updatedBy");
    updatedBy.addExtraProp("fieldLabel", "更新人");
    auditFields.add(updatedBy);
    return auditFields;
  }

  private TreeSelectOption buildFieldNode(BusinessFieldView field) {
    var node = new TreeSelectOption();
    node.setLabel(field.getTitle());
    node.setValue(field.getName());
    node.addExtraProp("fieldType", "BusinessModelField");
    node.addExtraProp("fieldName", field.getName());
    node.addExtraProp("fieldLabel", field.getTitle());
    return node;
  }

  // 需要递归出关系的关系，但是需要避免循环引用，这里待完善，同时参数列表也是要完善的，以便于排除循环引用
  private TreeSelectOption buildRelationNode(BusinessRelationView relation) {
    return buildRelationNode(relation, "", new HashSet<>());
  }

  /**
   * 递归构建关系节点
   * @param relation 业务模型关系
   * @param pathPrefix 路径前缀，用于构建完整的字段路径
   * @param visitedModels 已访问的模型集合，用于避免循环引用
   * @return 树形选择器节点
   */
  private TreeSelectOption buildRelationNode(BusinessRelationView relation, String pathPrefix, Set<String> visitedModels) {
    // 排除子表关系
    if ("child".equalsIgnoreCase(relation.getType())) {
      return null;
    }

    var node = new TreeSelectOption();
    node.setLabel(relation.getTitle());
    // 关系节点的 value 只设置关系名称本身，不包含路径前缀
    node.setValue(relation.getName());

    // 构建当前关系的完整路径（用于传递给子节点）
    String currentPath = pathPrefix.isEmpty() ? relation.getName() : pathPrefix + "." + relation.getName();

    // 获取关联的模型
    var relatedModel = relation.getRelatedModel();
    if (relatedModel == null) {
      return node;
    }

    // 检查是否已经访问过该模型，避免循环引用
    var modelFullName = relatedModel.getFullName();
    if (visitedModels.contains(modelFullName)) {
      return node;
    }

    // 标记当前模型已访问
    visitedModels.add(modelFullName);

    // 获取关联模型的字段和关系
    var children = new ArrayList<TreeSelectOption>();

    // 添加关联模型的字段
    relatedModel.getFields().forEach(field -> {
      var childNode = buildFieldNode(field);
      // 使用完整路径：当前关系路径 + 字段名
      String fieldPath = currentPath + "." + field.getName();
      childNode.setValue(fieldPath);
      childNode.addExtraProp("fieldName", fieldPath);
      children.add(childNode);
    });

    // 递归添加关联模型的关系
    relatedModel.getRelations().forEach(subRelation -> {
      // 创建新的访问记录集合副本，避免不同分支间的干扰
      // 传递当前完整路径作为前缀，这样子关系会自动构建正确的完整路径
      var childNode = buildRelationNode(subRelation, currentPath, new HashSet<>(visitedModels));
      if (childNode != null) {
        children.add(childNode);
      }
    });

    // 设置子节点
    if (!children.isEmpty()) {
      node.setChildren(children);
    }

    return node;
  }

  @PostMapping("/{fullName}/import-fields")
  public ItemsWebApiResult<BusinessModelFieldMeta> importFields(@RequestParam(name = "mode", defaultValue = "row") String mode,
                                                                @RequestParam(name = "sheetIndex", defaultValue = "0") Integer sheetIndex,
                                                                @RequestParam(name = "startRow", defaultValue = "0") Integer startRow,
                                                                @RequestParam(name = "startColumn", defaultValue = "0") Integer startColumn,
                                                                MultipartFile file) {
    if ("row".equalsIgnoreCase(mode)) {
      return ItemsWebApiResult.success(getFieldsFromExcelRowMode(file, sheetIndex, startRow, startColumn));
    }
    return ItemsWebApiResult.success(getFieldsFromExcelColumnMode(file, sheetIndex, startRow, startColumn));
  }

  private List<BusinessModelFieldMeta> getFieldsFromExcelRowMode(MultipartFile file, int sheetIndex, int startRow, int startColumn) {
    var headerRow = Try.of(() -> {
      var isXls = file.getOriginalFilename().endsWith(".xls");
      var row = ExcelReader
          .readRow(file.getInputStream(), isXls, sheetIndex, startRow)
          .stream()
          .skip(startColumn)
          .filter(Objects::nonNull)
          .map(Object::toString)
          .filter(field -> !"id".equalsIgnoreCase(field))
          .collect(Collectors.toList());
      return row;
    }).getOrElse(Collections::emptyList);
    var fieldList = headerRow.stream()
        .map(title -> {
          var name = chineseToPinyin(title);
          var fieldMeta = new BusinessModelFieldMeta();
          fieldMeta.setName(name);
          fieldMeta.setTitle(title);
          fieldMeta.setType("单行文本");
          return fieldMeta;
        })
        .collect(Collectors.toList());
    return fieldList;
  }

  private List<BusinessModelFieldMeta> getFieldsFromExcelColumnMode(MultipartFile file, int sheetIndex, int startRow, int startColumn) {
    var fields = Try.of(() -> {
      var isXls = file.getOriginalFilename().endsWith(".xls");
      var sheetData = ExcelReader.readExcel(file.getInputStream(), isXls);
      var sheet = sheetData.get(sheetIndex);
      return sheet.toMappedRows();
    }).getOrElse(Collections::emptyList);

    return fields.stream()
        .map(record -> {
          var fieldName = getByCandidateKeys(record, "字段名", "字段名称", "字段", "name");
          var fieldTitle = getByCandidateKeys(record, "显示名", "字段标题", "title", "label");
          var fieldType = getByCandidateKeys(record, "类型", "字段类型", "type");
          if (fieldName == null) {
            return null;
          }
          var fieldMeta = new BusinessModelFieldMeta();
          fieldMeta.setName(fieldName);
          fieldMeta.setTitle(fieldTitle);
          fieldMeta.setType(fieldType);
          return fieldMeta;
        })
        .filter(Objects::nonNull)
        .filter(field -> !"id".equalsIgnoreCase(field.getName()))
        .collect(Collectors.toList());
  }

  private static String chineseToPinyin(String chinese) {
    var pinyinArr = Pinyin.toPinyin(chinese, ",").split(",");
    StringBuilder pinyin = new StringBuilder(pinyinArr[0].toLowerCase());
    for (var index = 1; index < pinyinArr.length; index++) {
      pinyin.append(Strings.upperFirst(pinyinArr[index].toLowerCase()));
    }
    // 去掉特殊字符，只保留字母和数字
    var pinyinStr = pinyin.toString().replaceAll("[^a-zA-Z0-9]", "");
    // 去掉头部数字
    return pinyinStr.replaceAll("^[0-9]+", "");

  }

  private String getByCandidateKeys(Map<String, Object> map, String... keys) {
    for (var key : keys) {
      var value = map.get(key);
      if (value != null) {
        return value.toString();
      }
    }
    return null;
  }
}
