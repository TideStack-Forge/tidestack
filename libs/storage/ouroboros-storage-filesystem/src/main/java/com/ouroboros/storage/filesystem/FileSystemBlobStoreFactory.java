package com.ouroboros.storage.filesystem;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.storage.BlobStore;
import com.ouroboros.storage.BlobStoreFactory;

public final class FileSystemBlobStoreFactory implements BlobStoreFactory {
  @Override public String type() { return "filesystem"; }
  @Override public BlobStore create(Map<String, String> options) {
    if (!Set.of("root-directory").containsAll(options.keySet())) {
      throw new ValidationException("Unknown filesystem storage option");
    }
    String root = options.get("root-directory");
    if (root == null || root.isBlank()) { throw new ValidationException("root-directory is required"); }
    return new FileSystemBlobStore(Path.of(root));
  }
}
