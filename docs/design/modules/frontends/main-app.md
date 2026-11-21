# main-app 模块设计摘要

## 模块定位

`frontends/web/main-app` 是 Ouroboros Web 前端基座主应用，作为 `MainAppHost` 承担主应用的架构所有者职责，负责用户态初始化、微前端注册与挂载，以及平台级前端能力的统一出入口。这里的 `shell` 仅指浏览器侧的视觉框架/主壳层；当文档提到 `main-app host bootstrap proof` 时，特指主应用宿主启动验证面。其他微前端和 SDK 都围绕这个宿主协作。

## 责任边界

该模块负责前端主应用启动、登录/用户初始化、主题与 i18n 初始化、微前端容器管理和组件库加载；它不负责后端模型定义、不负责某个独立微前端的具体业务交互，也不负责 SDK 的公共 API 定义。作为 `MainAppHost`，它定义主应用的责任边界；作为 `main-app host bootstrap proof` 的承载面，它覆盖主应用宿主启动、认证种子、路由落地和宿主框架渲染；作为 `shell`，它只覆盖主壳层的视觉框架与装载容器，不等同于全部前端代码的实现容器。

## 依赖关系

从源码和依赖看，`main-app` 建立在 React 18、Amis 和 `qiankun` 之上，同时消费 `ouroboros-sdk`、后端配置接口和微前端资源入口。它与 `platform/ouroboros-develop`、`core/ouroboros-ui-model` 和 `ouroboros-dev-platform-component-lib` 的边界非常紧密，是前后端集成的关键接缝。

## 关键抽象

- `installHostRouter` 组装路由拓扑和导航守卫；`tabNavigationCommand → routerEngine` 是标签命令的正式提交入口。守卫接受后先投影页签状态，再提交 URL 与通知订阅者；取消和被替代的导航受同一序列围栏约束。历史 `preventTabOperation` 全局重入标记已移除。
- [共享 Amis runtime](../../../../frontends/web/ouroboros-amis-runtime/README.md) 承载 Web/IDE 共用 renderer、preset 和样式；主应用提供显式宿主端口，IDE 通过自身适配器消费同一份行为，不导入主应用私有源码。

- `initMain()`：主应用初始化入口，负责 app 配置、组件库、用户态和 React 根节点挂载。
- `common/micro/`：微前端注册、挂载和组件库加载的核心目录。
- `loadComponentLibrary.ts` 与 `common/micro/registerComponentLibrary.ts`：组件库加载与注册入口。
- `qiankun` 集成：承接微前端应用与组件库的运行时装载。
- `src/main.ts` / `src/layouts/` / store：React 主壳层启动、布局与状态管理。
- `Sidebar.tsx -> ScrollBar -> MenuComponent`：主壳导航的正式编排主路径。`Sidebar.tsx` 既是 `shell sidebar surface` 的唯一入口，也负责把 `collapsed search entry`、footer user section 和 sidebar body 的共享滚动宿主挂到当前 React shell 上；同时它也是 `activeTabId + breadcrumb` 进入 shell menu runtime 的唯一 sidebar 挂点。
- `SearchBar.tsx -> ScrollBar -> openMenuItem()`：主壳搜索的正式编排主路径。`SearchBar.tsx` 统一承接展开态输入框、`floating search panel`、`menu identity payload` 和搜索结果列表；当前只修“搜索输入侧的菜单身份真相”，不在这里另起 active/open 输出链路。
- `UserMenu.tsx`：`user menu trigger card` 的唯一正式入口，负责 avatar initial、`menu-arrow` 资产和 `top-end` upward dropdown。
- `ScrollBar.tsx`：主壳共享的 `shell scrollbar runtime`，同时服务 sidebar body 与 search results；它暴露 `scrollbar visibility mode`（`always` / `hidden` / `auto`），并固定 `1000ms` idle hide、`120ms` 内容变化反馈和 `body.is-c-scrollbar-dragging` 全局拖拽态。
- `Tabs.tsx -> tabsScroll.ts -> tabs.scss`：主壳工作区页签的正式编排主路径。`Tabs.tsx` 继续持有唯一 `tab strip scroll host`（`.app-tabs__wrapper.app-tabs__scroll`）并承接 active tab reveal、drag-sort、release settle、context menu 与 arrow hold runtime；`tabsScroll.ts` 集中定义 `100px` 短按 smooth scroll、`180ms` 长按延迟、每帧 `8px` 连续滚动和 direct fallback clamp；`tabs.scss` 集中表达 `track edge shadow`、`track line overhang`、hidden arrow、svg control icon 与 active tab 底边融合。

## 关键子模块

