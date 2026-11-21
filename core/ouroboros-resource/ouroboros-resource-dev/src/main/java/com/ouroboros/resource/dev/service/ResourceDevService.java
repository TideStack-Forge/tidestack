package com.ouroboros.resource.dev.service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.dev.event.UpdateModelEvent;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.resource.dev.model.ResourceModel;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.resource.util.ResourcePathUtils;
import com.ouroboros.web.TreeSelectOption;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Application resource development service.
 * Manages workspace resource files and metadata in VCS.
 */
@Service
@DevelopModule("resource")
public class ResourceDevService extends BaseDevelopService<ResourceModel> {

    static final String MODEL_FILE_DIRECTORY = "resources";
    static final String MODEL_FILE_EXTENSION = ".resource.json";
    static final String DIRECTORY_MARKER_FILE_NAME = "__resource_directory__.json";

    @Override
    protected String getModelFileDirectory() {
        return MODEL_FILE_DIRECTORY;
    }

    @Override
    protected String getModelFileExtension() {
        return MODEL_FILE_EXTENSION;
    }

    @Override
    protected boolean isAutoCommitWhenUpdate() {
        return false;
    }

    @Override
    protected String getFullName(ResourceModel model) {
        return model.getScopedPath();
    }

    @Override
    protected String getModelDisplayName() {
        return "Application Resource";
    }

    /**
     * Build actual resource file path (without .resource.json)
     */
    private String buildActualResourceFilePath(String fullName) {
        return Paths.get(getModelFileDirectory(), fullName).toString();
    }

    /**
     * Get associated files for create (actual resource file)
     */
    @Override
    protected Map<String, Supplier<InputStream>> getAssociatedFilesForCreate(String fullName, ResourceModel model) {
        Map<String, Supplier<InputStream>> files = new HashMap<>();
        String actualFilePath = buildActualResourceFilePath(fullName);

        // If resource content is provided, create the actual resource file
        // For now, we create an empty file as placeholder
        files.put(actualFilePath, () -> new ByteArrayInputStream(new byte[0]));

        return files;
    }

    /**
     * Get associated files for deletion (actual resource file)
     */
    @Override
    protected Set<String> getAssociatedFiles(ResourceModel model) {
        if (model == null) {
            return Collections.emptySet();
        }
        String actualFilePath = buildActualResourceFilePath(model.getScopedPath());
        return Collections.singleton(actualFilePath);
    }

    /**
     * Get associated file operations for update
     */
    @Override
    protected Map<String, Consumer<VCSAction.BatchAction>> getAssociatedFilesForUpdate(
            String fullName, ResourceModel model, ResourceModel oldModel) {
        // For resource update, we typically don't update the actual file content
        // unless explicitly requested
        return Collections.emptyMap();
    }

