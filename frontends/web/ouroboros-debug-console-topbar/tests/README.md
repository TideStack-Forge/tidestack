# Debug Console Topbar Testing Standard

`frontends/web/ouroboros-debug-console-topbar` 当前仅维护前端单元测试。按前端子树统一分类，它属于仅有 `unit`、暂未引入 `integration` 的包。

## Placement Rules

- 不要把测试文件放到 `src/` 或 `src/__tests__/`。
- 包级守卫与配置测试放到 `tests/unit/config/`。
- 面向源代码或入口装配的测试放到 `tests/unit/src/`。

## Commands

- `npm run test:unit`
- `npm test`
