package com.ouroboros.upload.generator;

import java.util.Optional;

import com.ouroboros.util.SPI;

public interface FileAccessUrlGeneratorBuilder {
  Boolean support(String strategyName);

  FileAccessUrlGenerator build();

  static Optional<FileAccessUrlGenerator> build(String strategyName) {
    return SPI.getCachedReversedServiceStream(FileAccessUrlGeneratorBuilder.class)
        .filter(builder -> builder.support(strategyName))
        .findFirst()
        .map(FileAccessUrlGeneratorBuilder::build);
  }
}
