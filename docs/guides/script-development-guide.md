# 脚本编写指南

## 概述

下列对象通过各模块的 ScriptContextContributor 绑定；运行环境必须加载相应模块和正式 SPI/自动配置。基础工具与日志来自 common-script，BeanFactory/Exceptions 来自 app-runtime，Scheduler、通知、组织、配置和数据模型对象各依赖对应运行时。

低代码平台为脚本执行环境提供了内置对象和功能模块，开发者可以在脚本中直接使用这些对象来实现各种业务逻辑，包括日志记录、异常处理、数据操作、通知发送、任务调度等功能。

## 脚本参数

在逻辑流中，脚本节点可以接收来自上游节点的输入参数。平台提供了多种方式来访问这些输入参数。

### 参数访问方式

#### 1. 按索引访问 (`$0`, `$1`, `$2`, ...)
使用 `$` + 数字的方式按顺序访问输入参数：

```javascript
// 获取第一个输入参数
var firstInput = $0;

// 获取第二个输入参数
var secondInput = $1;

// 获取第三个输入参数
var thirdInput = $2;

logger.info("第一个参数: {}, 第二个参数: {}", firstInput, secondInput);
```

#### 2. 按端口名称访问 (`$portName`)
如果脚本节点的输入端口定义了名称，可以使用 `$` + 端口名称的方式访问：

```javascript
// 假设输入端口名称为 "userData" 和 "config"
var user = $userData;
var settings = $config;

// 处理用户数据
if (user != null) {
    logger.info("处理用户: {}", user.name);
}
```

#### 3. Map 对象自动展开
当脚本节点只有一个输入参数且该参数是 Map 对象时，Map 中的键值对会自动展开到脚本上下文中：

```javascript
// 如果输入是 Map: {name: "张三", age: 30, department: "技术部"}
// 可以直接访问 Map 中的键

var userName = name;        // 等价于 $0.name
var userAge = age;          // 等价于 $0.age
var userDept = department;  // 等价于 $0.department

// 同时仍可以通过 $0 访问完整的 Map 对象
var fullUserData = $0;
```

### 参数使用示例

#### 示例1：处理单个用户对象
```javascript
// 输入参数：用户对象
var user = $0;

if (user?.id) {
    logger.info("处理用户ID: {}", user.id);

    // 更新用户状态
    user.lastLoginTime = new Date();
    user.status = "active";

    return user;
} else {
    Exceptions.business("用户对象无效");
}
```

#### 示例2：处理多个输入参数
```javascript
// 输入参数：用户数据($0)、配置信息($1)、操作类型($2)
var userData = $0;
var config = $1;
var operation = $2;

logger.info("执行操作: {}, 用户: {}", operation, userData?.name);

switch(operation) {
    case "create":
        return createUser(userData, config);
    case "update":
        return updateUser(userData, config);
    case "delete":
        return deleteUser(userData.id);
    default:
        Exceptions.business("不支持的操作类型: " + operation);
}
```

#### 示例3：使用命名端口
```javascript
// 假设输入端口名称为：user、settings、operation
var currentUser = $user;
var systemSettings = $settings;
var actionType = $operation;

// 验证必要参数
if (!currentUser?.id) {
    Exceptions.business("用户信息缺失");
}

if (!systemSettings?.enabled) {
    logger.warn("系统功能已禁用");
    return null;
}

// 执行业务逻辑
return processUserAction(currentUser, actionType, systemSettings);
```

#### 示例4：Map 自动展开
```javascript
// 输入是包含用户信息的 Map
// {id: "123", name: "张三", email: "zhang@example.com", role: "admin"}

logger.info("用户登录: {} ({})", name, email);

// 检查用户角色
if (role === "admin") {
    logger.info("管理员用户: {}", name);
    return {
        success: true,
        message: "管理员登录成功",
        userId: id,
        permissions: ["read", "write", "admin"]
    };
} else {
    return {
        success: true,
        message: "用户登录成功",
        userId: id,
        permissions: ["read"]
    };
}
```

### 参数处理最佳实践

#### 1. 参数验证
```javascript
// 验证必要参数是否存在
if (!$0) {
    Exceptions.business("缺少必要的输入参数");
}

// 使用可选链安全访问
var userId = $0?.user?.id;
if (!userId) {
    Exceptions.business("用户ID不能为空");
}
```

