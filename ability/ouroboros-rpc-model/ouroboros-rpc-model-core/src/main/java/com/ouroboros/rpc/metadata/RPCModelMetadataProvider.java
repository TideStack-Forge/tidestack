package com.ouroboros.rpc.metadata;

import java.util.List;

/**
 * @author liansz
 **/
public interface RPCModelMetadataProvider {

  /**
   * 获取所有Rpc模型元数据
   *
   * @return
   */
  List<RPCModelMetadata> getAllRpcModelMetadata();
}
