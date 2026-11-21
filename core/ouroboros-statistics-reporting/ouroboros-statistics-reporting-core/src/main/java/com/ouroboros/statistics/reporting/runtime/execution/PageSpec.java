package com.ouroboros.statistics.reporting.runtime.execution;

public class PageSpec {
  private Integer pageNo;
  private Integer pageSize;

  public Integer getPageNo() {
    return pageNo;
  }

  public void setPageNo(Integer pageNo) {
    this.pageNo = pageNo;
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public void setPageSize(Integer pageSize) {
    this.pageSize = pageSize;
  }

  public PageSpec copy() {
    PageSpec copied = new PageSpec();
    copied.setPageNo(pageNo);
    copied.setPageSize(pageSize);
    return copied;
  }
}
