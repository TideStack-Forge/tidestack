package com.ouroboros.widget;

/**
 * GridLayout内部类，用于定义组件的布局信息
 */
public class GridPosition {
  // 布局的X坐标
  private int x;
  // 布局的Y坐标
  private int y;

  /**
   * 获取X坐标
   *
   * @return X坐标值
   */
  public int getX() {
    return x;
  }

  /**
   * 设置X坐标
   *
   * @param x 要设置的X坐标值
   * @return 返回当前GridLayout对象，支持链式调用
   */
  public GridPosition setX(int x) {
    this.x = x;
    return this;
  }

  /**
   * 获取Y坐标
   *
   * @return Y坐标值
   */
  public int getY() {
    return y;
  }

  /**
   * 设置Y坐标
   *
   * @param y 要设置的Y坐标值
   * @return 返回当前GridLayout对象，支持链式调用
   */
  public GridPosition setY(int y) {
    this.y = y;
    return this;
  }
}
