package com.ouroboros.upload.generator;

import java.util.Map;

public interface FileAccessUrlGenerator {
  String generateUrl(String storageFullPath, Map<String, String> options);
}
