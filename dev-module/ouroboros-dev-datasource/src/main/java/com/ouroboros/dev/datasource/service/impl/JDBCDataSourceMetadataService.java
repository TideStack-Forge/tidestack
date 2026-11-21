package com.ouroboros.dev.datasource.service.impl;

import java.sql.*;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ouroboros.dev.datasource.model.DataSourceDefine;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.dev.datasource.service.DataSourceMetadataService;
import com.ouroboros.model.annotation.AllowDirectJDBC;
import com.ouroboros.util.Maps;

/**
 * JDBC数据源元数据服务实现类，用于处理JDBC类型数据源的元数据信息
 */
@Service
public class JDBCDataSourceMetadataService implements DataSourceMetadataService {
  private final static Logger logger = LoggerFactory.getLogger(JDBCDataSourceMetadataService.class);

  /**
   * 判断数据源是否支持当前类型
   * @param dataSourceDefine 数据源定义对象
   * @return 如果数据源类型为"jdbc"则返回true，否则返回false
   */
  @Override
  public Boolean isSupported(DataSourceDefine dataSourceDefine) {
    return "jdbc".equalsIgnoreCase(dataSourceDefine.getType());
  }

  /**
   * 获取数据源元数据
   * @param dataSourceDefine 数据源定义对象
   * @return 返回JDBC数据源元数据对象
   */
  @Override
  public DataSourceMetadata getMetadata(DataSourceDefine dataSourceDefine) {
    return new JDBCDataSourceMetadata(new JDBCDataSourceDefine(dataSourceDefine));
  }

  /**
   * JDBC数据源定义的内部类，继承自DataSourceDefine
   */
  private static class JDBCDataSourceDefine extends DataSourceDefine {
    private final DataSourceDefine define;

    /**
     * 构造函数，根据传入的Map初始化数据源定义
     *
     * @param define 包含数据源配置信息的Map，至少应包含"type"键
     */
    public JDBCDataSourceDefine(DataSourceDefine define) {
      super(define.getProperties());
      this.define = define;
    }

    @Override
    public String getType() {
      return define.getType();
    }

    /**
     * 获取JDBC驱动类名
     * @return 返回驱动类名字符串
     */
    public String getDriverClassName() {
      return Maps.getAsOrDefault(define.getProperties(), "driverClassName", String.class, "");
    }

    /**
     * 获取数据库连接URL
     * @return 返回数据库连接URL字符串
     */
    public String getUrl() {
      return Maps.getAsOrDefault(define.getProperties(), "url", String.class, "");
    }

    /**
     * 获取数据库用户名
     * @return 返回数据库用户名字符串
     */
    public String getUsername() {
      return Maps.getAsOrDefault(define.getProperties(), "username", String.class, "");
    }

    /**
     * 获取数据库密码
     * @return 返回数据库密码字符串
     */
    public String getPassword() {
      return Maps.getAsOrDefault(define.getProperties(), "password", String.class, "");
    }

    @Override
    public Map<String, Object> getProperties() {
      return define.getProperties();
    }
  }

  /**
   * JDBC数据源元数据的内部实现类
   */
  @AllowDirectJDBC("metadata introspection via DatabaseMetaData API")
  private static class JDBCDataSourceMetadata implements DataSourceMetadata, AutoCloseable {
    private final JDBCDataSourceDefine dataSourceDefine;
    private Connection connection;

    /**
     * 构造函数，初始化JDBC数据源元数据
     * @param dataSourceDefine JDBC数据源定义对象
     */
    public JDBCDataSourceMetadata(JDBCDataSourceDefine dataSourceDefine) {
      this.dataSourceDefine = dataSourceDefine;
    }

    /**
     * 获取数据库连接
     * @return 返回数据库连接对象；连接失败抛出业务异常
     */
    @AllowDirectJDBC("metadata introspection via DatabaseMetaData API")
    private Connection getConnection() {
      if (connection == null) {
        try {
          Class.forName(dataSourceDefine.getDriverClassName());
          connection = DriverManager.getConnection(dataSourceDefine.getUrl(), dataSourceDefine.getUsername(), dataSourceDefine.getPassword());
        } catch (ClassNotFoundException | SQLException e) {
          throw new BusinessException("无法连接数据源", e);
        }
      }
      return connection;
    }

