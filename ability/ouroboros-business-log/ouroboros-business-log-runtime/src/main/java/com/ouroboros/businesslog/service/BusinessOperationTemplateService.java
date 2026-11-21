package com.ouroboros.businesslog.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ouroboros.businesslog.model.BusinessOperationTemplateModel;

/**
 * @author liansz
 **/
public interface BusinessOperationTemplateService {

  /**
   * 添加
   *
   * @param model
   * @return
   */
  BusinessOperationTemplateModel add(BusinessOperationTemplateModel model);

  /**
   * 添加多个
   *
   * @param models
   * @return
   */
  List<BusinessOperationTemplateModel> add(Collection<BusinessOperationTemplateModel> models);

  /**
   * 删除
   *
   * @param id
   * @return
   */
  Boolean delete(String id);

  /**
   * 更新
   *
   * @param model
   * @return
   */
  Boolean update(String id, BusinessOperationTemplateModel model);

  /**
   * 通过ID获取
   *
   * @param id
   * @return
   */
  Optional<BusinessOperationTemplateModel> getById(String id);

  /**
   * 通过请求方法和URL获取
   *
   * @param httpMethod 请求方法
   * @param url URL
   * @return 模板
   */
  Optional<BusinessOperationTemplateModel> getByHttpMethodAndUrl(String httpMethod, String url);

  /**
   * 查询全部
   *
   * @return
   */
  List<BusinessOperationTemplateModel> searchAll();
}
