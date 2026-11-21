package com.ouroboros.security.rbac.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.security.rbac.model.BuiltinRoleAuthorityModel;
import com.ouroboros.security.rbac.model.BuiltinRoleModel;
import com.ouroboros.util.Json;

@Component
public class BuiltinRoleDefinitionStore {

  private static final Logger logger = LoggerFactory.getLogger(BuiltinRoleDefinitionStore.class);
  private static final String ROLE_DIRECTORY = "rbac/builtin-roles";
  private static final String ROLE_FILE_EXTENSION = ".role.json";
  private static final Pattern SAFE_ROLE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

  private final AppContext appContext;
  private final AppHomeResolver appHomeResolver;

  public BuiltinRoleDefinitionStore(AppContext appContext) {
    this(appContext, (AppHomeResolver) null);
  }

  public BuiltinRoleDefinitionStore(AppContext appContext, AppHomeResolver appHomeResolver) {
    this.appContext = appContext;
    this.appHomeResolver = appHomeResolver;
  }

  @Autowired
  public BuiltinRoleDefinitionStore(ObjectProvider<AppContext> appContextProvider, ObjectProvider<AppHomeResolver> appHomeResolverProvider) {
    this.appContext = appContextProvider == null ? null : appContextProvider.getIfAvailable();
    this.appHomeResolver = appHomeResolverProvider == null ? null : appHomeResolverProvider.getIfAvailable();
  }

  public List<BuiltinRoleModel> loadAll() {
    Path root = getRoleRoot();
    if (!Files.isDirectory(root)) {
      return new ArrayList<BuiltinRoleModel>();
    }

    try (Stream<Path> stream = Files.walk(root)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(ROLE_FILE_EXTENSION))
          .sorted()
          .map(this::readRoleFile)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());
    } catch (IOException error) {
      logger.warn("Failed to load builtin roles from {}", root, error);
      return new ArrayList<BuiltinRoleModel>();
    }
  }

  public Optional<BuiltinRoleModel> findById(String roleId) {
    if (!isSafeRoleId(roleId)) {
      return Optional.empty();
    }
    Path file = getRoleFile(roleId);
    return readRoleFile(file);
  }

  public Try<Void> save(BuiltinRoleModel model) {
    return Try.run(() -> {
      if (model == null) {
        throw new IllegalArgumentException("builtin role model is required");
      }
      if (model.getId() == null || model.getId().trim().isEmpty()) {
        throw new IllegalArgumentException("builtin role id is required");
      }
      if (!isSafeRoleId(model.getId())) {
        throw new IllegalArgumentException("builtin role id is invalid");
      }
      validateAuthorities(model);

      Path file = getRoleFile(model.getId());
      Files.createDirectories(file.getParent());
      String json = Json.toPrettyJsonString(model);
      writeAtomically(file, json);
    });
  }

  public Try<Void> updateAuthorities(String roleId, List<BuiltinRoleAuthorityModel> authorities) {
    return Try.run(() -> {
      BuiltinRoleModel role = findById(roleId)
          .orElseThrow(() -> new IllegalArgumentException("builtin role not found: " + roleId));
      role.setAuthorities(authorities);
      save(role).get();
    });
  }

  private Optional<BuiltinRoleModel> readRoleFile(Path file) {
    if (file == null || !Files.isRegularFile(file)) {
      return Optional.empty();
    }

    try {
      String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      return Json.tryToBean(BuiltinRoleModel.class, content).toJavaOptional();
    } catch (Exception error) {
      logger.warn("Failed to load builtin role file: {}", file, error);
      return Optional.empty();
    }
  }

  private Path getRoleRoot() {
    String devAppName = DevAppHolder.getAppName();
    if (devAppName != null && !devAppName.trim().isEmpty() && appHomeResolver != null) {
      return Paths.get(appHomeResolver.getAppHomeByAppId(devAppName), ROLE_DIRECTORY).toAbsolutePath().normalize();
    }
    if (appContext == null) {
      return Paths.get(System.getProperty("user.dir"), ROLE_DIRECTORY).toAbsolutePath().normalize();
    }
    return Paths.get(appContext.getHomePath(), ROLE_DIRECTORY).toAbsolutePath().normalize();
  }

  private Path getRoleFile(String roleId) {
    if (!isSafeRoleId(roleId)) {
      throw new IllegalArgumentException("builtin role id is invalid");
    }
    Path roleRoot = getRoleRoot();
    Path roleFile = roleRoot.resolve(roleId + ROLE_FILE_EXTENSION).normalize();
    if (!roleFile.startsWith(roleRoot)) {
      throw new IllegalArgumentException("builtin role path is invalid");
    }
    return roleFile;
  }

  private boolean isSafeRoleId(String roleId) {
    String normalized = StringUtils.trimToNull(roleId);
    return normalized != null
        && roleId.equals(normalized)
        && SAFE_ROLE_ID_PATTERN.matcher(normalized).matches()
        && !normalized.contains("..");
  }

  private void validateAuthorities(BuiltinRoleModel model) {
    var authorityNames = new java.util.LinkedHashSet<String>();
    var authorities = model.getAuthorityDefinitions();
    for (int index = 0; index < authorities.size(); index++) {
      BuiltinRoleAuthorityModel authority = authorities.get(index);
      if (authority == null || StringUtils.isBlank(authority.getAuthority())) {
        throw new IllegalArgumentException(String.format("builtin role authority is required: %d", index + 1));
      }
      String authorityName = authority.getAuthority();
      if (!authorityName.equals(authorityName.trim())) {
        throw new IllegalArgumentException(String.format("builtin role authority is invalid: %d", index + 1));
      }
      if (!authorityNames.add(authorityName)) {
        throw new IllegalArgumentException("builtin role authority is duplicated: " + authorityName);
      }
    }
  }

  private void writeAtomically(Path file, String content) throws IOException {
    Path tempFile = Files.createTempFile(file.getParent(), "." + file.getFileName().toString() + ".", ".tmp");
    try {
      Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
      try {
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException error) {
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (Exception error) {
      Files.deleteIfExists(tempFile);
      throw error;
    }
  }
}
