package com.ouroboros.rpc.security.metadata;

import java.util.List;

/**
 * @author liansz
 **/
public interface RPCAuthCredentialMetadataProvider {

  /**
   * 获取所有Rpc认证器
   *
   * @return
   */
  List<RPCCredentialMetadata> getAllRpcAuthCredentialMetadata();
}
