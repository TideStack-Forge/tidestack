package com.ouroboros.data.dict.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.vavr.control.Try;

import com.ouroboros.data.dict.DataDict;
import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.data.dict.DataDictService;
import com.ouroboros.data.dict.pojo.FlatDataDict;
import com.ouroboros.data.dict.pojo.TreeDataDict;
import com.ouroboros.data.dict.pojo.TreeDataDictItem;
import com.ouroboros.data.dict.service.DataDictCoreService;
import com.ouroboros.data.dict.service.DataDictItemService;
import com.ouroboros.data.dict.service.model.DataDictItemModel;
import com.ouroboros.data.dict.service.model.DataDictModel;
import com.ouroboros.data.dict.util.DataDictItemStructureConvertUtils;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.PlatformException;

/**
 * @author sunquan
 */
@Service
public class DataDictServiceImpl implements DataDictService {

  @Autowired
  private DataDictServiceImpl self;

  @Autowired
  private DataDictCoreService dataDictCoreService;

  @Autowired
  private DataDictItemService dataDictItemService;

  @Override
  public List<? extends DataDict> add(Collection<? extends DataDict> dictionaries) throws OuroborosException {
    var dictNames = dataDictCoreService.searchAll().stream().map(DataDict::getName).collect(Collectors.toSet());
    var existedNames = dictionaries.stream().map(DataDict::getName).filter(dictNames::contains).collect(Collectors.toSet());
    if (ObjectUtils.isNotEmpty(existedNames)) {
      throw new BusinessException(String.format("字典%s已存在", String.join(",", existedNames)));
    }

    // 解决事务问题，树形结构自动拆解为扁平结构
    var result = Try.of(() -> self.doAdd(dictionaries));
    if (result.isFailure()) {
      throw new PlatformException("字典新增失败", result.getCause());
    }
    return result.get();
  }

  @Override
  public Boolean delete(String name) throws OuroborosException {
    Optional<? extends DataDict> modelOptional = dataDictCoreService.getByName(name);
    if (!modelOptional.isPresent()) {
      return false;
    }

    if (!modelOptional.get().isEditable()) {
      throw new BusinessException(String.format("字典%s不允许修改", name));
    }

    // 解决事务问题
    var result = Try.of(() -> self.doDelete(name));
    if (result.isFailure()) {
      throw new PlatformException(String.format("字典%s删除失败", name), result.getCause());
    }
    return result.get();
  }

  @Override
  public Optional<DataDict> detail(String name) {
    return dataDictCoreService.getByName(name).map(dict -> {
      DataDict result = dict;
      result.setItems(searchListItemByDictName(name));
      return result;
    });
  }

  @Override
  public List<? extends DataDict> searchAll() {
    return dataDictCoreService.searchAll().stream().map(dict -> {
      DataDict result = dict;
      result.setItems(searchListItemByDictName(dict.getName()));

      return result;
    }).collect(Collectors.toList());
  }

  @Override
  public List<? extends DataDictItem> addItems(String dictName,
                                               Collection<? extends DataDictItem> items) throws OuroborosException {
    if (ObjectUtils.isEmpty(dictName)) {
      throw new BusinessException("必须指定字典字段");
    }

    List<DataDictItemModel> addModels = DataDictItemStructureConvertUtils.disassembleTree(items).stream()
        .peek(m -> m.setDict(dictName))
        .collect(Collectors.toList());
    if (ObjectUtils.isEmpty(addModels)) {
      return Collections.emptyList();
    }

    var dictOptional = dataDictCoreService.getByName(dictName);
    if (!dictOptional.isPresent()) {
      throw new BusinessException(404, String.format("字典%s不存在", dictName));
    }

    if (!dictOptional.get().isEditable()) {
      throw new BusinessException(String.format("字典%s不允许修改", dictName));
    }

    var currentItems = dataDictItemService.searchListByDict(dictName);
    List<String> existedCodes = addModels.stream()
        .map(DataDictItemModel::getCode)
        .filter(code -> currentItems.stream().anyMatch(i -> Objects.equals(i.getCode(), code)))
        .collect(Collectors.toList());
    if (ObjectUtils.isNotEmpty(existedCodes)) {
      throw new BusinessException(String.format("字典项%s已存在", String.join(",", existedCodes)));
    }

    var models = dataDictItemService.add(addModels);
    return DataDictItemStructureConvertUtils.assembleTree(dictOptional.get().isTreeMode(), models);
  }

