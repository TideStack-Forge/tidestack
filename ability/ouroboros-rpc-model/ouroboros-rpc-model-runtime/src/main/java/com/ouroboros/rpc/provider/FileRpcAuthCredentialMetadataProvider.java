package com.ouroboros.rpc.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.annotation.LowCodeProvider;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.rpc.security.metadata.RPCAuthCredentialMetadataProvider;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.util.Json;

/**
 * @author liansz
 **/
@Component
public class FileRpcAuthCredentialMetadataProvider {

  private static final Logger logger = LoggerFactory.getLogger(FileRpcAuthCredentialMetadataProvider.class);

  /**
   * 读取开发文件夹内元数据路径
   */
  private static final String METADATA_FILE_HOME = "rpc-model/auth";
  private static final String METADATA_FILE_SUFFIX = ".rpc.auth.json";

  private final AppContext appContext;

  public FileRpcAuthCredentialMetadataProvider(AppContext appContext) {
    this.appContext = appContext;
    Proxy.provider = this;
  }

  public List<RPCCredentialMetadata> getAllRpcAuthenticatorMetadata() {
    var metadataHome = Paths.get(appContext.getHomePath(), METADATA_FILE_HOME);
    if (!Files.isDirectory(metadataHome)) {
      return Collections.emptyList();
    }

    try (var stream = walkMetadataFiles(metadataHome)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(METADATA_FILE_SUFFIX))
          .map(this::loadMetadata)
          .filter(Try::isSuccess)
          .map(Try::get)
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.warn("Failed to scan RPC auth credential metadata directory {}: {}", metadataHome, e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  protected Stream<Path> walkMetadataFiles(Path metadataHome) throws IOException {
    return Files.walk(metadataHome);
  }

  protected String readFileContent(Path filePath) throws IOException {
    return String.join("", Files.readAllLines(filePath));
  }

  private Try<RPCCredentialMetadata> loadMetadata(Path filePath) {
    var fileContent = Try.of(() -> readFileContent(filePath))
        .onFailure(e -> logger.warn("Skipping RPC auth credential metadata file {} because it could not be read: {}",
            filePath, e.getMessage(), e));
    if (fileContent.isFailure()) {
      return Try.failure(fileContent.getCause());
    }

    if (fileContent.get().trim().isEmpty()) {
      var failure = new PlatformException(filePath.getFileName() + " is empty");
      logger.warn("Skipping RPC auth credential metadata file {} because it is empty: {}", filePath,
          failure.getMessage(), failure);
      return Try.failure(failure);
    }

    return Json.tryToBean(RPCCredentialMetadata.class, fileContent.get())
        .onFailure(e -> logger.warn("Skipping RPC auth credential metadata file {} because JSON parsing failed: {}",
            filePath, e.getMessage(), e));
  }

  @Priority(-10)
  @LowCodeProvider
  public static class Proxy implements RPCAuthCredentialMetadataProvider {
    private static FileRpcAuthCredentialMetadataProvider provider;

    @Override
    public List<RPCCredentialMetadata> getAllRpcAuthCredentialMetadata() {
      return provider != null ? provider.getAllRpcAuthenticatorMetadata() : Collections.emptyList();
    }
  }
}
