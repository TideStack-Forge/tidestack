package com.ouroboros.dev.app.initializer;

import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;

import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.ContainerDatasource;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.model.annotation.AllowDirectJDBC;

@Component
public class DefaultDataSourceInitializer extends AbstractSimpleDataSourceInitializer implements DataSourceInitializer {
  final private static Map<String, String> DROP_DB_TEMPLATE_SQL = new TreeMap<>(String::compareToIgnoreCase);
  final private static Map<String, String> CREATE_DB_TEMPLATE_SQL = new HashMap<>();

  static {
    DROP_DB_TEMPLATE_SQL.put("mysql", "DROP DATABASE IF EXISTS ${dbName};");
    DROP_DB_TEMPLATE_SQL.put("postgresql", """
        DO $$
        BEGIN
            IF EXISTS (
                SELECT FROM pg_database
                WHERE datname = '${dbName}'
            ) THEN
                DROP DATABASE ${dbName};
            END IF;
        END $$;
        """);
    DROP_DB_TEMPLATE_SQL.put("sqlserver", "IF EXISTS (SELECT * FROM sys.databases WHERE name = '${dbName}') DROP DATABASE ${dbName};");
    DROP_DB_TEMPLATE_SQL.put("unknown", "DROP DATABASE ${dbName};");
    CREATE_DB_TEMPLATE_SQL.put("mysql", "CREATE DATABASE IF NOT EXISTS ${dbName} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
    CREATE_DB_TEMPLATE_SQL.put("postgresql", """
        DO $$
        BEGIN
            IF NOT EXISTS (
                SELECT FROM pg_database
                WHERE datname = 'my_db'
            ) THEN
                CREATE DATABASE my_db;
            END IF;
        END $$;
        """);
    CREATE_DB_TEMPLATE_SQL.put("sqlserver", "IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = '${dbName}') CREATE DATABASE ${dbName};");
    CREATE_DB_TEMPLATE_SQL.put("unknown", "CREATE DATABASE ${dbName};");
  }

  private ConfigurationService configurationService;
  private DataSource dataSource;
  private Environment environment;

  public DefaultDataSourceInitializer(ConfigurationService configurationService, Environment environment, DataSource dataSource) {
    super("default");
    this.configurationService = configurationService;
    this.environment = environment;
    this.dataSource = dataSource;
  }

  @Override
  public Try<ContainerDatasource> init(String appName, String profileName, String environmentName,
                                       AppContainerRequest.DataSourceDefine dataSourceDefine) {

    String dbName = ("ouroboros_dev_" + appName + "_" + profileName).replaceAll("-", "_");

    ContainerDatasource datasource = new ContainerDatasource();
    datasource.setDriverClassName(getDataSourceDriverClassName());
    datasource.setUrl(getDataSourceUrlTemplate().replace("${dbName}", dbName));
    datasource.setUsername(getDataSourceUserName());
    datasource.setPassword(getDataSourcePassword());
    if (dataSourceDefine.getReinitialize()) {
      var tryDrop = dropDatabase(datasource, dbName);
      if (tryDrop.isFailure()) {
        return Try.failure(new PlatformException("删除数据库失败: ", tryDrop.getCause()));
      }
    }
    return createDatabase(datasource, dbName).map(v -> datasource);
  }

  private String getDataSourceDriverClassName() {
    return configurationService.get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_DATASOURCE_DRIVER_CLASS)
        .filter(DefaultDataSourceInitializer::isUsableConfiguredValue)
        .orElseGet(() -> firstNonBlank(
            environment.getProperty("spring.datasource.driver-class-name"),
            environment.getProperty("spring.datasource.driverClassName"),
            environment.getProperty("driverClassName")));
  }

