package com.ouroboros.dynamicform.service.impl;

import static com.ouroboros.data.dsl.query.Query.field;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.dsl.query.Query;
import com.ouroboros.data.model.BaseTypedDataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.PluginDescriptor;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.dynamicform.station.DynamicFormDataStation;
import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.dynamicform.service.DynamicFormDefineService;
import com.ouroboros.event.UIModelChangedEvent;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.util.Maps;

@Service
public class DynamicFormDefineServiceImpl implements DynamicFormDefineService {

  private DynamicFormDataStation dataStation;
  private FieldTypeCatalog fieldTypeCatalog;

  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;

  public DynamicFormDefineServiceImpl(DynamicFormDataStation dataStation, FieldTypeCatalog fieldTypeCatalog) {
    this.dataStation = dataStation;
    this.fieldTypeCatalog = fieldTypeCatalog;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationEvent(ApplicationReadyEvent event) {
    initDynamicFormModel();
  }

  private void initDynamicFormModel() {
    var where = field("status").in("已部署", "已归档").toRawCondition();
    var modelMetas = this.getModelMetaList(where);
    dataStation.initModelMetas(modelMetas);
  }

  private TypedDataModel<Integer, DynamicForm> getDataModel() {
    var dataModel = DataModelCenter.getDataModel("default:DynamicForm")
        .map(model -> new BaseTypedDataModel<Integer, DynamicForm>(model, DynamicForm.class));
    if (dataModel.isPresent()) {
      return dataModel.get();
    }
    return TypedDataModelCenter.getDataModel(Integer.class, DynamicForm.class)
        .orElseThrow(() -> new InternalException("获取动态表单定义数据模型失败", null));
  }

  @Override
  public Optional<DynamicForm> getDynamicFormByName(String name) {
    var result = getDataModel().query()
        .where(field("name").eq(name))
        .execute();
    if (result.isSuccess()) {
      var results = result.get();
      return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("查询动态表单失败", cause));
  }

  @Override
  public Optional<DynamicForm> getDynamicFormById(Integer id) {
    var result = getDataModel().get(id);
    if (result.isSuccess()) {
      return Optional.ofNullable(result.get());
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("查询动态表单失败", cause));
  }

  @Override
  public Long getTotal(Map<String, Object> where) {
    var result = getDataModel().count(where);
    if (result.isSuccess()) {
      return result.get();
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("查询总数失败", cause));
  }

  @Override
  public List<DynamicForm> search(Map<String, Object> query) {
    var result = getDataModel().query(query);
    if (result.isSuccess()) {
      return result.get();
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("查询动态表单失败", cause));
  }

  @Override
  public List<DataModelMeta> getModelMetaList(Map<String, Object> where) {
    var result = getDataModel().query()
        .where(where)
        .execute();
    if (result.isSuccess()) {
      return result.get()
          .stream()
          .map(this::buildModelMeta)
          .collect(Collectors.toList());
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("查询动态表单失败", cause));
  }

  @Override
  @Transactional
  public Optional<DynamicForm> add(DynamicForm dynamicForm) {
    if (dynamicForm.getId() != null) {
      dynamicForm.setId(null);
    }
    assertModelNameAvailable(dynamicForm);
    var result = getDataModel().insert(dynamicForm);
    if (result.isSuccess()) {
      return Optional.ofNullable(result.get());
    }
    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("插入动态表单失败", cause));
  }

