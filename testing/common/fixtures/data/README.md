# Data Fixtures

这里放测试数据类夹具，例如：

- 应用元数据仓
- 业务模型样本
- 组织结构种子数据
- 权限矩阵数据

数据夹具的目标是让测试输入可复用、可复现、可解释。

## 设计约束

公共数据夹具必须描述“数据类型”和“来源解析规则”，不能把某个具体项目实例直接写进夹具名字或默认路径里。

如果数据天然来自仓库 checkout，优先把“仓库绑定”建成显式输入，例如：

- `--repo app-metadata=/abs/path/to/repo`
- `--repo platform-config=/abs/path/to/repo`

而不是把某次 run 留下来的 `tmp/...` 目录当成公共夹具的默认前提。

例如：

- 应该写成 `data/app-metadata-root`
- 不应该把公共夹具直接命名成 `data/mes-business-model-app-root`

具体实例属于：

- 场景输入
- 本轮 run 的 evidence
- 历史经验材料

不属于公共夹具资产本身。
