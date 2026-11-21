# Ouroboros 通用小组件库

本模块包含一系列通用的小组件定义，可用于构建仪表板和数据展示页面。

## 小组件列表

### 1. 通用表格 (TableWidget)

**功能：** 展示数据表格，支持 CRUD 操作

**配置项：**
- `crudSchema.api`: 数据接口地址（必填）
- `crudSchema.syncLocation`: 是否将查询条件同步到 URL
- `crudSchema.columns`: 列配置数组
  - `name`: 字段名
  - `label`: 列标题
  - `type`: 列类型（text、date、datetime、status、mapping、image、link）

**接口格式：** 标准 amis CRUD 接口格式

**使用场景：** 需要展示结构化数据的列表，支持排序、筛选、分页等功能

---

### 2. 通用列表 (ListWidget)

**功能：** 以卡片形式展示数据列表

**配置项：**
- `api`: 数据接口地址（必填）
- `titleTpl`: 标题模板，默认 `${title}`
- `subTitleTpl`: 副标题模板
- `descTpl`: 描述模板，默认 `${description}`
- `avatarTpl`: 头像模板
- `remarkTpl`: 备注模板

这些值是 amis 模板表达式，不是字段名；当前定义没有 `linkField` 配置。

**接口格式：** 以下是 amis 数据域中的业务数据；平台响应需先经现有适配器解包。
```json
{
  "items": [
    {
      "title": "标题",
      "description": "描述信息",
      "link": "/detail/1"
    }
  ]
}
```

**使用场景：** 展示非结构化或半结构化的数据，如文章列表、通知列表等

---

### 3. 柱形图 (BarChartWidget)

**功能：** 展示柱形图，用于分类数据对比

**主要配置项：**
- `api`: 数据接口地址（必填）
- `intervalTime`: 刷新间隔（毫秒），0 表示不自动刷新
- `chartTitle.text`: 图表标题
- `chartTitle.left`: 标题位置（left/center/right）
- `xAxis.type`: X轴类型（category/value/time）
- `xAxis.data`: X轴数据，支持 amis 表达式（如 `${items | pick:name}`）
- `xAxis.name`: X轴名称
- `yAxis.type`: Y轴类型（value/category）
- `yAxis.name`: Y轴名称
- `series`: 数据系列数组
  - `name`: 系列名称
  - `data`: 系列数据，支持 amis 表达式（如 `${items | pick:sales}`）
  - `type`: 系列类型（bar/line）

**接口格式：** 对象数组（items）
```json
{
  "items": [
    {"name": "周一", "sales": 120, "profit": 50},
    {"name": "周二", "sales": 200, "profit": 80},
    {"name": "周三", "sales": 150, "profit": 60}
  ]
}
```

**使用场景：** 对比不同分类的数值大小

---

### 4. 折线图 (LineChartWidget)

**功能：** 展示折线图，展示数据趋势变化

**主要配置项：**
- `api`: 数据接口地址（必填）
- `intervalTime`: 刷新间隔（毫秒），0 表示不自动刷新
- `chartTitle.text`: 图表标题
- `chartTitle.left`: 标题位置（left/center/right）
- `xAxis.type`: X轴类型（category/value/time）
- `xAxis.data`: X轴数据，支持 amis 表达式（如 `${items | pick:name}`）
- `xAxis.name`: X轴名称
- `xAxis.boundaryGap`: X轴是否留白
- `yAxis.type`: Y轴类型（value/category）
- `yAxis.name`: Y轴名称
- `series`: 数据系列数组
  - `name`: 系列名称
  - `data`: 系列数据，支持 amis 表达式（如 `${items | pick:views}`）
  - `smooth`: 是否平滑曲线
  - `type`: 系列类型（line/bar）

**接口格式：** 对象数组（items）
```json
{
  "items": [
    {"name": "周一", "views": 120, "downloads": 50},
    {"name": "周二", "views": 200, "downloads": 80}
  ]
}
```

**使用场景：** 展示趋势变化，如时间序列数据

---

### 5. 饼图 (PieChartWidget)

**功能：** 展示饼图，展示数据占比关系

**主要配置项：**
- `api`: 数据接口地址（必填）
- `intervalTime`: 刷新间隔（毫秒），0 表示不自动刷新
- `chartTitle.text`: 图表标题
- `chartTitle.left`: 标题位置（left/center/right）
- `series[0].data`: 饼图数据，支持 amis 表达式（如 `${items}`）
- `series[0].radius`: 饼图半径（如 "60%" 或 100）
- `series[0].label.show`: 是否显示标签
- `legend.show`: 是否显示图例
- `legend.orient`: 图例方向（vertical/horizontal）
- `legend.left`: 图例位置（left/center/right）

**接口格式：** 对象数组（items），每个对象需包含 name 和 value 字段
```json
{
  "items": [
    {"name": "类别A", "value": 335},
    {"name": "类别B", "value": 234},
    {"name": "类别C", "value": 154}
  ]
}
```

**使用场景：** 展示数据占比关系

---

### 6. 通用图表 (ChartWidget)

**功能：** 支持所有 Echarts 图表类型

**配置项：**
- `api`: 数据接口地址（必填，必须返回完整的 Echarts 配置）
- `intervalTime`: 刷新间隔（毫秒），0 表示不自动刷新

**接口格式：** 完整的 Echarts 配置对象
```json
{
  "title": {
    "text": "销售数据统计"
  },
  "xAxis": {
    "type": "category",
    "data": ["周一", "周二", "周三"]
  },
  "yAxis": {
    "type": "value"
  },
  "series": [
    {
      "name": "销售额",
      "type": "line",
      "data": [120, 200, 150]
    }
  ],
  "tooltip": {
    "trigger": "axis"
  }
}
```

