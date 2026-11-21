package com.ouroboros.uischema.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.uischema.model.CustomUISchema;

/**
 * @author liansz
 **/
public interface CustomUISchemaService {

  /**
   * 添加
   *
   * @param schema
   * @return
   */
  default CustomUISchema add(CustomUISchema schema, String operatedBy) throws OuroborosException {
    var result = add(Collections.singletonList(schema), operatedBy);
    if (result.isEmpty()) {
      throw new InternalException("添加自定义UI Schema错误!");
    }
    return result.get(0);
  }

  /**
   * 添加多个
   *
   * @param schemas
   * @return
   */
  List<CustomUISchema> add(Collection<CustomUISchema> schemas, String operatedBy) throws OuroborosException;

  /**
   * 删除
   *
   * @param id
   * @return
   */
  Boolean delete(String id, String operatedBy) throws OuroborosException;

  /**
   * 更新
   *
   * @param schema
   * @return
   */
  Boolean update(String id, CustomUISchema schema, String operatedBy) throws OuroborosException;

  /**
   * 通过ID获取
   *
   * @param id
   * @return
   */
  CustomUISchema getById(String id) throws OuroborosException;

  /**
   * 查询全部
   *
   * @return
   */
  List<CustomUISchema> searchAll() throws OuroborosException;
}
