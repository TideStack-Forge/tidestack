package com.ouroboros.dynamicform.service;

import java.util.Optional;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.exception.PlatformException;

public interface DynamicFormDataService {
  Optional<DataModel> getDataModel(String name);

  default Optional<StatusGuardFailure> validateStatus(String name, String... statuses) {
    return Optional.empty();
  }

  default DataModel getReadableDataModel(String name) {
    return getDataModelInStatus(name, "已部署", "已归档");
  }

  default DataModel getWritableDataModel(String name) {
    return getDataModelInStatus(name, "已部署");
  }

  default DataModel getDataModelInStatus(String name, String... statuses) {
    var failure = validateStatus(name, statuses);
    if (failure.isPresent()) {
      throw new PlatformException(failure.get().message());
    }
    return getDataModel(name)
        .orElseThrow(() -> new PlatformException("未找到动态表单: " + name));
  }

  record StatusGuardFailure(int code, String message) {
    public static StatusGuardFailure notFound() {
      return new StatusGuardFailure(404, "表单不存在");
    }

    public static StatusGuardFailure unsupported(String actualStatus, String... expectedStatuses) {
      return new StatusGuardFailure(400, "当前表单不支持此操作");
    }
  }
}
