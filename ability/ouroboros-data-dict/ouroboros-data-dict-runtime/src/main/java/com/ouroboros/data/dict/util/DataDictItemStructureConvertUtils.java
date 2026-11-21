package com.ouroboros.data.dict.util;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.data.dict.pojo.FlatDataDictItem;
import com.ouroboros.data.dict.pojo.TreeDataDictItem;
import com.ouroboros.data.dict.service.model.DataDictItemModel;
import com.ouroboros.util.Trees;

/**
 * @author liansz
 **/
public class DataDictItemStructureConvertUtils {

  /**
   * 拆解树形结构为扁平结构
   *
   * @param items
   * @return
   */
  public static List<DataDictItemModel> disassembleTree(Collection<? extends DataDictItem> items) {
    List<DataDictItemModel> addModels = items.stream()
        .filter(item -> item instanceof FlatDataDictItem)
        .map(DataDictItemModel::new)
        .collect(Collectors.toList());
    Trees.walkTree(
        items.stream()
            .filter(item -> item instanceof TreeDataDictItem)
            .map(item -> (TreeDataDictItem) item)
            .collect(Collectors.toList()),
        (Function<TreeDataDictItem, Collection<TreeDataDictItem>>) node -> {
          List<TreeDataDictItem> children = node.getChildren() != null ? node.getChildren() : Collections.emptyList();
          return children.stream().peek(m -> m.setParent(node.getCode())).collect(Collectors.toList());
        },
        node -> {
          var model = new DataDictItemModel(node);
          model.setParent(node.getParent());
          addModels.add(model);
        }
    );

    return addModels;
  }

  /**
   * 组装扁平结构为树形结构
   *
   * @param models
   * @return
   */
  public static List<? extends DataDictItem> assembleTree(boolean isTreeMode, List<DataDictItemModel> models) {
    if (isTreeMode) {
      return Trees.buildTree(
          models.stream().filter(m -> ObjectUtils.isEmpty(m.getParent())).collect(Collectors.toList()),
          (node) -> models.stream()
              .filter(m -> Objects.equals(node.getCode(), m.getParent()))
              .collect(Collectors.toList()),
          (node, children) -> {
            return new TreeDataDictItem(
                toTreeItem(node),
                node.getParent(),
                children.stream().map(it -> {
                  TreeDataDictItem item = new TreeDataDictItem(it);
                  item.setParent(node.getCode());
                  if (it instanceof TreeDataDictItem treeItem) {
                    item.setChildren(treeItem.getChildren());
                  }

                  return item;
                }).collect(Collectors.toList())
            );
          }
      );
    }

    return models.stream().map(DataDictItemStructureConvertUtils::toFlatItem).collect(Collectors.toList());
  }

  public static FlatDataDictItem toFlatItem(DataDictItemModel model) {
    FlatDataDictItem item = new FlatDataDictItem();
    item.setDict(model.getDict());
    item.setCode(model.getCode());
    item.setLabel(model.getLabel());
    item.setOrder(model.getOrder());
    item.setExtraProps(model.getExtraProps());
    return item;
  }

  public static TreeDataDictItem toTreeItem(DataDictItemModel model) {
    TreeDataDictItem item = new TreeDataDictItem(toFlatItem(model));
    item.setParent(model.getParent());
    return item;
  }
}
