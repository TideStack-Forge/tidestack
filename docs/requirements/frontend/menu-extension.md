# 前端基座菜单扩展规格（菜单徽标 + 用户菜单注册）

> 状态：Accepted（2026-09-12 依据实现补写）
> 适用范围：`frontends/web/main-app` 主菜单/辅助菜单的运行时徽标、菜单树运行时增删、用户菜单注册制，以及 `frontends/web/ouroboros-sdk` 对微前端暴露的同名能力。
> 相关设计：[menu-extension-design.md](../../../frontends/web/main-app/docs/menu-extension-design.md)（Accepted）；使用指南：[menu-extension.md](../../../frontends/web/main-app/docs/menu-extension.md)；关联规格：[审批流外部引擎对接规格](../workflow/external-engine.md)

## ① 背景与问题

1. 菜单树完全由后端下发（`/api/authored-menu-tree` → `user/updateMenu` → store），前端运行时状态没有合法落点；徽标若写回菜单树对象会被 `normalizeMenu` 的树重建冲掉，因此必须是与菜单树分离的装饰层。
2. 业务微应用无法表达"待办数 / 红点 / NEW / HOT"等提示，用户菜单也硬编码"修改密码 / 切换主题 / 退出"，业务方无法登记自己的菜单项。
3. 微应用挂载可能早于平台能力注入，命令式调用必须有 pending 语义，否则调用静默丢失。

## ② 目标

1. 主菜单（`globalMenu`）与辅助菜单（`globalSubNav`）支持 `number / dot / text / ribbon` 四种徽标，按菜单引用可设置、修改、清除。
2. 徽标设置提供主应用 import、AMIS `setMenuBadge` action、SDK 三种通道；菜单树操作和用户菜单注册通过 import 或 SDK 调用。
3. SDK 提供菜单查询、遍历、增删；查询/遍历结果必须携带徽标状态。
4. 用户菜单改为注册制，内置三项以注册方式进入，支持查询、注册、移除。
5. 不破坏既有数据流：`updateMenu`、`devWorkspaceMenuSync`、i18n 翻译、面包屑、搜索、Tab 联动行为不变。

## ③ 非目标

- 不做后端持久化：徽标与运行时菜单增删均为会话内存级，刷新后回退后端菜单树。
- 不挂 `window` 全局对象；不做跨 Tab 同步（BroadcastChannel）。
- 不为 `setMenuBadge` 提供 amis 动作面板（`registerActionPanel`）配置界面。
- 不重构菜单渲染组件本身，不改变现有 DOM 结构与类名；不在运行时校验 amis action 传入的徽标结构合法性。

## ④ 用户故事

1. 作为业务微应用开发者，我希望按菜单全名设置数字徽标，以便把待办数标在主菜单上。
2. 作为 amis 应用作者，我希望用 `setMenuBadge` action 改徽标，以便不改前端代码就能加提示。
3. 作为基座开发者，我希望用 `menu.getMenus()` 读出带徽标的菜单树，以便做自定义导航面板。
4. 作为业务方，我希望注册"我的待办"到用户菜单，以便用户从头像下拉直达业务入口，且卸载时自动清理不残留。

## ⑤ 功能规格

**徽标**

- `menu.setMenuBadge(ref, badge)`：`badge` 为 `null` 即清除；`ref` 为空串时**静默 no-op**（`registry.ts:182-193`）。
- `number`：`max` 缺省 99，溢出渲染 `99+`；`value <= 0` 且未显式 `showZero: true` 时**不渲染任何节点**（`MenuBadge.tsx`）。
- `dot` / `text` / `ribbon`：`ribbon` 的 `position` 缺省右侧；`color` 优先于 `tone`（`tone` 缺省 `danger`）。
- **形态互斥**：同一引用同一时刻只保留一个徽标，后设覆盖前设。
- **未注册菜单**：目标菜单不存在时不报错，徽标挂起保留，菜单后续加载后按引用自然命中。
- **未知 `type`**：渲染组件不校验枚举，落到最后的 `ribbon` 分支用 `text` 渲染（`MenuBadge.tsx` 末尾 `return`）——这是 amis action 传任意 JSON 时的实际表现。

**菜单树操作**

- `menu.getMenu(ref)` / `getMenus({scope, flat})` / `traverseMenus(visitor, {scope})`：`ref` 可为 `fullName / path / url / fullPath / router_id / name`；`visitor` 返回 `false` 跳过该子树；输出为副本并合并徽标。
- `menu.addMenuItem({scope, item, parentFullName?, index?})` 返回 `boolean`：引用缺失、引用已存在、父节点不存在 → 返回 `false` 且菜单树不变；`index` 越界被收敛到 `[0, length]`（`registry.ts:331-362`）。
- `menu.removeMenuItem(ref)` 返回 `boolean`：删除节点及子树并**级联清理该子树全部徽标**；未命中返回 `false`（`registry.ts:368-409`）。
- 运行时增删经 `user/updateMenu` 落回 store，复用既有 normalize / 翻译 / 面包屑链路。

