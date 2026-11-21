package com.ouroboros.webapi.dev.controller;

import java.beans.Transient;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataProvider;
import com.ouroboros.dev.metadata.MetadataProviderMixer;
import com.ouroboros.dev.metadata.MetadataSourceType;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.util.Json;
import com.ouroboros.util.Trees;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.dev.model.LowcodeWebAPIModel;

/**
 * @author liansz
 **/
@RestController
@RequestMapping("/api/dev/{appName}")
public class WebAPIDevController {
  private static final Logger logger = LoggerFactory.getLogger(WebAPIDevController.class);

  private DevelopService<FlowModelMeta> logicModelDevelopService;

  private MetadataProviderMixer metadataProviderMixer;


  public WebAPIDevController(DevelopService<FlowModelMeta> logicModelDevelopService, List<MetadataProvider> metadataProviders) {
    this.logicModelDevelopService = logicModelDevelopService;
    this.metadataProviderMixer = MetadataProviderMixer.getConcatMixer(metadataProviders);
  }

  @GetMapping("/webapi")
  public ItemsWebApiResult<?> tree(@PathVariable String appName) {
    Map<String, List<Metadata>> metadataMap = metadataProviderMixer.getAppAllMetadataByModule(appName, "webapi").stream().filter(Objects::nonNull).sorted(
        Comparator.comparing(Metadata::getName).thenComparing(Metadata::getPriority)
    ).collect(
        Collectors.groupingBy(Metadata::getName)
    );

    Comparator<TreeWebAPIModel> comparator = Comparator.comparing(
        (TreeWebAPIModel model) -> Objects.equals(model.getNodeType(), "group") ? 0 : 1
    ).thenComparing(TreeWebAPIModel::getPath);

    List<LowcodeWebAPIModel> webApiModels = metadataMap.values().stream()
        .map(this::toValidWebApiModel)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());

    List<TreeWebAPIModel> treeModels = mapTreeDown(
        buildTree(webApiModels),
        TreeWebAPIModel::getChildren,
        TreeWebAPIModel::setChildren,
        (node, parent) -> {
          if (node.getChildren().size() == 1) {
            return node.getChildren().get(0);
          }

          node.getChildren().sort(comparator);

          return node;
        }
    ).stream().sorted(comparator).collect(Collectors.toList());

    mapTreeDown(treeModels, TreeWebAPIModel::getChildren, TreeWebAPIModel::setChildren, (node, parent) -> {
      node.getChildren().sort(comparator);
      return node;
    });

    Map<String, Metadata> lowCodeMetadataMap = metadataMap.values().stream().flatMap(Collection::stream).filter(m ->
        Objects.equals(m.getSourceType(), MetadataSourceType.LOW_CODE)
    ).collect(
        Collectors.toMap(Metadata::getName, metadata -> metadata, (v1, v2) -> v2)
    );

    Map<String, FlowModelMeta> logicFlowMap = logicModelDevelopService
        .search(Collections.emptyMap())
        .stream()
        .filter(ModelFileMetaWrapper.class::isInstance)
        .map(ModelFileMetaWrapper.class::cast)
        .map(ModelFileMetaWrapper::getModel)
        .filter(FlowModelMeta.class::isInstance)
        .map(FlowModelMeta.class::cast)
        .collect(Collectors.toMap(
            FlowModelMeta::getName,
            meta -> meta,
            (v1, v2) -> v2
        ));

    Trees.walkTree(
        treeModels,
      TreeWebAPIModel::getChildren,
      node -> {
          node.setChanged(lowCodeMetadataMap.containsKey(node.getApi()));

          if (Objects.equals("api", node.getNodeType())) {
            List<TreeWebAPIModel.Model> referenceModels = toReferenceModels(findMetadataList(metadataMap, node.getApi()), logicFlowMap);

            node.setModels(referenceModels);
            if (!referenceModels.isEmpty()) {
              node.setCurrentModel(referenceModels.get(0));
            }
          }
        }
    );