  private String getDataSourceUrlTemplate() {
    return configurationService.get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_DATASOURCE_URL)
        .filter(DefaultDataSourceInitializer::isUsableConfiguredValue)
        .orElseGet(() -> makeTemplateFromUrl(environment.getProperty("spring.datasource.url")));
  }

  private String getDataSourceUserName() {
    return configurationService.get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_DATASOURCE_USERNAME)
        .filter(DefaultDataSourceInitializer::isUsableConfiguredValue)
        .orElseGet(() -> environment.getProperty("spring.datasource.username"));
  }

  private String getDataSourcePassword() {
    return configurationService.get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_DATASOURCE_PASSWORD)
        .filter(DefaultDataSourceInitializer::isUsableConfiguredValue)
        .orElseGet(() -> environment.getProperty("spring.datasource.password"));
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (isUsableConfiguredValue(value)) {
        return value;
      }
    }
    return null;
  }

  private static boolean isUsableConfiguredValue(String value) {
    return StringUtils.isNotBlank(value) && !"null".equalsIgnoreCase(value.trim());
  }


  public static String makeTemplateFromUrl(String url) {
    if (url == null || url.isEmpty()) {
      return "";
    }

    // MySQL、PostgreSQL
    if (url.startsWith("jdbc:mysql")) {
      Pattern pattern = Pattern.compile("(jdbc:(mysql|postgresql)://[^/]+/)[^?]*(\\?.*)?");
      Matcher matcher = pattern.matcher(url);
      if (matcher.find()) {
        // 基础部分
        String base = matcher.group(1);
        // 查询参数
        String query = matcher.group(3) != null ? matcher.group(3) : "";
        return base + "${dbName}" + query;
      }
      // 如果无法匹配，返回原始 URL
      return url;
    }

    // SQL Server
    if (url.startsWith("jdbc:sqlserver")) {
      Pattern pattern = Pattern.compile("(jdbc:sqlserver://[^;]+;.*?databaseName=)([^;]+)(;.*)?");
      Matcher matcher = pattern.matcher(url);
      if (matcher.find()) {
        //前面的部分
        String base = matcher.group(1);
        //后面的部分
        String suffix = matcher.group(3) != null ? matcher.group(3) : "";
        return base + "${dbName}" + suffix;
      }
      return url;
    }

    // 未知类型，返回原始 URL
    return url;
  }

  /**
   * 删除数据库
   *
   * @param datasource
   * @param dbName
   * @return
   */
  private Try<Void> dropDatabase(ContainerDatasource datasource, String dbName) {
    String template = DROP_DB_TEMPLATE_SQL.get(getDatabaseType(datasource.getUrl()));
    String sql = template.replaceAll("\\$\\{dbName\\}", dbName);
    return executeDDL(datasource, String.format(sql));
  }

  /**
   * 创建数据库（如果数据库不存在则创建，如果数据库已存在什么都不会发生）
   *
   * @param datasource
   * @return
   */
  private Try<Void> createDatabase(ContainerDatasource datasource, String dbName) {
    String template = CREATE_DB_TEMPLATE_SQL.get(getDatabaseType(datasource.getUrl()));
    String sql = template.replaceAll("\\$\\{dbName\\}", dbName);
    return executeDDL(datasource, sql);
  }

  /**
   * 执行SQL语句
   *
   * @param datasource
   * @param sql
   * @return
   */
  @AllowDirectJDBC("database-level DDL operations (CREATE/DROP DATABASE)")
  private Try<Void> executeDDL(ContainerDatasource datasource, String sql) {
    Try.run(() -> Class.forName(datasource.getDriverClassName()));

    // 创建数据库
    try (var conn = DriverManager.getConnection(getDDLUrl(datasource.getUrl()), datasource.getUsername(), datasource.getPassword())) {
      Thread.sleep(1000);

      var stmt = conn.createStatement();
      stmt.executeUpdate(sql);
      stmt.close();
    } catch (Exception e) {
      return Try.failure(new BusinessException("执行数据库DDL失败: ", e));
    }

    return Try.success(null);
  }

  private static String getDatabaseType(String url) {
    if (url.startsWith("jdbc:mysql")) {
      return "mysql";
    } else if (url.startsWith("jdbc:postgresql")) {
      return "postgresql";
    } else if (url.startsWith("jdbc:sqlserver")) {
      return "sqlserver";
    } else {
      return "unknown";
    }
  }

  private static String getDDLUrl(String url) {
    // 删除数据库名 - MySQL 和 PostgreSQL
    Pattern pattern = Pattern.compile("(jdbc:(mysql|postgresql)://[^/]+)/[^?]*(\\?.*)?");
    Matcher matcher = pattern.matcher(url);
    if (matcher.find()) {
      // 基础 URL
      String base = matcher.group(1);
      // 查询参数
      String query = matcher.group(3) != null ? matcher.group(3) : "";
      return base + query;
    }

    // 删除数据库名 - SQL Server
    pattern = Pattern.compile("(jdbc:sqlserver://[^;]+;)(.*?)(databaseName=[^;]+;?)(.*)?");
    matcher = pattern.matcher(url);
    if (matcher.find()) {
      // 基础 URL
      String base = matcher.group(1);
      // 数据库前的其他参数
      String optionsBeforeDb = matcher.group(2) != null ? matcher.group(2) : "";
      // 数据库后的其他参数
      String optionsAfterDb = matcher.group(4) != null ? matcher.group(4) : "";
      return base + optionsBeforeDb + optionsAfterDb;
    }

    // 无法解析时，返回原始 URL
    return url;
  }
}