  @Override
  @Transactional
  public Boolean update(DynamicForm dynamicForm) {
    var id = dynamicForm.getId();
    assertModelNameAvailable(dynamicForm);
    var values = Maps.fromBean(dynamicForm).entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (v1, v2) -> v2));
    var result = getDataModel().update(Collections.singletonMap("id", id), values);
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("更新动态表单失败", cause));
  }

  @Override
  @Transactional
  public Boolean delete(Integer id) {
    var result = getDataModel().delete(id);
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("删除动态表单失败", cause));
  }

  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Boolean deploy(Integer id) {
    var form = getDynamicFormById(id);
    var modelMeta = form.map(this::buildModelMeta);
    var result = modelMeta
        .map(dataStation::addModel)
        .orElse(false);
    if (!result) {
      return false;
    }
    var updateResult = updateDynamicFormDeployed(form.get());
    if (updateResult) {
      applicationEventPublisher.publishEvent(new UIModelChangedEvent(this));
    }

    return updateResult;
  }

  @Override
  @Transactional
  public Boolean takeDown(Integer id) {
    var form = getDynamicFormById(id);
    var result = form
        .map(formEntity -> {
          if (!"已部署".equals(formEntity.getStatus())) {
            return false;
          }
          formEntity.setStatus("已下线");
          return update(formEntity);
        })
        .orElse(false);
    if (result) {
      applicationEventPublisher.publishEvent(new UIModelChangedEvent(this));
    }
    return result;
  }

  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Boolean reDeploy(Integer id) {
    var form = getDynamicFormById(id);
    if (!form.isPresent()) {
      return false;
    }

    // 只有增加了字段才需要重新部署
    final var needReDeploy = form.get().getFields().stream().anyMatch(field -> !field.getDeployed());
    var modelMeta = form.map(this::buildModelMeta);
    var result = modelMeta
        .map(model -> dataStation.replaceModel(model, needReDeploy))
        .orElse(false);
    if (!result) {
      return false;
    }

    var updateResult = updateDynamicFormDeployed(form.get());
    if (updateResult) {
      applicationEventPublisher.publishEvent(new UIModelChangedEvent(this));
    }
    return updateResult;
  }

  @Override
  @Transactional
  public Boolean archive(Integer id) {
    var form = getDynamicFormById(id);
    var result = form.map(formEntity -> {
      if (!"已部署".equals(formEntity.getStatus())) {
        return false;
      }
      formEntity.setStatus("已归档");
      return update(formEntity);
    });
    return result.orElse(false);
  }

  private Boolean updateDynamicFormDeployed(DynamicForm dynamicForm) {
    dynamicForm.getFields().forEach(field -> field.setDeployed(true));
    dynamicForm.setStatus("已部署");
    return update(dynamicForm);
  }

  DataModelMeta buildModelMeta(DynamicForm form) {
    var modelMeta = new DataModelMeta();
    var allFields = buildFields(form);

    modelMeta.setName(DynamicFormModelNames.modelName(form.getName()));
    modelMeta.setRawName(DynamicFormModelNames.rawTableName(form.getName()));
    modelMeta.setLabel(form.getTitle());
    modelMeta.setFields(allFields);
    modelMeta.setPrimaryKeys(Collections.singletonList("id"));
    modelMeta.setPrimaryKeyGenerator("snowflake");
    modelMeta.addPluginDescriptor(new PluginDescriptor(
        "BasicAudit",
        Collections.singletonMap("insertInitUpdatedAt", true)));
    return modelMeta;
  }

  private void assertModelNameAvailable(DynamicForm dynamicForm) {
    if (dynamicForm == null || dynamicForm.getName() == null) {
      return;
    }
    String modelName = DynamicFormModelNames.modelName(dynamicForm.getName());
    String rawTableName = DynamicFormModelNames.rawTableName(dynamicForm.getName());
    var query = Query.from("DynamicForm").build();
    search(query).stream()
        .filter(existing -> !Objects.equals(existing.getId(), dynamicForm.getId()))
        .filter(existing -> Objects.equals(DynamicFormModelNames.modelName(existing.getName()), modelName)
            || Objects.equals(DynamicFormModelNames.rawTableName(existing.getName()), rawTableName))
        .findFirst()
        .ifPresent(existing -> {
          throw new BusinessException("动态表单模型名冲突: " + dynamicForm.getName() + " 与 " + existing.getName());
        });
  }

  List<DataModelFieldMeta> buildFields(DynamicForm form) {

    var allFields = new ArrayList<DataModelFieldMeta>();
    var idField = new DataModelFieldMeta();
    var createdAt = new DataModelFieldMeta();
    var createdBy = new DataModelFieldMeta();
    var updatedAt = new DataModelFieldMeta();
    var updatedBy = new DataModelFieldMeta();

    idField.setName("id");
    idField.setLabel("ID");
    idField.setType("String");

    createdAt.setType("DateTime");
    createdAt.setName("createdAt");
    createdAt.setLabel("创建时间");
    createdAt.setRawName("created_at");

    createdBy.setType("String");
    createdBy.setName("createdBy");
    createdBy.setLabel("创建人");
    createdBy.setRawName("created_by");

    updatedAt.setType("DateTime");
    updatedAt.setName("updatedAt");
    updatedAt.setLabel("更新时间");
    updatedAt.setRawName("updated_at");

    updatedBy.setType("String");
    updatedBy.setName("updatedBy");
    updatedBy.setLabel("更新人");
    updatedBy.setRawName("updated_by");

    var userDefineFields = new ArrayList<DataModelFieldMeta>();
    for (var field : Optional.ofNullable(form.getFields()).orElse(Collections.emptyList())) {
      var fieldTypeParams = field.getParams() == null ? Collections.<String, Object>emptyMap() : field.getParams();
      var compiled = fieldTypeCatalog.compile(FieldTypeCatalogContext.runtime(), field.getType(), fieldTypeParams)
          .getOrElseThrow(cause -> new InternalException("动态表单字段类型编译失败: " + field.getName(), cause));
      var meta = compiled.toDataFieldMeta();
      if (meta == null) {
        throw new InternalException("动态表单字段类型编译结果为空: " + field.getName());
      }
      meta.setName(field.getName());
      meta.setLabel(field.getLabel());
      meta.setIsNullable(true);
      userDefineFields.add(meta);
    }

    allFields.add(idField);
    allFields.addAll(userDefineFields);
    allFields.add(createdAt);
    allFields.add(createdBy);
    allFields.add(updatedAt);
    allFields.add(updatedBy);

    return allFields;
  }

  @Override
  public void refreshModels() {
    initDynamicFormModel();
  }
}