    /**
     * 获取所有表元数据
     * @return 返回表元数据列表；读取失败抛出业务异常
     */
    @Override
    public List<DataTableMetadata> getAllTables() {
      var connection = getConnection();
      try(var resultSet = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), null, new String[]{"TABLE"})) {
        var tables = new ArrayList<DataTableMetadata>();
        while (resultSet.next()) {
          var name= resultSet.getString("TABLE_NAME");
          var remarks = resultSet.getString("REMARKS");
          tables.add(new JDBCDataTableMetadata(remarks, name));
        }
        return tables;
      } catch (SQLException e) {
        throw new BusinessException("无法读取数据库表结构", e);
      }
    }

    /**
     * 根据表名获取表元数据
     * @param tableName 表名
     * @return 返回表元数据的Optional包装对象；仅表不存在时返回空，读取失败抛出业务异常
     */
    @Override
    public Optional<DataTableMetadata> getTable(String tableName) {
      if (StringUtils.isEmpty(tableName)) {
        return Optional.empty();
      }
      var connection = getConnection();
      try(var resultSet = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), tableName, new String[]{"TABLE"})) {
        if (resultSet.next()) {
          var name= resultSet.getString("TABLE_NAME");
          var remarks = resultSet.getString("REMARKS");
          return Optional.of(new JDBCDataTableMetadata(remarks, name));
        }
      } catch (SQLException e) {
        throw new BusinessException("无法读取数据库表结构", e);
      }
      return Optional.empty();
    }

    @Override
    public void close() {
      if (connection != null) {
        try {
          if (!connection.isClosed()) {
            connection.close();
          }
        } catch (SQLException e) {
          logger.warn("关闭数据库连接失败", e);
        } finally {
          connection = null;
        }
      }
    }

    /**
     * JDBC表元数据的内部实现类
     */
    @AllowDirectJDBC("metadata introspection via DatabaseMetaData API")
    private class JDBCDataTableMetadata implements DataTableMetadata {
      private final String name;
      private final String remarks;

      /**
       * 构造函数，初始化表元数据
       * @param remarks 表备注信息
       * @param name 表名
       */
      public JDBCDataTableMetadata(String remarks, String name) {
        this.remarks = remarks;
        this.name = name;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getRemarks() {
        return remarks;
      }

      /**
       * 获取表的列元数据
       * @return 返回列元数据列表；读取失败抛出业务异常
       */
      @Override
      public List<ColumnMetadata> getColumns() {
        var connection = getConnection();
        try(var resultSet = connection.getMetaData().getColumns(connection.getCatalog(), connection.getSchema(), name, null)) {
          var columns = new ArrayList<ColumnMetadata>();
          while (resultSet.next()) {
            columns.add(new JDBCColumnMetadata(resultSet));
          }
          return columns;
        } catch (SQLException e) {
          throw new BusinessException("无法读取数据库列结构", e);
        }
      }

      /**
       * JDBC列元数据的内部实现类
       */
      @AllowDirectJDBC("metadata introspection via DatabaseMetaData API")
      private static class JDBCColumnMetadata implements ColumnMetadata {
        private String name;
        private String remarks;
        private String typeName;
        private boolean nullable;
        private String defaultValue;
        /**
         * 构造函数，根据ResultSet初始化列元数据
         * @param columnResultSet 包含列元数据的ResultSet对象
         */
        public JDBCColumnMetadata(ResultSet columnResultSet) {
          try {
            name = columnResultSet.getString("COLUMN_NAME");
            remarks = columnResultSet.getString("REMARKS");
            typeName = columnResultSet.getString("TYPE_NAME");
            nullable = columnResultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
            defaultValue = columnResultSet.getString("COLUMN_DEF");
          } catch (SQLException e) {
            throw new BusinessException("无法读取数据库表结构", e);
          }
        }

        @Override
        public String getName() {
          return name;
        }

        @Override
        public String getRemarks() {
          return remarks;
        }

        @Override
        public String getTypeName() {
          return typeName;
        }

        @Override
        public String getDefaultValue() {
          return defaultValue;
        }

        @Override
        public boolean isNullable() {
          return nullable;
        }

        @Override
        public Map<String, Object> getExtra() {
          return Collections.emptyMap();
        }
      }
    }
  }
}
