package com.ouroboros.security.authority.accesscontrol;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SimpleAccessControlDefine implements AccessControlDefine {
  private Boolean canControlFields;
  private boolean fieldSelectionRequired;
  private List<Field> commonFields;
  private List<Field> controlledFields;
  private List<AccessControlFilterDefine> filterDefine;


  @Override
  public Boolean getCanControlFields() {
    return Optional.ofNullable(canControlFields).orElse(false);
  }

  @Override
  public boolean isFieldSelectionRequired() { return fieldSelectionRequired; }


  @Override
  public List<Field> getCommonFields() {
    return Optional.ofNullable(commonFields)
        .orElseGet(Collections::emptyList);
  }


  @Override
  public List<Field> getControlledFields() {
    return Optional.ofNullable(controlledFields)
        .orElseGet(Collections::emptyList);
  }


  @Override
  public List<AccessControlFilterDefine> getFilterDefine() {
    return Optional.ofNullable(filterDefine)
        .orElseGet(Collections::emptyList);
  }
}
