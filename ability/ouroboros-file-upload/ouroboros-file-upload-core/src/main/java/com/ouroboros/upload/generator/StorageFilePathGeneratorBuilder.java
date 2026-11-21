package com.ouroboros.upload.generator;

import java.util.Optional;

import com.ouroboros.util.SPI;

public interface StorageFilePathGeneratorBuilder {
  Boolean support(String strategyName);

  StorageFilePathGenerator build();

  static Optional<StorageFilePathGenerator> build(String strategyName) {
    return SPI.getCachedReversedServiceStream(StorageFilePathGeneratorBuilder.class)
        .filter(builder -> builder.support(strategyName))
        .findFirst()
        .map(StorageFilePathGeneratorBuilder::build);
  }

}
