# ouroboros-field-type 设计文档

## 1. 概述

`ouroboros-field-type` 负责提供平台级字段类型定义，并将字段模板编译为数据字段元信息、表单输入态/展示态配置以及主键生成器配置。

本模块当前采用 `FieldTypeDefinition + FieldTypeCatalog + FieldTypeCompiler` 架构，替代历史上的旧字段模板聚合链路。

## 2. 模块结构

- `ouroboros-field-type-core`
  定义字段类型定义、字段类型目录、字段类型编译器和 provider 抽象。
- `ouroboros-field-type-default-types`
  提供默认字段类型定义 JSON。
- `ouroboros-field-type-runtime`
  提供运行时自动配置、JAR 字段类型加载和运行时扩展字段类型加载。
- `ouroboros-field-type-dev`
  提供开发态应用字段类型加载、开发态内置字段类型加载和开发端控制器。

`ouroboros-field-type-dev` 还提供字段类型 VCS 检查器与 `FieldTypeManagerServiceImpl`（开发态字段类型管理服务）。本模块向上被业务模型、数据模型与 UI schema 相关模块消费。

## 3. 核心抽象

### 3.1 FieldTypeDefinition

`FieldTypeDefinition` 是字段类型定义文件的稳定模型，负责承载：

- 基础信息：`name`、`label`、`description`、`order`
- 字段模板：`dataType`、`ui`、`validations`、`validationErrors`
- 参数定义：`paramsUiSchema`
- 默认参数：从 `paramsUiSchema[].value` 提取出的 `defaultParams`
- 主键模板：`primaryKeyGenerator`
- 来源信息：`FieldTypeDefinitionSource`、`sourceLocation`

### 3.2 FieldTypeDefinitionProvider

`FieldTypeDefinitionProvider` 负责按上下文装载定义，不负责模板求值和产物构建。

当前来源分为：

- `APP`
- `DEV_BUILTIN`
- `RUNTIME_EXTEND`
- `JAR`
- `DEFAULT`

### 3.3 FieldTypeCatalog

`FieldTypeCatalog` 是调用方的统一入口，负责：

- 按上下文聚合字段类型来源
- 按名称查找字段类型定义
- 调用 `FieldTypeCompiler` 生成 `CompiledFieldType`

场景由 `FieldTypeCatalogContext` 描述：

- `runtime()`
- `devApp(appName)`

### 3.4 FieldTypeCompiler

`FieldTypeCompiler` 负责：

- 合并 `defaultParams` 与调用参数
- 统一进行模板表达式解析
- 构建 `DataModelFieldMeta`
- 构建输入态和展示态 `FormItemNode`
- 构建主键生成器配置

`ui.display` 缺失时，回退逻辑由编译器统一处理。

## 4. 来源优先级

### 4.1 Runtime

运行时使用以下优先级：

`RUNTIME_EXTEND > JAR > DEFAULT`

### 4.2 Dev App

开发态应用使用以下优先级：

`APP > DEV_BUILTIN > RUNTIME_EXTEND > JAR > DEFAULT`

## 5. 错误边界

字段类型模块按阶段区分错误：

- `FieldTypeDefinitionException`
  字段类型定义读取、结构校验失败。
- `FieldTypeCatalogException`
  字段类型来源加载或目录解析失败。
- `FieldTypeCompileException`
  模板求值或编译产物构建失败。

## 6. 字段类型定义规范

- 字段类型定义文件保持扁平结构。
- 保留 `name`、`label`、`dataType`、`ui`、`validations`、`validationErrors`、`paramsUiSchema`、`primaryKeyGenerator` 等现有字段名。
- `paramsJsonSchema` 已移除，不再作为字段类型定义的一部分。
- 默认参数唯一来源是 `paramsUiSchema[].value`。

更多模板格式细节见 [field-template.md](docs/field-template.md)。
