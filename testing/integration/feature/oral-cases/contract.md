# Oral Feature Verification Contract

## Objective

把“新增功能启动后，按口头或即时指定用例执行验证”的临时联调过程，收敛成可复盘、可留档、可晋升的正式 harness 场景。

## Scope

- 在范围内：
  - 新功能主链路验证
  - 口头指定用例转写为结构化 case
  - 前后端联调、页面交互、接口回查
  - 必要的权限边界或上下文验证
- 不在范围内：
  - 已稳定功能的长期 smoke / regression 归档
  - 与当前 feature 无关的泛化平台回归

## Pass Criteria

- 口头指定用例被转写到 `cases.md`
- 本轮依赖、运行时、前端入口、账号上下文被明确记录
- 执行结果能区分：
  - 环境阻断
  - 功能缺陷
  - 未覆盖/待补资产
- 新增经验能回填到统一 playbook 或被抽象成正式 smoke / regression 场景

## Evidence Requirements

- `tmp/testing-evidence/feature-oral-cases/**` 下的 harness 报告
- 必要的日志、截图、网络请求和接口回查记录
- 本轮新增或修订的 case 表
