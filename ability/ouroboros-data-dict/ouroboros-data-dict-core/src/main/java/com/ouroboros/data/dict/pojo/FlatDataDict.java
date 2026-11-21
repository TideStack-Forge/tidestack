package com.ouroboros.data.dict.pojo;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.data.dict.DataDict;
import com.ouroboros.data.dict.DataDictItem;

/**
 * @author liansz
 **/
@JsonDeserialize
public class FlatDataDict extends AbstractDataDict implements DataDict {

  /**
   * 字典项
   */
  private List<FlatDataDictItem> items;

  public FlatDataDict() {
  }

  public FlatDataDict(DataDict dict) {
    super(dict);
  }

  public FlatDataDict(DataDict dict, List<FlatDataDictItem> items) {
    super(dict);
    this.items = items;
  }

  @Override
  public List<FlatDataDictItem> getItems() {
    return items;
  }

  @Override
  public void setItems(List<? extends DataDictItem> items) {
    this.items = (List<FlatDataDictItem>) items;
  }
}