#### 2. 默认值处理
```javascript
// 为参数设置默认值
var config = $1 ?? {timeout: 5000, retries: 3};
var batchSize = $2 ?? 100;

// 使用解构设置默认值
var {
    maxRetries = 3,
    timeout = 30000,
    enableLogging = true
} = $0 ?? {};
```

#### 3. 参数转换
```javascript
// 确保参数为正确类型
var userId = String($0?.id || "");
var count = Number($1) || 0;
var isEnabled = Boolean($2);

// 使用平台工具类进行转换
var dateValue = Dates.toLocalDate(String($0.date), "yyyy-MM-dd");
```

## 可用的脚本对象

### 1. 工具类模块

**提供的对象**:
- `Dates` - 日期工具类
- `Converter` - 转换工具类
- `Maps` - Map 工具类

**使用示例**:
```javascript
// 日期解析（Dates 只提供字符串→时间的解析，不提供 now()/format()）
var dt = Dates.toLocalDateTime("2026-09-12 10:30:00", "yyyy-MM-dd HH:mm:ss");
var day = Dates.toLocalDate("2026-09-12", "yyyy-MM-dd");

// 类型转换（Converter 是逐类型的静态方法，没有通用 convert(value, targetType)）
var n = Converter.toInteger("42");
var flag = Converter.toBoolean("true");
var amount = Converter.toBigDecimal("12.34");

// Map 操作（Maps 没有 of(...)；直接用脚本对象字面量，Maps 提供 merge/containsKey/isEmpty 等）
var map = { key1: "value1", key2: "value2" };
var merged = Maps.merge(map, { key3: "value3" });
```

### 2. 日志模块

**提供的对象**:
- `logger` - 日志记录器
- `console` - 控制台输出（与logger相同）

**支持的日志级别**:
- `trace(message, ...args)`
- `debug(message, ...args)`
- `info(message, ...args)`
- `warn(message, ...args)`
- `error(message, ...args)`
- `log(message, ...args)` - 默认info级别

**使用示例**:
```javascript
// 基础日志输出
logger.info("脚本执行开始");
console.log("处理数据: {}", data);

// 带参数的日志
logger.debug("用户ID: {}, 操作: {}", userId, operation);

// 错误日志
logger.error("处理失败: {}", errorMessage, exception);
```

### 3. 异常处理模块

**提供的对象**:
- `Exceptions` - 直接抛出异常的辅助对象（方法返回 void，不返回异常实例）

**支持的异常类型**:
- **校验异常** (`ValidationException`): `Exceptions.validation(message)` 或 `Exceptions.validation(errorsMap)`。
- **业务异常** (`BusinessException`):
  - `Exceptions.business(message)`
  - `Exceptions.business(code, message)`
  - `Exceptions.business(code, message, cause)`
  - `Exceptions.throwBusiness(...)` - 别名方法

- **内部异常** (`InternalException`):
  - `Exceptions.internal(message)`
  - `Exceptions.internal(code, message)`
  - `Exceptions.internal(code, message, cause)`
  - `Exceptions.throwInternal(...)` - 别名方法

- **平台异常** (`PlatformException`):
  - `Exceptions.platform(message)`
  - `Exceptions.platform(code, message)`
  - `Exceptions.platform(code, message, cause)`
  - `Exceptions.throwPlatform(...)` - 别名方法

**使用示例**:
```javascript
// 抛出业务异常
if (amount <= 0) {
    Exceptions.business("金额必须大于0");
}

// 抛出带错误码的异常
if (!isValid) {
    Exceptions.business(4001, "数据验证失败");
}

// 抛出内部异常
try {
    // 一些处理
} catch (e) {
    Exceptions.internal(5001, "内部处理错误", e);
}
```

### 4. Spring Bean 工厂模块

**提供的对象**:
- `BeanFactory` - Spring Bean 工厂

**支持的方法**:
- `get(beanName)` - 安全获取Bean，失败返回null
- `getBean(name)` - 获取Bean
- `getBean(name, requiredType)` - 按类型获取Bean
- `getBean(requiredType)` - 按类型获取Bean
- `containsBean(name)` - 检查Bean是否存在
- `isSingleton(name)` - 检查是否为单例Bean
- `isPrototype(name)` - 检查是否为原型Bean

