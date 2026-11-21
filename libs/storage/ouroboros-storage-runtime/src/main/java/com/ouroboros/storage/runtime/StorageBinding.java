package com.ouroboros.storage.runtime;

import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.storage.BlobStore;

/** Converts legacy consumer settings once, then uses the same provider assembly path. */
public final class StorageBinding {
  private StorageBinding() { }

  public static BlobStore create(Environment environment, String prefix,
                                 Supplier<StorageConfiguration> legacy, String... legacyKeys) {
    var binder = Binder.get(environment);
    var options = binder.bind(prefix + ".options", Bindable.mapOf(String.class, String.class)).orElse(Map.of());
    boolean configured = environment.containsProperty(prefix + ".type") || !options.isEmpty();
    StorageConfiguration configuration;
    if (configured) {
      for (String key : legacyKeys) {
        if (environment.containsProperty(key)) { throw new ValidationException("Conflicting storage configuration: " + prefix + " and " + key); }
      }
      configuration = binder.bind(prefix, Bindable.of(StorageConfiguration.class)).orElseThrow(
          () -> new ValidationException("Invalid storage configuration: " + prefix));
    } else { configuration = legacy.get(); }
    return new BlobStores().create(configuration);
  }
}
