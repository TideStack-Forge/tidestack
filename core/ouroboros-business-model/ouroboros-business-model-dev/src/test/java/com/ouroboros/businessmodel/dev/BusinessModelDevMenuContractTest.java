package com.ouroboros.businessmodel.dev;

import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.vavr.Tuple2;

import com.ouroboros.util.Json;
import com.ouroboros.util.ResourceUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessModelDevMenuContractTest {

  @Test
  void businessModelMenuStaysNearTheFrontOfDesignTimeMenus() {
    Map<?, ?> menu = ResourceUtil.getResourceNameAndContentListFromDirectory(
            "META-INF/ouroboros/metadata/",
            Pattern.compile(".*businessModelManagement\\.dev-menu\\.json"))
        .stream()
        .map(Tuple2::_2)
        .map(content -> Json.toBean(Map.class, content))
        .findFirst()
        .orElseThrow(AssertionError::new);

    assertEquals("业务模型", menu.get("title"));
    assertEquals(20, ((Number) menu.get("order")).intValue());
  }
}
