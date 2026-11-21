package com.ouroboros.rpc.dev.service.impl;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

/**
 * @author liansz
 */
@Service
@DevelopModule("rpc-auth")
@SuppressWarnings("unused")
public class RpcAuthCredentialMetadataDevelopServiceImpl extends BaseDevelopService<RPCCredentialMetadata> {

  private static final String METADATA_FILE_HOME = "rpc-model/auth";
  private static final String MODEL_FILE_SUFFIX = ".rpc.auth.json";

  @Override
  protected String getModelDisplayName() {
    return "RPC认证模型";
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
  protected String getFullName(RPCCredentialMetadata model) {
    return model.getName();
  }

  /**
   * 检查基本信息
   *
   * @param metadata 元数据
   */
  @Override
  protected Try<Void> validateModel(RPCCredentialMetadata metadata) {
    if (ObjectUtils.isEmpty(metadata)) {
      return Try.failure(new BusinessException("数据不能为空！"));
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

    return com.ouroboros.rpc.security.RPCAuthCredentialCenter.validate(metadata);
  }
}