- `src/common/micro/`：微前端和组件库集成主线。
- `src/common/i18n/`：国际化初始化与管理。
- `src/layouts/`、`src/pages/`：主应用 UI 壳层和页面入口；其中 `Sidebar.tsx`、`SearchBar.tsx`、`UserMenu.tsx` 与 `ScrollBar.tsx` 共同构成当前 main-app shell 的正式导航表面，`Tabs.tsx + tabsScroll.ts + tabs.scss` 共同构成 workspace tab strip 的正式工作区页签表面。稳定术语包括 `shell sidebar surface`、`collapsed search entry`、`floating search panel`、`menu identity payload`、`user menu trigger card`、`shell scrollbar runtime`、`scrollbar visibility mode`、`active trail`、`ancestor state`、`opened state`、`menu sync key`、`auto expand on tab switch`、`indent step`、`workspace tab strip`、`tab strip scroll host`、`arrow smooth scroll`、`arrow hold scroll`、`trailing arrow click suppression`、`track edge shadow` 与 `track line overhang`。当前菜单 runtime 已正式收口为 `Sidebar.tsx -> MenuTree/MenuDualMode/MenuFloatMenu` 共享同一组 `activeTabId + breadcrumb` 输入：`menu sync key` 由 `activeTabId + breadcrumb key chain` 拼成，tree/dual/float menu 统一按真实菜单 `key` 解释 `active trail`，其中叶子显示 `active`、父分支显示 `ancestor state`、展开分支写回 `opened state`；tab 切换后的自动展开只能由集中开关 `AUTO_EXPAND_ACTIVE_MENU_ON_TAB_SWITCH` 控制。二级及以上菜单缩进统一消费 `--menu-indent-step`，submenu 展开保持 `tree-menu__submenu-wrapper + tree-menu__submenu-inner` 的 grid 文档流方案，不再退回递归 `max-height` 状态机。当前 tabs runtime 保持 `.app-tabs__wrapper.app-tabs__scroll` 为唯一 scroll host：短按箭头请求 `100px` smooth scroll，长按 `180ms` 后进入每帧 `8px` auto scroll，`mouseup` / `blur` 立即停止，长按释放后的尾随 click 只消费一次；当 `scrollBy` 不可用时，fallback 直接 clamp `scrollLeft` 并同步刷新 arrow / edge shadow。tabs 的 wheel scroll、`moveToTarget()`、drag-sort、release settle 与 context menu 继续共用同一 React runtime，不新建第二套 tabs 组件、store 或全局滚动 runtime。
- `frontends/web/ouroboros-amis-runtime/src/`：Web/IDE 共用的 Amis renderer、editor 和样式；以下 renderer 与 style 路径以共享包为根，主应用通过宿主绑定接入；当前正式的 runtime patch 主链由 `renderers/patch/Table.tsx + Table.scss`、`renderers/patch/CRUD.tsx + CRUD.scss + crudFilterGridLayout.ts`，以及 `renderers/patch/Form.tsx + formSmartLayout.ts` 组成：`OuroborosTable` 统一承载 affixHeader 对齐修复、列配置持久化、表头搜索/筛选/排序、grouped export 和列设置弹层样式契约；`OuroborosCRUD` 统一承载 CRUD headerToolbar/filter 编排、`columns-toggler` 注入、当前页/全部导出收口、`filter.actions -> button-toolbar` 归一，以及 CRUD filter 专用 smartLayout 运行时。当前 CRUD filter 的正式顺序固定为“先 `normalizeFilterSchema()`，再在 smartLayout 打开时 `normalizeCrudFilterGridLayout()`，然后用当前 DOM measurements 推导 render-state，最后才应用 mode/body/class 变换”；toolbar 缺失或 measurements 不足时会直接回退到 disabled render-state，不保留第二套旧 state 真相。CRUD filter 的 mode derivation 先由 `buildCrudFilterLayoutContext()` 暴露 `unsplitRowCount`、`shouldSplitWideHead`、`tailRowsCode`、`tailFitCode` 等中间事实，再压成最终 mode：single-row wide candidate 回到 compact topology `C1P / C1E`，只有 rendered wide-head continuation 或确实多行的 wide 场景继续进入 `W*`。`crudFilterGridLayout.ts` 只是这条链路的 barrel，正式 helper 边界已经收口为 `crudFilterLayoutConstants.ts`、`crudFilterGridSchema.ts`、`crudFilterLayoutMode.ts`、`crudFilterLayoutBody.ts`、`crudFilterLayoutMeasurement.ts` 与 `crudFilterAffordance.ts`：其中 `crudFilterLayoutMeasurement.ts` 独立承接 toolbar/label/railTop DOM facts 与 ResizeObserver target 切换，`CRUD.tsx` 只保留 runtime shell、单次 RAF layout sync 和 measurements 写回；grid schema 会清洗 legacy `columnCount` / `columnsCount`，desktop 容量固定按 `5 / 4 / 1` 档位推导，并在 inline toolbar 场景输出语义 root class `ouroboros-crud-filter-toolbar-item`，而不是继续复用 grid toolbar marker；这个 inline toolbar root 会在 `CRUD.scss` 内局部把 trailing `button-toolbar` gap 收紧到 `4px`，而 rail / stacked / mobile 仍沿用通用 toolbar gap。`OuroborosCRUD` 还把 effective selectable 统一解释为“active bulkActions toolbar + bulkActions、`pickerMode` 或 `selectable`”，并且只在 body schema 未显式声明时默认补 `checkOnItemClick: true`；编辑器侧由 `frontends/web/ouroboros-amis-runtime/src/editor/plugins/patch/CRUD.ts` 镜像同一条默认值真相，让默认 `true` 只作为派生默认，不额外写回 schema。`OuroborosForm` 则统一承载普通 Form 的 opt-in smartLayout 归一，只在根 schema 显式 `smartLayout: true` 时推导 `mode: 'normal' + columnCount: 1|2`，并对 `group` / `flex` / 精调 fieldset 保持旁路。主壳 table/filter theme contract 则固定沿 `src/style/themes/index.scss -> light.scss/dark.scss -> defaults.scss -> src/style/vendor/amis/adaptations/table.scss -> renderers/patch/CRUD.scss|Table.scss` 发射与消费，其中 vendor table adaptation 只保留表格通用变量，不再覆盖 `--table-header-bg` / `--table-row-bg` 这类由 theme defaults 发射的背景 token；同时沿用 `lts-v1` 的未设计内容面语义：light / dark 两套 `--table-row-bg` 都保持 `tokens.$color-bg` 白底，dark 主题下 `--table-header-bg` 与 CRUD filter panel 背景暂取 `var(--color-bg-body)`，不提前切成深色内容面。编辑器侧通过 `frontends/web/ouroboros-amis-runtime/src/editor/plugins/patch/Table.ts`、`CRUD.ts` 与 `Form.ts` 把 AMIS editor 节点接到这三条正式 runtime 真相上；其中 Form editor 额外通过 `formSmartLayoutPanel.ts` 只在普通 Form 的“外观 -> 布局”区域注入默认关闭的 `smartLayout` 开关，并继续旁路 CRUD filter 上下文。对于通过 `render(...)` 代理单个 AMIS 子控件的 renderer proxy control，`label: false` 与 `wrap: false` 必须成对出现；`WhereEditor`、SQL editor panel 这类复合编辑器不沿用这条 proxy 约束。
- `src/dev-tools/`：开发辅助前端能力。

