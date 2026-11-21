package com.ouroboros.businessmodel.dev.service.impl;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vavr.Tuple;
import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.dev.DevAppModuleProvider;
import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelFormatUpgrade;
import com.ouroboros.businessmodel.BusinessModelScriptAction;
import com.ouroboros.businessmodel.behavior.BusinessBehaviorCatalog;
import com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts;
import com.ouroboros.businessmodel.dev.vo.BusinessModelBehaviorPreview;
import com.ouroboros.businessmodel.dev.vo.BusinessModelFormatUpgradePreview;
import com.ouroboros.businessmodel.dev.vo.BusinessModelResourceChange;
import com.ouroboros.businessmodel.dev.vo.BusinessModelScriptActionRequest;
import com.ouroboros.businessmodel.dev.vo.BusinessModelScriptActionPreview;
import com.ouroboros.businessmodel.dev.vo.BusinessModelValidationState;
import com.ouroboros.businessmodel.BusinessModelMetaParser;
import com.ouroboros.businessmodel.BusinessModelBuilder;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.dev.service.support.BusinessModelValidationSnapshotSupport;
import com.ouroboros.businessmodel.dev.vo.BusinessModelReadEnvelope;
import com.ouroboros.businessmodel.dev.vo.BusinessModelValidationSnapshot;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.model.BusinessModelModule;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.modelmanage.PreparedModelCreate;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.util.Converter;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

@Service
@DevelopModule("business-model")
@SuppressWarnings("unused")
public class BusinessModelDevService extends BaseDevelopService<BusinessModelMeta> implements BusinessModelProvider, DevAppModuleProvider {
  private static final List<String> VALIDATION_READ_FACE_FIELDS = List.of(
      "validationSnapshot",
      "validationState",
      "validationSummary",
      "validationErrors",
      "validationDiagnostics",
      "lastValidatedTime",
      "behaviorPreview",
      "behaviorFormatUpgrade",
      "behaviorCatalog"
  );
  private static final List<BusinessModelBuilder> businessModelBuilders = SPI.getReversedServiceStream(BusinessModelBuilder.class).collect(Collectors.toList());
  final private String MODEL_FILE_DIRECTORY = "business-model";
  final private String MODEL_FILE_SUFFIX = ".business.json";
  @Autowired(required = false)
  @Qualifier("logicModelDevelopServiceImpl")
  DevelopService<FlowModelMeta> logicModelDevService;

  @Override
  protected String getModelDisplayName() {
    return "业务模型";
  }

  @Override
  protected String getModelFileDirectory() {
    return MODEL_FILE_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_SUFFIX;
  }

  @Override
  protected boolean isAutoCommitWhenUpdate() {
    return false;
  }

  @Override
  protected Class<BusinessModelMeta> getModelClass() {
    return BusinessModelMeta.class;
  }

  @Override
  protected String getFullName(BusinessModelMeta model) {
    return model.getFullName();
  }

  public List<BusinessModelMeta> getAll() {
    var walkResult = walkModelFilesForRead()
        .map(VCSFile::getContent)
        .map(this::buildModelFromFileContent);
    return walkResult
        .filter(Try::isSuccess)
        .map(Try::get)
        .collect(Collectors.toList());
  }

  public BusinessModelGraphVO getGraph() {
    var models = getAll();
    var nodes = models.stream()
        .map(model -> new BusinessModelGraphNodeVO(model, getExistingOrPrimaryModelVCSFile(model.getFullName())))
        .collect(Collectors.toList());
    var edges = new ArrayList<BusinessModelGraphEdgeVO>();
    models.stream()
        .forEach(model -> model.relationDefinitions().forEach(relation -> {
          if (relation.getModel() == null || relation.getModel().trim().isEmpty()) {
            return;
          }
          edges.add(new BusinessModelGraphEdgeVO(
              model.getFullName(),
              relation.getModel(),
              relation.getName(),
              relation.getTitle(),
              relation.getType()));
        }));
    return new BusinessModelGraphVO(nodes, edges);
  }

