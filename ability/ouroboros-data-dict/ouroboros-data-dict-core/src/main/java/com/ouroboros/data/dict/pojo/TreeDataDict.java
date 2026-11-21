package com.ouroboros.data.dict.pojo;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.data.dict.DataDict;
import com.ouroboros.data.dict.DataDictItem;

/**
 * @author liansz
 **/
@JsonDeserialize
public class TreeDataDict extends AbstractDataDict implements DataDict {

  /**
   * 字典项
   */
  private List<TreeDataDictItem> items;

  public TreeDataDict() {
  }

  public TreeDataDict(DataDict dict) {
    super(dict);
  }

  public TreeDataDict(DataDict dict, List<TreeDataDictItem> items) {
    super(dict);
    this.items = items;
  }

  @Override
  public List<TreeDataDictItem> getItems() {
    return items;
  }

  @Override
  public void setItems(List<? extends DataDictItem> items) {
    this.items = (List<TreeDataDictItem>) items;
  }
}
