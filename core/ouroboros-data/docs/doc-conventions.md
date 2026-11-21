# 数据模块文档约定

本模块遵循仓库的[文档治理规范](../../../docs/rules/documentation.md)。

- [DESIGN.md](../DESIGN.md) 是模块机制与专题导航入口。
- `design/` 承载长期独立的机制或测试主题；有子主题时使用 overview.md 导航，简单主题保持单文件。
- DSL 行为用例只维护[共同矩阵](../design/dsl-test-cases/dsl-test-cases.md)，测试方法维护在[测试指南](../design/dsl-test-cases/dsl-test-cases-design.md)。矩阵中的预期与实际测试覆盖分别核对。
- 单次重构方案、模型候选稿、评审、对比和验证结果归关联任务、PR 或 Issue；必要结论回写相应机制，未闭合事项进入根技术债台账。普通修订历史由 Git 保存。

本页只补模块内入口，不另设 reviews 归档规则或复制根治理模板。
