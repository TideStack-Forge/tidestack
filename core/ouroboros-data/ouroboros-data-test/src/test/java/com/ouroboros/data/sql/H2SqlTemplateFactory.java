package com.ouroboros.data.sql;

import jakarta.annotation.Priority;
import javax.sql.DataSource;

import io.vavr.control.Try;

import com.querydsl.sql.*;

@Priority(100)
public class H2SqlTemplateFactory implements SqlTemplatesFactory {
  @Override
  public SQLTemplates apply(DataSource dataSource) {
    return Try.of(dataSource::getConnection)
        .mapTry(conn -> {
          var metaData = conn.getMetaData();
          var dbname = metaData.getDatabaseProductName();
          if (!dbname.contains("H2")) return null;
          var recordSet = conn
              .createStatement()
              .executeQuery("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'");
          if (!recordSet.next()) return null;
          var mode = recordSet.getString(1);
          conn.close();
          switch (mode.toUpperCase()) {
            case "POSTGRESQL":
              return PostgreSQLTemplates.DEFAULT;
            case "ORACLE":
              return OracleTemplates.DEFAULT;
            case "MYSQL":
            case "MARIADB":
              return MySQLTemplates.builder().quote().build();
            case "MSSQLSERVER":
              return SQLServer2012Templates.DEFAULT;
            case "DB2":
              return DB2Templates.DEFAULT;
            case "HSQLDB":
              return HSQLDBTemplates.DEFAULT;
            case "DERBY":
              return DerbyTemplates.DEFAULT;
            case "SQLITE":
              return SQLiteTemplates.DEFAULT;
            default:
              return null;
          }
        })
        .getOrElse(() -> null);
  }
}
