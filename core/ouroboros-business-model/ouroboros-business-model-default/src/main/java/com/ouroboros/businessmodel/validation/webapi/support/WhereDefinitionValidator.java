package com.ouroboros.businessmodel.validation.webapi.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.data.analyze.DefaultQueryAnalyzer;
import com.ouroboros.data.analyze.QueryAnalyzeContext;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.normalize.QueryNormalizeContext;

public class WhereDefinitionValidator {
  public List<String> validate(DefaultBusinessModel targetModel,
                               Object rawWhere,
                               String path,
                               BusinessModelDataModelResolver dataModelResolver) {
    List<String> errors = new ArrayList<String>();
    if (rawWhere == null) {
      return errors;
    }

    if (!(rawWhere instanceof Map<?, ?>) && !(rawWhere instanceof List<?>)) {
      errors.add(path + ": where 定义必须是对象或数组");
      return errors;
    }

    var dataModel = dataModelResolver.resolve(targetModel.getFullName());
    if (!dataModel.isPresent()) {
      return errors;
    }

    validateAgainstDataModel(dataModel.get(), rawWhere, path, errors);
    return errors;
  }

  private void validateAgainstDataModel(DataModel dataModel, Object rawWhere, String path, List<String> errors) {
    var queryMap = new LinkedHashMap<String, Object>();
    queryMap.put("FROM", dataModel.getFullName());
    queryMap.put("WHERE", rawWhere);

    var normalizeContext = QueryNormalizeContext.builder().withDefaultNormalizers().build();
    var normalized = normalizeContext.normalizeQuery(queryMap);
    if (normalized.isFailure()) {
      errors.add(path + ": " + normalized.getCause().getMessage());
      return;
    }

    var analyzed = new DefaultQueryAnalyzer().analyze(
        normalized.get(),
        QueryAnalyzeContext.builder().model(dataModel).build()
    );
    if (analyzed.isFailure()) {
      errors.add(path + ": " + analyzed.getCause().getMessage());
    }
  }
}
