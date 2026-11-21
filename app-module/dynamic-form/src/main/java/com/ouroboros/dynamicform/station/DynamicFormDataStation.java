package com.ouroboros.dynamicform.station;

import java.util.*;

import jakarta.annotation.Priority;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DefaultDataModel;
import com.ouroboros.data.station.DataStation;
import com.ouroboros.data.station.DataStationProvider;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.exception.InternalException;

@Component
public class DynamicFormDataStation extends SqlDataStation {
  @Autowired
  public DynamicFormDataStation(DataSource dataSource) {
    super("dynamicForm", dataSource, Collections.emptyList());
    SingleProvider.instance = this;
  }

  @Override
  public Optional<DataModel> getDataModel(String modelName) {
    return super.getDataModel(modelName);
  }

  @Override
  public List<DataModel> getDataModelList() {
    return super.getDataModelList();
  }

  public Boolean addModel(DataModelMeta modelMeta) {
    if (getModelMetas().stream().anyMatch(m -> m.getName().equalsIgnoreCase(modelMeta.getName()))) {
      return replaceModel(modelMeta, true);
    }
    if (!migrateModel(modelMeta)) {
      return false;
    }
    List<DataModelMeta> newList = new ArrayList<>(getModelMetas());
    newList.add(modelMeta);
    setModelMetas(newList);
    return true;
  }

  public Boolean replaceModel(DataModelMeta modelMeta, Boolean migrate) {
    if (migrate && !migrateModel(modelMeta)) {
      return false;
    }
    List<DataModelMeta> newList = new ArrayList<>(getModelMetas());
    newList.removeIf(m -> m.getName().equalsIgnoreCase(modelMeta.getName()));
    newList.add(modelMeta);
    setModelMetas(newList);
    return true;
  }

  public void initModelMetas(List<DataModelMeta> modelMetas) {
    setModelMetas(modelMetas);
  }

  private Boolean migrateModel(DataModelMeta modelMeta) {
    List<DataModel> dataModels = Collections.singletonList(new DefaultDataModel(modelMeta, this));
    var migrateResult = migrate(dataModels);
    if (migrateResult.isLeft()) {
      throw new InternalException("动态表单物理表迁移失败: " + modelMeta.getRawName(), migrateResult.getLeft());
    }
    if (!migrateResult.get()) {
      return false;
    }
    return true;
  }

  @Priority(99)
  public static class SingleProvider implements DataStationProvider {
    static DataStation<?> instance;

    @Override
    public Optional<DataStation<?>> getDataStation(String name) {
      return instance == null || !instance.getName().equalsIgnoreCase(name)
          ? Optional.empty()
          : Optional.of(instance);
    }

    @Override
    public Map<String, DataStation<?>> getDataStationMap() {
      return instance == null ? Collections.emptyMap() : Collections.singletonMap(instance.getName(), instance);
    }
  }
}
