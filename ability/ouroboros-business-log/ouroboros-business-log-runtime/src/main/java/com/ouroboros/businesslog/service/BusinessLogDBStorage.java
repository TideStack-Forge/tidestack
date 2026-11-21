package com.ouroboros.businesslog.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.businesslog.BusinessLogStorage;
import com.ouroboros.businesslog.model.BusinessLogModel;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;

@Component
public class BusinessLogDBStorage implements BusinessLogStorage<BusinessLogModel> {

  @Autowired
  private TypedDataModel<String, BusinessLogModel> dataModel;

  @Override
  public void save(BusinessLogModel businessLog) {
    dataModel.insert(businessLog)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }
}
