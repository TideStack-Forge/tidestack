package com.ouroboros.cache;

public class CacheEntryQuery {
  private String keyword;
  private Integer page;
  private Integer perPage;

  public CacheEntryQuery() {
  }

  public CacheEntryQuery(String keyword, Integer page, Integer perPage) {
    this.keyword = keyword;
    this.page = page;
    this.perPage = perPage;
  }

  public String getKeyword() {
    return keyword;
  }

  public CacheEntryQuery setKeyword(String keyword) {
    this.keyword = keyword;
    return this;
  }

  public Integer getPage() {
    return page;
  }

  public CacheEntryQuery setPage(Integer page) {
    this.page = page;
    return this;
  }

  public Integer getPerPage() {
    return perPage;
  }

  public CacheEntryQuery setPerPage(Integer perPage) {
    this.perPage = perPage;
    return this;
  }
}
