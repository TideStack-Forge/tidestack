package com.ouroboros.modelmanage.web;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/dev/{appName}/{moduleName}")
public class ModelDevelopController<M> {

  private final Map<String, DevelopService<?>> servicesMap;

  public ModelDevelopController(@Autowired(required = false) List<DevelopService<?>> services) {
    this.servicesMap = buildServicesMap(services == null ? Collections.emptyList() : services);
  }

  private Map<String, DevelopService<?>> buildServicesMap(List<DevelopService<?>> services) {
    Map<String, DevelopService<?>> map = new TreeMap<>(String::compareToIgnoreCase);
    for (DevelopService<?> service : services) {
      DevelopModule annotation = service.getClass().getAnnotation(DevelopModule.class);
      if (annotation != null) {
        map.put(annotation.value(), service);
      }
    }
    return map;
  }

  @PostMapping
  public WebApiResult<?> create(@PathVariable("appName") String appName,
                                @PathVariable("moduleName") String moduleName,
                                @RequestBody String modelString,
                                @RequestParam(name = "autoUnlock", defaultValue = "false") Boolean autoUnlock,
                                @RequestParam(name = "commitMsg", defaultValue = "") String commitMsg,
                                @RequestParam(name = "includeValidationSnapshot", defaultValue = "false") Boolean includeValidationSnapshot) {
    var service = getService(moduleName);

    // 准备提交数据
    var model = service.buildModelFromRequestBody(modelString);
    if (model.isFailure()) {
      return WebApiResult.fail("提交数据格式错误");
    }

    // 创建
    var createResult = service.create(model.get(), commitMsg, includeValidationSnapshot);
    if (createResult.isFailure()) {
      return WebApiResult.of(createResult.getCause());
    }

    // 解锁
    if (autoUnlock) {
      service.unlock(moduleName, false);
    }

    // 返回结果
    return WebApiResult.ok(createResult.get());
  }

  @GetMapping("/{fullName}")
  public WebApiResult<?> get(@PathVariable("moduleName") String moduleName,
                             @PathVariable("fullName") String fullName,
                             @RequestParam(name = "includeValidationSnapshot", defaultValue = "false") Boolean includeValidationSnapshot) {
    return WebApiResult.of(getService(moduleName).getOne(fullName, includeValidationSnapshot));
  }

  @GetMapping("/{fullName}/init-form")
  public WebApiResult<?> getInitForm(@PathVariable("moduleName") String moduleName,
                                     @PathVariable("fullName") String fullName,
                                     @RequestParam(name = "ignoreError", defaultValue = "false") Boolean ignoreError,
                                     @RequestParam(name = "lock", defaultValue = "false") Boolean lock,
                                     @RequestParam(name = "ignoreLockError", defaultValue = "true") Boolean ignoreLockError,
                                     @RequestParam(name = "includeValidationSnapshot", defaultValue = "false") Boolean includeValidationSnapshot) {
    var service = getService(moduleName);


    // 获取初始化数据
    var model = service.getInitFormData(fullName, includeValidationSnapshot);

    // 上锁
    var lockResult = lock && model.isSuccess() ? service.lock(fullName, false) : Try.success(null);

    if (!ignoreLockError && lockResult.isFailure()) {
      return WebApiResult.fail("锁定失败");
    }
    if (ignoreError && model.isFailure()) {
      return WebApiResult.ok(Collections.emptyMap());
    }

    return WebApiResult.of(model);
  }

  @GetMapping("/{fullName}/status")
  public WebApiResult<?> getStatus(@PathVariable("moduleName") String moduleName, @PathVariable("fullName") String fullName) {
    var service = getService(moduleName);
    var status = service.getStatus(fullName);
    return WebApiResult.of(status);
  }

  @GetMapping(value = "/{fullName}/change-log")
  public WebApiResult<List<ChangeLog>> getChangeLogs(@PathVariable("moduleName") String moduleName,
                                                     @PathVariable("fullName") String fullName,
                                                     @RequestParam(name = "page", required = false, defaultValue = "-1") int page,
                                                     @RequestParam(name = "perPage", required = false, defaultValue = "10") int perPage) {
    if (page == -1) {
      var allChangeLogs = getService(moduleName).getAllChangeLogs(fullName);
      return WebApiResult.ok(allChangeLogs);
    } else {
      var changeLogs = getService(moduleName).getChangeLogs(fullName, page, perPage);
      return WebApiResult.ok(changeLogs);
    }
  }

  @PutMapping("/{fullName}")
  public WebApiResult<?> update(@PathVariable("appName") String appName,
                                @PathVariable("moduleName") String moduleName,
                                @PathVariable("fullName") String fullName,
                                @RequestParam(name = "commit", defaultValue = "false") Boolean commit,
                                @RequestParam(name = "commitMsg", defaultValue = "") String commitMsg,
                                @RequestParam(name = "autoUnlock", defaultValue = "true") Boolean autoUnlock,
                                @RequestParam(name = "includeValidationSnapshot", defaultValue = "false") Boolean includeValidationSnapshot,
                                @RequestBody String modelString) {
    var service = getService(moduleName);

    // 构建提交数据
    var model = service.buildModelFromRequestBody(modelString);
    if (model.isFailure()) {
      return WebApiResult.fail("提交数据格式错误");
    }

    // 锁定
    var lockResult = service.lock(fullName, false);
    if (lockResult.isFailure()) {
      return WebApiResult.fail("锁定失败");
    }

    // 更新
    var updateResult = service.update(fullName, model.get(), includeValidationSnapshot);
    if (updateResult.isFailure()) {
      return WebApiResult.of(updateResult);
    }

    // 提交（若自动提交）
    if (commit) {
      var commitResult = service.commit(fullName, commitMsg);
      if (commitResult.isFailure()) {
        return WebApiResult.of(Try.<Boolean>failure(commitResult.getCause()));
      }
    }

    // 解锁（若自动解锁）
    if (commit && autoUnlock) {
      var unlockResult = service.unlock(fullName, false);
      if (unlockResult.isFailure()) {
        return WebApiResult.of(Try.<Boolean>failure(unlockResult.getCause()));
      }
    }

    return WebApiResult.ok(updateResult.get());
  }

