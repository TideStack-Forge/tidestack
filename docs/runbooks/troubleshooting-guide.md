# 故障排查指南

本指南总结了常见的故障排查方法论和最佳实践，帮助快速定位和解决问题。

## 问题诊断标准流程

### 1. 拦截网络请求（第一步）

**目的**：获取实际数据，避免基于假设诊断

```javascript
// Playwright 示例
page.on('request', request => {
  if (request.url().includes('/api/')) {
    console.log('请求:', request.method(), request.url());
    console.log('请求体:', request.postData()); // 同时容纳 JSON、表单和非 JSON 请求
  }
});

page.on('response', async response => {
  if (response.url().includes('/api/')) {
    console.log('响应:', response.status());
    const body = await response.text();
    console.log('响应体:', body); // 先保留原文，避免 HTML/纯文本响应触发 JSON 解析异常
  }
});
```

**检查清单**：
- [ ] 请求体是否包含异常数据（null、undefined、格式错误）？
- [ ] 响应状态码是什么（200、400、500）？
- [ ] 响应体中的错误码和错误信息是什么？
- [ ] 请求头是否正确（Content-Type、Authorization）？

### 2. 检查后端日志（优先级最高）

**原则**：API 返回错误时，第一时间查看后端日志

```bash
# 查看最近的错误日志
docker logs <container> --tail 200 | grep -A 10 "Exception"

# 查看特定时间段的日志
docker logs <container> --since "2026-04-26T12:00:00"

# 实时监控日志
docker logs <container> -f
```

**关键信息**：
- [ ] 异常链、最内层 cause 与触发上下文（第一行不一定是根因）
- [ ] 出错的代码文件和行号
- [ ] 异常类型（NullPointerException、IllegalArgumentException 等）
- [ ] 相关的业务上下文（用户 ID、请求参数等）

### 3. 检查前端状态

**浏览器开发工具检查顺序**：
1. **Console**：JavaScript 错误、警告
2. **Network**：请求/响应详情、时序
3. **Application**：LocalStorage、SessionStorage、Cookies
4. **React/Vue DevTools**：组件状态、Props

### 4. 验证修复

**验证层次**（从下到上）：
1. **单元测试**：修复的代码逻辑是否正确
2. **API 测试**：接口是否返回预期结果
3. **E2E 测试**：完整用户流程是否正常
4. **数据库验证**：数据是否真正持久化

## 字段定位标准流程

### 问题场景
需要在代码库中定位某个配置项、字段或功能的定义位置。

### 标准流程

#### 1. 定向搜索

先用项目文本搜索定位配置定义与调用方，再按语言使用 ast-grep 查看语法关系：

```bash
rg -n 'appName' -g '*.json' -g '*.java'
```

沿配置定义、UI Schema、API 和服务调用定位正式路径。独立查询可同时执行；是否委派 Agent 依当前任务授权和工具能力决定，不要求固定数量的 Agent，也不依赖某个宿主的专有 `task()` 接口。

#### 2. 数据库查询

先通过配置管理 API 和日志检查持久化结果。以下 SQL 仅供已获授权的运维人员在目标环境只读诊断，表名以实际部署 Schema 为准；应用代码仍通过 ouroboros-data QueryDSL，不可把诊断 SQL 引入业务路径。

```sql
-- 查找配置表中的键名
SELECT * FROM t_sys_configuration_value WHERE `key` LIKE '%appName%';

-- 查找表结构
SHOW CREATE TABLE t_sys_configuration_value;
```

#### 3. 浏览器验证

**步骤**：
1. 打开页面到目标位置
2. 右键 → 检查元素
3. 查看实际 DOM 结构
4. 确认选择器（name、placeholder、data-testid）

**常见错误**：
- ❌ 假设 `input[name="username"]` 存在
- ✅ 检查后发现实际是 `input[placeholder="请输入用户名"]`

#### 4. 失败后立即调整

**规则**：失败 2 次后必须切换方法

```
尝试 1: 手动 grep → 失败
尝试 2: 手动查找文件 → 失败
尝试 3: ❌ 继续手动尝试 → 浪费时间
尝试 3: ✅ 按调用链核对定义、配置和消费方，依据证据调整搜索
```

## 测试安全准则

