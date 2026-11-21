package com.ouroboros.data.runtime.adapter;

import java.util.Collections;
import java.util.Map;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.script.ScriptContextContributor;

public class DataModelCenterScriptContextContributor implements ScriptContextContributor {
  @Override
  public Map<String, Object> getBindings() {
    return Collections.singletonMap("DataModelCenter", new DataModelCenterContext() {
    });
  }

  public interface DataModelCenterContext {
    default DataModel get(String name) {
      return DataModelCenter.getDataModel(name).orElse(null);
    }
  }
}