  @Override
  protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
    return file -> {
      var content = file.getContent();
      var model = buildModelFromFileContent(content);
      return model.map(meta -> new ModelFileMetaWrapper<>(meta, file));
    };
  }

  @Override
  protected Predicate<ModelFileMetaWrapper<BusinessModelMeta>> buildSearchFilter(Map<String, Object> searchParam) {
    final var module = searchParam.getOrDefault("module", "").toString();
    final var keyword = searchParam.getOrDefault("keyword", "").toString();
    return m -> {
      if (m == null) {
        return false;
      }

      var model = m.getModel();
      var nsMatch = module.isEmpty() || module.equals(model.getNamespace());
      if (!nsMatch) {
        return false;
      }
      if (model.getName().contains(keyword)) {
        return true;
      }
      if (model.getTitle() != null && model.getTitle().contains(keyword)) {
        return true;
      }
      return model.getDescription() != null && model.getDescription().contains(keyword);
    };
  }

  @Override
  public Try<BusinessModelMeta> buildModelFromFileContent(String content) {
    return BusinessModelMetaParser.parse(content);
  }

  @Override
  public Try<BusinessModelMeta> buildModelFromRequestBody(String requestBody) {
    return Json.tryToMap(requestBody)
        .map(this::stripValidationReadFaceFields)
        .flatMap(Json::tryToPrettyJsonString)
        .flatMap(this::buildModelFromFileContent);
  }

  @Override
  protected Try<String> getPermanentJson(BusinessModelMeta model) {
    return Json.tryToJsonString(model)
        .map(Json::toMap)
        .map(this::stripValidationReadFaceFields)
        .flatMap(Json::tryToPrettyJsonString);
  }

  @Override
  public Try<?> create(BusinessModelMeta model, String commitMsg, boolean includeValidationSnapshot) {
    return create(model, commitMsg)
        .map(created -> includeValidationSnapshot ? buildReadEnvelope(created) : created);
  }

  @Override
  public Try<?> update(String fullName, BusinessModelMeta model, boolean includeValidationSnapshot) {
    return update(fullName, model)
        .map(updated -> includeValidationSnapshot ? buildReadEnvelope(updated) : updated);
  }

  @Override
  public Try<?> getOne(String fullName, boolean includeValidationSnapshot) {
    if (!includeValidationSnapshot) {
      return Try.success(super.getOne(fullName).orElse(null));
    }
    return Try.success(super.getOne(fullName)
        .map(this::buildReadEnvelope)
        .orElse(null));
  }

  @Override
  public Try<?> getInitFormData(String fullName, boolean includeValidationSnapshot) {
    if (!includeValidationSnapshot) {
      return super.getInitFormData(fullName);
    }
    return super.getInitFormData(fullName)
        .map(model -> model instanceof BusinessModelMeta meta ? buildReadEnvelope(meta) : model);
  }

  @Override
  public List<?> search(Map<String, Object> searchParam, boolean includeValidationSnapshot) {
    if (!includeValidationSnapshot) {
      return super.search(searchParam);
    }
    var searchResults = super.search(searchParam);
    var validationProvider = new SearchValidationBusinessModelProvider(searchResults, isFullBusinessModelSearch(searchParam));
    return searchResults.stream()
        .flatMap(rawResult -> buildSearchEnvelopeWrapper(rawResult, validationProvider))
        .collect(Collectors.toList());
  }

  private Stream<ModelFileMetaWrapper<BusinessModelReadEnvelope<BusinessModelVO>>> buildSearchEnvelopeWrapper(Object rawResult,
                                                                                                             BusinessModelProvider provider) {
    if (!(rawResult instanceof ModelFileMetaWrapper<?> wrapper)) {
      return Stream.empty();
    }
    if (!(wrapper.getModel() instanceof BusinessModelMeta meta)) {
      return Stream.empty();
    }
    var envelope = new BusinessModelReadEnvelope<>(new BusinessModelVO(meta), buildValidationSnapshot(meta, provider), getAppName());
    return Stream.of(new ModelFileMetaWrapper<>(envelope, wrapper.getFile()).setEnableCreationTime(true));
  }

  private BusinessModelReadEnvelope<BusinessModelMeta> buildReadEnvelope(BusinessModelMeta model) {
    return new BusinessModelReadEnvelope<>(model, buildValidationSnapshot(model), getAppName());
  }

  private BusinessModelValidationSnapshot buildValidationSnapshot(BusinessModelMeta model) {
    return buildValidationSnapshot(model, this);
  }

  private BusinessModelValidationSnapshot buildValidationSnapshot(BusinessModelMeta model, BusinessModelProvider provider) {
    return new BusinessModelValidationSnapshotSupport(businessModelBuilders, provider).snapshot(model);
  }

  public BusinessModelBehaviorPreview previewBehavior(String requestBody, String requestId) {
    var validation = new BusinessModelValidationSnapshotSupport(businessModelBuilders, this);
    var parsed = buildModelFromRequestBody(requestBody);
    if (parsed.isFailure()) {
      return new BusinessModelBehaviorPreview("draft", requestId, null,
          "development-environment-only", null, validation.failure(parsed.getCause(), "model"));
    }
    var meta = parsed.get();
    String fingerprint = BusinessBehaviorContracts.schemaFingerprint(
        Json.toMap(Json.toJsonString(meta)));
    try {
      var provider = new SearchValidationBusinessModelProvider(meta);
      var model = provider.getBusinessModel(meta.getFullName()).orElseThrow(() -> new BusinessException("不支持的业务模型类型"));
      return new BusinessModelBehaviorPreview("draft", requestId, fingerprint,
          "development-environment-only", BusinessBehaviorCatalog.from(model.getBehavior()),
          validation.snapshotModel(model));
    } catch (RuntimeException error) {
      return new BusinessModelBehaviorPreview("draft", requestId, fingerprint,
          "development-environment-only", null, validation.failure(error, "model"));
    }
  }

  public Try<BusinessModelFormatUpgradePreview> previewFormatUpgrade(String fullName, String draft, String requestId) {
    return Try.of(() -> prepareFormatUpgrade(fullName, draft, requestId).preview());
  }

  public Try<?> applyFormatUpgrade(String fullName, String draft, String expectedPlanFingerprint) {
    return Try.of(() -> {
      var prepared = prepareFormatUpgrade(fullName, draft, null);
      requireUpgradeFingerprint(prepared, expectedPlanFingerprint);
      return prepared;
    }).flatMap(prepared -> doUpdateAndCommit(fullName, prepared.preview().upgradedModel(), List.of(batch -> {
      batch.beforeWrite(() -> requireUpgradeFingerprint(prepareFormatUpgrade(fullName, draft, null), expectedPlanFingerprint));
      prepared.resources().forEach(resource -> resource.appendTo(batch));
    }), "升级业务模型格式至 2.0: " + fullName)).map(this::buildReadEnvelope);
  }

  private void requireUpgradeFingerprint(PreparedFormatUpgrade prepared, String expected) {
    if (expected == null || !expected.equals(prepared.preview().planFingerprint())) {
      throw new BusinessException("升级预览已过期，请重新预览并确认完整资源差异");
    }
  }

  private PreparedFormatUpgrade prepareFormatUpgrade(String fullName, String draft, String requestId) {
    var file = getExistingOrPrimaryModelVCSFile(fullName);
    if (!file.exists()) { throw new BusinessException("业务模型不存在: " + fullName); }
    byte[] originalBytes = file.getBytesArray();
    String originalContent = new String(originalBytes, java.nio.charset.StandardCharsets.UTF_8);
    var stored = buildModelFromFileContent(originalContent).get();
    var original = buildModelFromRequestBody(draft).get();
    if (!(original instanceof DefaultBusinessModelMeta defaults) || !fullName.equals(original.getFullName())
        || !fullName.equals(stored.getFullName()) || !stored.getModelType().equals(original.getModelType())
        || !java.util.Objects.equals(stored.getFormatVersion(), original.getFormatVersion())) {
      throw new BusinessException("升级草稿须保持原模型身份、类型和格式版本");
    }
    var before = previewBehavior(draft, requestId);
    requireValidBehaviorPreview(before);
    var plan = SPI.getCachedSortedServiceStream(BusinessModelFormatUpgrade.class).findFirst()
        .orElseThrow(() -> new BusinessException("业务模型编辑能力未注册")).prepare(defaults, new SearchValidationBusinessModelProvider(original));
    String upgradedJson = getPermanentJson(plan.model()).get();
    var after = previewBehavior(upgradedJson, requestId);
    requireValidBehaviorPreview(after);

    var prepared = prepareModelResources(file, originalBytes, upgradedJson, plan.model(), plan.scriptFlows(), before, after);
    return new PreparedFormatUpgrade(new BusinessModelFormatUpgradePreview(requestId, prepared.fingerprint(),
        plan.model(), prepared.changes(), before, after), prepared.resources());
  }

  private PreparedModelResources prepareModelResources(VCSFile file, byte[] originalBytes, String upgradedJson,
      DefaultBusinessModelMeta updated, List<FlowModelMeta> flows, BusinessModelBehaviorPreview before, BusinessModelBehaviorPreview after) {
    String originalContent = new String(originalBytes, java.nio.charset.StandardCharsets.UTF_8);
    var resources = new ArrayList<PreparedModelCreate>();
    var changes = new ArrayList<BusinessModelResourceChange>();
    changes.add(new BusinessModelResourceChange(file.getPath(), "update", "utf-8", originalContent, upgradedJson));
    var fingerprints = new LinkedHashMap<String, Object>();
    fingerprints.put(file.getPath(), Base64.getEncoder().encodeToString(originalBytes));
    var generatedNames = new java.util.HashSet<String>();
    for (var flow : flows) {
      requireLogicModelService();
      var resource = logicModelDevService.prepareCreate(flow).get();
      resources.add(resource);
      generatedNames.add(flow.getName());
      resource.getContents().forEach((path, bytes) -> {
        String encoded = Base64.getEncoder().encodeToString(bytes);
        if (fingerprints.putIfAbsent(path, encoded) != null) { throw new BusinessException("业务模型资源路径冲突: " + path); }
        boolean primary = path.equals(resource.getPrimaryPath());
        changes.add(new BusinessModelResourceChange(path, "create", primary ? "utf-8" : "base64", null,
            primary ? resource.getPrimaryContent() : encoded));
      });
    }
    var dependencies = new LinkedHashMap<String, Object>();
    for (var action : updated.getActions()) {
      var binding = action.getBinding();
      if (binding == null || !"logicflow".equals(binding.getKind()) || generatedNames.contains(binding.getFlowName())) { continue; }
      requireLogicModelService();
      var flow = logicModelDevService.getOne(binding.getFlowName())
          .orElseThrow(() -> new BusinessException("actions." + action.getName() + ".binding: 关联逻辑流不存在: " + binding.getFlowName()));
      var validated = logicModelDevService.validateForPublication(flow).get();
      if (!binding.getFlowName().equals(validated.getName()) || (action.getIsEnabled() && !validated.isEnabled())) {
        throw new BusinessException("actions." + action.getName() + ".binding: 关联逻辑流身份不匹配或已禁用");
      }
      dependencies.put(binding.getFlowName(), Json.toMap(Json.toJsonString(validated)));
    }
    String fingerprint = BusinessBehaviorContracts.schemaFingerprint(Map.of("resources", fingerprints,
        "draft", before.draftFingerprint(), "upgraded", after.draftFingerprint(), "dependencies", dependencies,
        "beforeRevision", before.catalog().revision(), "afterRevision", after.catalog().revision()));
    return new PreparedModelResources(fingerprint, List.copyOf(changes), List.copyOf(resources));
  }

  private void requireLogicModelService() {
    if (logicModelDevService == null) { throw new BusinessException("逻辑流模型开发服务未启用，无法核验完整关联资源"); }
  }

  private void requireValidBehaviorPreview(BusinessModelBehaviorPreview preview) {
    if (preview.catalog() == null || preview.validationSnapshot().getValidationState() != BusinessModelValidationState.VALID) {
      throw new BusinessException("完整业务模型须校验通过；代码贡献仅核验当前开发环境: "
          + String.join("; ", preview.validationSnapshot().getValidationErrors()));
    }
  }

  private record PreparedFormatUpgrade(BusinessModelFormatUpgradePreview preview, List<PreparedModelCreate> resources) { }

  private record PreparedModelResources(String fingerprint, List<BusinessModelResourceChange> changes,
                                       List<PreparedModelCreate> resources) { }

  public Try<BusinessModelScriptActionPreview> previewScriptAction(String fullName, BusinessModelScriptActionRequest request, String requestId) {
    return Try.of(() -> prepareScriptAction(fullName, request, requestId).preview());
  }

  public Try<?> createScriptAction(String fullName, BusinessModelScriptActionRequest request, String expectedPlanFingerprint) {
    return Try.of(() -> {
      var prepared = prepareScriptAction(fullName, request, null);
      requireScriptFingerprint(prepared, expectedPlanFingerprint);
      return prepared;
    }).flatMap(prepared -> doUpdateAndCommit(fullName, prepared.preview().businessModel(), List.of(batch -> {
      batch.beforeWrite(() -> requireScriptFingerprint(prepareScriptAction(fullName, request, null), expectedPlanFingerprint));
      prepared.resources().forEach(resource -> resource.appendTo(batch));
    }), "创建业务脚本动作: " + fullName + "." + request.actionName())).map(this::buildReadEnvelope);
  }

  private void requireScriptFingerprint(PreparedScriptAction prepared, String expected) {
    if (expected == null || !expected.equals(prepared.preview().planFingerprint())) {
      throw new BusinessException("脚本动作预览已过期，请重新预览完整资源差异");
    }
  }

  private PreparedScriptAction prepareScriptAction(String fullName, BusinessModelScriptActionRequest request, String requestId) {
    if (request == null || request.businessModel() == null) { throw new BusinessException("缺少完整模型草稿"); }
    var file = getExistingOrPrimaryModelVCSFile(fullName);
    if (!file.exists()) { throw new BusinessException("请先保存当前业务模型"); }
    byte[] originalBytes = file.getBytesArray();
    var stored = buildModelFromFileContent(new String(originalBytes, java.nio.charset.StandardCharsets.UTF_8)).get();
    String draft = Json.toJsonString(request.businessModel());
    var original = buildModelFromRequestBody(draft).get();
    if (!(original instanceof DefaultBusinessModelMeta defaults) || !fullName.equals(original.getFullName())
        || !fullName.equals(stored.getFullName()) || !stored.getModelType().equals(original.getModelType())
        || !BusinessModelMetaParser.isCurrent(stored) || !BusinessModelMetaParser.isCurrent(original)) {
      throw new BusinessException("脚本动作草稿须保持已保存的 2.0 默认模型身份和类型");
    }
    var plan = SPI.getCachedSortedServiceStream(BusinessModelScriptAction.class).findFirst()
        .orElseThrow(() -> new BusinessException("业务模型编辑能力未注册")).prepare(defaults, new SearchValidationBusinessModelProvider(original),
        request.functionName(), request.actionName(), request.scriptLanguage(), request.script(), request.readOnly());
    String updatedJson = getPermanentJson(plan.model()).get();
    var before = previewBehavior(draft, requestId);
    var after = previewBehavior(updatedJson, requestId);
    requireValidBehaviorPreview(after);
    var prepared = prepareModelResources(file, originalBytes, updatedJson, plan.model(), List.of(plan.flow()), before, after);
    return new PreparedScriptAction(new BusinessModelScriptActionPreview(requestId, prepared.fingerprint(), plan.model(),
        request.actionName(), plan.flow().getName(), prepared.changes(), after), prepared.resources());
  }

  private record PreparedScriptAction(BusinessModelScriptActionPreview preview, List<PreparedModelCreate> resources) { }

  private boolean isFullBusinessModelSearch(Map<String, Object> searchParam) {
    var module = Optional.ofNullable(searchParam.get("module")).map(Object::toString).orElse("");
    var keyword = Optional.ofNullable(searchParam.get("keyword")).map(Object::toString).orElse("");
    return module.isEmpty() && keyword.isEmpty();
  }

  private Map<String, Object> stripValidationReadFaceFields(Map<String, Object> payload) {
    VALIDATION_READ_FACE_FIELDS.forEach(payload::remove);
    return payload;
  }

  // TODO: 提到 DevelopService 里
  public Try<Void> move(String oldFullName, String newFullName, String msg) {
    var vcsManager = getVcsManager();
    var modelFile = getExistingOrPrimaryModelVCSFile(oldFullName);
    var oldModelFilePath = modelFile.getPath();
    var newModelFilePath = buildModelFilePath(newFullName);

    if (!modelFile.exists()) {
      return Try.failure(new BusinessException(-1, "旧文件不存在"));
    }

    if (buildModelFilePathsForRead(newFullName).stream()
        .map(vcsManager::getFile)
        .anyMatch(VCSFile::exists)) {
      return Try.failure(new BusinessException(-1, "新文件已存在"));
    }

    var moveResult = vcsManager
        .moveFile(oldModelFilePath, newModelFilePath)
        .autoLock()
        .unlockWhenCommit()
        .updateContent(content -> {
          var model = this.buildModelFromFileContent(content);
          if (model.isFailure()) {
            return content;
          }
          var changedModel = model.get();
          var ns = newFullName.substring(0, newFullName.lastIndexOf(".") + 1);
          var name = newFullName.substring(newFullName.lastIndexOf(".") + 1);
          changedModel.setName(name);
          changedModel.setNamespace(ns.isEmpty() ? ns : ns.substring(0, ns.length() - 1));
          return Json.toPrettyJsonString(changedModel);
        })
        .commit(msg);

    return moveResult.map(ignore -> null);
  }

  public List<ReferenceRelationVO> getReferenceRelations(String fullName) {
    return walkModelFilesForRead()
        .map(VCSFile::getContent)
        .map(this::buildModelFromFileContent)
        .filter(Try::isSuccess)
        .map(Try::get)
        .filter(model -> model.relationDefinitions().stream().anyMatch(relation -> fullName.equals(relation.getModel())))
        .flatMap(model -> model.relationDefinitions()
            .stream()
            .map(relation -> Tuple.of(model, relation)))
        .filter(tuple -> fullName.equals(tuple._2().getModel()))
        .map(tuple -> {
          var model = tuple._1();
          var relation = tuple._2();
          return new ReferenceRelationVO(model, relation);
        })
        .collect(Collectors.toList());
  }

  private Stream<VCSFile> walkModelFilesForRead() {
    return getModelFileExtensionsForRead().stream()
        .flatMap(extension -> getVcsManager().walkDirectory(MODEL_FILE_DIRECTORY, extension))
        .collect(() -> new TreeMap<String, VCSFile>(String::compareToIgnoreCase),
            (map, file) -> map.putIfAbsent(file.getPath(), file), TreeMap::putAll)
        .values()
        .stream();
  }

  public Either<OuroborosException, ModelFileMetaWrapper<BusinessModelVO>> getMeta(String fullName) {
    var file = getExistingOrPrimaryModelVCSFile(fullName);
    var content = file.getContent();
    var model = buildModelFromFileContent(content)
        .map(BusinessModelVO::new)
        .getOrElse(() -> null);
    if (model == null) {
      return Either.left(new InternalException("转换内容为模型元数据的时候发生错误"));
    }
    var result = new ModelFileMetaWrapper<>(model, file).setEnableCreationTime(true);
    return Either.right(result);
  }

  @Override
  public Optional<BusinessModel> getBusinessModel(String fullName) {
    return this.getOne(fullName)
        .flatMap(meta -> buildBusinessModel(meta, this));
  }

  @Override
  public Map<String, BusinessModel> getBusinessModelMap() {
    return this.getAll().stream()
        .map(meta -> buildBusinessModel(meta, this))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toMap(BusinessModel::getFullName, Function.identity(), (v1, v2) -> v2));
  }

  private Optional<BusinessModel> buildBusinessModel(BusinessModelMeta meta, BusinessModelProvider provider) {
    return businessModelBuilders.stream()
        .filter(builder -> builder.support(meta))
        .findFirst()
        .map(builder -> builder.build(meta, provider));
  }

  private class SearchValidationBusinessModelProvider implements BusinessModelProvider {
    private final Map<String, BusinessModelMeta> metas = new LinkedHashMap<>();
    private final Map<String, Optional<BusinessModel>> models = new HashMap<>();
    private final boolean allModelsKnown;
    private Map<String, BusinessModel> businessModelMap;

    private SearchValidationBusinessModelProvider(BusinessModelMeta draft) {
      this.allModelsKnown = false;
      metas.put(draft.getFullName(), draft);
    }

    private SearchValidationBusinessModelProvider(Collection<?> searchResults, boolean allModelsKnown) {
      this.allModelsKnown = allModelsKnown;
      searchResults.stream()
          .filter(ModelFileMetaWrapper.class::isInstance)
          .map(ModelFileMetaWrapper.class::cast)
          .map(ModelFileMetaWrapper::getModel)
          .filter(BusinessModelMeta.class::isInstance)
          .map(BusinessModelMeta.class::cast)
          .forEach(meta -> metas.put(meta.getFullName(), meta));
    }

    @Override
    public Optional<BusinessModel> getBusinessModel(String fullName) {
      if (fullName == null) {
        return Optional.empty();
      }
      return models.computeIfAbsent(fullName, this::loadBusinessModel);
    }

    @Override
    public Map<String, BusinessModel> getBusinessModelMap() {
      if (businessModelMap == null) {
        var allMetas = new LinkedHashMap<>(metas);
        if (!allModelsKnown) {
          BusinessModelDevService.this.getAll()
              .forEach(meta -> allMetas.putIfAbsent(meta.getFullName(), meta));
        }
        businessModelMap = allMetas.values().stream()
            .map(meta -> getBusinessModel(meta.getFullName()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toMap(BusinessModel::getFullName, Function.identity(), (v1, v2) -> v2));
      }
      return businessModelMap;
    }

    private Optional<BusinessModel> loadBusinessModel(String fullName) {
      var searchMeta = metas.get(fullName);
      if (searchMeta != null) {
        return buildBusinessModel(searchMeta, this);
      }
      return BusinessModelDevService.this.getOne(fullName)
          .flatMap(meta -> buildBusinessModel(meta, this));
    }
  }

  /** Uses the same read-only projection path as model preview, including unsaved model changes. */
  public ModelBundle previewModelBundle(String draft) {
    return previewBusinessModel(draft).buildModelBundle();
  }

  public BusinessModel previewBusinessModel(String draft) {
    var meta = buildModelFromRequestBody(draft).get();
    var provider = new SearchValidationBusinessModelProvider(meta);
    return provider.getBusinessModel(meta.getFullName())
        .orElseThrow(() -> new BusinessException("不支持的业务模型类型"));
  }

  public Optional<ModelBundle> getModelBundle(String fullName) {
    return getBusinessModel(fullName)
        .map(BusinessModel::buildModelBundle);
  }

  @Override
  public List<AppModule> getAppModules(String appName) {
    return getAll().stream().map(meta -> businessModelBuilders.stream()
            .filter(builder -> builder.support(meta))
            .findFirst()
            .map(builder -> builder.build(meta, this)))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(BusinessModel::isEnabled)
        .map(BusinessModelModule::new)
        .collect(Collectors.toList());
  }

  @Override
  protected Try<Void> validateModel(BusinessModelMeta model) {
    if (model.getName() == null || model.getName().trim().isEmpty()) {
      return Try.failure(new BusinessException(-1, "模型名不得为空"));
    }
    return Try.success(null);
  }

  @SuppressWarnings("unused")
  public static class BusinessModelVO implements com.ouroboros.modelmanage.ModelSummary {
    String name;
    String fullName;
    String namespace;
    String title;
    String icon;
    String description;
    Integer fieldsCount;
    Boolean isEnabled;
    Integer order;

    public BusinessModelVO(BusinessModelMeta meta) {
      this.name = meta.getName();
      this.fullName = meta.getFullName();
      this.namespace = meta.getNamespace();
      this.title = meta.getTitle();
      this.icon = meta.getIcon();
      this.description = meta.getDescription();
      this.fieldsCount = meta.fieldDefinitions().size();
      this.isEnabled = meta.getIsEnabled();
      this.order = meta.getExtraProp("order").map(Converter::toInteger).orElse(0);
    }

    public String getName() {
      return name;
    }

    public String getFullName() {
      return fullName;
    }

    public String getNamespace() {
      return namespace;
    }

    public String getTitle() {
      return title;
    }

    public String getIcon() {
      return icon;
    }

    public String getDescription() {
      return description;
    }

    public Integer getFieldsCount() {
      return fieldsCount;
    }

    public Boolean getIsEnabled() {
      return isEnabled;
    }

    public Integer getOrder() {
      return order;
    }
  }

  public static class BusinessModelGraphVO {
    private final List<BusinessModelGraphNodeVO> nodes;
    private final List<BusinessModelGraphEdgeVO> edges;

    public BusinessModelGraphVO(List<BusinessModelGraphNodeVO> nodes, List<BusinessModelGraphEdgeVO> edges) {
      this.nodes = nodes;
      this.edges = edges;
    }

    public List<BusinessModelGraphNodeVO> getNodes() {
      return nodes;
    }

    public List<BusinessModelGraphEdgeVO> getEdges() {
      return edges;
    }
  }

  public static class BusinessModelGraphNodeVO extends BusinessModelVO {
    private final String lockUser;
    private final String lockUserName;

    public BusinessModelGraphNodeVO(BusinessModelMeta meta, VCSFile file) {
      super(meta);
      this.lockUser = file.getLockOwner();
      this.lockUserName = file.getLockOwnerName();
    }

    public String getLockUser() { return lockUser; }

    public String getLockUserName() { return lockUserName; }
  }

  public static class BusinessModelGraphEdgeVO {
    private final String source;
    private final String target;
    private final String name;
    private final String title;
    private final String type;

    public BusinessModelGraphEdgeVO(String source, String target, String name, String title, String type) {
      this.source = source;
      this.target = target;
      this.name = name;
      this.title = title;
      this.type = type;
    }

    public String getSource() {
      return source;
    }

    public String getTarget() {
      return target;
    }

    public String getName() {
      return name;
    }

    public String getTitle() {
      return title;
    }

    public String getType() {
      return type;
    }
  }

  public static class ReferenceRelationVO {
    String relatedModel;
    String relatedModelTitle;
    String name;
    String title;
    String type;

    public ReferenceRelationVO(BusinessModelMeta model, BusinessModelRelationMeta relation) {
      this.relatedModel = model.getFullName();
      this.relatedModelTitle = model.getTitle();
      this.name = relation.getName();
      this.title = relation.getTitle();
      this.type = relation.getType();
    }

    public String getRelatedModel() {
      return relatedModel;
    }

    public String getRelatedModelTitle() {
      return relatedModelTitle;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }
  }
}