### 配置项风险分级

| 风险等级 | 配置类型 | 示例 | 测试策略 |
|---------|---------|------|---------|
| 🔴 高危 | 认证、授权、安全 | `security.auth.login.localPassword.enabled` | **禁止**在测试中修改 |
| 🟡 中危 | 功能开关、业务逻辑 | `feature.approval.enabled` | 谨慎使用，测试后立即恢复 |
| 🟢 低危 | 显示文本、UI 配置 | `appName`、`logo` | 可安全用于测试 |

### 测试脚本白名单机制

```javascript
// 定义安全的测试配置项
const SAFE_TEST_CONFIGS = [
  'appName',           // 应用名称
  'systemDescription', // 系统描述
  'copyrightText'      // 版权信息
];

// 定义禁止修改的配置项
const FORBIDDEN_CONFIGS = [
  'security.auth.login.localPassword.enabled',
  'security.auth.token.storageType',
  'security.session.timeout'
];

// 测试前检查
function validateTestConfig(configKey) {
  if (FORBIDDEN_CONFIGS.includes(configKey)) {
    throw new Error(`禁止在测试中修改高危配置: ${configKey}`);
  }
  if (!SAFE_TEST_CONFIGS.includes(configKey)) {
    console.warn(`警告: ${configKey} 不在安全配置白名单中`);
  }
}
```

### 备份与恢复机制

```javascript
// 测试前备份
const originalValue = await getConfigValue('appName');

try {
  // 执行测试
  await updateConfig('appName', 'test_value');
  await verifyConfig('appName', 'test_value');
} finally {
  // 测试后恢复
  await updateConfig('appName', originalValue);
  console.log(`已恢复配置: appName = ${originalValue}`);
}
```

## 常见错误模式与避免方法

### 1. 测试脚本误操作关键配置

**错误示例**：
```javascript
// ❌ 使用认证配置做测试
await updateConfig('security.auth.login.localPassword.enabled', false);
// 结果：登录框消失，系统无法登录
```

**正确做法**：
```javascript
// ✅ 使用安全的配置项
await updateConfig('appName', 'test_value');
```

**避免方法**：
- 建立配置项风险分级制度
- 实施白名单机制
- 测试前备份，测试后恢复

### 2. 选择器假设而非验证

**错误示例**：
```javascript
// ❌ 假设 name 属性存在
await page.locator('input[name="username"]').fill('admin');
// 结果：元素未找到，name 属性实际为空
```

**正确做法**：
```javascript
// ✅ 先检查实际 DOM，再写选择器
// 实际检查发现：<input placeholder="请输入用户名">
await page.locator('input[placeholder="请输入用户名"]').fill('admin');
```

**避免方法**：
- 写选择器前先用浏览器开发工具检查实际 DOM
- 使用多种选择器策略（name、placeholder、label、data-testid）
- 添加详细的元素检查日志

### 3. 盲目尝试而非系统搜索

**错误示例**：
```
尝试 1: 手动查找 → 失败
尝试 2: 再次手动查找 → 失败
尝试 3: 继续手动查找 → 失败
尝试 4: 还是手动查找 → 失败
```

**正确做法**：
```bash
rg -n 'keyword' -g '*.json' -g '*.java'
```

先缩小到相关模块，分别核对配置、UI Schema 和服务端定义。连续失败时检查假设、搜索范围和命名，再更换定位方法；增加 Agent 数量不能替代根因调查。

### 4. 关注表象而非根因

**错误示例**：
```
现象：登录框消失
调试：检查前端状态管理、Redux store、组件生命周期...
结果：花费大量时间，未找到根因
```

**正确做法**：
```
1. 拦截网络请求 → 发现 API 返回错误
2. 检查后端日志 → 发现 NullPointerException
3. 定位代码行 → 找到根因
4. 修复代码 → 问题解决
```

**避免方法**：
- 优先查看后端日志（API 错误时）
- 基于实际数据（请求体、响应体、日志）而非猜测
- 从下往上诊断（后端 → 前端 → UI）

### 5. 忽略缓存机制

**错误示例**：
```sql
-- 直接修改数据库
UPDATE t_sys_configuration_value SET value = 'true' WHERE `key` = 'xxx';
-- 结果：配置未生效，因为应用使用了缓存
```