    /**
     * Build search result mapper
     */
    @Override
    protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
        return file -> {
            var model = buildModelFromFileContent(file.getContent());
            return model.map(m -> new ModelFileMetaWrapper<>(m, file));
        };
    }

    /**
     * Build search filter
     */
    @Override
    protected Predicate<ModelFileMetaWrapper<ResourceModel>> buildSearchFilter(Map<String, Object> searchParam) {
        var keyword = searchParam.getOrDefault("keyword", "").toString().toLowerCase();
        var scope = searchParam.get("scope");
        var directory = normalizeDirectory(searchParam.get("directory"));

        return wrapper -> {
            var model = wrapper.getModel();

          if (StringUtils.isNotBlank(keyword)) {
            var scopedPath = Optional.ofNullable(model.getScopedPath()).orElse("").toLowerCase();
            var fileName = Optional.ofNullable(model.getFileName()).orElse("").toLowerCase();
            if (!scopedPath.contains(keyword) && !fileName.contains(keyword)) {
              return false;
            }
          }

          if (scope != null) {
            ResourceScope targetScope = scope instanceof ResourceScope
                ? (ResourceScope) scope
                : ResourceScope.fromValue(scope.toString());
            if (model.getScope() != targetScope) {
              return false;
            }
          }

          if (StringUtils.isNotBlank(directory)
              && !StringUtils.equals(StringUtils.defaultString(model.getDirectory()), directory)) {
            return false;
          }

          return true;
        };
    }

    /**
     * Validate resource model
     */
    @Override
    protected Try<Void> validateModel(ResourceModel model) {
        if (model == null) {
            return Try.failure(new BusinessException("Resource model cannot be null"));
        }

        if (model.getScope() == null) {
            return Try.failure(new BusinessException("Resource scope cannot be empty"));
        }

        String normalizedKey = ResourcePathUtils.normalize(model.getKey());
        if (ResourcePathUtils.isFrontendPath(normalizedKey) || ResourcePathUtils.isBackendPath(normalizedKey)) {
            normalizedKey = ResourcePathUtils.toResourceKey(normalizedKey);
        }

        if (StringUtils.isBlank(normalizedKey)) {
            return Try.failure(new BusinessException("Resource key cannot be empty"));
        }

        model.setKey(normalizedKey);
        if (model.getMetadata() == null) {
            model.setMetadata(Collections.emptyMap());
        }
        return Try.success(null);
    }

    /**
     * Search and sort resources
     */
    @Override
    public List<?> search(Map<String, Object> searchParam) {
        var result = (List<ModelFileMetaWrapper<ResourceModel>>) super.search(searchParam);

        return result.stream()
                .sorted(Comparator.comparing(o ->
                        StringUtils.defaultIfBlank(o.getModel().getScopedPath(), "")))
                .collect(Collectors.toList());
    }

    public List<TreeSelectOption> listDirectories(ResourceScope scope) {
        if (scope == null) {
            return Collections.emptyList();
        }

        Map<String, Object> searchParam = new LinkedHashMap<>();
        searchParam.put("scope", scope.getValue());
        var result = (List<ModelFileMetaWrapper<ResourceModel>>) search(searchParam);
        Set<String> directories = new LinkedHashSet<>();
        result.stream()
            .map(ModelFileMetaWrapper::getModel)
            .map(ResourceModel::getDirectory)
            .forEach(directory -> collectDirectories(directory, directories));
        listDirectoryMarkers(scope).forEach(directory -> collectDirectories(directory, directories));
        return buildDirectoryTree(directories);
    }

    public Try<Void> createDirectory(ResourceScope scope, String directory) {
        if (scope == null) {
            return Try.failure(new BusinessException("Resource scope cannot be empty"));
        }

        String normalizedDirectory = normalizeDirectory(directory);
        if (StringUtils.isBlank(normalizedDirectory)) {
            return Try.failure(new BusinessException("Directory cannot be empty"));
        }

        String scopedPath = ResourcePathUtils.toScopedResourcePath(scope, normalizedDirectory);
        if (getOne(scopedPath).isPresent()) {
            return Try.failure(new BusinessException(
                String.format("Resource already exists at directory path: %s", scopedPath)));
        }

        if (hasMetadataUnderDirectory(scope, normalizedDirectory)) {
            return Try.success(null);
        }

        String markerPath = buildDirectoryMarkerPath(scope, normalizedDirectory);
        if (getVcsManager().getFile(markerPath).exists()) {
            return Try.success(null);
        }

        return getVcsManager().batchOperation()
            .create(markerPath, "{}")
            .write()
            .map(ignore -> null);
    }

    @Override
    public Try<?> getStatus(String fullName) {
        List<VCSFile> managedFiles = getManagedFiles(fullName);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("lockState", mergeLockState(managedFiles).name());
        status.put("versionState", mergeVersionState(managedFiles).name());
        status.put("lockOwner", managedFiles.stream()
            .map(VCSFile::getLockOwner)
            .filter(StringUtils::isNotBlank)
            .findFirst()
            .orElse(null));
        return Try.success(status);
    }

    @Override
    public Try<Boolean> commit(String fullName, String commitMsg) {
        return doCommit(fullName, buildManagedFilePaths(fullName), commitMsg);
    }

    @Override
    public Try<Boolean> rollback(String fullName) {
        List<VCSFile> managedFiles = getManagedFiles(fullName);
        boolean rolledBack = false;
        for (VCSFile file : managedFiles) {
            Try<Boolean> rollbackResult = file.rollback(false);
            if (rollbackResult.isFailure()) {
                String errorMsg = String.format("回滚失败: %s", rollbackResult.getCause().getMessage());
                return Try.failure(new BusinessException(-1, errorMsg));
            }
            rolledBack = rolledBack || rollbackResult.get();
        }
        for (VCSFile file : managedFiles) {
            file.unlock(false);
        }

        if (rolledBack) {
            ResourceModel model = getOne(fullName).orElse(null);
            publishEvent(new UpdateModelEvent(this, getAppName(), model));
        }
        return Try.success(true);
    }

    @Override
    public List<com.ouroboros.vcs.versioning.ChangeLog> getAllChangeLogs(String fullName) {
        return getVcsManager().getChangeLogs(buildManagedFilePaths(fullName));
    }

    @Override
    public List<com.ouroboros.vcs.versioning.ChangeLog> getChangeLogs(String fullName, int skip, int limit) {
        return getVcsManager().getChangeLogs(buildManagedFilePaths(fullName), skip, limit);
    }

    /**
     * Upload resource file content
     * This is a special method for uploading actual resource file content
     *
     * @param fullName resource path
     * @param content file content
     * @return result
     */
    public Try<Void> uploadResourceFile(String fullName, InputStream content) {
        return uploadResourceFile(fullName, content, null, null);
    }

    public Try<Void> uploadResourceFile(String fullName,
                                        InputStream content,
                                        String contentType,
                                        String originalFileName) {
        String normalizedFullName = ResourcePathUtils.normalize(fullName);
        if (StringUtils.isBlank(normalizedFullName)) {
            return Try.failure(new BusinessException("Resource path cannot be empty"));
        }

        ResourceModel model = getOne(normalizedFullName).orElse(null);
        if (model == null) {
            return Try.failure(new BusinessException(
                    String.format("Resource metadata not found: %s. Please create metadata first.", normalizedFullName)));
        }

        byte[] bytes;
        try {
            bytes = readBytes(content);
        } catch (Exception e) {
            return Try.failure(new BusinessException(
                    String.format("Failed to read resource file: %s", e.getMessage())));
        }

        String actualFilePath = buildActualResourceFilePath(normalizedFullName);
        VCSFile actualFile = getVcsManager().getFile(actualFilePath);
        ResourceModel persistedModel = applyContentMetadata(model, normalizedFullName, bytes, contentType, originalFileName);

        return doUpdate(normalizedFullName, persistedModel, Collections.singletonList(batchAction -> {
            if (actualFile.exists()) {
                batchAction.update(actualFilePath, bytes);
            } else {
                batchAction.create(actualFilePath, bytes);
            }
        })).map(ignore -> null);
    }

    public Try<Void> deleteResourceFile(String fullName) {
        String normalizedFullName = ResourcePathUtils.normalize(fullName);
        if (StringUtils.isBlank(normalizedFullName)) {
            return Try.failure(new BusinessException("Resource path cannot be empty"));
        }

        ResourceModel model = getOne(normalizedFullName).orElse(null);
        if (model == null) {
            return Try.failure(new BusinessException(
                    String.format("Resource metadata not found: %s", normalizedFullName)));
        }

        var vcsManager = getVcsManager();
        String actualFilePath = buildActualResourceFilePath(normalizedFullName);
        ResourceModel persistedModel = model
            .setSize(null)
            .setChecksum(null)
            .setContentType(null);

        return doUpdate(normalizedFullName, persistedModel, Collections.singletonList(batchAction -> {
            if (vcsManager.getFile(actualFilePath).exists()) {
                batchAction.remove(actualFilePath);
            }
        })).map(ignore -> null);
    }

    /**
     * Download resource file content
     *
     * @param fullName resource path
     * @return file content
     */
    public Try<byte[]> downloadResourceFile(String fullName) {
        var vcsManager = getVcsManager();
        var actualFilePath = buildActualResourceFilePath(ResourcePathUtils.normalize(fullName));
        var actualFile = vcsManager.getFile(actualFilePath);

        if (!actualFile.exists()) {
            return Try.failure(new BusinessException(
                    String.format("Resource file not found: %s", fullName)));
        }

        try {
            byte[] content = actualFile.getBytesArray();
            return Try.success(content);
        } catch (Exception e) {
            return Try.failure(new BusinessException(
                    String.format("Failed to download resource file: %s", e.getMessage())));
        }
    }

    private ResourceModel applyContentMetadata(ResourceModel model,
                                               String fullName,
                                               byte[] bytes,
                                               String contentType,
                                               String originalFileName) {
        String key = ResourcePathUtils.toResourceKey(fullName);
        String fileName = ResourcePathUtils.getFileName(key);
        if (StringUtils.isBlank(fileName)) {
            fileName = StringUtils.defaultIfBlank(originalFileName, model.getFileName());
        }

        return model
            .setScope(resolveScope(fullName))
            .setKey(key)
            .setFileName(fileName)
            .setContentType(StringUtils.defaultIfBlank(contentType, model.getContentType()))
            .setSize((long) bytes.length)
            .setChecksum(calculateChecksum(bytes));
    }

    private ResourceScope resolveScope(String fullName) {
        String normalizedFullName = ResourcePathUtils.normalize(fullName);
        int slashIndex = normalizedFullName.indexOf('/');
        String scopeValue = slashIndex < 0 ? normalizedFullName : normalizedFullName.substring(0, slashIndex);
        return ResourceScope.fromValue(scopeValue);
    }

    private String normalizeDirectory(Object directory) {
        if (directory == null) {
            return "";
        }
        String normalized = ResourcePathUtils.normalize(directory.toString());
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        if (ResourcePathUtils.isFrontendPath(normalized) || ResourcePathUtils.isBackendPath(normalized)) {
            return ResourcePathUtils.toResourceKey(normalized);
        }
        return normalized;
    }

    private boolean hasMetadataUnderDirectory(ResourceScope scope, String directory) {
        String relativePath = buildScopedDirectoryPath(scope, directory);
        return getVcsManager().walkDirectory(relativePath, MODEL_FILE_EXTENSION).findAny().isPresent();
    }

    private List<String> buildManagedFilePaths(String fullName) {
        String normalizedFullName = ResourcePathUtils.normalize(fullName);
        Set<String> managedPaths = new TreeSet<>();
        managedPaths.add(buildActualResourceFilePath(normalizedFullName));
        managedPaths.add(buildModelFilePath(normalizedFullName));
        return new ArrayList<>(managedPaths);
    }

    private List<VCSFile> getManagedFiles(String fullName) {
        return buildManagedFilePaths(fullName).stream()
            .map(path -> getVcsManager().getFile(path))
            .collect(Collectors.toList());
    }

    private com.ouroboros.vcs.versioning.VersionState mergeVersionState(List<VCSFile> managedFiles) {
        if (managedFiles.stream().map(VCSFile::getVersionState)
            .anyMatch(state -> state == com.ouroboros.vcs.versioning.VersionState.UNKNOWN)) {
            return com.ouroboros.vcs.versioning.VersionState.UNKNOWN;
        }
        if (managedFiles.stream().map(VCSFile::getVersionState)
            .anyMatch(state -> state == com.ouroboros.vcs.versioning.VersionState.CHANGED)) {
            return com.ouroboros.vcs.versioning.VersionState.CHANGED;
        }
        if (managedFiles.stream().map(VCSFile::getVersionState)
            .anyMatch(state -> state == com.ouroboros.vcs.versioning.VersionState.UNVERSIONED)) {
            return com.ouroboros.vcs.versioning.VersionState.UNVERSIONED;
        }
        return com.ouroboros.vcs.versioning.VersionState.NOTCHANGED;
    }

    private com.ouroboros.vcs.lock.LockState mergeLockState(List<VCSFile> managedFiles) {
        if (managedFiles.stream().map(VCSFile::getLockState)
            .anyMatch(state -> state == com.ouroboros.vcs.lock.LockState.LOCKED)) {
            return com.ouroboros.vcs.lock.LockState.LOCKED;
        }
        if (managedFiles.stream().map(VCSFile::getLockState)
            .anyMatch(state -> state == com.ouroboros.vcs.lock.LockState.OWNED)) {
            return com.ouroboros.vcs.lock.LockState.OWNED;
        }
        return com.ouroboros.vcs.lock.LockState.UNLOCKED;
    }

    private List<String> listDirectoryMarkers(ResourceScope scope) {
        String relativePath = buildScopedDirectoryPath(scope, "");
        return getVcsManager().walkDirectory(relativePath, DIRECTORY_MARKER_FILE_NAME)
            .map(VCSFile::getPath)
            .map(this::toDirectoryFromMarkerPath)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
    }

    private String toDirectoryFromMarkerPath(String markerPath) {
        String normalizedPath = ResourcePathUtils.normalize(markerPath.replace('\\', '/'));
        String basePath = ResourcePathUtils.normalize(getModelFileDirectory()) + "/";
        if (!normalizedPath.startsWith(basePath)) {
            return "";
        }
        String scopedPath = normalizedPath.substring(basePath.length());
        String directoryWithScope = ResourcePathUtils.getDirectory(scopedPath);
        return normalizeDirectory(ResourcePathUtils.toResourceKey(directoryWithScope));
    }

    private String buildDirectoryMarkerPath(ResourceScope scope, String directory) {
        return Paths.get(buildScopedDirectoryPath(scope, directory), DIRECTORY_MARKER_FILE_NAME).toString();
    }

    private String buildScopedDirectoryPath(ResourceScope scope, String directory) {
        if (StringUtils.isBlank(directory)) {
            return Paths.get(getModelFileDirectory(), scope.getValue()).toString();
        }
        return Paths.get(getModelFileDirectory(), ResourcePathUtils.toScopedResourcePath(scope, directory)).toString();
    }

    private void collectDirectories(String directory, Set<String> directories) {
        String current = normalizeDirectory(directory);
        while (StringUtils.isNotBlank(current)) {
            directories.add(current);
            current = extractDirectory(current);
        }
    }

    private List<TreeSelectOption> buildDirectoryTree(Set<String> directories) {
        if (directories.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> sortedDirectories = new ArrayList<>(directories);
        Collections.sort(sortedDirectories);
        Map<String, TreeSelectOption> nodes = new LinkedHashMap<>();
        List<TreeSelectOption> roots = new ArrayList<>();
        for (String directory : sortedDirectories) {
            TreeSelectOption node = new TreeSelectOption()
                .setValue(directory)
                .setLabel(lastSegment(directory))
                .addExtraProp("icon", "fa fa-folder");
            nodes.put(directory, node);

            String parent = extractDirectory(directory);
            if (StringUtils.isBlank(parent)) {
                roots.add(node);
                continue;
            }

            TreeSelectOption parentNode = nodes.get(parent);
            if (parentNode == null) {
                roots.add(node);
                continue;
            }
            parentNode.addChild(node);
        }
        return roots;
    }

    private String extractDirectory(String path) {
        if (StringUtils.isBlank(path)) {
            return "";
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return "";
        }
        return path.substring(0, lastSlash);
    }

    private String lastSegment(String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return path;
        }
        return path.substring(lastSlash + 1);
    }

    private byte[] readBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) >= 0) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    private String calculateChecksum(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return "sha256:" + hexString;
        } catch (Exception e) {
            return null;
        }
    }
}
