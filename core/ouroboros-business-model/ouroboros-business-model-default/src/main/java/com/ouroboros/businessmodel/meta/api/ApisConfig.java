package com.ouroboros.businessmodel.meta.api;

public class ApisConfig {
  private SearchApiConfig search;
  private GetOneApiConfig getOne;
  private UpdateApiConfig update;
  private DeleteApiConfig delete;
  private AddApiConfig add;

  public SearchApiConfig getSearch() {
    return search;
  }

  public void setSearch(SearchApiConfig search) {
    this.search = search;
  }

  public GetOneApiConfig getGetOne() {
    return getOne;
  }

  public void setGetOne(GetOneApiConfig getOne) {
    this.getOne = getOne;
  }

  public UpdateApiConfig getUpdate() {
    return update;
  }

  public void setUpdate(UpdateApiConfig update) {
    this.update = update;
  }

  public DeleteApiConfig getDelete() {
    return delete;
  }

  public void setDelete(DeleteApiConfig delete) {
    this.delete = delete;
  }

  public AddApiConfig getAdd() {
    return add;
  }

  public void setAdd(AddApiConfig add) {
    this.add = add;
  }
}
