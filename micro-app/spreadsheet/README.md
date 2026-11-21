# Spreadsheet 微服务

## 介绍

* Spreadsheet 微服务能做什么？
  - 可以处理电子表格的数据
  - 可以提供一个友好的电子表格编辑和查看页面

* Spreadsheet 微服务不能做什么？
  - 不提供目录结构管理
  - 不严格检查电子表格名称（如允许存在重名）
  - 不提供细到用户的访问控制（由应用服务器决定是否向用户颁发访问token）


## 部署

* `mysql-db.sql` 为数据库初始化脚本
* `application.yml` 配置项：
  * spring.mvc.static-path-pattern # 本项目包含前端页面，需要配置静态资源路径
  * spring.redis        # 本项目强依赖 Redis 负载均衡调度，必须配置 Redis
  * db.mysql.druid      # Alibaba Druid 数据源的配置方法配置MySQL数据库

  * spreadsheet-service # 此配置节下面的都是与业务逻辑相关的配置
    - redis-dispatch-channel # 多服务端负载均衡的情况下协作广播频道名
    - row_size       # 表格存储分块的行数
    - col_size       # 表格存储分块的列数
    - snowflake:     # 雪花算法相关配置
      * datacenterId: 1
      * workerId: 1
    - management:    # 管理接口配置（管理OpenApi的app）
      * isEnabled: true
      * password: "123456"
    - accessTokenExpire:   # token过期时间（单位：分）
      * editToken: 3
      * readToken: 15

* 默认服务依赖 `spreadsheet-db-mysql`，父工程默认 reactor 也纳入 MySQL 实现；PostgreSQL/MongoDB 目录存在不代表默认装配。数据库脚本使用 MySQL 8 语法（如 `utf8mb4_0900_ai_ci`），不适用于 MySQL 5.7。
* 服务通过 Maven profile 过滤 `application.yml` 和 `application-${profiles.active}.yml`。`application-mysql.yml` 使用 `db.mysql.druid`、MySQL JDBC 驱动与 URL；连接地址可通过 `SPREADSHEET_DB_HOST`、`SPREADSHEET_DB_PORT`、`SPREADSHEET_DB_NAME` 配置，Redis 端口通过 `SPREADSHEET_REDIS_PORT` 配置，默认6379。独立工程使用原有 Boot 2.7 父 POM `com.xinpai.ouroboros:ouroboros-root:1.0.0-beta.1`，不继承主仓 Boot 3 根 POM；构建须取得该旧父 POM 及依赖。本轮使用本地缓存完成编译/打包及属性绑定验证，并在隔离 MySQL/Redis 环境验证服务启动和 JSON 创建/导出；见 [DOC-020](../../docs/tech-debt-tracker.md#doc-020-spreadsheet-multipart-导入失败)。


## 配置

配置地址：`http(s)://spreadsheet-service-host/[spreadsheet-context]/management/index.html`

输入密码后添加App，并记录 App ID 和 App Secret，调用OpenAPI的时候需要用到。


## 使用步骤

首先通过 OpenAPI 创建电子表格或获取访问令牌，然后通过以下URL打开新窗口即可：
```
http(s)://spreadsheet-service-host/[spreadsheet-context]?token=<token>&infobar=1
```
>`spreadsheet-context` 取决于部署时 application.yml 中的配置；当前默认端口 9010、context-path 为 `/spreadsheet`

地址参数（QueryString）：
* token 参数为必填项
* infobar 为选填项，传 1 时在页面顶部显示信息栏

### 创建文件流程

> 若图片无法显示请点击图片在新窗口中查看

![创建文件时序图](https://gitee.com/bemxself/pages/raw/master/spreadsheet-service/%E5%88%9B%E5%BB%BA%E6%96%87%E4%BB%B6.png)


### 访问文件流程

![访问文件时序图](https://gitee.com/bemxself/pages/raw/master/spreadsheet-service/%E8%AE%BF%E9%97%AE%E6%96%87%E4%BB%B6.png)


### 重命名文件流程

![重命名文件时序图](https://gitee.com/bemxself/pages/raw/master/spreadsheet-service/%E9%87%8D%E5%91%BD%E5%90%8D%E6%96%87%E4%BB%B6.png)

### 删除文件流程

![删除文件时序图](https://gitee.com/bemxself/pages/raw/master/spreadsheet-service/%E5%88%A0%E9%99%A4%E6%96%87%E4%BB%B6.png)


## OpenAPI

### 1. 创建电子表格

  * 请求参数
    - POST /api/open/create
    - Body: Json
      ```json
      {
        "appId": "<App ID>",
        "appSecret": "<App Secret>",
        "spreadsheetName": "电子表格名称",
        "editorName": "创建者名字"
      }
      ```

  * 返回格式
    ```json
    {
      "status": 0,
      "data": {
        "spreadsheetId": "电子表格Id",
        "spreadsheetName": "电子表格名称",
        "token": "编辑令牌"
      }
    }
    ```

### 2. 请求电子表格访问令牌

  * 请求参数
    - POST /api/open/token
    - Body: Json
      ```json
      {
        "appId": "<App ID>",
        "appSecret": "<App Secret>",
        "spreadsheetId": "电子表格Id",
        "editorName": "编辑者名字",
        "readonly": false // or true，不传递则默认为false
      }
      ```

  * 返回格式
    > 同上

### 3. 重命名电子表格
  
  * 请求参数
    - POST /api/open/rename
    - Body: Json
      ```json
      {
        "appId": "<App ID>",
        "appSecret": "<App Secret>",
        "spreadsheetId": "电子表格Id",
        "spreadsheetName": "电子表格名称",
        "editorName": "编辑者名字"
      }
      ```

  * 返回格式
    ```json
    {
      "status": 0,
      "data": {}
    }
    ```

### 4. 删除电子表格

  * 请求参数
    - POST /api/open/delete
    - Body: Json
      ```json
      {
        "appId": "<App ID>",
        "appSecret": "<App Secret>",
        "spreadsheetId": "电子表格Id",
        "editorName": "删除者名字"
      }
      ```

  * 返回格式
    ```json
    {
      "status": 0,
      "data": {}
    }
    ```

### 5. 导出电子表格为Excel文件

  * 请求参数
    - POST /api/open/export
    - Body: Json
      ```json
      {
        "appId": "<App ID>",
        "appSecret": "<App Secret>",
        "spreadsheetId": "电子表格Id"
      }
      ```

  * 成功时返回 XLSX 二进制流，下载名以 `.xlsx` 结尾；请求 JSON 中 `zip` 为字符串 `"1"` 时返回 ZIP，内部包含同名 XLSX。export 控制器通过 `@RequestBody` 接收 JSON Map；[导出行为测试](spreadsheet-service/src/test/java/com/xinpai/spreadsheet/controller/OpenApiExportTest.java) 已验证 JSON 绑定、两种文件格式及工作簿单元格内容，该测试的数据查询使用 mock；隔离服务另验证了真实 MySQL/Redis 下默认空表的创建与导出，附加 Excel 导入返回 HTTP 500 待查，生产部署未验收；见 [DOC-020](../../docs/tech-debt-tracker.md#doc-020-spreadsheet-multipart-导入失败)。

### 6. 导入Excel文件

  * 请求参数
    - POST /api/open/import
    - Body: multipart/form-data
      > appId = App ID
      >
      > appSecret = App Secret
      > 
      > spreadsheetName = [电子表格名称]（可选）
      >
      > editorName = 编辑人名字
      > 
      > file = 文件流


  * 返回格式
    > 同 **创建电子表格**
