package com.ouroboros.upload.generator;

import java.util.Optional;

import com.ouroboros.util.SPI;

public interface StorageFileNameGeneratorBuilder {
  Boolean support(String strategyName);

  StorageFileNameGenerator build();

  static Optional<StorageFileNameGenerator> build(String strategyName) {
    return SPI.getCachedReversedServiceStream(StorageFileNameGeneratorBuilder.class)
        .filter(builder -> builder.support(strategyName))
        .findFirst()
        .map(StorageFileNameGeneratorBuilder::build);
  }
}
