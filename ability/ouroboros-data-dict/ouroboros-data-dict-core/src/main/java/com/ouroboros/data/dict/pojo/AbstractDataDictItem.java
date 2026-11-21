package com.ouroboros.data.dict.pojo;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.data.dict.DataDictItem;

/**
 * @author liansz
 **/
public abstract class AbstractDataDictItem implements DataDictItem {

  private String code;

  private String label;

  @JsonIgnore
  private String dict;

  private Map<String, Object> extraProps;

  private int order;

  public AbstractDataDictItem() {
  }

  public AbstractDataDictItem(DataDictItem item) {
    setDict(item.getDict());
    setCode(item.getCode());
    setLabel(item.getLabel());
    setOrder(item.getOrder());
    setExtraProps(item.getExtraProps());
  }

  @Override
  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  @Override
  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  @Override
  public String getDict() {
    return dict;
  }

  public void setDict(String dict) {
    this.dict = dict;
  }

  @Override
  @JsonGetter
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  @JsonSetter
  public void setExtraProps(Map<String, Object> extraProps) {
    this.extraProps = extraProps;
  }

  @Override
  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }
}
