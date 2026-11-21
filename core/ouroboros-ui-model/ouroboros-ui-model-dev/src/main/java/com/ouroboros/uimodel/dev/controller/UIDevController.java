package com.ouroboros.uimodel.dev.controller;

import java.beans.Transient;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataProvider;
import com.ouroboros.dev.metadata.MetadataProviderMixer;
import com.ouroboros.dev.metadata.MetadataSourceType;
import com.ouroboros.uimodel.SimpleUIModel;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uischema.UISchemaProvider;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.uischema.wrapper.SchemaNodeWrapper;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;
import com.ouroboros.util.Trees;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 **/
@RestController
@RequestMapping("/api/dev/{appName}")
public class UIDevController {

  private DevelopService<SchemaNodeWrapper> uiSchemaDevService;
  private MetadataProviderMixer metadataProviderMixer;

  public UIDevController(DevelopService<SchemaNodeWrapper> uiSchemaDevService, List<MetadataProvider> metadataProviders) {
    this.uiSchemaDevService = uiSchemaDevService;
    this.metadataProviderMixer = MetadataProviderMixer.getDistinctMixer(metadataProviders);
  }

  @GetMapping("/ui")
  public ItemsWebApiResult<?> tree(@PathVariable String appName) {
    Map<String, List<Metadata>> groupedMetadata = metadataProviderMixer
        .getAppAllMetadataByModule(appName, "ui-model")
        .stream()
        .sorted(Comparator.comparing(Metadata::getName).thenComparing(Metadata::getPriority))
        .collect(Collectors.groupingBy(Metadata::getName));

    Comparator<TreeUIModel> comparator = Comparator
        // 分组节点在前
        .comparing((TreeUIModel model) -> "group".equals(model.getNodeType()) ? 0 : 1)
        // 然后根据 Path 排序
        .thenComparing(UIModel::getPath);

    // TODO: 下面构建树的逻辑不够简洁，需要重构
    List<TreeUIModel> treeModels = mapTreeDown(
        buildTree(
            groupedMetadata.values().stream().map(metadata ->
                Json.toBean(SimpleUIModel.class, Json.toJsonString(metadata.get(0).getDetails()))
            ).collect(Collectors.toList())
        ),
        TreeUIModel::getChildren,
        TreeUIModel::setChildren,
        (node, parent) -> {
          if (node.getChildren().size() == 1) {
            return node.getChildren().get(0);
          }

          return node;
        }
    ).stream().sorted(comparator).collect(Collectors.toList());

    mapTreeDown(treeModels, TreeUIModel::getChildren, TreeUIModel::setChildren, (node, parent) -> {
      node.getChildren().sort(comparator);
      return node;
    });

    Map<String, Metadata> lowCodeMetadataMap = groupedMetadata.values().stream().flatMap(Collection::stream).filter(m ->
        Objects.equals(m.getSourceType(), MetadataSourceType.LOW_CODE)
    ).collect(
        Collectors.toMap(Metadata::getName, metadata -> metadata, (v1, v2) -> v2)
    );

    Map<String, SchemaNodeWrapper> schemaNodeWrappers = uiSchemaDevService.search(Collections.emptyMap())
        .stream()
        .filter(SchemaNodeWrapper.class::isInstance)
        .map(SchemaNodeWrapper.class::cast)
        .collect(Collectors.toMap(
            SchemaNodeWrapper::getName,
            wrapper -> wrapper,
            (v1, v2) -> v2));

    Trees.walkTree(
        treeModels,
        TreeUIModel::getChildren,
        node -> {
          if (Objects.equals("ui", node.getNodeType())) {
            List<TreeUIModel.Model> referenceModels = groupedMetadata.get(node.getPath()).stream().map(m -> {
              UIModel uiModel = Json.toBean(SimpleUIModel.class, Json.toJsonString(m.getDetails()));
              uiModel.getExtraProps().remove("fullName");

              TreeUIModel.Model model = new TreeUIModel.Model();
              model.setTitle(uiModel.getTitle());
              model.setType(uiModel.getType());
              model.setIcon(uiModel.getIcon());
              if ("amis".equals(uiModel.getType())) {
                String schemaId = (String) Optional.ofNullable(
                    m.getDetails().get("schemaId")
                ).filter(ObjectUtils::isNotEmpty).orElse(m.getName());

                String fullName = Stream.of(schemaId.split("/"))
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining("."));

                model.setExtraProp("schemaId", schemaId);
                model.setExtraProp("schemaFullName", fullName);

                model.setExtraProp("isChanged", schemaNodeWrappers.containsKey(fullName));
              }

              uiModel.getExtraProps().forEach(model::setExtraProp);

              return model;
            }).collect(Collectors.toList());

            node.setCurrentModel(referenceModels.get(0));
            node.setModels(referenceModels);

            node.setChanged(lowCodeMetadataMap.containsKey(node.getPath()));
          }
        }
    );

