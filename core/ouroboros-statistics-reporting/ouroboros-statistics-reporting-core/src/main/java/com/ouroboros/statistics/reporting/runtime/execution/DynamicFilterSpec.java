package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.ArrayList;
import java.util.List;

public class DynamicFilterSpec {
  private FilterNode filter;
  private List<SortSpec> sort = new ArrayList<SortSpec>();

  public FilterNode getFilter() {
    return filter;
  }

  public void setFilter(FilterNode filter) {
    this.filter = filter;
  }

  public List<SortSpec> getSort() {
    return sort;
  }

  public void setSort(List<SortSpec> sort) {
    this.sort = sort;
  }

  public DynamicFilterSpec copy() {
    DynamicFilterSpec copied = new DynamicFilterSpec();
    copied.setFilter(filter == null ? null : filter.copy());
    List<SortSpec> copiedSort = new ArrayList<SortSpec>();
    if (sort != null) {
      for (SortSpec sortSpec : sort) {
        copiedSort.add(sortSpec == null ? null : sortSpec.copy());
      }
    }
    copied.setSort(copiedSort);
    return copied;
  }
}
