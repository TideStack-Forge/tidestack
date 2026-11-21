package com.ouroboros.widget;

/**
 * GridSize内部类，用于定义网格尺寸
 * 包含列数和行数
 */
public class GridSize {
  // 列数
  private int cols;
  // 行数
  private int rows;

  /**
   * 获取列数
   *
   * @return 列数
   */
  public int getCols() {
    return cols;
  }

  /**
   * 设置列数
   *
   * @param cols 列数
   * @return 当前对象实例，支持链式调用
   */
  public GridSize setCols(int cols) {
    this.cols = cols;
    return this;
  }

  /**
   * 获取行数
   *
   * @return 行数
   */
  public int getRows() {
    return rows;
  }

  /**
   * 设置行数
   *
   * @param rows 行数
   * @return 当前对象实例，支持链式调用
   */
  public GridSize setRows(int rows) {
    this.rows = rows;
    return this;
  }
}