**正确做法**：
```bash
# 修改配置后重启服务
docker restart <container>

# 优先通过配置管理 API 修改；先 GET 配置列表取得目标 key 的最新 revision
# 以下 expectedRevision 必须替换为本次读取值；冲突后重新读取，不覆盖他人更新
curl -X PUT "$BASE_URL/api/sys/configuration" \
  -H 'Content-Type: application/json' \
  --data '[{"key":"appName","value":"test_value","expectedRevision":"<本次读取的revision>"}]'
```

**避免方法**：
- 了解系统的缓存策略（Spring Cache、Redis 等）
- 通过正式 API 更新后核对缓存失效事件与读取结果；手工数据库改动不会触发该链，必要时按目标实例范围恢复，不能把每次修改都要求重启
- 通过 API 验证配置是否真正生效

### 6. 只看页面现象，不校验真实状态接口

**错误示例**：
```
现象：详情页没有显示“已克隆”或“开始编辑”
调试：反复点击页面、猜测路由、怀疑按钮渲染
结果：花费大量时间，但没有先确认后端到底返回了什么状态
```

**正确做法**：
```
1. 先找到该页面依赖的真实状态接口
2. 直接检查响应体中是否已有关键字段
3. 例如先确认 isCloned / editUrl / editName 是否正确
4. 如果接口正确而 UI 不对，再检查入口上下文、弹窗层级和渲染分支
```

**避免方法**：
- 对按钮态、徽标态、只读/可编辑态，优先看真实 API 返回，而不是只看页面表象
- 详情页、弹窗页、列表行二级详情往往依赖调用入口上下文，不能默认裸 URL 等价
- 后端状态正确但前端未切换时，优先排查渲染分支、弹窗层级和页面入口

### 7. 本地运行时依赖失配，被误判为业务代码故障

**错误示例**：
```
现象：本机启动 develop 后端时出现 NoClassDefFoundError 或 Application run failed
调试：直接怀疑最新业务改动、Spring 配置或接口逻辑
结果：根因其实是本地 Maven 仓库里的 runtime 依赖或 SPI 提供者失配
```

**正确做法**：
```bash
# 对可疑 runtime 模块重新 install
mvn -pl <runtime-module> -am -DskipTests -Djacoco.skip=true install

# 然后重新生成或复用现有 runtime classpath 再启动后端
curl http://127.0.0.1:<port>/actuator/health
```

**避免方法**：
- 遇到 `NoClassDefFoundError`、启动期 `Application run failed` 时，先检查本地 runtime 依赖是否陈旧或不一致
- 不要把启动期 classpath / SPI 问题直接归因为当前业务改动
- 修复后用 `/actuator/health` 验证服务是否真正就绪，再进入浏览器 E2E

### 8. 配置与运行时行为不一致，先核对动态元数据和挂载扩展

**错误示例**：
```
现象：`configuration.json`、镜像内资源或仓库源码看起来都正确，但页面或运行时行为仍然不一致
调试：直接把问题归因为前端缓存、数据库脏数据或“代码没发布”
结果：忽略了运行时还可能从 SPI 提供动态配置元数据，或被容器挂载的 `extend/` 目录覆盖
```

**正确做法**：
```
1. 配置行为与当前 `.configuration.json` typed 定义（旧 `configuration.json` 仅兼容）不一致时，先检查是否存在 `ConfigurationMetadataProvider` 一类的动态元数据提供者
2. 确认最终生效的配置元数据来自静态文件、SPI 扩展，还是两者叠加
3. 容器内行为与源码/镜像不一致时，检查挂载的 `extend/` 目录是否覆盖了镜像中的类或资源
4. 先确认运行时实际加载的内容，再判断是代码问题、部署残留还是扩展优先级问题
```

**避免方法**：
- 统一配置元数据不一定只来自 `configuration.json`；排查配置展示或默认值异常时，要先检查动态 provider
- 容器化部署里，宿主机挂载的 `extend/` 目录常常拥有更高优先级；源码正确不代表运行时实际加载的也是同一份内容
- 当“源码、镜像、运行时”三者不一致时，优先验证运行时类路径和挂载目录，而不是只比对仓库文件

## 多用户突然无法登录或无菜单

