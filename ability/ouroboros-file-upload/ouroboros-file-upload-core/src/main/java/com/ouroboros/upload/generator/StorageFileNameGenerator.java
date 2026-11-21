package com.ouroboros.upload.generator;

import java.util.Map;

public interface StorageFileNameGenerator {
  String generatorName(String fileName, String path, Map<String, String> options);
}
