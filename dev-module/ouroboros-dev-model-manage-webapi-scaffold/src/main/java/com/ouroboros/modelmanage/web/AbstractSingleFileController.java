package com.ouroboros.modelmanage.web;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.modelmanage.web.vo.SingleFileStatus;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.web.WebApiResult;

/**
 * 单文件控制器
 *
 * @param <T> 提交数据模型，提交的数据里可能会有一些状态位等信息，因此要单列
 * @param <F> 初始化表单数据模型，初始化的表单里可能会有一些状态位等信息，因此要单列
 */
public abstract class AbstractSingleFileController<T, F> {
  @Autowired
  VCSManagerCenter vcsManagerCenter;

  /**
   * 获取单文件路径，如 app.metadata.json
   *
   * @return 单文件路径
   */
  abstract protected String getSingleFilePath();

  /**
   * 构建初始化表单数据，有些时候不能直接把文件内容反序列化后给到前端，需要做一些加工，因此这里必须要单独实现。
   *
   * @param file VcsFile 文件
   * @return 发送给前端的表单数据
   */
  abstract protected Try<F> getInitFormData(VCSFile file);

  /**
   * 前端提交过来的数据，如何反序列化成类型化的数据
   *
   * @param jsonString json字符串
   * @return 提交数据模型
   */
  abstract protected Try<T> getSubmitModelFromJson(String jsonString);

  /**
   * 类型化的数据进行二次加工，如：去掉一些不必要的字段，或者对某些字段进行加密等，生成存储的json字符串
   *
   * @param model 提交数据模型
   * @return 用于存储的json字符串
   */
  protected Try<String> getPermanentJson(T model) {
    return Json.tryToPrettyJsonString(model);
  }

  /**
   * 获取默认的提交信息，当用户没有填写提交信息时，使用这个默认的提交信息
   *
   * @return 默认的提交信息
   */
  protected String getDefaultCommitMsg() {
    return "<空>";
  }

  /**
   * 获取初始化表单数据
   *
   * @param appName 应用名，从路径变量中获取
   *                \
   */
  @GetMapping("/init-form")
  public WebApiResult<F> initForm(@PathVariable("appName") String appName,
                                  @RequestParam(defaultValue = "false") Boolean lock,
                                  @RequestParam(defaultValue = "true") Boolean ignoreLockState) {
    if (lock) {
      var lockResult = getSingleFile(appName).lock(false);
      if (lockResult.isFailure() && !ignoreLockState) {
        return WebApiResult.fail("锁定文件失败");
      }
    }
    var file = getSingleFile(appName);
    return WebApiResult.of(getInitFormData(file));
  }

  /**
   * 获取当前文件的状态
   *
   * @param appName 应用名，从路径变量中获取
   */
  @GetMapping("/status")
  public WebApiResult<SingleFileStatus> getStatus(@PathVariable("appName") String appName) {
    var file = getSingleFile(appName);
    var status = new SingleFileStatus(file);
    return WebApiResult.ok(status);
  }

  /**
   * 获取锁
   *
   * @param appName 应用名，从路径变量中获取
   * @param force   是否强制获取锁
   */
  @PostMapping("/lock")
  public WebApiResult<Void> takeLock(@PathVariable("appName") String appName,
                                     @RequestParam(defaultValue = "false") Boolean force) {
    var file = getSingleFile(appName);
    var lockResult = file.lock(force);
    return WebApiResult.of(lockResult.map(ignore -> null));
  }

  /**
   * 释放锁
   *
   * @param appName 应用名，从路径变量中获取
   * @param force   是否强制释放锁
   */
  @PostMapping("/release-lock")
  public WebApiResult<Void> releaseLock(@PathVariable("appName") String appName,
                                        @RequestParam(defaultValue = "false") Boolean force) {
    var file = getSingleFile(appName);
    var releaseResult = file.unlock(force);
    return WebApiResult.of(releaseResult);
  }

