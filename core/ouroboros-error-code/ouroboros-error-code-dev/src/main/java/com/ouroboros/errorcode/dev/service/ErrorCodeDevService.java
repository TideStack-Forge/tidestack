package com.ouroboros.errorcode.dev.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vavr.control.Try;

import com.ouroboros.errorcode.dev.model.ErrorCodeDefinitionModel;
import com.ouroboros.dev.event.CreateModelEvent;
import com.ouroboros.dev.event.DeleteModelEvent;
import com.ouroboros.dev.event.UpdateModelEvent;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.modelmanage.web.vo.SingleFileStatus;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;
import com.ouroboros.vcs.versioning.ChangeLog;

@Service
@DevelopModule("error-code")
public class ErrorCodeDevService extends BaseDevelopService<ErrorCodeDefinitionModel> {
  static final String MODEL_FILE_EXTENSION = ".error.json";
  static final String DEFAULT_COLLECTION_FILE = "error-codes" + MODEL_FILE_EXTENSION;

  @Override
  protected String getModelFileDirectory() {
    return "";
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
  protected String buildModelFilePath(String fullName) {
    return DEFAULT_COLLECTION_FILE;
  }

  @Override
  protected String getFullName(ErrorCodeDefinitionModel model) {
    return String.valueOf(model.getCode());
  }

  @Override
  protected String getModelDisplayName() {
    return "错误码";
  }

  @Override
  protected Try<Void> validateModel(ErrorCodeDefinitionModel model) {
    if (model == null) {
      return Try.failure(new BusinessException("错误码定义不能为空"));
    }
    if (model.getCode() == null) {
      return Try.failure(new BusinessException("错误码不能为空"));
    }
    if (StringUtils.isBlank(model.getTitle())) {
      return Try.failure(new BusinessException("错误码标题不能为空"));
    }
    if (StringUtils.isBlank(model.getDefaultMessageTemplate())) {
      return Try.failure(new BusinessException("默认错误消息不能为空"));
    }

    var existingModels = listAllModels();
    if (existingModels.isFailure()) {
      return Try.failure(existingModels.getCause());
    }

    var normalizedBizCode = normalize(model.getBizCode());
    if (normalizedBizCode != null && existingModels.get().stream()
        .filter(existing -> existing.getCode() != null)
        .filter(existing -> !existing.getCode().equals(model.getCode()))
        .map(ErrorCodeDefinitionModel::getBizCode)
        .map(ErrorCodeDevService::normalize)
        .anyMatch(normalizedBizCode::equals)) {
      return Try.failure(new BusinessException("业务错误标识已存在: " + normalizedBizCode));
    }

    return Try.success(null);
  }

  @Override
  public Try<ErrorCodeDefinitionModel> create(ErrorCodeDefinitionModel model, String commitMsg) {
    var validation = validateModel(model);
    if (validation.isFailure()) {
      return Try.failure(validation.getCause());
    }
    String fullName = getFullName(model);
    if (getOne(fullName).isPresent()) {
      return Try.failure(new BusinessException(-1, getModelDisplayName() + "文件已存在: " + fullName));
    }

    var file = getDefaultCollectionFile();
    var existingModels = readModels(file);
    if (existingModels.isFailure()) {
      return Try.failure(existingModels.getCause());
    }

    var models = new ArrayList<>(existingModels.get());
    models.add(model);
    sortModels(models);
    return writeModels(file, models)
        .map(ignore -> {
          publishEvent(new CreateModelEvent(this, getAppName(), model));
          return model;
        });
  }

  @Override
  public Try<ErrorCodeDefinitionModel> update(String fullName, ErrorCodeDefinitionModel model) {
    if (model == null || !StringUtils.equals(fullName, getFullName(model))) {
      return Try.failure(new BusinessException("错误码与路径不一致，不能隐式重命名错误码定义"));
    }
    var validation = validateModel(model);
    if (validation.isFailure()) {
      return Try.failure(validation.getCause());
    }

    var file = findFileContaining(fullName).orElseGet(this::getDefaultCollectionFile);
    if (!file.exists()) {
      return Try.failure(new BusinessException(-1, getModelDisplayName() + "文件不存在: " + fullName));
    }

    var existingModels = readModels(file);
    if (existingModels.isFailure()) {
      return Try.failure(existingModels.getCause());
    }

    var models = new ArrayList<>(existingModels.get());
    boolean replaced = false;
    for (int i = 0; i < models.size(); i++) {
      if (StringUtils.equals(fullName, getFullName(models.get(i)))) {
        models.set(i, model);
        replaced = true;
        break;
      }
    }
    if (!replaced) {
      return Try.failure(new BusinessException(-1, getModelDisplayName() + "文件不存在: " + fullName));
    }
    sortModels(models);
    return writeModels(file, models)
        .map(ignore -> {
          publishEvent(new UpdateModelEvent(this, getAppName(), model));
          return model;
        });
  }

  @Override
  public Try<ErrorCodeDefinitionModel> delete(String fullName, String commitMsg) {
    var file = findFileContaining(fullName).orElseGet(this::getDefaultCollectionFile);
    if (!file.exists()) {
      return Try.success(null);
    }

    var existingModels = readModels(file);
    if (existingModels.isFailure()) {
      return Try.failure(existingModels.getCause());
    }

    var models = new ArrayList<>(existingModels.get());
    ErrorCodeDefinitionModel deleted = null;
    for (int i = 0; i < models.size(); i++) {
      if (StringUtils.equals(fullName, getFullName(models.get(i)))) {
        deleted = models.remove(i);
        break;
      }
    }
    if (deleted == null) {
      return Try.success(null);
    }
    ErrorCodeDefinitionModel eventModel = deleted;
    return writeModels(file, models)
        .map(ignore -> {
          publishEvent(new DeleteModelEvent(this, getAppName(), eventModel));
          return eventModel;
        });
  }

  @Override
  public Optional<ErrorCodeDefinitionModel> getOne(String fullName) {
    return loadAllModelWrappers().stream()
        .map(ModelFileMetaWrapper::getModel)
        .filter(model -> StringUtils.equals(fullName, getFullName(model)))
        .findFirst();
  }

  @Override
  public Try<?> getInitFormData(String fullName) {
    return Try.success(getOne(fullName).orElse(null));
  }

  @Override
  public Try<?> getStatus(String fullName) {
    return Try.success(new SingleFileStatus(findFileContaining(fullName).orElseGet(this::getDefaultCollectionFile)));
  }

  @Override
  public Try<Boolean> commit(String fullName, String commitMsg) {
    var file = findFileContaining(fullName).orElseGet(this::getDefaultCollectionFile);
    var msg = StringUtils.isBlank(commitMsg) ? getDefaultUpdateMessage(fullName) : commitMsg;
    return file.commit(msg);
  }

  @Override
  public Try<Boolean> rollback(String fullName) {
    var file = findFileContaining(fullName).orElseGet(this::getDefaultCollectionFile);
    return file.rollback(false)
        .andThen(() -> file.unlock(true));
  }

  @Override
  public Try<Void> lock(String fullName, boolean force) {
    return findFileContaining(fullName).orElseGet(this::getDefaultCollectionFile).lock(force);
  }

  @Override
  public Try<Void> unlock(String fullName, boolean force) {
    return findFileContaining(fullName).orElseGet(this::getDefaultCollectionFile).unlock(force);
  }

  @Override
  public List<ChangeLog> getAllChangeLogs(String fullName) {
    var file = findFileContaining(fullName).orElseGet(this::getDefaultCollectionFile);
    return getVcsManager().getChangeLogs(file.getPath());
  }

  @Override
  public List<ChangeLog> getChangeLogs(String fullName, int page, int size) {
    var file = findFileContaining(fullName).orElseGet(this::getDefaultCollectionFile);
    return getVcsManager().getChangeLogs(file.getPath(), page, size);
  }

  @Override
  public List<?> search(Map<String, Object> searchParam) {
    Predicate<ModelFileMetaWrapper<ErrorCodeDefinitionModel>> filter = buildSearchFilter(searchParam);
    return loadAllModelWrappers().stream()
        .filter(wrapper -> Try.of(() -> filter.test(wrapper)).getOrElse(false))
        .collect(Collectors.toList());
  }

  @Override
  protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
    return file -> buildModelFromFileContent(file.getContent())
        .map(model -> new ModelFileMetaWrapper<>(model, file));
  }