  @Override
  public Long deleteItems(String dictName, Collection<String> codes) throws OuroborosException {
    var dictOptional = dataDictCoreService.getByName(dictName);
    if (!dictOptional.isPresent()) {
      throw new BusinessException(404, String.format("字典%s不存在", dictName));
    }

    if (!dictOptional.get().isEditable()) {
      throw new BusinessException(String.format("字典%s不允许修改", dictName));
    }

    return dataDictItemService.deleteByDictCodes(dictName, codes);
  }

  @Override
  public Boolean updateItem(String dictName, String code, DataDictItem item) throws OuroborosException {
    var dictOptional = dataDictCoreService.getByName(dictName);
    if (!dictOptional.isPresent()) {
      throw new BusinessException(404, String.format("字典%s不存在", dictName));
    }

    if (!dictOptional.get().isEditable()) {
      throw new BusinessException(String.format("字典%s不允许修改", dictName));
    }

    var itemOptional = dataDictItemService.getByDictCode(dictName, code);
    if (!itemOptional.isPresent()) {
      throw new BusinessException(404, String.format("字典项%s不存在", item.getCode()));
    }

    // 确保关键字段不会被修改
    DataDictItemModel model = new DataDictItemModel(itemOptional.get());
    if (item instanceof TreeDataDictItem treeItem) {
      model.setParent(treeItem.getParent());
    }
    model.setLabel(item.getLabel());
    model.setExtraProps(item.getExtraProps());
    model.setOrder(item.getOrder());

    return dataDictItemService.update(dictName, code, model);
  }

  @Override
  public Optional<DataDictItem> getItemByCode(String dictName, String code) {
    return dataDictCoreService.getByName(dictName).flatMap(dict ->
        dataDictItemService.getByDictCode(dictName, code).map(item -> dict.isTreeMode()
            ? DataDictItemStructureConvertUtils.toTreeItem(item)
            : DataDictItemStructureConvertUtils.toFlatItem(item))
    );
  }

  @Override
  public List<? extends DataDictItem> searchListItemByDictName(String dictName) {
    var dictOptional = dataDictCoreService.getByName(dictName);
    if (!dictOptional.isPresent()) {
      return Collections.emptyList();
    }

    return DataDictItemStructureConvertUtils.assembleTree(
        dictOptional.get().isTreeMode(),
        dataDictItemService.searchListByDict(dictName)
    );
  }

  @Transactional(rollbackFor = Exception.class)
  public List<? extends DataDict> doAdd(Collection<? extends DataDict> dictionaries) throws OuroborosException {
    var itemModels = dictionaries.stream().filter(dict -> ObjectUtils.isNotEmpty(dict.getItems())).flatMap(dict ->
        DataDictItemStructureConvertUtils.disassembleTree(dict.getItems()).stream().peek(m -> m.setDict(dict.getName()))
    ).collect(Collectors.toList());

    List<DataDictItemModel> addItemModels;
    if (ObjectUtils.isNotEmpty(itemModels)) {
      addItemModels = dataDictItemService.add(itemModels);
    } else {
      addItemModels = Collections.emptyList();
    }

    var dictModels = dictionaries.stream().map(DataDictModel::new).collect(Collectors.toList());
    var dictCoreList = dataDictCoreService.add(dictModels);
    return dictCoreList.stream().map(model ->
        toDataDict(model)
    ).peek(dict -> dict.setItems(
        DataDictItemStructureConvertUtils.assembleTree(
            dict.isTreeMode(),
            addItemModels.stream()
                .filter(m -> Objects.equals(m.getDict(), dict.getName()))
                .collect(Collectors.toList())
        )
    )).collect(Collectors.toList());
  }

  private static DataDict toDataDict(DataDictModel model) {
    var dict = model.isTreeMode() ? new TreeDataDict() : new FlatDataDict();
    dict.setName(model.getName());
    dict.setTitle(model.getTitle());
    dict.setType(model.getType());
    dict.setGroup(model.getGroup());
    dict.setEditable(model.isEditable());
    dict.setTreeMode(model.isTreeMode());
    return dict;
  }

  @Transactional(rollbackFor = Exception.class)
  public Boolean doDelete(String name) throws OuroborosException {
    dataDictItemService.deleteByDict(name);
    return dataDictCoreService.delete(name);
  }
}