**用户菜单注册**

- `userMenu.register(item, owner?)` 返回 `id`；缺 `title` 或 `onClick` 不是函数时返回**空串**（不抛异常，`registry.ts:427-430`）；同 `key`（或同 `id`）重复注册视为**覆盖**，保留原 id，幂等。
- `unregister(id)` 未命中为 no-op；`unregisterAll(owner)` 传空值为 no-op（`registry.ts:454-481`）。
- 渲染按 `order` 升序（缺省 100）；`visible` / `disabled` 渲染时求值；`titleKey` 存在时按 common 命名空间翻译。

**生命周期与拒绝语义**

- 菜单树重建（updateMenu / 重新登录 / locale 切换）：徽标保留，按引用重新命中。
- 用户登出（`user/LOGOUT`）：清空全部徽标与全部非内置用户菜单项（`registry.ts:498-507`）。
- 微应用卸载（SDK）：清理该实例注册的用户菜单项；**徽标保留**。

**SDK（`ouroboros-sdk`）**

- 导出 `menu` / `userMenu` 活绑定桩（`main.ts:798-799`、`848-849`），平台注入后直达真实实现（`main.ts:901-913`）。
- 注入前：`setMenuBadge / addMenuItem / removeMenuItem / register` 进入 FIFO pending 队列（`main.ts:715-792`）；其中 `addMenuItem / removeMenuItem` **返回 `false`**，`register` 返回占位 id `__pending-user-menu-N`。
- 注入前查询类（`getMenuBadge / getMenu / getMenus / traverseMenus / getItems`）返回 `undefined` 或 `[]`（`main.ts:724-737`、`783-785`）。
- 未进入生命周期就发生的注册落在"无卸载钩子"的兜底集合，不会自动清理（`main.ts:690-691`）。

## ⑥ 验收口径

- [ ] `number` 徽标在双栏 / 树形 / 悬浮三个渲染面均可见；`value: 0` 不渲染，`value: 200` 渲染 `99+`。
- [ ] `setMenuBadge(ref, null)` 后徽标消失；`setMenuBadge('', badge)` 不产生任何状态变化。
- [ ] 菜单刷新（`updateMenu`）后徽标仍在，且清除徽标不影响菜单树本身。
- [ ] `addMenuItem` 在引用冲突或父节点缺失时返回 `false` 且菜单树不变；成功时新节点出现在 store 菜单树中。
- [ ] `removeMenuItem` 删除子树后，该子树所有菜单的徽标一并消失。
- [ ] `userMenu.register` 缺 `onClick` 时返回空串且用户菜单不新增条目；同 `key` 重复注册不产生重复项。
- [ ] 登出后非内置用户菜单项与全部徽标清空、三个内置项仍在；微应用卸载后其注册项消失且徽标不受影响。
- [ ] `bash scripts/docs/check-links.sh` 输出 `No broken links found`。

## ⑦ 已知限制与未实施项

- 徽标与运行时菜单增删不持久化，刷新即失；`devWorkspaceMenuSync` 全量覆盖会清掉运行时新增节点（设计文档 §生命周期与清理，代码未逐条复核）。
- 徽标只接受纯文本、不接受 HTML；无跨 Tab / 跨设备同步；无 amis 动作面板配置界面。
- 未核实：主菜单/辅助菜单徽标的完整样式类名与无障碍标注清单（仅在 `MenuBadge.tsx` 确认 `aria-hidden="true"`）。

此前 `getMenus`、`removeMenuItem` 与 `unregisterAll` 的设计签名差异已在共同设计正文修正。当前接口边界见该正文；本规格的验收清单仍需运行验证。

## ⑧ 相关文档

- 设计：[menu-extension-design.md](../../../frontends/web/main-app/docs/menu-extension-design.md)；使用指南：[menu-extension.md](../../../frontends/web/main-app/docs/menu-extension.md)
- 关键实现：[registry.ts](../../../frontends/web/main-app/src/common/menus/registry.ts)、[MenuBadge.tsx](../../../frontends/web/main-app/src/layouts/menu/MenuBadge.tsx)、[SetMenuBadge.js](../../../frontends/web/ouroboros-amis-runtime/src/actions/SetMenuBadge.js)、[main.ts](../../../frontends/web/ouroboros-sdk/src/main.ts)、[types.ts](../../../frontends/web/ouroboros-sdk/src/types.ts)
- 测试规范：[frontends/web/TESTING.md](../../../frontends/web/TESTING.md)
