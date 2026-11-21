package com.ouroboros.businessmodel.dev.controller;

import static com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService.BusinessModelVO;
import static com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService.BusinessModelGraphVO;
import static com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService.ReferenceRelationVO;
import static com.ouroboros.businessmodel.dev.service.BusinessModelModuleTreeService.ModuleTreeNodeVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.dev.vo.BusinessModelBehaviorPreview;
import com.ouroboros.businessmodel.dev.vo.BusinessModelFormatUpgradePreview;
import com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService;
import com.ouroboros.businessmodel.dev.service.BusinessModelModuleTreeService;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.uischema.wrapper.SchemaNodeWrapper;
import com.ouroboros.util.Json;
import com.ouroboros.util.ResourceUtil;
import com.ouroboros.util.Trees;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/business-model")
@SuppressWarnings("unused")
public class BusinessModelController {
  @Autowired
  BusinessModelDevService businessModelDevService;
  @Autowired
  BusinessModelModuleTreeService moduleTreeService;
  @Autowired(required = false)
  @Qualifier("logicModelDevelopServiceImpl")
  DevelopService<FlowModelMeta> logicModelDevService;

  @Autowired(required = false)
  DevelopService<SchemaNodeWrapper> uiSchemaDevService;

  @GetMapping("/init-form")
  public WebApiResult<BusinessModelMeta> getBusinessModelList(@PathVariable String appName) {
    var initBusinessModelMetaJson = ResourceUtil.getResourceContentStream("META-INF/business-model-meta-templates/default.json")
        .findFirst()
        .orElse("");
    if (initBusinessModelMetaJson.isEmpty()) {
      return WebApiResult.ok(new BusinessModelMeta());
    }
    return WebApiResult.ok(Json.toBean(BusinessModelMeta.class, initBusinessModelMetaJson));
  }

  @GetMapping("/list")
  public ItemsWebApiResult<BusinessModelVO> getAllBusinessModel(@PathVariable String appName, @RequestParam Map<String, Object> requestParam) {
    var businessModelList = businessModelDevService.search(requestParam)
        .stream()
        .filter(ModelFileMetaWrapper.class::isInstance)
        .map(ModelFileMetaWrapper.class::cast)
        .map(ModelFileMetaWrapper::getModel)
        .collect(Collectors.toList());
    var result = businessModelList.stream()
        .map(BusinessModelMeta.class::cast)
        .map(BusinessModelVO::new)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(result);
  }

  @GetMapping("/graph")
  public WebApiResult<BusinessModelGraphVO> getBusinessModelGraph(@PathVariable String appName) {
    return WebApiResult.ok(businessModelDevService.getGraph());
  }

  @GetMapping("/{fullName}/meta")
  public WebApiResult<ModelFileMetaWrapper<BusinessModelVO>> getBusinessModelMeta(@PathVariable String fullName) {
    var meta = businessModelDevService.getMeta(fullName);
    return WebApiResult.of(meta);
  }