若多个原本正常的账号同时提示密码错误、菜单未配置，且重启恢复，先按日志行内时间检查用户、用户角色和角色权限读取是否失败。日志滚动文件名不能证明故障日期；对大日志先用时间和异常关键词定位行号，再读取有限上下文及 cause，避免全量输出。

旧版本会将 RBAC 查询失败返回的空列表写入默认内存缓存，持续影响后续请求。修复版本的失败加载以异常结束，不写缓存，数据库恢复后下次请求会重新读取；真正成功的空列表仍会缓存。dev 的直接用户/密码查询也区分“查不到”与“查询失败”，后者返回安全服务故障提示。

对外内部错误是 HTTP `500` / code `50000` / `服务暂时不可用，请稍后重试`，原因从同一请求的服务端完整 cause 查找；客户端不显示内部细节。宿主已持有 token 而初始化失败时，应显示加载失败并允许重试，不误导为菜单未配置，也不因为刷新服务 `5xx` 或网络故障自动注销。

发布时更新后端和宿主前端，并完成每个实例的正常更新，使旧进程中已污染的内存缓存随进程清空；修复不会追溯修正未更新实例里的旧缓存条目。通过同一会话的“故障→重试失败→恢复→进入原页面”验证恢复，核对必要读取失败期间没有成功事件、业务页面或重复认证写入。

这项修复消除短暂读取失败被放大为持续错误的机制。SQL Server `Read timed out` 的底层原因仍需结合数据库负载、慢查询、连接池与网络证据单独排查。

## 诊断工具箱

### 网络请求拦截

```javascript
// Playwright
page.on('request', request => {
  console.log('→', request.method(), request.url());
});

page.on('response', async response => {
  console.log('←', response.status(), response.url());
});
```

### 日志查询

```bash
# 查看最近的错误
docker logs <container> --tail 200 | grep -B 5 -A 10 "Exception"

# 查看特定 API 的日志
docker logs <container> | grep "/api/sys/configuration"

# 实时监控
docker logs <container> -f | grep --color=always "ERROR\|WARN"
```

### 数据库查询

仅适用于前述已授权的只读运维诊断；优先使用公开 API 和日志，不修改业务数据。

```sql
-- 查找配置
SELECT * FROM t_sys_configuration_value WHERE `key` LIKE '%keyword%';

-- 查看最近更新的配置
SELECT * FROM t_sys_configuration_value ORDER BY update_at DESC LIMIT 10;

-- 验证配置值
SELECT `key`, value, update_at FROM t_sys_configuration_value WHERE `key` = 'xxx';
```

### 代码搜索

```bash
# 搜索配置键名
rg -n 'appName' -g '*.json' -g '*.java'

# 搜索类名
rg -n 'ConfigurationController' -g '*.java'

# 搜索 SQL 表名
rg -n 't_sys_configuration_value' -g '*.java' -g '*.xml'
```

## 快速参考

### 诊断决策树

```
API 返回错误？
├─ 是 → 查看后端日志（优先）
│   ├─ 有异常堆栈 → 定位代码行 → 修复
│   └─ 无异常 → 拦截请求查看请求体
└─ 否 → 检查前端
    ├─ Console 有错误 → 修复 JS 错误
    ├─ 网络请求失败 → 检查网络配置
    └─ 状态异常 → 检查状态管理
```

### 字段定位决策树

```
需要定位字段？
├─ 先定位 → 用 rg / ast-grep 查定义和调用方
├─ 再核对 → UI Schema、API、服务的实际字段
├─ 必要时 → 已授权只读运维查询持久化状态
└─ 失败 2 次 → 切换策略（不要继续手动尝试）
```

### 测试安全检查清单

- [ ] 使用的配置项是否在安全白名单中？
- [ ] 是否避免修改认证、授权相关配置？
- [ ] 是否在测试前备份了原始值？
- [ ] 是否在测试后恢复了原始值？
- [ ] 是否验证了配置修改真正生效？

## 相关文档

- [Agent 反馈循环](agent-feedback-loop.md)：日志、测试、metrics 查询
- [Agent 会话恢复](agent-session-recovery.md)：上下文重置后的恢复步骤
- [开发指南](../development-guide.md)：开发环境配置和常用命令
