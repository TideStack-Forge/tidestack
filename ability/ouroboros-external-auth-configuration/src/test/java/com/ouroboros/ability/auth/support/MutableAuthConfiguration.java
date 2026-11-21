package com.ouroboros.ability.auth.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.ouroboros.configuration.ConfigurationService;

/** Configuration-service boundary fixture, shared by authentication component tests. */
public class MutableAuthConfiguration {
  public final ConfigurationService service = mock(ConfigurationService.class);
  private final Map<String, Object> values = new HashMap<>();
  private final Map<String, String> revisions = new HashMap<>();
  private long revision;

  public MutableAuthConfiguration() {
    lenient().when(service.get(anyString())).thenAnswer(call -> Optional.ofNullable(values.get(call.getArgument(0))));
    lenient().when(service.getRequired(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
    lenient().when(service.getRevision(anyString())).thenAnswer(call -> Optional.ofNullable(revisions.get(call.getArgument(0))));
    lenient().when(service.getStoredRawValue(anyString())).thenAnswer(call -> {
      Object value = values.get(call.getArgument(0));
      return value == null ? Optional.empty() : Optional.of(value instanceof String text ? text : com.ouroboros.ability.auth.config.UnifiedExternalAuthConfiguration.toJson(value));
    });
  }

  public void put(String key, Object value) {
    values.put(key, value);
    revisions.put(key, Long.toString(++revision));
  }
}
