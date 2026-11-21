package com.ouroboros.businessmodel.validation.webapi.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.data.adapter.DataAdapter;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.station.AbstractDataStation;

public class BusinessModelDataModelResolver {
  private final Map<String, DataModel> dataModels;

  public BusinessModelDataModelResolver(DefaultBusinessModel rootModel) {
    this.dataModels = buildDataModels(rootModel);
  }

  public Optional<DataModel> resolve(String targetModelFullName) {
    if (targetModelFullName == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(dataModels.get(targetModelFullName.toUpperCase()));
  }

  private Map<String, DataModel> buildDataModels(DefaultBusinessModel rootModel) {
    List<DataModelMeta> modelMetas = collectReachableModels(rootModel).stream()
        .map(this::extractDataModelMeta)
        .filter(Objects::nonNull)
        .collect(ArrayList::new, List::add, List::addAll);

    if (modelMetas.isEmpty()) {
      return Collections.emptyMap();
    }

    var station = new ValidationDataStation(modelMetas);
    var models = new LinkedHashMap<String, DataModel>();
    for (DataModel dataModel : station.getDataModelList()) {
      models.put(dataModel.getFullName().toUpperCase(), dataModel);
    }
    return models;
  }

  private List<DefaultBusinessModel> collectReachableModels(DefaultBusinessModel rootModel) {
    var queue = new ArrayDeque<DefaultBusinessModel>();
    var visited = new LinkedHashMap<String, DefaultBusinessModel>();

    queue.add(rootModel);
    while (!queue.isEmpty()) {
      var model = queue.removeFirst();
      if (model == null) {
        continue;
      }
      var modelKey = model.getFullName().toUpperCase();
      if (visited.containsKey(modelKey)) {
        continue;
      }
      visited.put(modelKey, model);

      for (BusinessModelRelation relation : model.getRelations()) {
        var relatedModel = relation.getRelatedModel();
        if (relatedModel != null) {
          queue.addLast(relatedModel);
        }
      }
    }

    return new ArrayList<DefaultBusinessModel>(visited.values());
  }

  private DataModelMeta extractDataModelMeta(DefaultBusinessModel model) {
    try {
      ModelBundle bundle = model.buildModelBundle();
      return bundle.getModel(DataModelMeta.class, model.getFullName()).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static class ValidationDataStation extends AbstractDataStation<Object> {
    private ValidationDataStation(Collection<DataModelMeta> modelMetas) {
      setName("business-model-validation");
      setModelMetas(new ArrayList<DataModelMeta>(modelMetas));
    }

    @Override
    public DataAdapter getDataAdapter() {
      return null;
    }

    @Override
    public Object getDataSource() {
      return null;
    }
  }
}
