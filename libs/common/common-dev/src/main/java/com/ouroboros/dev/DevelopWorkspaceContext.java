package com.ouroboros.dev;

public interface DevelopWorkspaceContext {

  /**
   * 获取根目录的方法
   * 该方法用于获取程序的根目录路径
   *
   * @return 返回一个String类型的值，表示根目录的路径
   */
  String getRootDir();

  /**
   * 获取开发工作空间
   *
   * @return
   */
  String getDevRootDir();

  /**
   * 获取运行根目录的方法
   *
   * @return 返回运行根目录的路径字符串
   */
  String getRunRootDir();

  /**
   * 获取调试支持文件的目录路径
   * 该方法返回一个字符串，表示用于存储调试支持文件的目录路径
   *
   * @return 返回调试支持文件的目录路径字符串
   */
  String getDebugSupportFilesDir();

  /**
   * 获取开发支持文件的目录路径
   *
   * @return 返回开发支持文件的目录路径字符串
   * 如果未设置或不可用，可能返回null
   */
  String getDevelopSupportFilesDir();
}