## 扩展机制

`main-app` 的主要扩展面是微前端挂载、组件库注册和通过 SDK 注入的平台能力。AMIS renderer/editor 的正式扩展入口则固定在 `frontends/web/ouroboros-amis-runtime/src/renderers/patch/index.ts` 和 `frontends/web/ouroboros-amis-runtime/src/editor/plugins/patch/index.ts` 这两条静态注册链上：页面 schema 继续只声明 `crud` / `table` / `form` 等既有类型，不允许在页面 helper 或普通 Form patch 再起第二套 CRUD toolbar/filter 解释路径。相关边界治理可参考 [../../cross-cutting/integration-boundaries.md](../../cross-cutting/integration-boundaries.md)；与后端扩展机制的配合可参考 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- `推荐阅读`：[frontends/web/main-app/README.md](../../../../frontends/web/main-app/README.md)
- `补充深度资料`：[前端基座菜单扩展机制设计（菜单徽标 + 用户菜单注册）](../../../../frontends/web/main-app/docs/menu-extension-design.md)
- `补充深度资料`：[菜单扩展机制使用指南（徽标 + 用户菜单注册）](../../../../frontends/web/main-app/docs/menu-extension.md)
- `补充深度资料`：[frontends/web/main-app/docs/amis-plugin-mechanism.md](../../../../frontends/web/main-app/docs/amis-plugin-mechanism.md)
- `补充深度资料`：[frontends/web/main-app/docs/i18n-design.md](../../../../frontends/web/main-app/docs/i18n-design.md)
- `补充深度资料`：[frontends/web/main-app/docs/permission.md](../../../../frontends/web/main-app/docs/permission.md)

## 演进状态

`main-app` 仍处于持续演进状态，但主壳层边界已经比较明确。后续维护应继续把“壳层职责”和“独立微前端职责”分开，避免主应用重新吞回过多独立模块逻辑。
