package com.ouroboros.modelmanage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.exception.BusinessException;

/**
 * @author liansz
 */
public interface DevelopService<M> {

  /**
   * 锁定
   *
   * @param fullName
   * @return
   */
  Try<Void> lock(String fullName, boolean force);

  Try<Void> lock(String fullName, boolean force, M oldModel, M newModel);

  /**
   * 释放锁定
   *
   * @param fullName
   * @param force
   * @return
   */
  Try<Void> unlock(String fullName, boolean force);

  Try<Void> unlock(String fullNames, boolean force, M oldModel, M newModel);

  /**
   * 创建
   *
   * @param m
   * @return
   */
  Try<M> create(M m, String commitMsg);

  /** Prepare formal resources for an associated update; this call never writes or publishes. */
  default Try<PreparedModelCreate> prepareCreate(M model) {
    return Try.failure(new BusinessException("当前模型开发服务不支持关联资源准备"));
  }

  /** Validate a dependency for publication without changing the editable draft or writing resources. */
  default Try<M> validateForPublication(M model) {
    return Try.failure(new BusinessException("当前模型开发服务不支持发布依赖校验"));
  }

  /**
   * 删除
   *
   * @param fullName
   * @return
   */
  Try<M> delete(String fullName, String commitMsg);


  /**
   * 暂存
   *
   * @param fullName
   * @param m
   * @return
   */
  Try<M> update(String fullName, M m);

  /**
   * 清空暂存区并解锁
   *
   * @param fullName
   * @return
   */
  Try<Boolean> rollback(String fullName);

  /**
   * 提交并解锁
   *
   * @param fullName
   * @param commitMsg
   * @return
   */
  Try<Boolean> commit(String fullName, String commitMsg);

  /**
   * 获取
   *
   * @param fullName
   * @return
   */
  Optional<M> getOne(String fullName);

  default Try<?> getOne(String fullName, boolean includeValidationSnapshot) {
    return Try.success(getOne(fullName).orElse(null));
  }

  Try<?> getInitFormData(String fullName);

  default Try<?> getInitFormData(String fullName, boolean includeValidationSnapshot) {
    return getInitFormData(fullName);
  }

  Try<?> getStatus(String fullName);

  /**
   * 查找模型
   */
  List<?> search(Map<String, Object> searchParam);

  default List<?> search(Map<String, Object> searchParam, boolean includeValidationSnapshot) {
    return search(searchParam);
  }

  List<ChangeLog> getAllChangeLogs(String fullName);

  List<ChangeLog> getChangeLogs(String fullName, int page, int size);

  Try<M> buildModelFromFileContent(String fileContent);

  Try<M> buildModelFromRequestBody(String requestBody);

  default Try<?> create(M m, String commitMsg, boolean includeValidationSnapshot) {
    return create(m, commitMsg);
  }

  default Try<?> update(String fullName, M m, boolean includeValidationSnapshot) {
    return update(fullName, m);
  }
}
