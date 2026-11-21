package com.ouroboros.rpc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RPC 模型静态定义。
 */
public interface RPCModelDefinition {

  String getName();

  String getTitle();

  default String getGroup() {
    return null;
  }

  String getType();

  String getAuthCredential();

  Map<String, Object> getExtraProps();

  class Simple implements RPCModelDefinition {
    private final String name;
    private final String title;
    private final String group;
    private final String type;
    private final String authCredential;
    private final Map<String, Object> extraProps;

    public Simple(
        String name,
        String title,
        String type,
        String authCredential,
        Map<String, Object> extraProps
    ) {
      this(name, title, null, type, authCredential, extraProps);
    }

    public Simple(
        String name,
        String title,
        String group,
        String type,
        String authCredential,
        Map<String, Object> extraProps
    ) {
      this.name = name;
      this.title = title;
      this.group = group;
      this.type = type;
      this.authCredential = authCredential;
      this.extraProps = extraProps == null
          ? Collections.emptyMap()
          : Collections.unmodifiableMap(new LinkedHashMap<>(extraProps));
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public String getGroup() {
      return group;
    }

    @Override
    public String getType() {
      return type;
    }

    @Override
    public String getAuthCredential() {
      return authCredential;
    }

    @Override
    public Map<String, Object> getExtraProps() {
      return extraProps;
    }
  }
}
