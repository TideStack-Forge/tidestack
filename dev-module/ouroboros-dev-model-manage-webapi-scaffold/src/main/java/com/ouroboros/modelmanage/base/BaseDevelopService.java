package com.ouroboros.modelmanage.base;

import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import io.vavr.control.Try;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.dev.event.CreateModelEvent;
import com.ouroboros.dev.event.DeleteModelEvent;
import com.ouroboros.dev.event.UpdateModelEvent;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.modelmanage.PreparedModelCreate;
import com.ouroboros.modelmanage.web.vo.SingleFileStatus;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.exception.VCSPostCommitException;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

/**
 * @author liansz
 */
public abstract class BaseDevelopService<M> implements DevelopService<M> {

  @Autowired
  protected AppHomeResolver appHomeResolver;

  @Autowired
  protected VCSManagerCenter vcsManagerCenter;

  @Autowired
  protected ApplicationEventPublisher publisher;


  TypeReference<M> getModelTypeReference() {
    return null;
  }

  /**
   * 获取模型类
   */
  protected Class<M> getModelClass() {
    Class<?> currentClass = getClass();
    while (currentClass != null && currentClass != Object.class) {
      Type genericSuperclass = currentClass.getGenericSuperclass();
      if (genericSuperclass instanceof ParameterizedType) {
        Type[] actualTypeArguments = ((ParameterizedType) genericSuperclass).getActualTypeArguments();
        if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class) {
          return (Class<M>) actualTypeArguments[0];
        }
      }
      currentClass = currentClass.getSuperclass();
    }
    return null;
  }

  /**
   * 获取模块的home路径
   */
  protected abstract String getModelFileDirectory();

  /**
   * 获取模型文件扩展名
   */
  protected String getModelFileExtension() {
    return ".json";
  }

  /**
   * 获取历史模型文件扩展名，用于平滑迁移到类型化模型后缀。
   */
  protected List<String> getLegacyModelFileExtensions() {
    return Collections.emptyList();
  }

  protected List<String> getModelFileExtensionsForRead() {
    var extensions = new ArrayList<String>();
    addModelFileExtension(extensions, getModelFileExtension());
    getLegacyModelFileExtensions().forEach(extension -> addModelFileExtension(extensions, extension));
    return extensions;
  }

  private void addModelFileExtension(List<String> extensions, String extension) {
    if (StringUtils.isNotBlank(extension) && !extensions.contains(extension)) {
      extensions.add(extension);
    }
  }

  protected String buildModelFilePath(String fullName) {
    return Paths.get(getModelFileDirectory(), fullName + getModelFileExtension()).toString();
  }

  protected List<String> buildModelFilePathsForRead(String fullName) {
    var paths = new ArrayList<String>();
    paths.add(buildModelFilePath(fullName));
    getLegacyModelFileExtensions().stream()
        .map(extension -> Paths.get(getModelFileDirectory(), fullName + extension).toString())
        .filter(path -> !paths.contains(path))
        .forEach(paths::add);
    return paths;
  }

  protected VCSFile getExistingOrPrimaryModelVCSFile(String fullName) {
    var vcsManager = getVcsManager();
    var modelFilePaths = buildModelFilePathsForRead(fullName);
    return modelFilePaths.stream()
        .map(vcsManager::getFile)
        .filter(VCSFile::exists)
        .findFirst()
        .orElseGet(() -> vcsManager.getFile(modelFilePaths.get(0)));
  }

  protected List<VCSFile> getExistingModelVCSFiles(String fullName) {
    var vcsManager = getVcsManager();
    return buildModelFilePathsForRead(fullName).stream()
        .map(vcsManager::getFile)
        .filter(VCSFile::exists)
        .collect(Collectors.toList());
  }

  protected Map<String, Supplier<InputStream>> getAssociatedFilesForCreate(String fullName, M model) {
    return Collections.emptyMap();
  }

  protected Set<String> getAssociatedFiles(M model) {
    return Collections.emptySet();
  }

  protected Map<String, Consumer<VCSAction.BatchAction>> getAssociatedFilesForUpdate(String fullName, M model, M oldModel) {
    return Collections.emptyMap();
  }

  @Override
  public Try<M> buildModelFromFileContent(String fileContent) {
    var modelTypeReference = getModelTypeReference();
    if (modelTypeReference != null) {
      return Json.tryToBean(modelTypeReference, fileContent);
    }

    var modelClass = getModelClass();
    if (modelClass != null) {
      return Json.tryToBean(modelClass, fileContent);
    }

    return Try.failure(new BusinessException(-1, "Json转换为模型元数据对象失败，未找到模型元数据的类型信息"));
  }

  @Override
  public Try<M> buildModelFromRequestBody(String requestBody) {
    return buildModelFromFileContent(requestBody);
  }

  public final VCSManager getVcsManager() {
    return vcsManagerCenter.getVcsManager(getAppName());
  }


  /**
   * 获取应用id
   *
   * @return
   */
  protected final String getAppName() {
    return DevAppHolder.getAppName();
  }

  /**
   * 获取应用根目录
   */
  protected final String getAppHomePath() {
    return appHomeResolver.getAppHomeByAppId(getAppName());
  }

  /**
   * 是否在更新模型时提交
   *
   * @return
   */
  protected boolean isAutoCommitWhenCreate() {
    return true;
  }

  protected boolean isAutoCommitWhenUpdate() {
    return true;
  }

  /**
   * 获取模型显示名称（用于生成提交信息）
   *
   * @return
   */
  abstract protected String getModelDisplayName();

  protected Try<Void> validateModel(M model) {
    return Try.success(null);
  }

  protected Try<String> getPermanentJson(M model) {
    return Json.tryToPrettyJsonString(model);
  }

  @Override
  public Try<PreparedModelCreate> prepareCreate(M model) {
    return prepareCreateResources(model);
  }

  private Try<PreparedModelCreate> prepareCreateResources(M model) {
    return validateModel(model).flatMap(ignore -> getPermanentJson(model)).flatMap(content -> Try.of(() -> {
      var fullName = getFullName(model);
      var vcsManager = getVcsManager();
      var primaryPath = buildModelFilePath(fullName);
      var contents = new LinkedHashMap<String, byte[]>();
      contents.put(primaryPath, content.getBytes(StandardCharsets.UTF_8));
      for (var associated : getAssociatedFilesForCreate(fullName, model).entrySet()) {
        if (contents.containsKey(associated.getKey())) {
          throw new BusinessException("关联模型资源路径重复: " + associated.getKey());
        }
        try (var stream = associated.getValue().get()) {
          contents.put(associated.getKey(), stream.readAllBytes());
        }
      }
      var collisionPaths = new LinkedHashSet<>(buildModelFilePathsForRead(fullName));
      collisionPaths.addAll(contents.keySet());
      for (String path : collisionPaths) {
        if (vcsManager.getFile(path).exists()) {
          throw new BusinessException("准备创建的模型资源已存在: " + path);
        }
      }
      return new PreparedModelCreate(primaryPath, content, contents, List.copyOf(collisionPaths), vcsManager);
    }));
  }

  @Override
  public Try<M> create(M m, String commitMsg) {
    return prepareCreateResources(m).flatMap(prepared -> {
      var fullName = getFullName(m);
      var result = Try.of(() -> {
        var batch = getVcsManager().batchOperation();
        prepared.appendTo(batch);
        return batch;
      }).flatMap(batch -> {
        if (isAutoCommitWhenCreate()) {
          var message = StringUtils.isBlank(commitMsg) ? getDefaultCreateMessage(fullName) : commitMsg;
          return batch.autoLock().unlockWhenCommit().commit(message);
        }
        return writePreparedCreate(prepared, batch);
      });
      if (result.isSuccess() || result.getCause() instanceof VCSPostCommitException) {
        publishEvent(new CreateModelEvent(this, getAppName(), m));
      }
      if (result.isSuccess()) {
        return Try.success(m);
      }
      var message = String.format("创建%s (%s) 失败: %s", getModelDisplayName(), fullName, result.getCause().getMessage());
      return Try.failure(new BusinessException(-1, message, result.getCause()));
    });
  }

  private Try<Boolean> writePreparedCreate(PreparedModelCreate prepared, VCSAction.BatchAction batch) {
    var manager = getVcsManager();
    var acquiredPaths = prepared.getPaths().stream()
        .filter(path -> StringUtils.isBlank(manager.getFile(path).getLockOwner()))
        .toList();
    return manager.lock(prepared.getPaths(), false).flatMap(ignore -> {
      var result = batch.write(false);
      if (result.isFailure() && prepared.getPaths().stream().noneMatch(path -> manager.getFile(path).exists())) {
        var cleanup = manager.unlock(acquiredPaths, false);
        if (cleanup.isFailure()) {
          result.getCause().addSuppressed(cleanup.getCause());
        }
      }
      return result;
    });
  }

  @Override
  public Optional<M> getOne(String fullName) {
    var modelFile = getExistingOrPrimaryModelVCSFile(fullName);

    if (!modelFile.exists()) {
      return Optional.empty();
    }

    var bean = buildModelFromFileContent(modelFile.getContent());
    return bean.toJavaOptional();
  }

  @Override
  public Try<?> getInitFormData(String fullName) {
    var modelFile = getModelVCSFile(fullName);
    var model = buildModelFromFileContent(modelFile.getContent());
    return model;
  }

  @Override
  public Try<?> getStatus(String fullName) {
    var file = getModelVCSFile(fullName);
    var status = new SingleFileStatus(file);
    return Try.success(status);
  }

  @Override
  public Try<M> delete(String fullName, String commitMsg) {
    var vcsManager = getVcsManager();
    var existingModelFiles = getExistingModelVCSFiles(fullName);
    var modelFile = existingModelFiles.stream()
        .findFirst()
        .orElseGet(() -> getExistingOrPrimaryModelVCSFile(fullName));

    if (!modelFile.exists()) {
      return Try.success(null);
    }

    var model = buildModelFromFileContent(modelFile.getContent()).getOrElse((M) null);
    var associatedFiles = model == null ? Collections.<String>emptySet() : getAssociatedFiles(model);

    var finalMsg = StringUtils.isBlank(commitMsg) ? getDefaultDeleteMessage(fullName) : commitMsg;
    var batchOperation = vcsManager
        .batchOperation()
        .autoLock()
        .unlockWhenCommit()
        .remove(existingModelFiles.stream().map(VCSFile::getPath).collect(Collectors.toList()))
        .remove(associatedFiles);

    var deleteResult = batchOperation.commit(finalMsg);

    if (deleteResult.isSuccess()) {
      var event = new DeleteModelEvent(this, getAppName(), model);
      publishEvent(event);
      return Try.success(model);
    }

    var errorMsg = String.format("删除%s失败: %s", getModelDisplayName(), deleteResult.getCause().getMessage());
    return Try.failure(new BusinessException(-1, errorMsg));
  }

  @Override
  public Try<M> update(String fullName, M model) {
    var modelFile = getExistingOrPrimaryModelVCSFile(fullName);
    var oldModel = buildModelFromFileContent(modelFile.getContent()).getOrElse((M) null);

    var associatedFiles = getAssociatedFilesForUpdate(fullName, model, oldModel);
    return doUpdate(fullName, model, associatedFiles.values());
  }

  protected Try<M> doUpdate(String fullName, M model, Collection<Consumer<VCSAction.BatchAction>> additionalOperations) {
    return writeUpdate(fullName, model, additionalOperations, batch -> isAutoCommitWhenUpdate()
        ? batch.commit(getDefaultUpdateMessage(fullName)) : batch.write(false));
  }

  protected Try<M> doUpdateAndCommit(String fullName, M model,
      Collection<Consumer<VCSAction.BatchAction>> additionalOperations, String commitMsg) {
    String message = StringUtils.isBlank(commitMsg) ? getDefaultUpdateMessage(fullName) : commitMsg;
    return writeUpdate(fullName, model, additionalOperations, batch -> batch.autoLock().unlockWhenCommit().commit(message));
  }

  private Try<M> writeUpdate(String fullName, M model, Collection<Consumer<VCSAction.BatchAction>> additionalOperations,
      Function<VCSAction.BatchAction, Try<Boolean>> executor) {
    var checkResult = validateModel(model);
    if (checkResult.isFailure()) {
      return Try.failure(checkResult.getCause());
    }

    var vcsManager = getVcsManager();
    var modelFile = getExistingOrPrimaryModelVCSFile(fullName);
    var modelFilePath = modelFile.getPath();
    var newFullName = getFullName(model);
    var newModelFilePath = StringUtils.equals(fullName, newFullName)
        ? modelFilePath
        : buildModelFilePath(newFullName);

    if (!modelFile.exists()) {
      var errorMsg = String.format("%s文件不存在: %s", getModelDisplayName(), fullName);
      return Try.failure(new BusinessException(-1, errorMsg));
    }

    if (!StringUtils.equals(fullName, newFullName)) {
      var targetFiles = buildModelFilePathsForRead(newFullName).stream()
          .map(vcsManager::getFile)
          .filter(VCSFile::exists)
          .filter(file -> !StringUtils.equals(file.getPath(), modelFilePath))
          .collect(Collectors.toList());
      if (!targetFiles.isEmpty()) {
        var errorMsg = String.format("%s文件已存在: %s", getModelDisplayName(), newFullName);
        return Try.failure(new BusinessException(-1, errorMsg));
      }
    }

    var newModelContent = getPermanentJson(model);
    if (newModelContent.isFailure()) {
      var errorMsg = String.format("更新%s失败, 序列化模型失败: %s", getModelDisplayName(), newModelContent.getCause().getMessage());
      return Try.failure(new BusinessException(-1, errorMsg));
    }

    var batchOperation = vcsManager.batchOperation();
    if (StringUtils.equals(modelFilePath, newModelFilePath)) {
      batchOperation.update(modelFilePath, newModelContent.get());
    } else {
      batchOperation.move(modelFilePath, newModelFilePath, ignore -> newModelContent.get());
    }
    var executeResult = Try.run(() -> additionalOperations.forEach(c -> c.accept(batchOperation)))
        .flatMap(ignore -> executor.apply(batchOperation));

    if (executeResult.isSuccess() || executeResult.getCause() instanceof VCSPostCommitException) {
      var event = new UpdateModelEvent(this, getAppName(), model);
      publishEvent(event);
    }
    if (executeResult.isSuccess()) {
      return Try.success(model);
    }

    var errorMsg = String.format("更新%s失败: %s", getModelDisplayName(), executeResult.getCause().getMessage());
    return Try.failure(new BusinessException(-1, errorMsg, executeResult.getCause()));
  }

  @Override
  public List<?> search(Map<String, Object> searchParam) {
    var vcsManager = getVcsManager();
    var searchResultMapper = buildSearchResultMapper();
    var modelFiles = getModelFileExtensionsForRead().stream()
        .flatMap(extension -> vcsManager.walkDirectory(getModelFileDirectory(), extension))
        .collect(() -> new LinkedHashMap<String, VCSFile>(),
            (map, file) -> map.putIfAbsent(file.getPath().toLowerCase(Locale.ROOT), file), LinkedHashMap::putAll)
        .values()
        .stream()
        .map(file -> new AbstractMap.SimpleImmutableEntry<>(file, searchResultMapper.apply(file)))
        .filter(entry -> entry.getValue().isSuccess())
        .map(entry -> new AbstractMap.SimpleImmutableEntry<>(
            buildSearchResultDedupKey(entry.getValue().get(), entry.getKey()),
            entry.getValue().get()))
        .collect(() -> new TreeMap<String, Object>(String::compareToIgnoreCase),
            (map, entry) -> map.putIfAbsent(entry.getKey(), entry.getValue()), TreeMap::putAll)
        .values();

    Predicate filter = buildSearchFilter(searchParam);
    Predicate<Object> wrappedFilter = model -> {
      var result = Try.of(() -> filter.test(model));
      return result.getOrElse(false);
    };

    return modelFiles.stream()
        .filter(wrappedFilter)
        .collect(Collectors.toList());
  }

  private String buildSearchResultDedupKey(Object searchResult, VCSFile file) {
    var model = searchResult instanceof ModelFileMetaWrapper<?> wrapper ? wrapper.getModel() : searchResult;
    var modelClass = getModelClass();
    if (modelClass != null && modelClass.isInstance(model)) {
      return StringUtils.defaultIfBlank(getFullName(modelClass.cast(model)), file.getPath());
    }
    return file.getPath();
  }

  protected Predicate<?> buildSearchFilter(Map<String, Object> searchParam) {
    return ignore -> true;
  }

  protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
    return file -> buildModelFromFileContent(file.getContent());
  }

  @Override
  public Try<Boolean> commit(String fullName, String commitMsg) {
    var files = Collections.singleton(getExistingOrPrimaryModelVCSFile(fullName).getPath());
    return doCommit(fullName, files, commitMsg);
  }

  protected Try<Boolean> doCommit(String fullName, Collection<String> files, String commitMsg) {
    var modelFilePath = getModelVCSFile(fullName).getPath();
    var primaryModelFilePath = buildModelFilePath(fullName);
    var batchOperation = getVcsManager()
        .batchOperation()
        .unlockWhenCommit();

    batchOperation.add(modelFilePath);
    files.stream()
        .filter(path -> modelFilePath.equals(primaryModelFilePath) || !primaryModelFilePath.equals(path))
        .map(getVcsManager()::getFile)
        .forEach(f -> {
          if (f.exists()) {
            batchOperation.add(f.getPath());
          } else {
            batchOperation.remove(f.getPath());
          }
        });

    var msg = StringUtils.isBlank(commitMsg)
        ? getDefaultUpdateMessage(fullName)
        : commitMsg;
    var commitResult = batchOperation
        .commit(msg);
    if (commitResult.isSuccess()) {
      return Try.success(true);
    }

    var errorMsg = String.format("提交失败: %s", commitResult.getCause().getMessage());
    return Try.failure(new BusinessException(-1, errorMsg, commitResult.getCause()));
  }

  @Override
  public Try<Boolean> rollback(String fullName) {
    var modelFile = getExistingOrPrimaryModelVCSFile(fullName);

    var rollbackResult = modelFile
        .rollback(false)
        .andThen(() -> modelFile.unlock());

    if (rollbackResult.isSuccess()) {
      if (rollbackResult.get()) {
        var model = buildModelFromFileContent(modelFile.getContent()).getOrElse((M) null);
        var event = new UpdateModelEvent(this, getAppName(), model);
        publishEvent(event);
      }
      return Try.success(true);
    }

    var errorMsg = String.format("回滚失败: %s", rollbackResult.getCause().getMessage());
    return Try.failure(new BusinessException(-1, errorMsg));
  }

  @Override
  public Try<Void> lock(String fullName, boolean force) {
    return lock(fullName, force, null, null);
  }

  @Override
  public Try<Void> lock(String fullName, boolean force, M oldModel, M newModel) {
    var vcsManager = getVcsManager();
    Set<String> allFiles = new HashSet<>();
    var modelFilePath = getExistingOrPrimaryModelVCSFile(fullName).getPath();
    var oldModelFiles = oldModel == null ? Collections.<String>emptySet() : getAssociatedFiles(oldModel);
    var newModelFiles = newModel == null ? Collections.<String>emptySet() : getAssociatedFiles(newModel);

    allFiles.add(modelFilePath);
    allFiles.addAll(oldModelFiles);
    allFiles.addAll(newModelFiles);

    var lockResult = vcsManager.lock(allFiles, force);
    if (lockResult.isSuccess()) {
      return lockResult;
    }

    var errorMsg = String.format("锁定失败: %s", lockResult.getCause().getMessage());
    return Try.failure(new BusinessException(-1, errorMsg));
  }

  @Override
  public Try<Void> unlock(String fullName, boolean force) {
    return unlock(fullName, force, null, null);
  }

  @Override
  public Try<Void> unlock(String fullName, boolean force, M oldModel, M newModel) {
    var vcsManager = getVcsManager();
    Set<String> allFiles = new HashSet<>();
    var modelFilePath = getExistingOrPrimaryModelVCSFile(fullName).getPath();
    var oldModelFiles = oldModel == null ? Collections.<String>emptySet() : getAssociatedFiles(oldModel);
    var newModelFiles = newModel == null ? Collections.<String>emptySet() : getAssociatedFiles(newModel);

    allFiles.add(modelFilePath);
    allFiles.addAll(oldModelFiles);
    allFiles.addAll(newModelFiles);

    var unlockResult = vcsManager.unlock(allFiles, force);
    if (unlockResult.isSuccess()) {
      return unlockResult;
    }

    var errorMsg = String.format("解锁失败: %s", unlockResult.getCause().getMessage());
    return Try.failure(new BusinessException(-1, errorMsg));
  }

  @Override
  public List<ChangeLog> getAllChangeLogs(String fullName) {
    var vcsManager = getVcsManager();
    var modelFilePath = getExistingOrPrimaryModelVCSFile(fullName).getPath();
    return vcsManager.getChangeLogs(modelFilePath);
  }

  @Override
  public List<ChangeLog> getChangeLogs(String fullName, int skip, int limit) {
    var vcsManager = getVcsManager();
    var modelFilePath = getExistingOrPrimaryModelVCSFile(fullName).getPath();
    return vcsManager.getChangeLogs(modelFilePath, skip, limit);
  }

  public VCSFile getModelVCSFile(String fullName) {
    return getExistingOrPrimaryModelVCSFile(fullName);
  }

  protected String getDefaultDeleteMessage(String fullName) {
    var template = "删除%s (%s)";
    return String.format(template, getModelDisplayName(), fullName);
  }

  protected String getDefaultUpdateMessage(String fullName) {
    var template = "更新%s (%s)";
    return String.format(template, getModelDisplayName(), fullName);
  }

  protected String getDefaultCreateMessage(String fullName) {
    var template = "创建%s (%s)";
    return String.format(template, getModelDisplayName(), fullName);
  }

  abstract protected String getFullName(M model);

  protected void publishEvent(EventObject event) {
    publisher.publishEvent(event);
  }
}
