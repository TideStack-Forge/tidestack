package com.ouroboros.data.dict.pojo;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.data.dict.DataDictItem;

/**
 * @author liansz
 **/
@JsonDeserialize
public class TreeDataDictItem extends AbstractDataDictItem implements DataDictItem {

  private String parent;

  private List<TreeDataDictItem> children;

  public TreeDataDictItem() {
  }

  public TreeDataDictItem(DataDictItem item) {
    super(item);
  }

  public TreeDataDictItem(DataDictItem item, String parent, List<TreeDataDictItem> children) {
    this(item);
    setParent(parent);
    setChildren(children);
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }

  public List<TreeDataDictItem> getChildren() {
    return children;
  }

  public void setChildren(List<TreeDataDictItem> children) {
    this.children = children;
  }
}
