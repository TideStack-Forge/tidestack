# Templates

`testing/common/templates/` 用来放正式测试资产的模板，例如：

- 新场景目录模板
- `scenario.yaml` 模板
- `contract.md` 模板
- `cases.md` 模板
- `fixture.yaml` 模板
- `e2e.yaml` 模板

这里存放的是“如何构造资产”的模板，不存放某次执行结果，也不存放场景私有脚本。

新增 `cases.md` 必须保留 Branch 字段，并用 Observable Result / Pass Criteria 描述行为结果；没有分支、场景或 case ID 归属，或只描述代码结构/源码模式的一次性测试不能进入正式模板体系。