  @Override
  protected Predicate<ModelFileMetaWrapper<ErrorCodeDefinitionModel>> buildSearchFilter(Map<String, Object> searchParam) {
    var keyword = searchParam.getOrDefault("keyword", "").toString().trim().toLowerCase();
    return wrapper -> {
      if (StringUtils.isBlank(keyword)) {
        return true;
      }
      var model = wrapper.getModel();
      return contains(model.getCode(), keyword)
          || contains(model.getTitle(), keyword)
          || contains(model.getBizCode(), keyword)
          || contains(model.getCategory(), keyword)
          || contains(model.getDescription(), keyword)
          || contains(model.getDefaultMessageTemplate(), keyword);
    };
  }

  private Try<List<ErrorCodeDefinitionModel>> listAllModels() {
    return readModels(getDefaultCollectionFile());
  }

  private List<ErrorCodeDefinitionModel> listReadableModels() {
    return listAllModels().getOrElse(Collections::emptyList);
  }

  private List<ModelFileMetaWrapper<ErrorCodeDefinitionModel>> loadAllModelWrappers() {
    var file = getDefaultCollectionFile();
    return listReadableModels().stream()
        .map(model -> new ModelFileMetaWrapper<>(model, file))
        .collect(Collectors.toList());
  }

  private Optional<VCSFile> findFileContaining(String fullName) {
    var file = getDefaultCollectionFile();
    return readModels(file)
        .map(models -> models.stream().anyMatch(model -> StringUtils.equals(fullName, getFullName(model))) ? file : null)
        .toJavaOptional();
  }

