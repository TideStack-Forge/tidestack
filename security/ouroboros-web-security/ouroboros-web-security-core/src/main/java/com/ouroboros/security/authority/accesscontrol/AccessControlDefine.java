package com.ouroboros.security.authority.accesscontrol;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * 访问控制方式定义，包括字段（列）控制方式，过滤器（行）控制方式
 */
@JsonDeserialize(as = SimpleAccessControlDefine.class)
public interface AccessControlDefine {

  static AccessControlDefine merge(AccessControlDefine accessControlDefineA, AccessControlDefine accessControlDefineB) {
    if (accessControlDefineA instanceof EmptyAccessControlDefine) {
      if (accessControlDefineB instanceof EmptyAccessControlDefine || accessControlDefineB == null) {
        return accessControlDefineA;
      }
      return accessControlDefineB;
    }

    if (accessControlDefineB instanceof EmptyAccessControlDefine) {
      if (accessControlDefineA == null) {
        return accessControlDefineB;
      }
      return accessControlDefineA;
    }

    return new MergedAccessControlDefine(accessControlDefineA, accessControlDefineB);
  }

  static AccessControlDefine empty() {
    return EmptyAccessControlDefine.INSTANCE;
  }

  Boolean getCanControlFields();

  /** Sensitive management authorities may require an explicit selection instead of the legacy all-fields default. */
  default boolean isFieldSelectionRequired() { return false; }

  List<Field> getCommonFields();

  List<Field> getControlledFields();

  List<AccessControlFilterDefine> getFilterDefine();

  // TODO: 将来做成接口
  class Field {
    private final String name;
    private final String label;
    private final String type;
    private final String description;

    @JsonCreator
    public Field(
            @JsonProperty("name") String name,
            @JsonProperty("label") String label,
            @JsonProperty("type") String type,
            @JsonProperty("description") String description
    ) {
      this.name = name;
      this.label = label;
      this.type = type;
      this.description = description;
    }

    public String getName() {
      return name;
    }

    public String getLabel() {
      return label;
    }

    public String getDescription() {
      return description;
    }

    public String getType() {
      return type;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (obj == this) {
        return true;
      }
      if (obj instanceof Field other) {
        return other.name.equals(this.name);
      }
      return false;
    }
  }

  class EmptyAccessControlDefine implements AccessControlDefine {
    static EmptyAccessControlDefine INSTANCE = new EmptyAccessControlDefine();

    @Override
    public Boolean getCanControlFields() {
      return false;
    }


    @Override
    public List<Field> getCommonFields() {
      return Collections.emptyList();
    }


    @Override
    public List<Field> getControlledFields() {
      return Collections.emptyList();
    }


    @Override
    public List<AccessControlFilterDefine> getFilterDefine() {
      return Collections.emptyList();
    }
  }

  class MergedAccessControlDefine implements AccessControlDefine {
    private final AccessControlDefine accessControlDefineA;
    private final AccessControlDefine accessControlDefineB;

    public MergedAccessControlDefine(AccessControlDefine accessControlDefineA, AccessControlDefine accessControlDefineB) {
      this.accessControlDefineA = accessControlDefineA;
      this.accessControlDefineB = accessControlDefineB;
    }


    @Override
    public Boolean getCanControlFields() {
      return accessControlDefineA.getCanControlFields() || accessControlDefineB.getCanControlFields();
    }

    @Override
    public boolean isFieldSelectionRequired() {
      return accessControlDefineA.isFieldSelectionRequired() || accessControlDefineB.isFieldSelectionRequired();
    }


    @Override
    public List<Field> getCommonFields() {
      return Stream.concat(accessControlDefineA.getCommonFields().stream(), accessControlDefineB.getCommonFields().stream())
          .distinct()
          .collect(Collectors.toList());
    }


    @Override
    public List<Field> getControlledFields() {
      return Stream.concat(accessControlDefineA.getControlledFields().stream(), accessControlDefineB.getControlledFields().stream())
          .distinct().collect(Collectors.toList());
    }


    @Override
    public List<AccessControlFilterDefine> getFilterDefine() {
      return Stream.concat(accessControlDefineA.getFilterDefine().stream(), accessControlDefineB.getFilterDefine().stream())
          .distinct()
          .collect(Collectors.toList());
    }
  }
}
