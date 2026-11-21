package com.ouroboros.businessmodel.meta.view;

public class ViewsConfig {
  private AddViewConfig add;
  private EditViewConfig edit;
  private DetailViewConfig detail;
  private IndexViewConfig index;

  public AddViewConfig getAdd() {
    return add;
  }

  public void setAdd(AddViewConfig add) {
    this.add = add;
  }

  public EditViewConfig getEdit() {
    return edit;
  }

  public void setEdit(EditViewConfig edit) {
    this.edit = edit;
  }

  public DetailViewConfig getDetail() {
    return detail;
  }

  public void setDetail(DetailViewConfig detail) {
    this.detail = detail;
  }

  public IndexViewConfig getIndex() {
    return index;
  }

  public void setIndex(IndexViewConfig index) {
    this.index = index;
  }
}
