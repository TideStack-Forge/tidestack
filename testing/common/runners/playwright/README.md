# Playwright Runners

这里放 Playwright 相关 runner 和桥接器，用来对接现有前端工程内的 Playwright 实现或平台浏览器 smoke。

## 当前能力

当前提供一个通用 browser asset bridge：

- 入口：`testing/common/runners/playwright/browser_asset_bridge.py`
- 目标：把 `testing/e2e/**/e2e.yaml` 中引用的浏览器资产统一落到标准化报告

它当前检查以下资产：

- contract 文档
- 相关源码入口文件
- 前端工程内声明的 npm 脚本
- repo 级 shell helper

每次执行都会生成 `browser-asset-bridge.json`，其中包含：

- preflight 检查
- 可执行脚本计划
- 实际执行结果
- 汇总 summary

## 脚本类型

当前 script entry 支持两类：

- `kind: npm`
  - 使用 `projectRoot + script`
- `kind: shell`
  - 使用 `command + workdir`

两类脚本都可以声明 `requiredPaths`，缺失时会被标记为 `blocked`，而不是盲目执行。

## 适用边界

这个 bridge runner 适合过渡阶段：

- 先确认场景已经接入真实浏览器资产
- 先判断脚本、合同、源码入口、依赖就绪度
- 再决定是否真的执行某个 npm/browser 脚本

它还不是最终的全自动 E2E orchestrator，但已经能把“浏览器资产是否真实存在、是否可运行、为什么被阻断”纳入统一证据模型。