**使用示例**:
```javascript
// 安全获取Bean
var userService = BeanFactory.get("userService");
if (userService != null) {
    userService.createUser(userData);
}

// 直接获取Bean
var configService = BeanFactory.getBean("configService");
var result = configService.getConfig("app.name");

// 按类型获取Bean
var dataService = BeanFactory.getBean(java.lang.Class.forName("com.example.DataService"));
```

### 5. 任务调度模块

**提供的对象**:
- `Scheduler` - 任务调度器

**支持的方法**:
- `scheduleCron(taskName, cron, flowName, data)` - 定时任务
- `scheduleOnce(taskName, dateTime, flowName, data)` - 一次性任务
- `scheduleOnce(taskName, delay, flowName, data)` - 延迟任务
- `cancel(taskName)` - 取消任务
- `isScheduled(taskName)` - 检查任务是否已调度

**使用示例**:
```javascript
// 调度接口返回 Try；get() 将失败传播给脚本
Scheduler.scheduleCron("dailyReport", "0 0 9 * * ?", "generateReport", {reportType: "daily"}).get();

// 创建一次性任务
var tomorrow = java.time.LocalDateTime.now().plusDays(1);
Scheduler.scheduleOnce("reminderTask", tomorrow, "sendReminder", {userId: "123"}).get();

// 延迟任务（5分钟后执行）
Scheduler.scheduleOnce("delayedTask", java.time.Duration.ofMinutes(5), "processData", {id: "abc"}).get();

// 取消任务
Scheduler.cancel("taskName").get();
```

### 6. 通知模块

**提供的对象**:
- `NotificationDispatcher` - 通知发送器

**支持的方法**:
- **按地址发送**:
  - `sendByAddress(addressMap, template, payload)`
  - `sendByAddress(addressMap, template, payload, options)`

- **按员工发送**:
  - `sendByEmployee(employeeId, template, payload)`
  - `sendByEmployee(employeeId, template, payload, options)`
  - `sendByEmployee(employeeIds, template, payload)`
  - `sendByEmployee(employeeIds, template, payload, options)`

**使用示例**:
```javascript
// 按邮箱发送通知
var addresses = {
    "email": ["user1@example.com", "user2@example.com"],
    "sms": ["13800138000"]
};
NotificationDispatcher.sendByAddress(addresses, "orderNotification", {orderId: "12345"});

// 按员工ID发送
NotificationDispatcher.sendByEmployee("emp001", "taskAssigned", {taskId: "task123"});

// 批量发送给多个员工
var employeeIds = ["emp001", "emp002", "emp003"];
NotificationDispatcher.sendByEmployee(employeeIds, "meetingReminder", {
    meetingId: "meeting456",
    time: "2023-12-01 14:00"
});
```

### 7. 安全上下文模块

**提供的对象**:
- `AuthContext` - 认证上下文

**AuthContext 支持的方法**:
- `getUserId()` - 获取当前用户ID
- `getUserDetail()` - 获取当前用户详情
- `getAccessControlParams()` - 获取访问控制参数
- `getAccessControlParams(authKey)` - 获取指定认证键的访问控制参数
- `getAllowedFields()` - 获取允许访问的字段列表
- `getDisallowedFields()` - 获取禁止访问的字段列表
- `getFilters()` - 获取过滤条件

**使用示例**:
```javascript
// 获取当前用户信息
var currentUserId = AuthContext.getUserId();
var userDetail = AuthContext.getUserDetail();

// 权限控制
var allowedFields = AuthContext.getAllowedFields();
var filters = AuthContext.getFilters();

// 检查字段访问权限
if (allowedFields.contains("salary")) {
    // 可以访问薪资字段
    result.salary = employee.salary;
}

// 应用数据过滤
var accessParams = AuthContext.getAccessControlParams();
// 根据 accessParams 过滤数据
```

### 8. 组织架构模块

**提供的对象**:
- `OrganizationHolder` - 组织架构持有者
- `OrganizationContext` - 组织架构上下文

