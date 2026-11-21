package com.ouroboros.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.ouroboros.app.runtime.ResolvedRuntimeLockProvider;
import com.ouroboros.app.runtime.RuntimeLockConsumptionException;

class AppRuntimeAutoConfigurationRuntimeLockStartupInputTest {

  @Test
  void shouldFailStartupInputAssemblyWhenRuntimeLockRequiredButLatestLockMissing() {
    AppRuntimeAutoConfiguration configuration = new AppRuntimeAutoConfiguration();
    ResolvedRuntimeLockProvider provider = Optional::empty;

    assertThrows(RuntimeLockConsumptionException.class,
        () -> configuration.runtimeLockStartupInput(provider, true));
  }

  @Test
  void shouldReadStartupInputFromProviderWhenRuntimeLockRequired() {
    AppRuntimeAutoConfiguration configuration = new AppRuntimeAutoConfiguration();
    AtomicBoolean called = new AtomicBoolean(false);
    ResolvedRuntimeLockProvider provider = () -> {
      called.set(true);
      Map<String, Object> input = new LinkedHashMap<String, Object>();
      input.put("status", "READY");
      return Optional.of(input);
    };

    Map<String, Object> input = configuration.runtimeLockStartupInput(provider, true);

    assertTrue(called.get());
    assertEquals("READY", input.get("status"));
  }

  @Test
  void shouldReturnEmptyStartupInputWhenRuntimeLockNotRequiredAndMissing() {
    AppRuntimeAutoConfiguration configuration = new AppRuntimeAutoConfiguration();
    AtomicBoolean called = new AtomicBoolean(false);
    ResolvedRuntimeLockProvider provider = () -> {
      called.set(true);
      return Optional.empty();
    };

    Map<String, Object> input = configuration.runtimeLockStartupInput(provider, false);

    assertFalse(called.get());
    assertNotNull(input);
    assertEquals(Collections.emptyMap(), input);
  }
}
