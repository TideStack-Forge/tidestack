package com.ouroboros.businesslog.service;

import java.util.Optional;

import com.ouroboros.businesslog.model.BusinessOperationTemplateModel;
import com.ouroboros.businesslog.service.model.BusinessOperationTemplateSourceView;
import com.ouroboros.web.PagedResult;

/**
 * 业务日志模板合并视图查询服务。
 */
public interface BusinessOperationTemplateQueryService {

  PagedResult<BusinessOperationTemplateSourceView> search(String keyword,
                                                          String source,
                                                          String httpMethod,
                                                          String moduleName,
                                                          int page,
                                                          int perPage);

  Optional<BusinessOperationTemplateSourceView> getDetail(String templateKey);

  BusinessOperationTemplateModel buildOverrideFromSource(String templateKey);
}