**OrganizationContext 支持的方法**:
- `getEmployee()` - 获取当前员工对象
- `getEmployeeId()` - 获取当前员工ID
- `getDepartment()` - 获取当前部门对象
- `getDepartmentId()` - 获取当前部门ID
- `getCompany()` - 获取当前公司对象
- `getCompanyId()` - 获取当前公司ID

**使用示例**:
```javascript
// 获取当前用户的组织信息
var employee = OrganizationContext.getEmployee();
var department = OrganizationContext.getDepartment();
var company = OrganizationContext.getCompany();

// 获取ID
var empId = OrganizationContext.getEmployeeId();
var deptId = OrganizationContext.getDepartmentId();
var companyId = OrganizationContext.getCompanyId();

// 基于组织架构的业务逻辑
if (department.getName() == "财务部") {
    // 财务部特殊处理
    processFinanceData();
}
```

### 9. 配置服务模块

**提供的对象**:
- `Config` - 配置服务

**支持的方法**:
- `get(key)` - 根据键获取配置值（`ConfigurationContextContributor.Proxy.get`，绑定名即 `Config`）

**使用示例**:
```javascript
// 获取系统配置
var maxFileSize = Config.get("system.upload.maxFileSize");
var timeout = Config.get("system.timeout");
var appName = Config.get("app.name");

// 基于配置的条件处理
if (Config.get("feature.audit.enabled") == true) {
    // 启用审计功能
    logAuditEvent(event);
}
```

### 10. 数据模型中心模块

**提供的对象**:
- `DataModelCenter` - 数据模型中心

**支持的方法**:
- `get(name)` - 根据名称获取数据模型

**使用示例**:
```javascript
// 获取数据模型
var userModel = DataModelCenter.get("User");
var orderModel = DataModelCenter.get("Order");

if (userModel != null) {
    // 使用数据模型进行操作
    var users = userModel.query({status: "active"});
}
```

### 11. 编码生成器

**提供的对象**:
- `codeGen` - 编码生成器

**支持的方法**:
- `get(codingTemplate)` - 根据模板生成编码，模版需要带有 `coding:` 前缀，如：`coding:{yyyy}-<0001>`
- `get(codingTemplate, record)` - 根据模板和当前数据记录生成编码（如果模版中需要用到当前记录的字段）
- `peek(codingTemplate)` - 预览编码（不累加序号）
- `peek(codingTemplate, record)` - 根据模板和当前数据记录预览编码

**使用示例**:
```javascript
// 生成订单号
var orderNo = codeGen.get("coding:{yyyy}-<0001>");

// 根据数据生成编码
var productCode = codeGen.get("coding:{yyyy}-<0001>", {
    category: "electronics",
    brand: "apple"
});

// 预览生成的编码（不消费序号）
var previewCode = codeGen.peek("USER_CODE");
```

### 12. ~~数据权限访问控制服务（`AccessControlService`）~~（已移除）

> **2026-09-12 更正**：本节描述的 `AccessControlService`（`addUser` / `removeUser` / `addUsers` / `removeUsers`）**在本仓库中不存在**（全仓 `AccessControlService` 0 命中），此前作为脚本可用对象列出属于错误信息。
>
> 数据记录级访问控制在平台的实现方式是 **数据模型插件 + 权限过滤**（`ability/ouroboros-app-runtime` 下的 `AccessControlDataModelPlugin`，基于 `CurrentSecurityContextHolder` 生成过滤条件），不是脚本可直接调用的服务。若确需在脚本里做记录级授权，请先确认对应能力是否已通过 `ScriptContextContributor` 绑定；当前文档不提供该示例。

## 脚本编写最佳实践

### 1. 错误处理
```javascript
try {
    // 业务逻辑
    var result = processData(input);
    logger.info("处理成功: {}", result);
} catch (e) {
    logger.error("处理失败", e);
    Exceptions.business("数据处理失败: " + e.message);
}
```

### 2. 日志记录
```javascript
// 脚本开始
logger.info("脚本开始执行: {}", scriptName);

// 关键步骤日志
logger.debug("处理用户: {}", userId);

// 脚本结束
logger.info("脚本执行完成，耗时: {}ms", endTime - startTime);
```

