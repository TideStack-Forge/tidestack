package com.ouroboros.data.dict.pojo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.data.dict.DataDictItem;

/**
 * @author liansz
 **/
@JsonDeserialize
public class FlatDataDictItem extends AbstractDataDictItem implements DataDictItem {

  public FlatDataDictItem() {
  }

  public FlatDataDictItem(DataDictItem item) {
    super(item);
  }
}
