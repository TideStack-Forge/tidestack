# Logicflow Editor Static Frontend Fixture

这个 fixture 负责把 `frontends/web/ouroboros-logicflow-editor` 的打包产物以一个最小静态服务方式暴露出来，供 develop 壳通过 `--logicflow_editor` 参数加载。

它不负责：

- 启动 develop 前端壳
- 准备逻辑流模型数据
- 浏览器页面级断言

这些职责分别属于：

- `frontends/develop-shell`
- `data/logicflow-validation-diagnostics`
- `testing/integration/feature/lowcode/logicflow-validation-diagnostics`