### 3. 配置驱动
```javascript
// 使用配置控制业务逻辑
var batchSize = Config.get("batch.processing.size") || 100;
var enableNotification = Config.get("notification.enabled") || false;

if (enableNotification) {
    NotificationDispatcher.sendByEmployee(userId, "taskCompleted", result);
}
```

### 4. 权限检查
```javascript
// 检查用户权限
var currentUserId = AuthContext.getUserId();
var allowedFields = AuthContext.getAllowedFields();

// 根据权限过滤返回数据
var filteredResult = {};
allowedFields.forEach(function(field) {
    if (result[field] !== undefined) {
        filteredResult[field] = result[field];
    }
});
```

### 5. 组织架构相关逻辑
```javascript
// 基于组织架构的业务处理
var currentDept = OrganizationContext.getDepartment();
var currentEmployee = OrganizationContext.getEmployee();

if (currentDept.getName() === "管理部") {
    // 管理部门特殊逻辑
    processManagementTask();
} else {
    // 普通部门逻辑
    processNormalTask();
}
```

## Javascript 技巧

### 1. Javascript 与 Java 互操作

#### Java 对象访问
```javascript
// 访问 Java 类的静态方法
var LocalDate = Java.type('java.time.LocalDate');
var today = LocalDate.now();

// 创建 Java 对象
var ArrayList = Java.type('java.util.ArrayList');
var list = new ArrayList();
list.add("item1");
list.add("item2");

// 访问 Java 枚举
var TimeUnit = Java.type('java.util.concurrent.TimeUnit');
var seconds = TimeUnit.SECONDS;
```

#### 类型转换
```javascript
// JavaScript 数组转 Java List
var jsArray = ["a", "b", "c"];
var javaList = Java.from(jsArray);

// Java List/Array 转 JavaScript 数组
var javaList = new ArrayList();
javaList.add("x");
javaList.add("y");
var jsArray = Java.to(javaList);

// 基础类型转换
var javaString = Java.type('java.lang.String').valueOf("hello");
var jsString = String(javaObject); // Java对象转JS字符串
```

#### 异常处理
```javascript
try {
    // 调用可能抛出异常的Java方法
    var result = javaObject.someMethod();
} catch (e) {
    // 捕获Java异常
    if (e instanceof Java.type('java.lang.IllegalArgumentException')) {
        logger.warn("参数错误: " + e.message);
    } else {
        logger.error("未知错误", e);
    }
}
```

### 2. JavaScript 语法特性

#### 可选链操作符 (?.)
```javascript
// 安全访问嵌套属性，避免 null/undefined 错误
var userId = user?.profile?.id;
var email = user?.contacts?.email;

// 安全调用方法
var result = obj?.method?.();

// 安全访问数组元素
var firstItem = items?.[0];
var dynamicKey = obj?.[keyName];
```

#### 空值合并操作符 (??)
```javascript
// 当左侧为 null 或 undefined 时使用右侧值
var name = user.name ?? "未知用户";
var config = settings.timeout ?? 30000;

// 与 || 的区别：?? 只在 null/undefined 时使用默认值
var value = 0;
var result1 = value || "default";  // "default" (因为0是falsy)
var result2 = value ?? "default";  // 0 (只有null/undefined才用默认值)
```

#### 空值合并赋值 (??=)
```javascript
// 仅在变量为 null 或 undefined 时赋值
config.timeout ??= 5000;
user.settings ??= {};

// 等价于
if (config.timeout == null) {
    config.timeout = 5000;
}
```

#### 逻辑赋值操作符
```javascript
// 逻辑与赋值 (&&=)
// 仅在左侧为真值时赋值
user.isActive &&= checkUserStatus();

// 逻辑或赋值 (||=)
// 仅在左侧为假值时赋值
config.retries ||= 3;
```

#### 解构赋值
```javascript
// 对象解构
var {name, age, email = "unknown"} = user;
var {id: userId, name: userName} = user; // 重命名

// 数组解构
var [first, second, ...rest] = items;
var [, , third] = items; // 跳过前两个元素

// 函数参数解构
function processUser({name, age, email}) {
    logger.info("处理用户: " + name);
}
```