  private VCSFile getDefaultCollectionFile() {
    return getVcsManager().getFile(DEFAULT_COLLECTION_FILE);
  }

  private Try<List<ErrorCodeDefinitionModel>> readModels(VCSFile file) {
    if (file == null || !file.exists() || StringUtils.isBlank(file.getContent())) {
      return Try.success(Collections.emptyList());
    }
    var parsed = Json.tryToBean(new TypeReference<List<ErrorCodeDefinitionModel>>() {
    }, file.getContent());
    if (parsed.isFailure()) {
      return Try.failure(new BusinessException("错误码集合文件格式错误: " + file.getPath(), parsed.getCause()));
    }
    return Try.success(Optional.ofNullable(parsed.get()).orElseGet(Collections::emptyList));
  }

  private Try<Void> writeModels(VCSFile file, List<ErrorCodeDefinitionModel> models) {
    return Json.tryToPrettyJsonString(models)
        .flatMap(content -> {
          if (file.exists()) {
            return file.write(content).map(ignore -> null);
          }
          return getVcsManager().batchOperation()
              .autoLock()
              .create(file.getPath(), content)
              .write(false)
              .map(ignore -> null);
        });
  }

  private void sortModels(List<ErrorCodeDefinitionModel> models) {
    models.sort(Comparator.comparing(ErrorCodeDefinitionModel::getCode, Comparator.nullsLast(Integer::compareTo)));
  }

  private static boolean contains(Object value, String keyword) {
    if (value == null) {
      return false;
    }
    return value.toString().toLowerCase().contains(keyword);
  }

  private static String normalize(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    return value.trim();
  }
}
