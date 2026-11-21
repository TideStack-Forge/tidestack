package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataProvider;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.util.Json;

public abstract class AbstractDevelopServiceAdapter<M> implements MetadataProvider {
  protected final DevelopService<M> developService;
  protected final String moduleName;

  public AbstractDevelopServiceAdapter(DevelopService<M> developService) {
    this.developService = developService;
    DevelopModule developModule = developService.getClass().getAnnotation(DevelopModule.class);
    this.moduleName = developModule == null ? null : developModule.value();
  }

  abstract protected Metadata convert(Object model);

  @Override
  public List<Metadata> getAppAllMetadataByModule(String appName, String moduleName) {
    if (moduleName == null || !moduleName.equals(this.moduleName)) {
      return Collections.emptyList();
    }
    return DevAppHolder.runWithAppName(appName, () -> developService.search(Collections.emptyMap())
        .stream()
        .map(this::convert)
        .filter(Objects::nonNull)
        .peek(metadata -> metadata.setPriority(Integer.MAX_VALUE))
        .collect(Collectors.toList()));
  }

  public static class LazyMetadata extends Metadata {
    private Supplier<Map<String, Object>> lazyDetails;

    public Metadata setRawDetails(Object rawDetails) {
      return setLazyDetails(() -> Json.toMap(Json.toJsonString(rawDetails)));
    }

    public Metadata setLazyDetails(Supplier<Map<String, Object>> lazyDetails) {
      this.lazyDetails = lazyDetails;
      return this;
    }

    @Override
    public Map<String, Object> getDetails() {
      var superDetails = super.getDetails();
      if (superDetails != null) {
        return superDetails;
      }
      var details = lazyDetails.get();
      setDetails(details);
      return details;
    }
  }
}
