package com.ouroboros.workflow.approval.runtime.lifecycle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.AppContext;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.FileSystemMetadataSource;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionDocument;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.manage.service.ApprovalWorkflowDesignTimeDefinitionProvider;

import io.vavr.control.Try;

public class ApprovalWorkflowMetadataDefinitionProvider implements ApprovalWorkflowDesignTimeDefinitionProvider {
  static final String DEFINITION_RESOURCE_DIRECTORY = "workflow";
  static final String DEFINITION_FILE_SUFFIX = ".workflow.json";

  private final AppContext appContext;
  private final MetadataSource jarMetadataSource;

  public ApprovalWorkflowMetadataDefinitionProvider(AppContext appContext) {
    this(appContext, new ClasspathMetadataSource());
  }

  ApprovalWorkflowMetadataDefinitionProvider(AppContext appContext, MetadataSource jarMetadataSource) {
    this.appContext = Objects.requireNonNull(appContext);
    this.jarMetadataSource = Objects.requireNonNull(jarMetadataSource);
  }

  @Override
  public Optional<ApprovalWorkflowDefinitionMetadata> findDefinition(String id) throws OuroborosException {
    return discoverWorkflowDefinitions().stream()
        .filter(definition -> Objects.equals(id, definition.getName()))
        .findFirst();
  }

  List<ApprovalWorkflowDefinitionMetadata> discoverWorkflowDefinitions() {
    Map<String, ApprovalWorkflowDefinitionMetadata> definitions = new TreeMap<>(String::compareToIgnoreCase);
    discoverJarWorkflowDefinitions().forEach(definition -> definitions.put(definition.getName(), definition));
    discoverAppHomeWorkflowDefinitions().forEach(definition -> definitions.put(definition.getName(), definition));
    return new ArrayList<>(definitions.values());
  }

  private List<ApprovalWorkflowDefinitionMetadata> discoverJarWorkflowDefinitions() {
    try {
      return new ArrayList<>(new TypedMetadataCatalog(jarMetadataSource)
          .load(workflowDefinitionSpec(TypedMetadataSpec.MergeStrategy.LAST_WINS))
          .values()
          .values());
    } catch (IllegalStateException e) {
      throw new InternalException("加载JAR审批流定义元数据失败", e);
    }
  }

  private List<ApprovalWorkflowDefinitionMetadata> discoverAppHomeWorkflowDefinitions() {
    Path root = definitionRoot();
    if (!Files.exists(root)) {
      return List.of();
    }
    if (!Files.isDirectory(root)) {
      throw new InternalException("审批流定义元数据路径不是目录: " + root);
    }
    try {
      return new ArrayList<>(new TypedMetadataCatalog(new FileSystemMetadataSource(root))
          .load(workflowDefinitionSpec(TypedMetadataSpec.MergeStrategy.FAIL_ON_DUPLICATE))
          .values()
          .values());
    } catch (IllegalStateException e) {
      throw new InternalException("扫描审批流定义元数据目录失败: " + root, e);
    }
  }

  private Path definitionRoot() {
    String homePath = appContext.getHomePath();
    if (StringUtils.isBlank(homePath)) {
      throw new InternalException("AppContext.homePath 不能为空，无法发现审批流定义元数据");
    }
    return Paths.get(homePath, DEFINITION_RESOURCE_DIRECTORY);
  }

  private ApprovalWorkflowDefinitionMetadata toWorkflowDefinition(String resourceName, String content) {
    String definitionId = definitionIdFromResourceName(resourceName);
    Try<ApprovalWorkflowDefinitionDocument> documentResult = ApprovalWorkflowDefinitionDocument.decode(content);
    if (documentResult.isFailure()) {
      throw new InternalException("审批流定义资产不合法: " + resourceName, documentResult.getCause());
    }
    ApprovalWorkflowDefinitionMetadata metadata = documentResult.get().metadata();
    if (!definitionId.equals(metadata.getName())) {
      throw new InternalException("审批流定义资产 name 与文件名不一致: " + resourceName);
    }
    return metadata;
  }

  private TypedMetadataSpec<ApprovalWorkflowDefinitionMetadata> workflowDefinitionSpec(
      TypedMetadataSpec.MergeStrategy mergeStrategy) {
    return TypedMetadataSpec.<ApprovalWorkflowDefinitionMetadata>builder(ApprovalWorkflowDefinitionMetadata.class)
        .typedSuffix(DEFINITION_FILE_SUFFIX)
        .resourceParser(resource -> Try.of(() -> toWorkflowDefinition(resource.resourceName(), resource.content())))
        .keyExtractor(ApprovalWorkflowDefinitionMetadata::getName)
        .mergeStrategy(mergeStrategy)
        .parseFailurePolicy(TypedMetadataSpec.ParseFailurePolicy.ERROR)
        .blankKeyPolicy(TypedMetadataSpec.BlankKeyPolicy.ERROR)
        .build();
  }

  private String definitionIdFromResourceName(String resourceName) {
    String fileName = resourceName;
    int slashIndex = fileName.lastIndexOf('/');
    if (slashIndex >= 0) {
      fileName = fileName.substring(slashIndex + 1);
    }
    return fileName.endsWith(DEFINITION_FILE_SUFFIX)
        ? fileName.substring(0, fileName.length() - DEFINITION_FILE_SUFFIX.length())
        : fileName;
  }
}
