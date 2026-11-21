package com.ouroboros.dynamicform.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.dynamicform.model.DynamicForm;

public interface DynamicFormDefineService {
  /**
   * 根据表单名称获取表单定义
   *
   * @param name
   * @return
   */
  Optional<DynamicForm> getDynamicFormByName(String name);

  /**
   * 根据表单ID获取表单定义
   *
   * @param id
   * @return
   */
  Optional<DynamicForm> getDynamicFormById(Integer id);

  /**
   * 根据条件获取表单定义数量
   *
   * @param where
   * @return
   */
  Long getTotal(Map<String, Object> where);

  /**
   * 根据条件获取表单定义列表
   *
   * @param query
   * @return
   */
  List<DynamicForm> search(Map<String, Object> query);

  /**
   * 根据条件获取表单定义，并转为模型元数据
   *
   * @param where
   * @return
   */
  List<DataModelMeta> getModelMetaList(Map<String, Object> where);

  /**
   * 添加新的动态表单定义
   *
   * @param dynamicForm
   * @return
   */
  Optional<DynamicForm> add(DynamicForm dynamicForm);

  /**
   * 更新动态表单定义
   *
   * @param dynamicForm
   * @return
   */
  Boolean update(DynamicForm dynamicForm);

  /**
   * 删除动态表单定义
   *
   * @param id
   * @return
   */
  Boolean delete(Integer id);

  /**
   * 部署动态表单，生成数据表
   *
   * @param id
   * @return
   */
  Boolean deploy(Integer id);

  /**
   * 下线动态表单，使表单不可用，以便于修改
   *
   * @param id
   * @return
   */
  Boolean takeDown(Integer id);

  /**
   * 重新部署表单
   *
   * @param id
   * @return
   */
  Boolean reDeploy(Integer id);

  /**
   * 归档表单，只能查询，不再允许修改
   *
   * @param id
   * @return
   */
  Boolean archive(Integer id);

  void refreshModels();
}