**使用场景：** 需要自定义复杂图表配置，或使用 Echarts 的高级特性

**参考文档：** [Echarts 官方文档](https://echarts.apache.org/zh/option.html)

---

## 标题配置说明

**`allowHeader` 不为 `false` 的小组件会显示系统标题配置**，不需要在 `configFormItems` 中配置。小组件系统会自动添加以下配置项：

- `showHeader`: 是否显示标题栏（开关）
- `title`: 标题内容（文本输入）

本模块六个定义均设置 `allowHeader: true`，因此这些配置会自动显示在配置表单中。标题栏使用字符串 `title`，图表内部标题另用对象 `chartTitle`，避免字段冲突。

---

## 图表配置说明

### Echarts 配置结构

柱形图、折线图、饼图模板把已声明的 Echarts 配置段嵌入最终 chart schema；并非任意顶层属性都会透传。需要完整 option 时使用通用图表的 API 返回配置，或扩展正式模板。

**配置层次：**
- `chartTitle`: 图表标题配置（映射为 Echarts 的 `title`）
- `xAxis`: X轴配置（type, data, name 等）
- `yAxis`: Y轴配置（type, name 等）
- `series`: 数据系列配置（数组，每项包含 type, name, data 等）
- `tooltip`: 提示框配置
- `legend`: 图例配置

### Amis 数据映射表达式

图表组件支持在配置中使用 amis 的数据映射表达式 `${}`，用于从 API 返回的数据中提取和转换数据：

**常用表达式：**
- `${items}`: 直接使用 items 数组
- `${items | pick:name}`: 提取 items 数组中每个对象的 name 字段，返回字段值数组
- `${items | pick:sales}`: 提取 items 数组中每个对象的 sales 字段
- `${items | filter:status:active}`: 过滤数据

**示例配置：**
```json
{
  "xAxis": {
    "type": "category",
    "data": "${items | pick:week}"
  },
  "series": [
    {
      "type": "bar",
      "name": "销售额",
      "data": "${items | pick:sales}"
    },
    {
      "type": "bar",
      "name": "利润",
      "data": "${items | pick:profit}"
    }
  ]
}
```

当 API 返回：
```json
{
  "items": [
    {"week": "周一", "sales": 120, "profit": 50},
    {"week": "周二", "sales": 200, "profit": 80}
  ]
}
```

会自动转换为：
```json
{
  "xAxis": {
    "type": "category",
    "data": ["周一", "周二"]
  },
  "series": [
    {
      "type": "bar",
      "name": "销售额",
      "data": [120, 200]
    },
    {
      "type": "bar",
      "name": "利润",
      "data": [50, 80]
    }
  ]
}
```

---

## schemaTpl 和 config 的关系

小组件使用模板机制生成最终的渲染 schema：

1. **schemaTpl（模板）：** 包含 `#{}` 占位符的 schema 模板
2. **config（配置）：** 用户在配置面板中设置的值
3. **生成过程：** 遍历 schemaTpl，将所有 `#{key}` 替换为 config 中的对应值

**图表组件的 schemaTpl：**
```json
{
  "type": "chart",
  "api": "#{api}",
  "interval": "#{intervalTime}",
  "config": {
    "title": "#{chartTitle}",
    "xAxis": "#{xAxis}",
    "yAxis": "#{yAxis}",
    "series": "#{series}",
    "tooltip": "#{tooltip}",
    "legend": "#{legend}"
  }
}
```

**注意：**
- `#{}` 用于构建时替换（小组件配置 → schema）
- `${}` 用于运行时数据绑定（API 数据 → 图表）
- 两个阶段分离：先通过 `#{}` 生成 schema，再由 amis 通过 `${}` 绑定数据

---

## 目录结构

```
ouroboros-widget-common/
├── pom.xml
├── README.md
└── src/main/metadata/
    ├── TableWidget.widget.json
    ├── ListWidget.widget.json
    ├── BarChartWidget.widget.json
    ├── LineChartWidget.widget.json
    ├── PieChartWidget.widget.json
    └── ChartWidget.widget.json
```

---

## 扩展与自定义

如需创建新的小组件定义：

1. 在 `src/main/metadata/` 下创建 `<name>.widget.json`，构建时打包到 `META-INF/ouroboros/metadata/` typed 元数据根
2. 设置 `generatorName` 为 `"simple"`（使用简单生成器）
3. 定义 `configFormItems`（配置表单，不包括标题和显示标题栏）
4. 定义 `schemaTpl`（渲染模板，使用 `#{}` 占位符）
5. 在 schema 配置中可以使用 amis 的 `${}` 数据映射表达式
6. 提供 `thumbSchema`（缩略图预览）和 `exampleData`（示例数据）
7. 确保 `allowHeader` 设置正确（是否允许显示标题）

沿用本模块 Maven 元数据打包配置后，新的定义由正式资源扫描机制加载。上面的目录是已生成 `WidgetDefinition` 的 typed 格式；若通过管理页保存 `WidgetSchema`，生成器为 `simple`，模板等配置置于其 `config` 对象。

---

## 参考资料

- [Amis 官方文档](https://aisuda.bce.baidu.com/amis)
- [Amis Chart 组件](https://aisuda.bce.baidu.com/amis/zh-CN/components/chart)
- [Amis 数据映射](https://aisuda.bce.baidu.com/amis/zh-CN/docs/concepts/data-mapping)
- [Echarts 官方文档](https://echarts.apache.org/zh/option.html)
