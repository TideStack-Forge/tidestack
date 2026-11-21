package com.ouroboros.dynamicform.service.impl;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.dynamicform.station.DynamicFormDataStation;
import com.ouroboros.dynamicform.service.DynamicFormDataService;
import com.ouroboros.dynamicform.service.DynamicFormDefineService;

@Service
public class DynamicFormDataServiceImpl implements DynamicFormDataService {
  @Autowired
  DynamicFormDataStation dataStation;

  @Autowired
  DynamicFormDefineService dynamicFormDefineService;

  @Override
  public Optional<DataModel> getDataModel(String name) {
    return dataStation.getDataModel(DynamicFormModelNames.modelName(name));
  }

  @Override
  public Optional<StatusGuardFailure> validateStatus(String name, String... statuses) {
    var dynamicForm = dynamicFormDefineService.getDynamicFormByName(name);
    if (dynamicForm.isEmpty()) {
      return Optional.of(StatusGuardFailure.notFound());
    }
    var status = dynamicForm.get().getStatus();
    if (!Arrays.asList(statuses).contains(status)) {
      return Optional.of(StatusGuardFailure.unsupported(status, statuses));
    }
    return Optional.empty();
  }
}