#### 模板字符串
```javascript
// 基础用法
var message = `用户 ${user.name} 的年龄是 ${user.age}`;

// 多行字符串
var sql = `
    SELECT *
    FROM users
    WHERE age > ${minAge}
    AND status = '${status}'
`;

// 表达式求值
var info = `结果: ${calculateResult() + 10}`;
```

#### 箭头函数
```javascript
// 基础箭头函数
var add = (a, b) => a + b;
var square = x => x * x;

// 多行箭头函数
var processData = (data) => {
    var result = data.map(item => item.value);
    return result.filter(value => value > 0);
};

// 数组方法中的应用
var activeUsers = users.filter(user => user.status === 'active');
var userNames = users.map(user => user.name);
```

#### 数组方法
```javascript
// find/findIndex - 查找元素
var user = users.find(u => u.id === targetId);
var index = users.findIndex(u => u.email === targetEmail);

// some/every - 检查条件
var hasAdmin = users.some(u => u.role === 'admin');
var allActive = users.every(u => u.status === 'active');

// includes - 包含检查
var hasValue = arr.includes(searchValue);

// 链式调用
var result = data
    .filter(item => item.status === 'active')
    .map(item => ({
        id: item.id,
        name: item.name,
        score: item.score * 1.1
    }))
    .sort((a, b) => b.score - a.score);
```

#### 对象展开和简写
```javascript
// 对象属性简写
var name = "张三";
var age = 30;
var user = {name, age}; // 等价于 {name: name, age: age}

// 对象展开
var baseConfig = {timeout: 5000, retries: 3};
var config = {
    ...baseConfig,
    url: "http://api.example.com",
    timeout: 10000  // 覆盖基础配置
};

// 数组展开
var arr1 = [1, 2, 3];
var arr2 = [4, 5, 6];
var combined = [...arr1, ...arr2]; // [1, 2, 3, 4, 5, 6]
```

#### Set 和 Map
```javascript
// Set - 唯一值集合
var uniqueIds = new Set();
uniqueIds.add(1);
uniqueIds.add(2);
uniqueIds.add(1); // 重复值会被忽略

var hasId = uniqueIds.has(1); // true
var arrayFromSet = [...uniqueIds]; // 转为数组

// Map - 键值对映射
var userMap = new Map();
userMap.set("user1", {name: "张三"});
userMap.set("user2", {name: "李四"});

var user = userMap.get("user1");
var hasUser = userMap.has("user1");
```

### 3. 常见模式和技巧

#### 默认参数处理
```javascript
function processRequest(data, options = {}) {
    var {
        timeout = 5000,
        retries = 3,
        validateInput = true
    } = options;

    // 使用参数
    if (validateInput && !isValidData(data)) {
        throw new Error("数据验证失败");
    }
}
```

#### 错误处理模式
```javascript
// 使用可选链避免错误
var errorMessage = error?.response?.data?.message ?? "未知错误";

// 安全的JSON解析
function safeJsonParse(jsonString) {
    try {
        return JSON.parse(jsonString);
    } catch (e) {
        logger.warn("JSON解析失败: " + e.message);
        return null;
    }
}
```

#### 条件执行
```javascript
// 短路求值
user.isAdmin && performAdminAction();
user.profile?.email && sendEmail(user.profile.email);

// 三元操作符
var status = user.isActive ? "激活" : "未激活";
var className = isError ? "error" : isWarning ? "warning" : "normal";
```

## 注意事项

1. **空值检查**: 所有从上下文获取的对象都可能为null，使用前需要检查
2. **异常处理**: 建议使用try-catch包裹可能出错的代码
3. **日志级别**: 根据重要性选择合适的日志级别
4. **性能考虑**: 避免在循环中频繁调用重量级操作
5. **安全考虑**: 敏感信息不要记录到日志中

## 总结

低代码平台为脚本执行环境提供了丰富的内置功能模块，包括工具类、日志、异常处理、Spring Bean访问、任务调度、通知发送、安全控制、组织架构、配置管理、数据模型操作和代码生成等。在相应运行时模块和 contributor 已加载的环境中，开发者可以直接使用这些绑定；独立内核并不自动具有所有平台对象，从而快速实现复杂的业务逻辑。合理使用这些模块可以大大提升脚本的功能性和健壮性。