    return ItemsWebApiResult.ok(treeModels);
  }

  @GetMapping("/all-channel/webapi")
  public WebApiResult<?> getUiModel(@PathVariable String appName,
                                    @RequestParam String method, @RequestParam String path) {
    String api = method + " " + path;
    Map<String, List<Metadata>> metadataMap = metadataProviderMixer.getAppAllMetadataByModule(appName, "webapi").stream().filter(Objects::nonNull).collect(
        Collectors.groupingBy(Metadata::getName)
    );
    return WebApiResult.of(
        findMetadataList(metadataMap, api).stream().sorted(
            Comparator.comparing(Metadata::getName).thenComparing(Metadata::getPriority)
        ).map(
            Metadata::getDetails
        ).findFirst()
    );
  }

  @GetMapping("/all-channel/logic-flow/{name}")
  public WebApiResult<?> getUiSchema(@PathVariable String appName, @PathVariable String name) {
    // 先从低代码获取逻辑流程
    Optional<?> lowcodeModel = logicModelDevelopService.getOne(name);
    if (lowcodeModel.isPresent()) {
      return WebApiResult.success(lowcodeModel.get());
    }

    // 如果获取不到，再从元数据获取
    return WebApiResult.of(
        metadataProviderMixer.getAppAllMetadataByModule(appName, "logic-flow").stream().filter(m ->
            m.getName().equals(name)
        ).sorted(
            Comparator.comparing(Metadata::getName).thenComparing(Metadata::getPriority)
        ).map(
            Metadata::getDetails
        ).findFirst()
    );
  }

  private List<TreeWebAPIModel> buildTree(List<? extends WebAPIModel> models) {
    models.sort(
        Comparator.comparing(model ->
            ((WebAPIModel) model).getPath()
        ).thenComparing(model ->
            ((WebAPIModel) model).getPath().split("/").length
        )
    );

    return models.stream().map(m ->
        m.getPath().substring(1).split("/")[0]
    ).distinct().map(path -> "/" + path).map(rootPath ->
        buildModel(
            rootPath,
            models.stream().filter(m ->
                m.getPath().startsWith(rootPath)
            ).map(
                TreeWebAPIModel::new
            ).collect(Collectors.toList())
        )
    ).collect(Collectors.toList());
  }

  private TreeWebAPIModel buildModel(String rootPath, List<TreeWebAPIModel> models) {
    TreeWebAPIModel model = new TreeWebAPIModel("GROUP " + rootPath, null);

    int layer = groupByPath(rootPath).size();

    models.stream().filter(m ->
        m.getPath().startsWith(rootPath) && groupByPath(m.getPath()).size() == layer
    ).forEach(model::addChildren);

    List<String> paths = models.stream().map(TreeWebAPIModel::getPath)
        .filter(path -> !Objects.equals(path, rootPath))
        .filter(path -> path.startsWith(rootPath))
        .filter(path -> groupByPath(path).size() > layer)
        .map(path -> prefixByLayer(path, layer + 1))
        .distinct()
        .collect(Collectors.toList());

    Iterator<String> iterator = paths.iterator();
    while (iterator.hasNext()) {
      String path = iterator.next();
      String[] pathArray = path.split("/");
      String lastPath = pathArray[pathArray.length - 1];
      if (lastPath.startsWith("{") && lastPath.endsWith("}")) {
        String parentPath = Arrays.asList(pathArray).subList(
            0, pathArray.length - 1
        ).stream().filter(
            StringUtils::isNotEmpty
        ).collect(
            Collectors.joining("/", "/", "")
        );

        if (paths.contains(parentPath)) {
          iterator.remove();
        }
      }
    }

    paths.forEach(path -> model.addChildren(buildModel(path, models)));

    return model;
  }

  private String prefixByLayer(String path, int layer) {
    List<String> prefixes = new ArrayList<>();
    int currentLayer = 0;

    for (String segment : path.split("/")) {
      if (StringUtils.isEmpty(segment)) {
        continue;
      }

      prefixes.add(segment);
      if (!(segment.startsWith("{") && segment.endsWith("}"))) {
        currentLayer++;
      }

      if (currentLayer >= layer) {
        break;
      }
    }

    return prefixes.stream().collect(Collectors.joining("/", "/", ""));
  }

  private <T, R> List<R> mapTreeDown(Collection<T> roots, Function<T, Collection<T>> childrenGetter, BiConsumer<R, List<R>> childrenSetter, BiFunction<T, R, R> mapper) {
    return roots.stream()
        .map(root -> mapTreeDown(root, childrenGetter, childrenSetter, mapper))
        .collect(Collectors.toList());
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

  private List<String> groupByPath(String path) {
    return Arrays.stream(path.split("/")).filter(str ->
        !(str.startsWith("{") && str.endsWith("}")) && StringUtils.isNotEmpty(str)
    ).collect(Collectors.toList());
  }

  private List<Metadata> findMetadataList(Map<String, List<Metadata>> metadataMap, String api) {
    if (StringUtils.isBlank(api)) {
      return Collections.emptyList();
    }

    return metadataMap.entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(api))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(Collections.emptyList());
  }

  private Optional<LowcodeWebAPIModel> toValidWebApiModel(List<Metadata> metadataList) {
    if (metadataList == null || metadataList.isEmpty()) {
      return Optional.empty();
    }

    return metadataList.stream()
        .map(this::toValidWebApiModel)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  private List<TreeWebAPIModel.Model> toReferenceModels(List<Metadata> metadataList, Map<String, FlowModelMeta> logicFlowMap) {
    if (metadataList == null || metadataList.isEmpty()) {
      return Collections.emptyList();
    }

    return metadataList.stream()
        .sorted(Comparator.comparing(Metadata::getPriority))
        .map(metadata -> toReferenceModel(metadata, logicFlowMap))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .sorted((left, right) -> Boolean.compare(right.isPreferred(), left.isPreferred()))
        .map(ReferenceModelCandidate::getModel)
        .collect(Collectors.toList());
  }

  private Optional<ReferenceModelCandidate> toReferenceModel(Metadata metadata, Map<String, FlowModelMeta> logicFlowMap) {
    return toValidWebApiModel(metadata).map(webapiModel -> {
      TreeWebAPIModel.Model model = new TreeWebAPIModel.Model();
      model.setScope(webapiModel.getScope());
      model.setHandlerType(webapiModel.getHandlerType());
      model.setSummary(webapiModel.getSummary());
      switch (model.getHandlerType()) {
        case REQUEST_MAPPING:
          webapiModel.getExtraProp("className").map(String.class::cast).ifPresent(className -> {
            if (!className.contains(".")) {
              return;
            }

            model.setExtraProp("package", className.substring(0, className.lastIndexOf(".")));
            model.setExtraProp("className", className.substring(className.lastIndexOf(".") + 1));
          });

          webapiModel.getExtraProp("methodName").map(String.class::cast).ifPresent(method -> {
            if (!method.contains("(") || !method.contains(")")) {
              return;
            }

            model.setExtraProp("methodName", method.substring(0, method.indexOf("(")));
            model.setExtraProp(
                "methodArgument",
                Arrays.stream(
                    method.substring(method.indexOf("(") + 1, method.lastIndexOf(")")).split(",")
                ).filter(
                    StringUtils::isNotEmpty
                ).collect(Collectors.toList())
            );
          });
          break;

        case BUSINESS_ACTION:
          webapiModel.getExtraProp("businessModel").ifPresent(value -> model.setExtraProp("businessModel", value));
          webapiModel.getExtraProp("action").ifPresent(value -> model.setExtraProp("action", value));
          break;
        case LOGIC_FLOW:
          webapiModel.getExtraProp("logicFlow").ifPresent(logicFlow -> {
            model.setExtraProp("logicFlowName", logicFlow);
            model.setExtraProp("isChanged", logicFlowMap.containsKey((String) logicFlow));
          });
          break;
        default:
          break;
      }

      boolean preferred = StringUtils.equals(metadata.getLabel(), model.getSummary());
      return new ReferenceModelCandidate(model, preferred);
    });
  }

  private Optional<LowcodeWebAPIModel> toValidWebApiModel(Metadata metadata) {
    if (metadata == null) {
      logger.warn("Skip invalid webapi metadata: metadata is null");
      return Optional.empty();
    }

    if (metadata.getDetails() == null) {
      logger.warn("Skip invalid webapi metadata: name={}, priority={}, sourceType={}, details is null",
          metadata.getName(), metadata.getPriority(), metadata.getSourceType());
      return Optional.empty();
    }

    final LowcodeWebAPIModel model;
    try {
      model = Json.toBean(LowcodeWebAPIModel.class, Json.toJsonString(metadata.getDetails()));
    } catch (Exception e) {
      logger.warn("Skip invalid webapi metadata: name={}, priority={}, sourceType={}, details={}, error={}",
          metadata.getName(), metadata.getPriority(), metadata.getSourceType(),
          Json.toJsonString(metadata.getDetails()), e.getMessage(), e);
      return Optional.empty();
    }

    if (model == null) {
      logger.warn("Skip invalid webapi metadata: name={}, priority={}, sourceType={}, details={}, deserialized model is null",
          metadata.getName(), metadata.getPriority(), metadata.getSourceType(), Json.toJsonString(metadata.getDetails()));
      return Optional.empty();
    }

    if (StringUtils.isBlank(model.getMethod()) || StringUtils.isBlank(model.getPath()) || model.getHandlerType() == null) {
      logger.warn("Skip invalid webapi metadata: name={}, priority={}, sourceType={}, method={}, path={}, handlerType={}, details={}",
          metadata.getName(), metadata.getPriority(), metadata.getSourceType(),
          model.getMethod(), model.getPath(), model.getHandlerType(), Json.toJsonString(metadata.getDetails()));
      return Optional.empty();
    }

    model.setMethod(model.getMethod().toUpperCase(Locale.ROOT));

    return Optional.of(model);
  }

  private static class ReferenceModelCandidate {
    private final TreeWebAPIModel.Model model;
    private final boolean preferred;

    private ReferenceModelCandidate(TreeWebAPIModel.Model model, boolean preferred) {
      this.model = model;
      this.preferred = preferred;
    }

    private TreeWebAPIModel.Model getModel() {
      return model;
    }

    private boolean isPreferred() {
      return preferred;
    }
  }

  public static class TreeWebAPIModel implements WebAPIModel {

    private WebAPIModel model;

    private String nodeType;

    private boolean isChanged;

    private Model currentModel;

    private List<Model> models;

    private List<TreeWebAPIModel> children = new ArrayList<>();

    public TreeWebAPIModel(String api, WebAPIScope scope) {
      setNodeType("group");
      model = SimpleWebAPIModel.builder()
          .api(api)
          .scope(scope)
          .build();
    }

    public TreeWebAPIModel(WebAPIModel model) {
      setNodeType("api");
      this.model = SimpleWebAPIModel.builder()
          .api(model.getApi())
          .scope(model.getScope())
          .build();
    }

    public static class Model {
      private WebAPIScope scope;
      private HandlerType handlerType;
      private String summary;
      private Map<String, Object> extraProps = new HashMap<>();

      public WebAPIScope getScope() {
        return scope;
      }

      public void setScope(WebAPIScope scope) {
        this.scope = scope;
      }

      public HandlerType getHandlerType() {
        return handlerType;
      }

      public void setHandlerType(HandlerType handlerType) {
        this.handlerType = handlerType;
      }

      public String getSummary() {
        return summary;
      }

      public void setSummary(String summary) {
        this.summary = summary;
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

    public String getFullName() {
      if (Objects.equals(getNodeType(), "group")) {
        return null;
      }

      if (StringUtils.isEmpty(getMethod()) || StringUtils.isEmpty(getPath())) {
        return null;
      }

      return String.format(
          "%s %s",
          getMethod(),
          Arrays.stream(getPath().split("/")).filter(
              StringUtils::isNotEmpty
          ).collect(Collectors.joining("."))
      );
    }

    @Override
    public String getMethod() {
      return !Objects.equals(getNodeType(), "group") ? model.getMethod() : null;
    }

    @Override
    public String getPath() {
      return model.getPath();
    }

    @Transient
    @Override
    public String getApi() {
      return !Objects.equals(getNodeType(), "group") ? model.getApi() : model.getPath();
    }

    @Transient
    @Override
    public WebAPIScope getScope() {
      return model.getScope();
    }

    @Transient
    @Override
    public HandlerType getHandlerType() {
      return model.getHandlerType();
    }

    @Transient
    @Override
    public String getSummary() {
      return model.getSummary();
    }

    @Transient
    @Override
    public Map<String, Object> getExtraProps() {
      return model.getExtraProps();
    }

    @Transient
    @Override
    public Optional<Object> getExtraProp(String key) {
      return model.getExtraProp(key);
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

    public List<TreeWebAPIModel> getChildren() {
      return children;
    }

    public void setChildren(List<TreeWebAPIModel> children) {
      this.children = children;
    }

    public void addChildren(TreeWebAPIModel children) {
      this.children.add(children);
    }
  }
}
