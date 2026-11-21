package com.ouroboros.logicflow.dev.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.EnhancedDataModelProxy;
import com.ouroboros.data.model.MetadataOnlyDataModel;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshotProvider;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataProvider;
import com.ouroboros.dev.metadata.MetadataProviderMixer;
import com.ouroboros.dev.metadata.MetadataSnapshotStatus;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.dev.vo.LogicFlowModelReadEnvelope;
import com.ouroboros.logicflow.dev.vo.LogicFlowValidationSnapshot;
import com.ouroboros.logicflow.exception.DesignException;
import com.ouroboros.logicflow.model.DataTypeEnum;
import com.ouroboros.logicflow.model.FlowConnection;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelReferences;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.dev.service.support.LogicFlowValidationSnapshotSupport;
import com.ouroboros.logicflow.nodes.DataModelReferences;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeReferenceIndeterminateReason;
import com.ouroboros.logicflow.nodes.NodeReferenceKind;
import com.ouroboros.logicflow.nodes.NodeReferenceResolution;
import com.ouroboros.logicflow.nodes.NodeReferenceSet;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.PreparedModelCreate;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

/**
 * @author liansz
 */
@Service
@DevelopModule("logic-flow")
public class LogicModelDevelopServiceImpl extends BaseDevelopService<FlowModelMeta> {

  private static final String MODEL_FILE_DIRECTORY = "logic-flow";
  private static final String MODEL_FILE_EXTENSION = ".logicflow.json";
  private static final String LEGACY_MODEL_FILE_EXTENSION = ".json";
  private static final String DATA_MODEL_METADATA_MODULE = "data-model";
  private final ObjectProvider<MetadataProvider> metadataProviders;

  public LogicModelDevelopServiceImpl() {
    this(new EmptyMetadataProviderObjectProvider());
  }

  @Autowired
  public LogicModelDevelopServiceImpl(ObjectProvider<MetadataProvider> metadataProviders) {
    this.metadataProviders = metadataProviders;
  }

  @Override
  protected String getModelDisplayName() {
    return "逻辑流";
  }

  @Override
  protected String getModelFileDirectory() {
    return MODEL_FILE_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_EXTENSION;
  }

  @Override
  protected List<String> getLegacyModelFileExtensions() {
    return Collections.singletonList(LEGACY_MODEL_FILE_EXTENSION);
  }

  @Override
  protected boolean isAutoCommitWhenUpdate() {
    return false;
  }

  @Override
  protected String getFullName(FlowModelMeta model) {
    return model.getName();
  }

