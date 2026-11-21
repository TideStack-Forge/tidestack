package com.ouroboros.data.dict;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;

public interface DataDictService {

  /**
   * 添加字典
   *
   * @param dict
   * @return
   */
  default DataDict add(DataDict dict) throws OuroborosException {
    var result = add(Collections.singletonList(dict));
    if (result.isEmpty()) {
      throw new InternalException("添加数据字典错误!");
    }
    return result.get(0);
  }

  /**
   * 添加字典
   *
   * @param dictionaries
   * @return
   */
  List<? extends DataDict> add(Collection<? extends DataDict> dictionaries) throws OuroborosException;

  /**
   * 删除字典
   *
   * @param name
   * @return
   */
  Boolean delete(String name) throws OuroborosException;

  /**
   * 字典详情
   *
   * @param name
   * @return
   */
  Optional<DataDict> detail(String name);

  /**
   * 返回所有字典
   *
   * @return
   */
  List<? extends DataDict> searchAll();

  /**
   * 添加字典项
   *
   * @param dictName
   * @param item
   * @return
   */
  default DataDictItem addItem(String dictName, DataDictItem item) throws OuroborosException {
    var result = addItems(dictName, Collections.singleton(item));
    if (result.isEmpty()) {
      throw new InternalException("添加字典项错误!");
    }
    return result.get(0);
  }

  /**
   * 添加字典项列表（遇到已存在的会跳过）
   *
   * @param dictName
   * @param items
   * @return
   */
  List<? extends DataDictItem> addItems(String dictName, Collection<? extends DataDictItem> items) throws OuroborosException;

  /**
   * 删除字典项
   *
   * @param dictName
   * @param code
   * @return
   */
  default Boolean deleteItem(String dictName, String code) throws OuroborosException {
    return deleteItems(dictName, Collections.singleton(code)) > 0;
  }

  /**
   * 删除字典项列表
   *
   * @param dictName
   * @param codes
   * @return
   */
  Long deleteItems(String dictName, Collection<String> codes) throws OuroborosException;

  /**
   * 修改字典项
   *
   * @param dictName
   * @param code
   * @param item
   * @return
   */
  Boolean updateItem(String dictName, String code, DataDictItem item) throws OuroborosException;

  /**
   * 根据code，获取字典项
   *
   * @param dictName
   * @param code
   * @return
   */
  Optional<DataDictItem> getItemByCode(String dictName, String code);

  /**
   * 返回字典的所有item，如果树形的就返回树形结构
   *
   * @param dictName
   * @return
   */
  List<? extends DataDictItem> searchListItemByDictName(String dictName);
}
