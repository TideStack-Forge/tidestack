package com.ouroboros.storage.runtime;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.storage.BlobStore;
import com.ouroboros.storage.BlobStoreFactory;
import com.ouroboros.util.SPI;

/** Discovers factories through the platform SPI; owns no stores or shared client cache. */
public final class BlobStores {
  private final List<BlobStoreFactory> factories;
  public BlobStores() { this(SPI.getServiceStream(BlobStoreFactory.class).toList()); }
  BlobStores(List<BlobStoreFactory> factories) { this.factories = List.copyOf(factories); }

  public BlobStore create(StorageConfiguration configuration) {
    String type = configuration.getType();
    if (type == null || !type.matches("[a-z][a-z0-9-]*")) {
      throw new ValidationException("Storage type must be a lowercase provider name");
    }
    Map<String, BlobStoreFactory> providers = factories.stream().collect(Collectors.toMap(
        factory -> {
          String name = factory.type();
          if (!name.equals(name.toLowerCase(Locale.ROOT)) || !name.matches("[a-z][a-z0-9-]*")) {
            throw new ValidationException("Storage factory has an invalid type");
          }
          return name;
        }, Function.identity(), (first, second) -> { throw new ValidationException("Duplicate storage factory type: " + first.type()); }));
    var factory = providers.get(type);
    if (factory == null) { throw new ValidationException("Storage backend is not installed: " + type); }
    return factory.create(configuration.getOptions());
  }
}