  @GetMapping("/{fullName}/model-bundle")
  public WebApiResult<Map<String, List<Map<String, Object>>>> getProjectModel(@PathVariable String appName, @PathVariable String fullName) {
    var modelMap = businessModelDevService.getModelBundle(fullName)
        .map(bundle -> bundle.getAll().entrySet().stream()
            .map(entry -> Tuple.of(entry.getKey().getSimpleName(), modelMap2List(appName, entry.getKey(), entry.getValue())))
            .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2, (v1, v2) -> v2)));
    return WebApiResult.of(modelMap);
  }

  @PostMapping("/behavior-preview")
  public WebApiResult<BusinessModelBehaviorPreview> previewBehavior(
      @RequestBody String draft, @RequestParam String requestId) {
    return WebApiResult.ok(businessModelDevService.previewBehavior(draft, requestId));
  }

  @PostMapping("/{fullName}/format-upgrade-preview")
  public WebApiResult<BusinessModelFormatUpgradePreview> previewFormatUpgrade(
      @PathVariable String fullName, @RequestBody String draft, @RequestParam String requestId) {
    return WebApiResult.of(businessModelDevService.previewFormatUpgrade(fullName, draft, requestId));
  }

  @PostMapping("/{fullName}/format-upgrade")
  public WebApiResult<?> applyFormatUpgrade(@PathVariable String fullName, @RequestBody String draft,
      @RequestParam String planFingerprint) {
    return WebApiResult.of(businessModelDevService.applyFormatUpgrade(fullName, draft, planFingerprint));
  }

  @PostMapping("/{fullName}/script-action-preview")
  public WebApiResult<com.ouroboros.businessmodel.dev.vo.BusinessModelScriptActionPreview> previewScriptAction(
      @PathVariable String fullName, @RequestBody com.ouroboros.businessmodel.dev.vo.BusinessModelScriptActionRequest request,
      @RequestParam String requestId) {
    return WebApiResult.of(businessModelDevService.previewScriptAction(fullName, request, requestId));
  }

  @PostMapping("/{fullName}/script-action")
  public WebApiResult<?> createScriptAction(@PathVariable String fullName,
      @RequestBody com.ouroboros.businessmodel.dev.vo.BusinessModelScriptActionRequest request,
      @RequestParam String planFingerprint) {
    return WebApiResult.of(businessModelDevService.createScriptAction(fullName, request, planFingerprint));
  }

  @GetMapping("/{fullName}/types")
  public WebApiResult<com.ouroboros.businessmodel.codegen.BusinessBehaviorTypeGenerator.GeneratedTypes> getTypes(
      @PathVariable String fullName, @RequestParam(defaultValue = "com.ouroboros.generated") String packageName,
      @RequestParam(defaultValue = "BusinessModelClient") String className) {
    return WebApiResult.of(businessModelDevService.getModelBundle(fullName)
        .flatMap(bundle -> bundle.getModel(com.ouroboros.businessmodel.behavior.BusinessModelBehavior.class, fullName))
        .map(behavior -> com.ouroboros.businessmodel.codegen.BusinessBehaviorTypeGenerator.generate(behavior, packageName, className)));
  }

  @PostMapping("/{fullName}/clone-logicflow-model")
  public WebApiResult<String> cloneFlowModel(@PathVariable String fullName, @RequestParam String name) {
    if (logicModelDevService == null) {
      return WebApiResult.error("逻辑流模型开发服务未启用");
    }
    if (name == null || name.trim().isEmpty()) {
      return WebApiResult.error("流程模型名为必填项");
    }
    if (logicModelDevService.getOne(name).isPresent()) {
      return WebApiResult.ok(name);
    }
    var flowModel = businessModelDevService.getModelBundle(fullName)
        .flatMap(bundle -> bundle.getModel(FlowModelMeta.class, name));
    if (!flowModel.isPresent()) {
      return WebApiResult.error("该业务模型上没有找到流程模型：" + name);
    }
    var flowModelMeta = flowModel.get();
    var createResult = logicModelDevService.create(flowModelMeta, "克隆流程模型");
    return WebApiResult.of(createResult.map(FlowModelMeta::getName));
  }

  @GetMapping("/{fullName}/preview-logicflow-model")
  public WebApiResult<FlowModelMeta> previewFlowModel(@PathVariable String fullName, @RequestParam String name) {
    var flowModel = businessModelDevService.getModelBundle(fullName)
        .flatMap(bundle -> bundle.getModel(FlowModelMeta.class, name));
    return WebApiResult.of(flowModel);
  }

  @PostMapping("/{fullName}/clone-ui-schema")
  public WebApiResult<String> cloneUiSchema(@PathVariable String fullName, @RequestParam String name) {
    if (uiSchemaDevService == null) {
      return WebApiResult.error("UI Schema 开发服务未启用");
    }
    if (name == null || name.trim().isEmpty()) {
      return WebApiResult.error("UI Schema 模型名为必填项");
    }
    String targetName = schemaModelName(name);
    if (uiSchemaDevService.getOne(targetName).isPresent()) {
      return WebApiResult.ok(targetName);
    }
    var uiSchema = businessModelDevService.getModelBundle(fullName)
        .flatMap(bundle -> {
          var uiModel = bundle.getModel(UIModel.class, name);
          return bundle.getModel(SchemaNode.class, name)
              .map(schema -> {
                var schemaNodeWrapper = new SchemaNodeWrapper();
                schemaNodeWrapper.setName(targetName);
                schemaNodeWrapper.setSchema(schema);
                schemaNodeWrapper.setTitle(uiModel.map(UIModel::getTitle).orElse(name));
                return schemaNodeWrapper;
              });
        });

    if (!uiSchema.isPresent()) {
      return WebApiResult.error("该业务模型上没有找到 UI Schema：" + name);
    }
    var uiSchemaMeta = uiSchema.get();
    var createResult = uiSchemaDevService.create(uiSchemaMeta, "克隆 UI Schema");
    return WebApiResult.of(createResult.map(SchemaNodeWrapper::getName));
  }

  @GetMapping("/{fullName}/preview-ui-schema")
  public WebApiResult<SchemaNode> previewUiSchema(@PathVariable String fullName, @RequestParam String name) {
    var uiSchema = businessModelDevService.getModelBundle(fullName)
        .flatMap(bundle -> bundle.getModel(SchemaNode.class, name));
    return WebApiResult.of(uiSchema);
  }

  @GetMapping("/{fullName}/projection-target")
  public WebApiResult<Map<String, Object>> projectionTarget(@PathVariable String appName,
      @PathVariable String fullName, @RequestParam String kind, @RequestParam String name) {
    var bundle = businessModelDevService.getModelBundle(fullName);
    Class<?> modelClass = switch (kind) {
      case "logicflow" -> FlowModelMeta.class;
      case "ui-schema" -> SchemaNode.class;
      default -> throw new com.ouroboros.exception.BusinessException("不支持的投射模型类型：" + kind);
    };
    return WebApiResult.of(bundle.flatMap(value -> value.getModel(modelClass, name))
        .map(model -> modelMap2List(appName, modelClass, Map.of(name, model)).getFirst()));
  }

  private String schemaModelName(String name) {
    return Stream.of(name.split("/"))
        .filter(StringUtils::isNotBlank).collect(Collectors.joining("."));
  }


  private List<Map<String, Object>> modelMap2List(String appName, Class<?> modelClass, Map<String, Object> modelMap) {
    return modelMap.entrySet().stream().map(entry -> {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", entry.getKey());
      map.put("model", entry.getValue());
      if (logicModelDevService != null && FlowModelMeta.class.equals(modelClass)) {
        map.put("editName", entry.getKey());
        map.put("editUrl", "/dev/" + appName + "/logicflow-model/" + entry.getKey());
        map.put("isCloned", logicModelDevService.getOne(entry.getKey()).isPresent());
      }
      if (uiSchemaDevService != null && SchemaNode.class.equals(modelClass)) {
        var schemaName = schemaModelName(entry.getKey());
        map.put("editUrl", "/dev/" + appName + "/ui-schema/" + schemaName);
        map.put("editName", schemaName);
        map.put("isCloned", uiSchemaDevService.getOne(schemaName).isPresent());
      }
      return map;
    }).collect(Collectors.toList());
  }

  @GetMapping("/{fullName}/reference-relations")
  public ItemsWebApiResult<ReferenceRelationVO> getReferenceRelations(@PathVariable String fullName) {
    var referenceRelationList = businessModelDevService.getReferenceRelations(fullName);
    return ItemsWebApiResult.ok(referenceRelationList);
  }

  @PostMapping("/{fullName}/move")
  public WebApiResult<Void> move(@PathVariable String fullName, @RequestBody MoveVO move) {
    if (!businessModelDevService.getReferenceRelations(fullName).isEmpty()) {
      return WebApiResult.error("该模型已被引用，不能移动");
    }
    var name = fullName.substring(fullName.lastIndexOf(".") + 1);

    var newFullName = move.getNamespace() + (move.getNamespace().isEmpty() ? "" : ".") + name;
    if (newFullName.equalsIgnoreCase(fullName)) {
      return WebApiResult.error("模型已在目标位置");
    }

    var result = businessModelDevService.move(fullName, newFullName, "移动业务模型：" + fullName + " -> " + newFullName);
    return WebApiResult.of(result);
  }

  @PostMapping("/{fullName}/rename")
  public WebApiResult<Void> rename(@PathVariable String fullName, @RequestBody RenameVO rename) {
    if (!businessModelDevService.getReferenceRelations(fullName).isEmpty()) {
      return WebApiResult.error("该模型已被引用，不能重命名");
    }
    var namespacePart = fullName.substring(0, fullName.lastIndexOf(".") + 1);
    var newFullName = namespacePart + rename.getName();

    if (newFullName.equalsIgnoreCase(fullName)) {
      return WebApiResult.error("模型名没有变化");
    }

    var result = businessModelDevService.move(fullName, newFullName, "重命名业务模型：" + fullName + " -> " + newFullName);
    return WebApiResult.of(result);
  }

  @GetMapping("/module-tree")
  public ItemsWebApiResult<ModuleTreeNodeVO> getModuleTree(@RequestParam(defaultValue = "") String except) {
    var moduleTree = moduleTreeService.getModuleTree().get();
    if (StringUtils.isEmpty(except)) {
      return ItemsWebApiResult.ok(moduleTree);
    }
    var filteredTree = Trees.filterTree(moduleTree,
        node -> !StringUtils.equalsIgnoreCase(node.getFullName(), except),
        ModuleTreeNodeVO::getChildren,
        ModuleTreeNodeVO::setChildren
    );
    return ItemsWebApiResult.ok(filteredTree);
  }

  @PostMapping("/module-tree")
  public WebApiResult<Void> addModuleTree(@RequestBody ModuleTreeNodeVO node) {
    node.setFullName(node.getParentName().isEmpty() ? node.getName() : node.getParentName() + "." + node.getName());
    var result = moduleTreeService.addModule(node).toEither();
    return WebApiResult.of(result);
  }

  @PutMapping("/module-tree/{fullName}")
  public WebApiResult<Void> updateModuleTree(@PathVariable String fullName, @RequestBody ModuleTreeNodeVO node) {
    node.setFullName(node.getParentName().isEmpty() ? node.getName() : node.getParentName() + "." + node.getName());
    var result = moduleTreeService.updateModule(fullName, node).toEither();
    return WebApiResult.of(result);
  }

  @DeleteMapping("/module-tree/{fullName}")
  public WebApiResult<Void> deleteModuleTree(@PathVariable String fullName) {
    var result = moduleTreeService.deleteModule(fullName).toEither();
    return WebApiResult.of(result);
  }

  public static class MoveVO {
    String namespace;

    public String getNamespace() {
      return namespace;
    }

    public void setNamespace(String namespace) {
      this.namespace = namespace;
    }
  }

  public static class RenameVO {
    String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
