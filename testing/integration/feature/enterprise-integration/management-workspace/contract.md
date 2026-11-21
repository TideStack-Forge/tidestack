# 企业集成管理工作区行为契约

本场景核对原用户详情第三方登录管理，以及上下分区集成管理。组件浏览器测试使用生产 Schema 与真实 Amis 渲染，网络边界使用确定性响应；不能作为真实数据库、RBAC 过滤器、企业协议或线上迁移已经验收的证据。

另设独立的真实 HTTP 验收：启动正式 Mothership、隔离 MySQL/Redis，沿原登录及角色管理 API 创建受限操作员，不替换认证过滤器、权限服务或配置持久化。验证查看、配置、换密钥、诊断、企业 API 说明、登录配置、应用范围管理和授权服务器设置的权限隔离，以及双版本并发替换、拒绝后的版本保全和预算独立保存。测试连接保持停用，不调用三家企业 API；该证据也不能替代真实企业授权或供应商协议联调。

正式页面验收使用生产构建、原隔离网关和同一真实后端，不拦截或替换浏览器请求。逐一打开目录、通知和工作台页，核对原 Schema 加载及注册的 GET 接口；随后在同一页面连续换密钥两次，核对版本更新和密码输入清除。真实只读角色登录后只能查看企业连接，不加载应用列表。该层与组件浏览器夹具分别记录。

配置页面另设四个正式前后端场景：仅登录配置角色编辑停用 LDAP 身份源，保全其他身份源；仅授权服务器设置角色连续修改并恢复有效期；安全管理角色连续保存 DCR 数量限制，保持注册关闭；两个真实接入应用中，范围管理员只能编辑获准应用的授权字段。范围外应用沿原投影返回 404，越权修改安全字段返回 403，拒绝后数据和版本不变。各角色不请求无权管理区域的业务接口。

管理员查看和解绑沿原 UserSsoService、管理员 API 和用户管理权限；取消不写入，失败不显示成功，成功后刷新列表。外部连接与应用接入分别加载；仅登录配置权或仅授权服务器设置权时不能依赖应用列表。外部登录修改携带配置 revision，冲突保留输入，未编辑协议、未知字段及未替换密码保全。管理读取不返回已有密码。权限名迁移沿 RBAC 更新，只改正式标识及已知权限引用，保留全部范围和字段限制。

企业连接页的共享预算使用独立受保护配置及其 revision，保存预算不提交连接表单。孤立凭据维护仅显示脱敏状态，取消不写入，确认时提交连接与凭据两个 expectedRevisions，并在成功后刷新候选。后端在原配置事务内检查活动、停用及缺失插件连接的引用，拒绝清除有引用的凭据；浏览器夹具不替代该事务与权限验证。

连接诊断显式检查已保存且已启用的连接，操作权限独立于查看和配置。浏览器不提交草稿及密钥；失败保留输入。服务端沿原应用令牌协议发起新鲜检查，不读写或清除共享缓存；结束时复核连接代次，拒绝把旧代次结果当成新配置成功。检查不改变启停，也不证明目录、通知或回调权限已通过。

企业 API 说明页只贡献原 RPC 管理的导航，加载说明不调用企业 API。独立说明权限不授予原 RPC 管理及企业调用权限；原入口仍按原授权执行真实测试。

定向执行：仓库根目录运行相关 Maven 模块的行为测试；前端 main-app 运行 `./node_modules/.bin/playwright test --config=playwright.enterprise-integration.config.js`。浏览器与后端定向测试分别证明其组件边界，完整部署需另核对真实权限、配置事务和迁移维护窗口。

真实 HTTP 执行复用 `auth/sso-dynamic-config-oidc` 的隔离后端 fixture：按其运行说明创建专用空库、Redis、运行密钥及管理员密码，构建并启动正式后端后，在仓库根目录执行 `node testing/integration/feature/enterprise-integration/management-workspace/scripts/http-acceptance.cjs`。须设置 `SSO_ISOLATED_FIXTURE=1`、`SSO_ADMIN_PASSWORD`、`SSO_FIXTURE_ROOT`、`SSO_BASE_URL` 和 `SSO_EVIDENCE_DIR`；地址限本机。脚本会创建角色、用户及停用连接，要求首次运行的干净数据库；不得用于已有开发库或生产库。输出只记录 HTTP 状态及场景结论，不保存 token、密钥或请求正文。

正式页面验收在 HTTP 场景通过后执行：先运行 `npm --prefix frontends/web/main-app run build`；设置 `SSO_BACKEND_PORT` 为专用后端端口、`SSO_FRONTEND_PORT` 为另一个空闲本机端口，启动原 `auth/sso-dynamic-config-oidc/fixtures/gateway.cjs`。将 `SSO_BASE_URL` 指向该网关，在仓库根目录运行本场景 `scripts/browser-acceptance.cjs`。使用 main-app 已安装的 Playwright；只在登录完成、密码输入已清除后保存页面截图，不记录网络正文。退出后停止本次网关、后端及自有容器。

配置页面的 EIM-LIVE-004 至 007 使用同样的生产构建、专用后端和网关，运行 `node testing/integration/feature/enterprise-integration/management-workspace/scripts/configuration-acceptance.cjs`。该脚本可独立运行，不依赖企业连接种子；专用实例的 DCR 必须保持关闭。脚本创建一次性角色、用户及两个停用应用，结束时恢复原 LDAP、授权服务器和 DCR 配置；角色、用户、应用随本次专用数据库销毁。状态和结论写入 `enterprise-configuration-browser.jsonl`，截图不包含登录密码或令牌。四个场景的整体通过记录在配置恢复成功后写入。
