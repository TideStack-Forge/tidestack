package com.ouroboros.upload.generator;

import java.util.Map;

public interface StorageFilePathGenerator {
  String generatorPath(String fileName, Map<String, String> options);
}
