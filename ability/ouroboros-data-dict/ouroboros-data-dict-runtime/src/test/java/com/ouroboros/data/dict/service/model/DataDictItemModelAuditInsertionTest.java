package com.ouroboros.data.dict.service.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.data.adapter.DataAdapter;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.AbstractDataModel;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DataOperationIdentityProviders;
import com.ouroboros.data.model.EnhancedDataModelProxy;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.data.station.DataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.util.Maps;

class DataDictItemModelAuditInsertionTest {

  @Test
  void batchInsertUsesSystemIdentityWhenImportContextProvidesCurrentOperator() {
    DataModel dataModel = new EnhancedDataModelProxy(dataDictItemMeta(), ValidatingDataModel::new);

    DataDictItemModel item = new DataDictItemModel();
    item.setId("SEX-001");
    item.setDict("SEX");
    item.setCode("001");
    item.setLabel("男");
    item.setOrder(0);

    Try<RecordList> result = DataOperationIdentityProviders.withCurrentOperator(
        DataOperationIdentityProviders.SYSTEM_OPERATOR,
        () -> dataModel.batchInsert(List.of(Maps.fromBean(item))));

    assertTrue(result.isSuccess(), () -> result.isFailure() ? result.getCause().toString() : "");
    assertEquals(DataOperationIdentityProviders.SYSTEM_OPERATOR, result.get().get(0).get("createdBy"));
    assertFalse(DataOperationIdentityProviders.findCurrentOperator().isPresent());
  }

  private DataModelMeta dataDictItemMeta() {
    DataModelMeta meta = TypedModelMetaHelper.scanModelMate("com.ouroboros.data.dict.service.model")
        .get("DataDictItemModel");
    meta.getFields().stream()
        .filter(field -> "id".equals(field.getName()))
        .findFirst()
        .ifPresent(field -> field.getExtraProps().remove("primaryKeyGenerator"));
    return meta;
  }

  private static final class ValidatingDataModel extends AbstractDataModel {

    private ValidatingDataModel(DataModelMeta meta) {
      super(meta);
    }

    @Override
    public DataAdapter getAdapter() {
      return null;
    }

    @Override
    public DataStation<?> getDataStation() {
      return null;
    }

    @Override
    public Try<Record> insert(Map<String, Object> data) {
      return buildInsertRecord(data);
    }

    @Override
    public Try<RecordList> batchInsert(List<Map<String, Object>> dataList) {
      List<Map<String, Object>> records = new ArrayList<>();
      for (Map<String, Object> data : dataList) {
        Try<Record> record = buildInsertRecord(data);
        if (record.isFailure()) {
          return Try.failure(record.getCause());
        }
        records.add(new HashMap<String, Object>(record.get()));
      }
      return Try.success(RecordList.of(records));
    }

    @Override
    public Try<Long> update(Map<String, Object> where, Map<String, Object> data) {
      return Try.success(0L);
    }

    @Override
    public Try<Long> delete(Map<String, Object> where) {
      return Try.success(0L);
    }

    @Override
    public Try<Long> count(Map<String, Object> where) {
      return Try.success(0L);
    }

    @Override
    public Try<RecordList> query(Map<String, Object> statement) {
      return Try.success(RecordList.empty());
    }

    @Override
    public Try<RecordList> query(QueryStatement statement) {
      return Try.success(RecordList.empty());
    }
  }
}
