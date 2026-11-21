# 后端代码上下文编辑器验收合同

验证生产 CodeContextController → CodeContextService → 领域 provider → AMIS/Monaco 的代码提示链路。前端执行脚本排除。使用隔离应用元数据目录与表单草稿夹具；代码上下文 HTTP 响应不得模拟。RPC 与日志使用生产 UI Schema 中的控件声明；业务模型使用正式投影所识别的脚本位置。完整登录、画布导航、目标应用发布不在此切片。

每个场景需保留真实请求/响应、可见候选截图、视频与 trace。接口必须 HTTP 200 且平台 code=0，pageerror 必须为空。缺少服务或失败不能降级为跳过或替换模拟响应。页面的缺失 STOMP 路由 404 不证明实时环境刷新，刷新功能另见语言服务场景。

运行时配置解析、凭据授权、并发 token 代际、子流程和脚本返回结构由各自模块的行为测试独立验证；浏览器候选不能替代运行时执行测试。Groovy LSP、helper 真实环境同步及浏览器对象隔离沿 development/groovy-language-service 场景验证。
