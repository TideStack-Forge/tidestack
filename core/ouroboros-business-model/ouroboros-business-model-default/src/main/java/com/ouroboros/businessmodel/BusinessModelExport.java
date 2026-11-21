package com.ouroboros.businessmodel;

import java.util.*;

import jakarta.annotation.Priority;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.AppModuleProvider;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.model.BusinessModelModule;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DataModelMetaProvider;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshedEvent;
import com.ouroboros.workflow.approval.callback.CallbackBinding;
import com.ouroboros.workflow.approval.callback.CallbackBindingProvider;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowModelMetaProvider;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelProvider;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uimodel.UIModelProvider;
import com.ouroboros.uischema.UISchemaProvider;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIModelProvider;

@Component
public class BusinessModelExport {
  private record ExportSnapshot(
      Map<String, DataModelMeta> dataModelMetaMap,
      Map<String, FlowModelMeta> flowModelMetaMap,
      Map<String, Authority> authorityMap,
      Map<String, MenuModel> menuModelMap,
      Map<String, WebAPIModel> webapiModelMap,
      Map<String, UIModel> uiModelMap,
      Map<String, SchemaNode> amisSchemaMap,
      Map<String, CallbackBinding> callbackBindingMap,
      Map<String, BusinessModelBehavior> behaviorMap,
      List<AppModule> businessAppModules) {
    private static ExportSnapshot empty() {
      return new ExportSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());
    }
  }

  private volatile ExportSnapshot snapshot = ExportSnapshot.empty();

  private BusinessModelExport() {
    SingletonHolder.INSTANCE = this;
  }

  private void init() {
    var businessModels = List.copyOf(BusinessModelCenter.getBusinessModelMap().values());
    List<BusinessModel> enabled = businessModels.stream().filter(BusinessModel::isEnabled).toList();
    List<String> errors = new ArrayList<>();
    for (var model : enabled) {
      model.getValidationResult().getErrors().forEach(error ->
          errors.add(model.getFullName() + ": " + error.getMessage()));
      model.getRelationValidationResult().getErrors().forEach(error ->
          errors.add(model.getFullName() + ": " + error.getMessage()));
    }
    if (!errors.isEmpty()) {
      throw new BusinessException("业务模型候选验证失败：" + String.join("; ", errors));
    }
    Map<String, DataModelMeta> dataModelMetaMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<String, FlowModelMeta> flowModelMetaMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<String, Authority> authorityMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<String, MenuModel> menuModelMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<String, WebAPIModel> webapiModelMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<String, UIModel> uiModelMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<String, SchemaNode> amisSchemaMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<String, CallbackBinding> callbackBindingMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<String, BusinessModelBehavior> behaviorMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    List<AppModule> businessAppModules = new ArrayList<>();
    for (var model : enabled) {
      ModelBundle bundle;
      try {
        bundle = model.buildModelBundle();
      } catch (RuntimeException failure) {
        throw new BusinessException("业务模型投影失败：" + model.getFullName() + ": " + failure.getMessage(), failure);
      }
      merge(dataModelMetaMap, bundle.getModelMap(DataModelMeta.class), model.getFullName());
      merge(flowModelMetaMap, bundle.getModelMap(FlowModelMeta.class), model.getFullName());
      merge(authorityMap, bundle.getModelMap(Authority.class), model.getFullName());
      merge(menuModelMap, bundle.getModelMap(MenuModel.class), model.getFullName());
      merge(webapiModelMap, bundle.getModelMap(WebAPIModel.class), model.getFullName());
      merge(uiModelMap, bundle.getModelMap(UIModel.class), model.getFullName());
      merge(amisSchemaMap, bundle.getModelMap(SchemaNode.class), model.getFullName());
      merge(callbackBindingMap, bundle.getModelMap(CallbackBinding.class), model.getFullName());
      merge(behaviorMap, bundle.getModelMap(BusinessModelBehavior.class), model.getFullName());
      if (!model.isAttachment()) {
        businessAppModules.add(new BusinessModelModule(model, bundle));
      }
    }
    snapshot = new ExportSnapshot(
        Collections.unmodifiableMap(dataModelMetaMap),
        Collections.unmodifiableMap(flowModelMetaMap),
        Collections.unmodifiableMap(authorityMap),
        Collections.unmodifiableMap(menuModelMap),
        Collections.unmodifiableMap(webapiModelMap),
        Collections.unmodifiableMap(uiModelMap),
        Collections.unmodifiableMap(amisSchemaMap),
        Collections.unmodifiableMap(callbackBindingMap),
        Collections.unmodifiableMap(behaviorMap),
        List.copyOf(businessAppModules));
  }

  private static <T> void merge(Map<String, T> target, Map<String, T> source, String modelName) {
    source.forEach((name, value) -> {
      if (target.putIfAbsent(name, value) != null) {
        throw new BusinessException("业务模型投影身份冲突：" + modelName + " / " + name);
      }
    });
  }

  public void refresh() {
    init();
  }

  public List<String> getMenuNameList() {
    return new ArrayList<>(snapshot.menuModelMap().keySet());
  }

  public List<String> getAuthorityNameList() {
    return new ArrayList<>(snapshot.authorityMap().keySet());
  }

  // 模型元数据刷新后，刷新业务模型投射
  @EventListener
  @Order(9)
  @SuppressWarnings("unused")
  public void onOuroborosMetadataRefreshed(OuroborosMetadataRefreshedEvent event) {
    refreshBusinessModelExport();
  }

  @EventListener
  @Order(9)
  @SuppressWarnings("unused")
  public void onOuroborosModelRefreshed(OuroborosModelRefreshedEvent event) {
    refreshBusinessModelExport();
  }

  private void refreshBusinessModelExport() {
    BusinessModelCenter.refresh();
    refresh();
  }

  @Priority(9)
  public static class SingletonHolder implements DataModelMetaProvider, WebAPIModelProvider, UIModelProvider, UISchemaProvider, FlowModelMetaProvider, AuthorityProvider, MenuModelProvider, AppModuleProvider, CallbackBindingProvider, BusinessModelBehaviorProvider {
    private static volatile BusinessModelExport INSTANCE;

    private static ExportSnapshot current() {
      var instance = INSTANCE;
      return instance == null ? ExportSnapshot.empty() : instance.snapshot;
    }

    @Override
    public List<CallbackBinding> getCallbackBindings() {
      return Optional.ofNullable(INSTANCE)
          .map(instance -> instance.snapshot.callbackBindingMap())
          .map(map -> new ArrayList<>(map.values()))
          .orElseGet(ArrayList::new);
    }

    @Override
    public Optional<DataModelMeta> getDataModelMeta(String name) {
      return Optional.ofNullable(current().dataModelMetaMap().get(name));
    }

    @Override
    public Map<String, DataModelMeta> getDataModelMetaMap() {
      return Collections.unmodifiableMap(current().dataModelMetaMap());
    }

    @Override
    public Optional<FlowModelMeta> getFlowModelMeta(String flowName) {
      return Optional.ofNullable(current().flowModelMetaMap().get(flowName));
    }

    @Override
    public com.ouroboros.logicflow.model.FlowModelSource observeSource() {
      return com.ouroboros.logicflow.model.FlowModelSource.capture("business-model", 9, false,
          true, java.util.List.of(), current().flowModelMetaMap());
    }

    @Override
    public Map<String, FlowModelMeta> getFlowModelMetas() {
      return Collections.unmodifiableMap(current().flowModelMetaMap());
    }

    @Override
    public Map<String, MenuModel> getAllMenus() {
      return Collections.unmodifiableMap(current().menuModelMap());
    }

    @Override
    public Optional<Object> getUISchema(String path) {
      return Optional.ofNullable(INSTANCE)
          .map(instance -> instance.snapshot.amisSchemaMap())
          .map(map -> map.get(path));
    }

    @Override
    public Map<String, Object> getUISchemaMap() {
      return Optional.ofNullable(INSTANCE)
          .map(instance -> instance.snapshot.amisSchemaMap())
          .map(map -> Collections.unmodifiableMap(new HashMap<String, Object>(map)))
          .orElse(Collections.emptyMap());
    }

    @Override
    public Optional<UIModel> getUIModel(String path) {
      return Optional.ofNullable(INSTANCE)
          .map(instance -> instance.snapshot.uiModelMap())
          .map(map -> map.get(path));
    }

    @Override
    public Map<String, UIModel> getUIModelMap() {
      return Optional.ofNullable(INSTANCE)
          .map(instance -> instance.snapshot.uiModelMap())
          .map(Collections::unmodifiableMap)
          .orElse(Collections.emptyMap());
    }

    @Override
    public List<Authority> getAuthority(String fullName) {
      return Optional.ofNullable(current().authorityMap().get(fullName)).map(List::of).orElseGet(List::of);
    }

    @Override
    public List<Authority> getAllAuthorities() {
      return new ArrayList<>(current().authorityMap().values());
    }

    @Override
    public boolean isBusinessModelProjection() { return true; }

    @Override
    public Map<String, WebAPIModel> getWebAPIModelMap() {
      return Collections.unmodifiableMap(current().webapiModelMap());
    }

    @Override
    public List<AppModule> getAppModules() {
      return Collections.unmodifiableList(current().businessAppModules());
    }

    @Override
    public Optional<BusinessModelBehavior> getBusinessModelBehavior(String modelFullName) {
      return Optional.ofNullable(INSTANCE).map(instance -> instance.snapshot.behaviorMap().get(modelFullName));
    }

    @Override
    public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() {
      return Optional.ofNullable(INSTANCE)
          .map(instance -> Collections.unmodifiableMap(instance.snapshot.behaviorMap()))
          .orElseGet(Collections::emptyMap);
    }
  }
}