  /**
   * 提交（只做提交动作，不更新文件）
   *
   * @param appName      应用名，从路径变量中获取
   * @param force        是否强制提交
   * @param requestParam 提交信息
   */
  @PostMapping("/commit")
  public WebApiResult<Void> commit(@PathVariable("appName") String appName,
                                   @RequestParam(defaultValue = "false") Boolean force,
                                   @RequestParam(defaultValue = "true") Boolean unlock,
                                   @RequestBody Map<String, Object> requestParam) {
    var vcsManager = getVcsManager(appName);
    var vcsOperation = vcsManager.batchOperation();
    var commitFiles = getCommitAddFiles();
    var commitRemoveFiles = getCommitRemoveFiles();
    var msg = (String) requestParam.getOrDefault("commitMsg", getDefaultCommitMsg());

    if (force) {
      var lockResult = vcsManager.lock(commitFiles, true);
      if (lockResult.isFailure()) {
        return WebApiResult.fail("锁定文件失败");
      }
    }
    if (unlock) {
      vcsOperation.unlockWhenCommit();
    }

    var commitResult = vcsOperation
        .add(commitFiles)
        .remove(commitRemoveFiles)
        .autoLock()
        .commit(msg);

    Try<Void> result = commitResult
        .map(ignore -> null);

    return WebApiResult.of(result);
  }

  /**
   * 回滚
   *
   * @param appName 应用名，从路径变量中获取
   * @param force   是否强制回滚
   */
  @PostMapping("/rollback")
  public WebApiResult<Void> rollback(@PathVariable("appName") String appName,
                                     @RequestParam(defaultValue = "false") Boolean force,
                                     @RequestParam(defaultValue = "true") Boolean unlock) {
    var file = getSingleFile(appName);
    var rollbackResult = file.rollback(force);
    var result = rollbackResult
        .peek(ignore -> {
          if (unlock) {
            file.unlock(true);
          }
        })
        .<Void>map(ignore -> null);
    return WebApiResult.of(result);
  }

  /**
   * 保存（默认只保存，不提交）
   *
   * @param appName   应用名，从路径变量中获取
   * @param commit    是否同时提交
   * @param commitMsg 提交信息
   * @param content   保存内容
   */
  @PutMapping
  @PatchMapping
  public WebApiResult<?> save(@PathVariable("appName") String appName,
                              @RequestParam(defaultValue = "false") Boolean commit,
                              @RequestParam(defaultValue = "true") Boolean unlock,
                              @RequestParam(defaultValue = "") String commitMsg,
                              @RequestBody String content) {
    var file = getSingleFile(appName);

    var model = getSubmitModelFromJson(content);
    if (model.isFailure()) {
      return WebApiResult.ok();
    }

    var jsonString = getPermanentJson(model.get());
    if (jsonString.isFailure()) {
      return WebApiResult.fail(jsonString.getCause().getMessage());
    }

    var writeResult = file.write(jsonString.get());
    if (writeResult.isFailure()) {
      return WebApiResult.fail("写入文件失败");
    }

    if (commit) {
      var msg = commitMsg.isEmpty() ? getDefaultCommitMsg() : commitMsg;
      var commitResult = file.commit(msg)
          .peek(ignore -> {
            if (unlock) {
              file.unlock(true);
            }
          });
      if (commitResult.isFailure()) {
        return WebApiResult.fail("提交失败");
      }
    }
    if (returnDataWhenSaved()) {
      return WebApiResult.of(getInitFormData(getSingleFile()));
    }
    return WebApiResult.ok();
  }

  protected VCSManager getVcsManager(String appName) {
    return vcsManagerCenter.getVcsManager(appName);
  }

  protected VCSManager getVcsManager() {
    return getVcsManager(DevAppHolder.getAppName());
  }

  protected VCSFile getSingleFile(String appName) {
    return getVcsManager(appName).getFile(getSingleFilePath());
  }

  protected VCSFile getSingleFile() {
    return getSingleFile(DevAppHolder.getAppName());
  }

  protected List<String> getCommitAddFiles() {
    return Collections.singletonList(getSingleFilePath());
  }

  protected List<String> getCommitRemoveFiles() {
    return Collections.emptyList();
  }

  protected boolean returnDataWhenSaved() {
    return false;
  }

}
