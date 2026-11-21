package com.ouroboros.data.dict.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.ouroboros.data.dict.service.model.DataDictItemModel;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;

/**
 * @author liansz
 */
public interface DataDictItemService {

  /**
   * 添加字典项
   *
   * @param model
   * @return
   */
  default DataDictItemModel add(DataDictItemModel model) throws OuroborosException {
    var result = add(Collections.singletonList(model));
    if (result.isEmpty()) {
      throw new InternalException("添加字典项失败");
    }
    return result.get(0);
  }

  /**
   * 添加字典项
   *
   * @param models
   * @return
   */
  List<DataDictItemModel> add(List<DataDictItemModel> models) throws OuroborosException;

  /**
   * 根据字典删除所有字典项
   *
   * @param dict
   * @return
   */
  Long deleteByDict(String dict) throws OuroborosException;

  /**
   * 根据字典编码删除字典项
   *
   * @param dict
   * @param code
   * @return
   */
  default Boolean deleteByDictCode(String dict, String code) throws OuroborosException {
    var result = deleteByDictCodes(dict, Collections.singletonList(code));
    return result > 0;
  }

  /**
   * 根据字典编码删除字典项
   *
   * @param dict
   * @param codes
   * @return
   */
  Long deleteByDictCodes(String dict, Collection<String> codes) throws OuroborosException;

  /**
   * 按字典删除所有字典项（物理删除）
   *
   * @param dict
   * @return
   */
  Long dropByDict(String dict) throws OuroborosException;

  /**
   * 根据字典编码删除字典项（物理删除）
   *
   * @param dict
   * @return
   */
  default Boolean dropByDictCode(String dict, String code) throws OuroborosException {
    var result = dropByDictCodes(dict, Collections.singletonList(code));
    return result > 0;
  }

  /**
   * 根据字典编码删除字典项（物理删除）
   *
   * @param dict
   * @return
   */
  Long dropByDictCodes(String dict, Collection<String> codes);

  /**
   * 更新字典项
   *
   * @param dict
   * @param code
   * @param model
   * @return
   */
  Boolean update(String dict, String code, DataDictItemModel model) throws OuroborosException;

  /**
   * 按字典编码查询字典项
   *
   * @param dict
   * @param code
   * @return
   */
  Optional<DataDictItemModel> getByDictCode(String dict, String code) throws OuroborosException;

  /**
   * 按字典查询字典项
   *
   * @param dict
   * @return
   */
  List<DataDictItemModel> searchListByDict(String dict) throws OuroborosException;

  /**
   * 查询所有字典项
   *
   * @return
   */
  List<DataDictItemModel> searchAll() throws OuroborosException;
}
