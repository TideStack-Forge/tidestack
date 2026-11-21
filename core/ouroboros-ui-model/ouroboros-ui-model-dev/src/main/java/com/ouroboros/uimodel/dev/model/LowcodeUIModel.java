package com.ouroboros.uimodel.dev.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ouroboros.uimodel.SimpleUIModel;

public class LowcodeUIModel extends SimpleUIModel {
  public String getFullName() {
    var path = getPath();
    if (StringUtils.isBlank(path)) {
      return "";
    }

    return Arrays.stream(path.split("/")).filter(
        StringUtils::isNotEmpty
    ).collect(Collectors.joining("."));
  }

  @Override
  @JsonAnySetter
  public void setExtraProp(String key, Object value) {
    if (Objects.equals(key, "fullName")) {
      return;
    }

    super.setExtraProp(key, value);
  }
}
