package com.ouroboros.uimodel.dev.microweb.service.impl;

import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.uimodel.dev.microweb.model.LowCodeMicroWebModel;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.upload.UploadStorageService;
import com.ouroboros.vcs.action.VCSAction;

@Service
@DevelopModule("micro-web")
@SuppressWarnings("unused")
public class MicroWebDevService extends BaseDevelopService<LowCodeMicroWebModel> {
  final static private String MICRO_WEB_MODEL_PATH = "micro-web";
  final static private String MICRO_WEB_PACKAGE_PATH = "package";
  final static private String MICRO_WEB_MODEL_FILE_EXTENSION = ".micro-web.json";
  final static private String LEGACY_MICRO_WEB_MODEL_FILE_EXTENSION = ".json";
  @Autowired
  @Qualifier("devAppUploadStorageService")
  UploadStorageService uploadStorageService;

  @Override
  protected String getModelDisplayName() {
    return "微前端";
  }

  @Override
  protected String getModelFileDirectory() {
    return MICRO_WEB_MODEL_PATH;
  }

  @Override
  protected String getModelFileExtension() {
    return MICRO_WEB_MODEL_FILE_EXTENSION;
  }

  @Override
  protected List<String> getLegacyModelFileExtensions() {
    return Collections.singletonList(LEGACY_MICRO_WEB_MODEL_FILE_EXTENSION);
  }

  @Override
  protected String getFullName(LowCodeMicroWebModel model) {
    return model.getName();
  }

  @Override
  protected Set<String> getAssociatedFiles(LowCodeMicroWebModel model) {
    var packagePath = getPackagePath(model.getName());
    var packageFile = getVcsManager().getFile(packagePath);
    if (packageFile.exists()) {
      return Collections.singleton(packagePath);
    }
    return Collections.emptySet();
  }

  @Override
  protected Map<String, Consumer<VCSAction.BatchAction>> getAssociatedFilesForUpdate(String fullName, LowCodeMicroWebModel model, LowCodeMicroWebModel oldModel) {
    var packagePath = getPackagePath(fullName);
    var packageFile = getVcsManager().getFile(packagePath);
    var map = new HashMap<String, Consumer<VCSAction.BatchAction>>();
    if (StringUtils.isBlank(model.getPackagePath()) && packageFile.exists()) {
      map.put(packagePath, (action) -> action.remove(packagePath));
    }
    return map;
  }


  @Override
  public Try<LowCodeMicroWebModel> create(LowCodeMicroWebModel model, String commitMsg) {
    var tempPackageName = model.getPackagePath();
    var moveResult = movePackageFile(tempPackageName, model.getName())
        .peek(moved -> model.setPackagePath(moved ? model.getName() + ".zip" : null));
    if (moveResult.isFailure()) {
      return Try.failure(moveResult.getCause());
    }

    return super.create(model, commitMsg);
  }

  @Override
  public Try<LowCodeMicroWebModel> update(String name, LowCodeMicroWebModel model) {
    var moveResult = movePackageFile(model.getPackagePath(), name)
        .peek(moved -> model.setPackagePath(moved ? name + ".zip" : null));
    if (moveResult.isFailure()) {
      return Try.failure(moveResult.getCause());
    }
    model.setName(name);
    return super.update(name, model);
  }

  @Override
  public Try<Boolean> commit(String fullName, String commitMsg) {
    var files = new ArrayList<String>();
    files.add(getPackagePath(fullName));

    return doCommit(fullName, files, commitMsg);
  }

  @Override
  protected Predicate<LowCodeMicroWebModel> buildSearchFilter(Map<String, Object> searchParam) {
    var type = (String) searchParam.get("type");
    if (ObjectUtils.isEmpty(type)) {
      return m -> true;
    }
    return model -> Optional.ofNullable(model.getType())
        .map(t -> t.getValue().equalsIgnoreCase(type))
        .orElse(false);
  }

  private Try<Boolean> movePackageFile(String tempPackageName, String modelName) {
    var packageName = modelName + ".zip";
    var vcsManager = getVcsManager();
    var packagePath = getPackagePath(modelName);
    var packageFile = vcsManager.getFile(packagePath);

    // 如果没有更新包文件，则表示没有更新包文件
    if (StringUtils.isBlank(tempPackageName)) {
      return Try.success(false);
    }

    // 如果包名相同，则表示没有更新包文件
    if (packageName.equals(tempPackageName)) {
      return Try.success(true);
    }

    // 获取临时文件内容
    var fileContent = uploadStorageService.getTempFileContent(tempPackageName)
        .mapLeft(e -> {
          if (e instanceof OuroborosException oe) {
            return oe;
          }
          return new InternalException("微前端组件包文件不存在，请重新上传");
        });
    if (fileContent.isLeft()) {
      return Try.failure(fileContent.getLeft());
    }

    var commitMessage = packageFile.exists()
        ? String.format("更新微前端包(%s", modelName)
        : String.format("初始上传微前端包(%s)", modelName);

    var writeResult = packageFile.write(fileContent.get());
    if (writeResult.isFailure()) {
      return Try.failure(writeResult.getCause());
    }

    packageFile.commit(commitMessage);
    packageFile.unlock(true);

    return Try.success(true);
  }

  private String getPackagePath(String name) {
    return Paths.get(getModelFileDirectory(), MICRO_WEB_PACKAGE_PATH, name + ".zip").toString();
  }
}
