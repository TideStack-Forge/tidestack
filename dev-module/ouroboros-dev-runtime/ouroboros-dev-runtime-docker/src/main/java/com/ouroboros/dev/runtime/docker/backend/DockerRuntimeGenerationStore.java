package com.ouroboros.dev.runtime.docker.backend;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import io.vavr.control.Try;

import com.ouroboros.container.model.Container;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;

final class DockerRuntimeGenerationStore {
  static final String CONTAINER_WORKSPACE_PATH = "/app/deploy";
  private static final String STATE_DIRECTORY = ".ouroboros";
  private static final String STATE_FILE = "dev-helper-runtime.properties";
  private static final String APP_INCARNATION_ID = "appIncarnationId";
  private static final String RUNTIME_GENERATION = "runtimeGeneration";

  Try<Optional<Long>> read(Container container) {
    return read(statePath(container), appIncarnationId(container));
  }

  Try<Void> write(Container container, long runtimeGeneration) {
    return write(statePath(container), appIncarnationId(container), runtimeGeneration);
  }

  private Try<Optional<Long>> read(Path statePath, String expectedAppIncarnationId) {
    if (statePath == null || !hasText(expectedAppIncarnationId) || Files.notExists(statePath)) {
      return Try.success(Optional.empty());
    }
    return Try.of(() -> {
      Properties properties = new Properties();
      try (InputStream input = Files.newInputStream(statePath)) {
        properties.load(input);
      }
      if (!Objects.equals(expectedAppIncarnationId, properties.getProperty(APP_INCARNATION_ID))) {
        return Optional.empty();
      }
      String generationValue = properties.getProperty(RUNTIME_GENERATION);
      if (!hasText(generationValue)) {
        throw new IllegalStateException("Docker runtime generation metadata is incomplete");
      }
      long runtimeGeneration = Long.parseLong(generationValue);
      if (runtimeGeneration < 0L) {
        throw new IllegalStateException("Docker runtime generation metadata is invalid");
      }
      return Optional.of(Long.valueOf(runtimeGeneration));
    });
  }

  private Try<Void> write(Path statePath, String appIncarnationId, long runtimeGeneration) {
    if (statePath == null || !hasText(appIncarnationId)) {
      return Try.failure(new IllegalStateException("Docker runtime generation workspace is unavailable"));
    }
    if (runtimeGeneration < 0L) {
      return Try.failure(new IllegalArgumentException("runtimeGeneration cannot be negative"));
    }
    return Try.run(() -> {
      Path stateDirectory = statePath.getParent();
      Files.createDirectories(stateDirectory);
      Path stagingFile = Files.createTempFile(stateDirectory, ".dev-helper-runtime-", ".tmp");
      try {
        Properties properties = new Properties();
        properties.setProperty(APP_INCARNATION_ID, appIncarnationId);
        properties.setProperty(RUNTIME_GENERATION, String.valueOf(runtimeGeneration));
        try (OutputStream output = Files.newOutputStream(stagingFile)) {
          properties.store(output, null);
        }
        moveAtomically(stagingFile, statePath);
      } finally {
        Files.deleteIfExists(stagingFile);
      }
    });
  }

  private void moveAtomically(Path source, Path target) throws java.io.IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException unsupported) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private Path statePath(Container container) {
    if (container == null || container.getVolumeBindings() == null) {
      return null;
    }
    String hostWorkspacePath = container.getVolumeBindings().get(CONTAINER_WORKSPACE_PATH);
    return hasText(hostWorkspacePath) ? Paths.get(hostWorkspacePath, STATE_DIRECTORY, STATE_FILE) : null;
  }

  private String appIncarnationId(Container container) {
    return container == null || container.getEnvironmentalVariables() == null
        ? null
        : container.getEnvironmentalVariables().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV);
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
