package com.ouroboros.storage;

import java.util.List;
import java.util.Optional;

public record ObjectPage(List<ObjectInfo> objects, Optional<String> continuationToken) {
  public ObjectPage { objects = List.copyOf(objects); }
}