  @DeleteMapping("/{fullName}")
  public WebApiResult<Void> delete(@PathVariable("appName") String appName,
                                   @PathVariable("moduleName") String moduleName,
                                   @PathVariable("fullName") String fullName,
                                   @RequestParam(name = "force", defaultValue = "false") Boolean force,
                                   @RequestParam(name = "autoUnlock", defaultValue = "true") Boolean autoUnlock,
                                   @RequestParam(name = "commitMsg", defaultValue = "") String commitMsg) {
    var service = getService(moduleName);

    // 上锁
    var lockResult = service.lock(fullName, force);
    if (lockResult.isFailure()) {
      return WebApiResult.fail("锁定失败");
    }

    // 删除
    var deleteResult = service.delete(fullName, commitMsg);

    // 解锁
    if (autoUnlock) {
      service.unlock(fullName, false);
    }

    return WebApiResult.of(deleteResult.map(m -> null));
  }

  @PostMapping("/{fullName}/lock")
  public WebApiResult<Void> lock(@PathVariable("moduleName") String moduleName,
                                 @PathVariable("fullName") String fullName,
                                 @RequestParam(name = "force", defaultValue = "false") Boolean force) {
    var service = getService(moduleName);
    var lockResult = service.lock(fullName, force);
    return WebApiResult.of(lockResult);
  }

  @PostMapping("/{fullName}/release-lock")
  public WebApiResult<Void> releaseLock(@PathVariable("moduleName") String moduleName,
                                        @PathVariable("fullName") String fullName,
                                        @RequestParam(name = "force", defaultValue = "false") Boolean force) {
    var service = getService(moduleName);
    return WebApiResult.of(service.unlock(fullName, force));
  }

  @PostMapping("/{fullName}/rollback")
  public WebApiResult<Boolean> rollback(@PathVariable("appName") String appName,
                                        @PathVariable("moduleName") String moduleName,
                                        @PathVariable("fullName") String fullName,
                                        @RequestParam(name = "force", defaultValue = "false") Boolean force,
                                        @RequestParam(name = "autoUnlock", defaultValue = "true") Boolean autoUnlock) {
    var service = getService(moduleName);

    // 上锁
    var lockResult = service.lock(fullName, force);
    if (lockResult.isFailure()) {
      return WebApiResult.fail("锁定失败");
    }

    // 回滚
    var rollbackResult = service.rollback(fullName);

    // 解锁
    if (autoUnlock) {
      service.unlock(fullName, false);
    }

    if (rollbackResult.isFailure()) {
      return WebApiResult.fail("回滚失败");
    }
    return WebApiResult.of(rollbackResult);
  }

  @PostMapping("/{fullName}/commit")
  public WebApiResult<Boolean> commit(@PathVariable("moduleName") String moduleName,
                                      @PathVariable("fullName") String fullName,
                                      @RequestParam(name = "autoUnlock", defaultValue = "true") Boolean autoUnlock,
                                      @RequestParam(name = "commitMsg", defaultValue = "") String commitMsg,
                                      @RequestBody Map<String, Object> requestParam) {
    // 提交信息
    var finalMsg = commitMsg.isEmpty() ? (String) requestParam.get("commitMsg") : commitMsg;
    var service = getService(moduleName);

    // 锁定
    var lockResult = service.lock(fullName, false);
    if (lockResult.isFailure()) {
      return WebApiResult.fail("相关资源已被其他用户锁定");
    }

    // 提交
    var commitResult = service.commit(fullName, finalMsg);
    if (commitResult.isFailure()) {
      return WebApiResult.of(Try.<Boolean>failure(commitResult.getCause()));
    }

    // 解锁
    if (autoUnlock) {
      var unlockResult = service.unlock(fullName, false);
      if (unlockResult.isFailure()) {
        return WebApiResult.of(Try.<Boolean>failure(unlockResult.getCause()));
      }
    }
    return WebApiResult.of(commitResult);
  }

  @GetMapping("")
  public WebApiResult<List<?>> search(@PathVariable("moduleName") String moduleName,
                                      @RequestParam Map<String, Object> requestParam,
                                      @RequestParam(name = "includeValidationSnapshot", defaultValue = "false") Boolean includeValidationSnapshot) {
    var searchResult = getService(moduleName).search(requestParam, includeValidationSnapshot);
    return WebApiResult.ok(searchResult);
  }

  /**
   * 获取服务
   */
  private DevelopService<M> getService(String moduleName) {
    DevelopService<?> service = servicesMap.get(moduleName);
    if (service == null) {
      throw new PlatformException("未找到服务: " + moduleName);
    }
    return (DevelopService<M>) service;
  }
}
