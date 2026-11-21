package com.ouroboros.uischema;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class TestUISchemaProvider implements UISchemaProvider {

  @Override
  public Optional<Object> getUISchema(String path) {
    if ("/runtime/page".equals(path)) {
      return Optional.<Object>of("schema:" + path);
    }
    return Optional.empty();
  }

  @Override
  public Map<String, Object> getUISchemaMap() {
    return Collections.emptyMap();
  }
}
