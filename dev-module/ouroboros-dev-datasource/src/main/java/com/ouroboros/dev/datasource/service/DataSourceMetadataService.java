package com.ouroboros.dev.datasource.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.dev.datasource.model.DataSourceDefine;

/**
 * 数据源元数据服务接口
 * 该接口提供了获取数据库表结构相关信息的操作，包括表名、表信息以及列信息等
 */
public interface DataSourceMetadataService {

  /**
   * 检查数据源定义是否被支持
   *
   * @param dataSourceDefine 数据源定义对象，包含需要检查的数据源配置信息
   * @return 返回一个Boolean值，true表示支持该数据源定义，false表示不支持
   */
  Boolean isSupported(DataSourceDefine dataSourceDefine);

  /**
   * 获取数据源元数据信息
   *
   * @param dataSourceDefine 数据源定义对象，包含数据源的基本配置信息
   * @return DataSourceMetadata 返回数据源的元数据对象，包含数据源的详细描述信息
   */
  DataSourceMetadata getMetadata(DataSourceDefine dataSourceDefine);


  /**
   * 数据源元数据接口
   * 该接口用于获取数据源的元数据信息，包括所有表名、表结构等
   */
  interface DataSourceMetadata extends AutoCloseable {

    /**
     * 获取数据源中所有表的元数据信息
     *
     * @return 包含所有表元数据的List集合，每个元素是一个DataTableMetadata对象
     */
    List<DataTableMetadata> getAllTables();

    /**
     * 根据表名获取指定表的元数据信息
     *
     * @param tableName 要查询的表名
     * @return 对应表的DataTableMetadata对象，如果表不存在则返回null
     */
    Optional<DataTableMetadata> getTable(String tableName);
  }

  /**
   * 数据表元数据接口
   * 该接口用于定义数据表的基本元数据信息，包括表名、表注释和列信息
   */
  interface DataTableMetadata {
    /**
     * 获取表名
     *
     * @return 返回表的名称
     */
    String getName();

    /**
     * 获取表注释
     *
     * @return 返回表的注释信息
     */
    String getRemarks();

    /**
     * 获取列元数据列表
     *
     * @return 返回包含表中所有列元数据的列表
     */
    List<ColumnMetadata> getColumns();
  }

  /**
   * 表字段信息接口
   * 该接口定义了获取数据库表中列元数据的方法，提供了列的各种属性信息
   */
  interface ColumnMetadata {
    /**
     * 获取列名
     *
     * @return 返回列的名称
     */
    String getName();

    /**
     * 获取列注释
     *
     * @return 返回列的注释信息
     */
    String getRemarks();

    /**
     * 获取列的数据类型
     *
     * @return 返回列的数据类型
     */
    String getTypeName();

    /**
     * 获取列的默认值
     *
     * @return 返回列的默认值
     */
    String getDefaultValue();

    /**
     * 列是否允许为空
     *
     * @return 如果列允许为空返回true，否则返回false
     */
    boolean isNullable();

    /**
     * 获取列的额外信息
     *
     * @return 返回列的额外信息，如自增等
     */
    Map<String, Object> getExtra();
  }
}