  @Override
  protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
    return file -> Json.tryToBean(FlowModelMeta.class, file.getContent())
        .map(meta -> new ModelFileMetaWrapper<>(meta, file));
  }

  @Override
  protected Predicate<ModelFileMetaWrapper<FlowModelMeta>> buildSearchFilter(Map<String, Object> searchParam) {
    final var keyword = searchParam.getOrDefault("keyword", "").toString();
    return m -> {
      var model = m.getModel();
      if (model.getName().contains(keyword)) {
        return true;
      }
      return model.getDescription() != null && model.getDescription().contains(keyword);
    };
  }

  @Override
  protected Try<Void> validateModel(FlowModelMeta model) {
    if (StringUtils.isBlank(model.getName())) {
      return Try.failure(new BusinessException("模型名称不能为空"));
    }
    return Try.success(null);
  }

  @Override
  protected Try<String> getPermanentJson(FlowModelMeta model) {
    return Json.tryToJsonString(toFormalModel(model))
        .map(Json::toMap)
        .map(serialized -> {
          serialized.remove("validationState");
          serialized.remove("validationSummary");
          serialized.remove("validationErrors");
          serialized.remove("validationDiagnostics");
          serialized.remove("lastValidatedTime");
          return serialized;
        })
        .flatMap(Json::tryToPrettyJsonString);
  }

  @Override
  public Try<PreparedModelCreate> prepareCreate(FlowModelMeta model) {
    return validateForPublication(model).flatMap(super::prepareCreate);
  }

  @Override
  public Try<FlowModelMeta> validateForPublication(FlowModelMeta model) {
    return validateModel(model).flatMap(ignore -> Try.of(() -> normalizeWithDesignTimeMetadata(model))).flatMap(normalized -> {
      if (normalized.getValidationState() != com.ouroboros.logicflow.model.FlowModelValidationState.VALID) {
        return Try.failure(new BusinessException("关联逻辑流校验失败: " + normalized.getValidationSummary()));
      }
      return Try.success(toFormalModel(normalized));
    });
  }

  @Override
  public Try<FlowModelMeta> create(FlowModelMeta model, String commitMsg) {
    var normalized = normalizeWithDesignTimeMetadata(model);
    return super.create(normalized, commitMsg);
  }

  @Override
  public Try<?> create(FlowModelMeta model, String commitMsg, boolean includeValidationSnapshot) {
    return create(model, commitMsg)
        .map(created -> includeValidationSnapshot ? buildReadEnvelope(created) : created);
  }

  @Override
  public Try<FlowModelMeta> update(String fullName, FlowModelMeta model) {
    var normalized = normalizeWithDesignTimeMetadata(model);
    return super.update(fullName, normalized);
  }

  @Override
  public Try<?> update(String fullName, FlowModelMeta model, boolean includeValidationSnapshot) {
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
    var file = getModelVCSFile(fullName);
    return buildModelFromFileContent(file.getContent())
        .map(this::buildReadEnvelope);
  }

  @Override
  public List<?> search(Map<String, Object> searchParam, boolean includeValidationSnapshot) {
    if (!includeValidationSnapshot) {
      return super.search(searchParam);
    }
    return super.search(searchParam)
        .stream()
        .filter(ModelFileMetaWrapper.class::isInstance)
        .map(ModelFileMetaWrapper.class::cast)
        .filter(wrapper -> wrapper.getModel() instanceof FlowModelMeta)
        .map(wrapper -> new ModelFileMetaWrapper<>((FlowModelMeta) wrapper.getModel(), wrapper.getFile()))
        .map(wrapper -> new ModelFileMetaWrapper<>(buildReadEnvelope(wrapper.getModel()), wrapper.getFile())
            .setEnableCreationTime(true))
        .collect(Collectors.toList());
  }

  private LogicFlowModelReadEnvelope buildReadEnvelope(FlowModelMeta model) {
    var normalized = normalizeWithDesignTimeMetadata(model);
    return new LogicFlowModelReadEnvelope(
        toFormalModel(normalized),
        LogicFlowValidationSnapshot.from(normalized)
    );
  }

  private FlowModelMeta normalizeWithDesignTimeMetadata(FlowModelMeta model) {
    var appName = getAppName();
    var providers = metadataProviders.orderedStream().collect(Collectors.toList());
    var snapshot = getApplicationMetadataSnapshot(appName, providers);
    var workspaceMetadata = MetadataProviderMixer.getDistinctMixer(providers.stream()
        .filter(provider -> !(provider instanceof ApplicationMetadataSnapshotProvider))
        .collect(Collectors.toList()));

    var dataModelReferences = buildMetadataReferenceSet(
        DataModelReferences.KIND,
        snapshot,
        workspaceMetadata,
        appName,
        DATA_MODEL_METADATA_MODULE,
        this::toDesignTimeDataModel
    );
    var existingFlowModels = buildFlowModelReferenceSet(snapshot, workspaceMetadata, appName, model);
    var currentModelName = model.getName() == null ? "" : model.getName();
    var builtModels = FlowModelFactory.prepare(Collections.singletonMap(currentModelName, model))
        .initialize(NodeReferenceCatalog.of(List.of(dataModelReferences)), existingFlowModels);
    var builtModel = builtModels.get(currentModelName);
    return LogicFlowValidationSnapshotSupport.normalize(model, builtModel);
  }

  private ApplicationMetadataSnapshot getApplicationMetadataSnapshot(
      String appName,
      List<MetadataProvider> providers
  ) {
    if (StringUtils.isBlank(appName)) {
      return ApplicationMetadataSnapshot.unavailable();
    }
    return providers.stream()
        .filter(ApplicationMetadataSnapshotProvider.class::isInstance)
        .map(ApplicationMetadataSnapshotProvider.class::cast)
        .map(provider -> provider.getApplicationMetadataSnapshot(appName))
        .findFirst()
        .orElseGet(ApplicationMetadataSnapshot::unavailable);
  }

  private Optional<DataModel> toDesignTimeDataModel(Metadata metadata) {
    return metadataDetails(metadata)
        .flatMap(details -> Json.tryToBean(DataModelMeta.class, Json.toJsonString(details)).toJavaOptional())
        .flatMap(meta -> Try.<DataModel>of(() -> new EnhancedDataModelProxy(meta, MetadataOnlyDataModel::new))
            .toJavaOptional());
  }

  private Optional<FlowModelMeta> toFlowModelMeta(Metadata metadata) {
    return metadataDetails(metadata)
        .flatMap(details -> Json.tryToBean(FlowModelMeta.class, Json.toJsonString(details)).toJavaOptional());
  }

  private Optional<Map<String, Object>> metadataDetails(Metadata metadata) {
    return Optional.ofNullable(metadata)
        .map(Metadata::getDetails)
        .filter(details -> !details.isEmpty());
  }

  private <T> NodeReferenceSet<T> buildMetadataReferenceSet(
      NodeReferenceKind<T> kind,
      ApplicationMetadataSnapshot snapshot,
      MetadataProviderMixer workspaceMetadata,
      String appName,
      String moduleName,
      Function<Metadata, Optional<T>> converter
  ) {
    var reason = indeterminateReason(snapshot, moduleName);
    return NodeReferenceSet.lazy(
        kind,
        name -> resolveMetadataReference(
            snapshot,
            workspaceMetadata,
            appName,
            moduleName,
            name,
            converter,
            reason
        ),
        String::compareToIgnoreCase,
        reason == null,
        reason == null ? NodeReferenceIndeterminateReason.INCOMPLETE : reason
    );
  }

  private NodeReferenceSet<FlowModel> buildFlowModelReferenceSet(
      ApplicationMetadataSnapshot snapshot,
      MetadataProviderMixer workspaceMetadata,
      String appName,
      FlowModelMeta currentModel
  ) {
    var reason = indeterminateReason(snapshot, MODEL_FILE_DIRECTORY);
    var currentModelName = currentModel.getName() == null ? "" : currentModel.getName();
    return NodeReferenceSet.lazy(
        FlowModelReferences.KIND,
        name -> currentModelName.equalsIgnoreCase(name)
            ? NodeReferenceResolution.found(new MetadataFlowModel(currentModel))
            : resolveMetadataReference(
                snapshot,
                workspaceMetadata,
                appName,
                MODEL_FILE_DIRECTORY,
                name,
                this::toDesignTimeFlowModel,
                reason
            ),
        String::compareToIgnoreCase,
        reason == null,
        reason == null ? NodeReferenceIndeterminateReason.INCOMPLETE : reason
    );
  }

  private Optional<FlowModel> toDesignTimeFlowModel(Metadata metadata) {
    return toFlowModelMeta(metadata).map(MetadataFlowModel::new);
  }

  private <T> NodeReferenceResolution<T> resolveMetadataReference(
      ApplicationMetadataSnapshot snapshot,
      MetadataProviderMixer workspaceMetadata,
      String appName,
      String moduleName,
      String name,
      Function<Metadata, Optional<T>> converter,
      NodeReferenceIndeterminateReason unavailableReason
  ) {
    var workspace = workspaceMetadata.getOne(appName, moduleName, name);
    if (workspace.isPresent()) {
      return convertMetadataReference(workspace.get(), converter);
    }
    if (snapshot.getStatus() == MetadataSnapshotStatus.CURRENT) {
      var snapshotMatches = snapshot.findModuleMetadata(moduleName, name);
      if (snapshotMatches.size() > 1) {
        return NodeReferenceResolution.indeterminate(NodeReferenceIndeterminateReason.INVALID);
      }
      if (snapshotMatches.size() == 1) {
        return convertMetadataReference(snapshotMatches.get(0), converter);
      }
    }
    return unavailableReason == null
        ? NodeReferenceResolution.missing()
        : NodeReferenceResolution.indeterminate(unavailableReason);
  }

  private <T> NodeReferenceResolution<T> convertMetadataReference(
      Metadata metadata,
      Function<Metadata, Optional<T>> converter
  ) {
    return converter.apply(metadata)
        .<NodeReferenceResolution<T>>map(NodeReferenceResolution::found)
        .orElseGet(() -> NodeReferenceResolution.indeterminate(NodeReferenceIndeterminateReason.INVALID));
  }

  private NodeReferenceIndeterminateReason indeterminateReason(
      ApplicationMetadataSnapshot snapshot,
      String moduleName
  ) {
    if (snapshot.getStatus() == MetadataSnapshotStatus.CURRENT
        && snapshot.hasInvalidModuleMetadata(moduleName)) {
      return NodeReferenceIndeterminateReason.INVALID;
    }
    return switch (snapshot.getStatus()) {
      case CURRENT -> null;
      case STALE -> NodeReferenceIndeterminateReason.INCOMPLETE;
      case UNAVAILABLE -> NodeReferenceIndeterminateReason.NOT_READY;
    };
  }

  private FlowModelMeta toFormalModel(FlowModelMeta model) {
    if (model == null) {
      return null;
    }
    return model.map(builder -> {
      builder.validationSummary(null);
      builder.validationErrors(Collections.emptyList());
      builder.validationDiagnostics(Collections.emptyList());
      builder.lastValidatedTime(null);
    });
  }

  private static final class MetadataFlowModel implements FlowModel {
    private final FlowModelMeta metadata;

    private MetadataFlowModel(FlowModelMeta metadata) {
      this.metadata = metadata;
    }

    @Override
    public String getName() {
      return metadata.getName();
    }

    @Override
    public String getDescription() {
      return metadata.getDescription();
    }

    @Override
    public List<FlowNode> getNodes() {
      return Collections.emptyList();
    }

    @Override
    public List<FlowConnection> getConnections() {
      return Collections.emptyList();
    }

    @Override
    public Boolean isEnabled() {
      return metadata.isEnabled();
    }

    @Override
    public Boolean isValid() {
      return true;
    }

    @Override
    public DataTypeEnum getInputDataType() {
      return metadata.getInputDataTypeEnum();
    }

    @Override
    public DataTypeEnum getOutputDataType() {
      return metadata.getOutputDataTypeEnum();
    }

    @Override
    public Try<Object> execute(Object... inputs) {
      return Try.failure(new UnsupportedOperationException("metadata-only flow model does not support execution"));
    }

    @Override
    public FlowNode getEntryNode() {
      return null;
    }

    @Override
    public FlowNode getExitNode() {
      return null;
    }

    @Override
    public FlowNode getNode(String name) {
      return null;
    }

    @Override
    public List<DesignException> getErrors() {
      return Collections.emptyList();
    }
  }

  private static final class EmptyMetadataProviderObjectProvider implements ObjectProvider<MetadataProvider> {
    @Override
    public MetadataProvider getObject(Object... args) {
      return null;
    }

    @Override
    public MetadataProvider getIfAvailable() {
      return null;
    }

    @Override
    public MetadataProvider getIfUnique() {
      return null;
    }

    @Override
    public MetadataProvider getObject() {
      return null;
    }

    @Override
    public java.util.Iterator<MetadataProvider> iterator() {
      return Collections.emptyIterator();
    }
  }
}
