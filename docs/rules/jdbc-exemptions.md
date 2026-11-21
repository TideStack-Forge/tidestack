# JDBC Direct Access Exemptions

## Overview

This document tracks all legitimate uses of direct JDBC access in the Ouroboros codebase. These exemptions are marked with the `@AllowDirectJDBC` annotation and represent scenarios where the ouroboros-data abstraction layer cannot be used.

## Exemption Categories

### 1. Database-Level DDL Operations

**Rationale**: CREATE/DROP DATABASE operations occur at the database server level, outside the scope of any specific database schema. Migration tools like Liquibase operate within a database context and cannot handle server-level operations.

**Files**:
- `dev-module/ouroboros-dev-app-management/src/main/java/com/ouroboros/dev/app/initializer/DefaultDataSourceInitializer.java`
  - Method: `executeDDL(ContainerDatasource, String)`
  - Usage: `DriverManager.getConnection()` + `Statement.executeUpdate()` for CREATE/DROP DATABASE
  - Reason: "database-level DDL operations (CREATE/DROP DATABASE)"

### 2. Metadata Introspection

**Rationale**: DatabaseMetaData API provides read-only access to database schema information (tables, columns, types, etc.). This is a standard JDBC feature for metadata discovery and does not involve data manipulation.

**Files**:
- `dev-module/ouroboros-dev-datasource/src/main/java/com/ouroboros/dev/datasource/service/impl/JDBCDataSourceMetadataService.java`
  - Method: `getConnection()`
  - Usage: `DriverManager.getConnection()` + `DatabaseMetaData.getTables()` for schema introspection
  - Reason: "metadata introspection via DatabaseMetaData API"

- `dev-module/ouroboros-dev-datasource/src/main/java/com/ouroboros/dev/datasource/service/impl/JDBCDataSourceMetadataService.java`
  - Type: `JDBCDataSourceMetadata`
  - Usage: holds JDBC `Connection` lifecycle and metadata table access for schema introspection
  - Reason: "metadata introspection via DatabaseMetaData API"

- `dev-module/ouroboros-dev-datasource/src/main/java/com/ouroboros/dev/datasource/service/impl/JDBCDataSourceMetadataService.java`
  - Type: `JDBCDataTableMetadata`
  - Usage: reads table columns via `DatabaseMetaData.getColumns()`
  - Reason: "metadata introspection via DatabaseMetaData API"

- `dev-module/ouroboros-dev-datasource/src/main/java/com/ouroboros/dev/datasource/service/impl/JDBCDataSourceMetadataService.java`
  - Type: `JDBCColumnMetadata`
  - Usage: maps JDBC `ResultSet` rows into column metadata values
  - Reason: "metadata introspection via DatabaseMetaData API"
