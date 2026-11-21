package com.ouroboros.rpc.dev.service.impl;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.rpc.metadata.RPCModelMetadata;

/**
 * @author liansz
 */
@Service
@DevelopModule("rpc-model")
@SuppressWarnings("unused")
public class RpcMetadataDevelopServiceImpl extends BaseDevelopService<RPCModelMetadata> {

  private static final String METADATA_FILE_HOME = "rpc-model";
  private static final String MODEL_FILE_SUFFIX = ".rpc.json";

  @Override
  protected String getModelDisplayName() {
    return "RPC模型";
  }

  @Override
  protected String getModelFileDirectory() {
    return METADATA_FILE_HOME;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_SUFFIX;
  }

  @Override
  protected String getFullName(RPCModelMetadata model) {
    return model.getName();
  }

  @Override
  protected boolean isAutoCommitWhenCreate() {
    return false;
  }

  @Override
  protected boolean isAutoCommitWhenUpdate() {
    return false;
  }

  @Override
  public Try<Boolean> commit(String fullName, String commitMsg) {
    var message = commitMsg == null ? "" : commitMsg.strip();
    if (message.isEmpty() || message.length() > 200) {
      return Try.failure(new BusinessException("提交信息须为 1～200 个字符"));
    }
    return super.commit(fullName, message);
  }

  /**
   * 检查基本信息
   *
   * @param metadata 元数据
   */
  @Override
  protected Try<Void> validateModel(RPCModelMetadata metadata) {
    if (ObjectUtils.isEmpty(metadata)) {
      return Try.failure(new BusinessException("元数据不能为空！"));
    }

    if (ObjectUtils.isEmpty(metadata.getName())) {
      return Try.failure(new BusinessException("名称不能为空！"));
    }

    if (ObjectUtils.isEmpty(metadata.getTitle())) {
      return Try.failure(new BusinessException("标题不能为空！"));
    }

    if (ObjectUtils.isEmpty(metadata.getType())) {
      return Try.failure(new BusinessException("类型不能为空！"));
    }

    if (ObjectUtils.isEmpty(metadata.getRequest())) {
      return Try.failure(new BusinessException("请求定义不能为空！"));
    }

    return Try.success(null);
  }
}
