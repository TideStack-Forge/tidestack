package com.ouroboros.resource.runtime.provider;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.resource.model.Resource;
import com.ouroboros.resource.provider.ResourceProvider;

/**
 * Aggregates app default resources from filesystem and classpath.
 */
@Priority(100)
public class AppDefaultResourceProvider implements ResourceProvider {

  private static final Logger logger = LoggerFactory.getLogger(AppDefaultResourceProvider.class);
  private static final String DEFAULT_BASE_DIR = "resources";

  private final List<ResourceProvider> delegates;

  public AppDefaultResourceProvider() {
    this(Paths.get(DEFAULT_BASE_DIR));
  }

  public AppDefaultResourceProvider(String baseDirectory) {
    this(Paths.get(baseDirectory));
  }

  public AppDefaultResourceProvider(Path baseDirectory) {
    this(buildDelegates(baseDirectory));
  }

  AppDefaultResourceProvider(List<ResourceProvider> delegates) {
    this.delegates = delegates;
    logger.info("AppDefaultResourceProvider initialized with {} delegates", delegates.size());
  }

  @Override
  public Optional<Resource> getResource(String path) {
    for (ResourceProvider delegate : delegates) {
      Optional<Resource> resource = delegate.getResource(path);
      if (resource.isPresent()) {
        return resource;
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean exists(String path) {
    for (ResourceProvider delegate : delegates) {
      if (delegate.exists(path)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<String> listResources(String directory) {
    Set<String> resources = new LinkedHashSet<>();
    for (ResourceProvider delegate : delegates) {
      resources.addAll(delegate.listResources(directory));
    }
    return new ArrayList<>(resources);
  }

  private static List<ResourceProvider> buildDelegates(Path baseDirectory) {
    List<ResourceProvider> delegates = new ArrayList<>();
    if (baseDirectory != null) {
      delegates.add(new FileSystemAppDefaultProvider(baseDirectory));
    }
    delegates.add(new ClasspathResourceProvider());
    return delegates;
  }

  private static class FileSystemAppDefaultProvider extends AbstractFileBasedResourceProvider {

    private final Path baseDirectory;

    private FileSystemAppDefaultProvider(Path baseDirectory) {
      this.baseDirectory = baseDirectory;
    }

    @Override
    protected Path getBaseDirectory() {
      return baseDirectory;
    }

    @Override
    protected com.ouroboros.resource.model.ResourceLayer getLayer() {
      return com.ouroboros.resource.model.ResourceLayer.APP_DEFAULT;
    }
  }
}
