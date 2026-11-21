package com.ouroboros.data.dict.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.ouroboros.data.dict.DataDict;
import com.ouroboros.data.dict.service.model.DataDictModel;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;

/**
 * @author liansz
 */
public interface DataDictCoreService {

  /**
   * 添加
   *
   * @param model
   * @return
   */
  default DataDictModel add(DataDictModel model) throws OuroborosException {
    var result = add(Collections.singletonList(model));
    if (result.isEmpty()) {
      throw new InternalException("添加数据字典错误!");
    }
    return result.get(0);
  }

  /**
   * 添加多个
   *
   * @param models
   * @return
   */
  List<DataDictModel> add(Collection<DataDictModel> models) throws OuroborosException;

  /**
   * 删除
   *
   * @param name
   * @return
   */
  default Boolean delete(String name) throws OuroborosException {
    return delete(Collections.singletonList(name));
  }

  /**
   * 删除多个
   *
   * @param names
   * @return
   */
  Boolean delete(Collection<String> names) throws OuroborosException;

  /**
   * 硬删除
   *
   * @param name
   * @return
   */
  Boolean drop(String name) throws OuroborosException;

  /**
   * 更新
   *
   * @param name
   * @param model
   * @return
   */
  Boolean update(String name, DataDictModel model) throws OuroborosException;

  /**
   * 通过主键获取
   *
   * @param name
   * @return
   */
  Optional<? extends DataDict> getByName(String name);

  /**
   * 查询全部
   *
   * @return
   */
  List<? extends DataDict> searchAll() throws OuroborosException;
}