    return ItemsWebApiResult.ok(treeModels);
  }

  @GetMapping("/all-channel/ui-model")
  public WebApiResult<?> getUiModel(@PathVariable String appName, @RequestParam String path) {
    Optional<Map<String, Object>> result = metadataProviderMixer
        .getAppAllMetadataByModule(appName, "ui-model")
        .stream()
        .filter(m -> m.getName().equals(path))
        .sorted(Comparator.comparing(Metadata::getPriority).reversed())
        .map(Metadata::getDetails)
        .findFirst();
    return WebApiResult.of(result);
  }

  @GetMapping("/all-channel/ui-schema")
  public WebApiResult<?> getUiSchema(@PathVariable String appName, @RequestParam String schemaId) {
    Optional<Map<String, Object>> result = metadataProviderMixer.getAppAllMetadataByModule(appName, "ui-schema")
        .stream()
        .filter(m -> m.getName().equals(schemaId))
        .sorted(Comparator.comparing(Metadata::getPriority).reversed())
        .map(Metadata::getDetails)
        .findFirst();
    if (result.isPresent()) {
      return WebApiResult.of(result);
    }

    String finalSchemaId = schemaId.startsWith("/") ? schemaId : "/" + schemaId;
    Optional<Object> providerResult = SPI.getCachedReversedServiceStream(UISchemaProvider.class)
        .map(provider -> provider.getUISchema(finalSchemaId))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();

    return WebApiResult.of(providerResult);
  }

  private List<TreeUIModel> buildTree(List<? extends UIModel> models) {
    Comparator<UIModel> sortByPath = Comparator
        .comparing(UIModel::getPath)
        .thenComparing(model -> model.getPath().split("/").length);

    // 构建子树
    Function<String, List<TreeUIModel>> buildModels = (String rootPath) ->
        models.stream()
            .filter(m -> m.getPath().startsWith(rootPath))
            .sorted(sortByPath)
            .map(TreeUIModel::new)
            .collect(Collectors.toList());

    return models
        .stream()
        .sorted(sortByPath)
        .map(m -> m.getPath().substring(1).split("/")[0])
        .distinct()
        .map(path -> "/" + path)
        .map(rootPath -> buildModel(rootPath, buildModels.apply(rootPath)))
        .collect(Collectors.toList());
  }

  private TreeUIModel buildModel(String rootPath, List<TreeUIModel> models) {
    TreeUIModel model = new TreeUIModel(rootPath, null, null);

    int layer = groupByPath(rootPath).size();
    int maxLayer = models.stream()
        .map(TreeUIModel::getPath)
        .map(path -> groupByPath(path).size())
        .max(Integer::compareTo)
        .orElse(0);

    models.stream().filter(m ->
        m.getPath().startsWith(rootPath) && groupByPath(m.getPath()).size() == layer
    ).forEach(model::addChildren);

    List<String> paths = new ArrayList<>();

    for (int i = layer; i <= maxLayer; i++) {
      int currentLayer = i;
      models.stream().map(TreeUIModel::getPath).filter(path ->
          path.startsWith(rootPath) && groupByPath(path).size() == currentLayer + 1
      ).distinct().forEach(paths::add);

      if (!paths.isEmpty()) {
        break;
      }
    }

    paths.forEach(path -> model.addChildren(buildModel(path, models)));

    return model;
  }

  private <T, R> R mapTreeDown(T root, Function<T, Collection<T>> childrenGetter, BiConsumer<R, List<R>> childrenSetter, BiFunction<T, R, R> mapper) {
    AtomicReference<BiFunction<T, R, R>> recursiveMapper = new AtomicReference<>();
    recursiveMapper.set((node, parent) -> {
      R mapped = mapper.apply(node, parent);
      List<R> mappedChildren = Optional.ofNullable(childrenGetter.apply((T) mapped))
          .map(children -> children
              .stream()
              .map(child -> recursiveMapper.get().apply(child, mapped))
              .collect(Collectors.toList()))
          .orElse(Collections.emptyList());
      childrenSetter.accept(mapped, mappedChildren);
      return mapped;
    });
    return recursiveMapper.get().apply(root, null);
  }

  private <T, R> List<R> mapTreeDown(Collection<T> roots, Function<T, Collection<T>> childrenGetter, BiConsumer<R, List<R>> childrenSetter, BiFunction<T, R, R> mapper) {
    return roots.stream()
        .map(root -> mapTreeDown(root, childrenGetter, childrenSetter, mapper))
        .collect(Collectors.toList());
  }

  private List<String> groupByPath(String path) {
    return Arrays.stream(path.split("/"))
        .filter(str -> !(str.startsWith("{") && str.endsWith("}")) && StringUtils.isNotEmpty(str))
        .collect(Collectors.toList());
  }

  public static class TreeUIModel extends SimpleUIModel {

    private String nodeType;

    private boolean isChanged;

    private Model currentModel;

    private List<Model> models;

    private List<TreeUIModel> children = new ArrayList<>();

    public TreeUIModel(String path, String title, String type) {
      setNodeType("group");
      setPath(path);
      setTitle(title);
      setType(type);
    }

    public TreeUIModel(UIModel model) {
      setNodeType("ui");
      setPath(model.getPath());
      setTitle(model.getTitle());
      setType(model.getType());
      setIcon(model.getIcon());
    }

    public static class Model {
      private String title;
      private String type;
      private String icon;
      private Map<String, Object> extraProps = new HashMap<>();

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

      public String getIcon() {
        return icon;
      }

      public void setIcon(String icon) {
        this.icon = icon;
      }

      @JsonAnyGetter
      public Map<String, Object> getExtraProps() {
        return extraProps;
      }

      public void setExtraProps(Map<String, Object> extraProps) {
        this.extraProps = extraProps;
      }

      @JsonAnySetter
      public void setExtraProp(String key, Object value) {
        extraProps.put(key, value);
      }
    }

    @Transient
    @Override
    public String getTitle() {
      return super.getTitle();
    }

    @Transient
    @Override
    public String getType() {
      return super.getType();
    }

    @Transient
    @Override
    public Map<String, Object> getExtraProps() {
      return super.getExtraProps();
    }

    public String getFullName() {
      if (Objects.equals(getNodeType(), "group")) {
        return null;
      }

      if (StringUtils.isEmpty(getPath())) {
        return null;
      }

      return Arrays.stream(getPath().split("/")).filter(
          StringUtils::isNotEmpty
      ).collect(Collectors.joining("."));
    }

    public String getNodeType() {
      return nodeType;
    }

    public void setNodeType(String nodeType) {
      this.nodeType = nodeType;
    }

    @JsonProperty("isChanged")
    public boolean isChanged() {
      return isChanged;
    }

    public void setChanged(boolean changed) {
      isChanged = changed;
    }

    public Model getCurrentModel() {
      return currentModel;
    }

    public void setCurrentModel(Model currentModel) {
      this.currentModel = currentModel;
    }

    public List<Model> getModels() {
      return models;
    }

    public void setModels(List<Model> models) {
      this.models = models;
    }

    public List<TreeUIModel> getChildren() {
      return children;
    }

    public void setChildren(List<TreeUIModel> children) {
      this.children = children;
    }

    public void addChildren(TreeUIModel children) {
      this.children.add(children);
    }
  }
}
