# <Scenario Title> Harness Playbook

## Preflight

1. 确认运行模型三元组
2. 确认夹具状态
3. 确认入口地址
4. 确认账号和权限上下文

## Execution Order

1. 拉起依赖夹具
2. 验证后端
3. 验证前端壳
4. 执行业务链路

## Observation Points

- 看后端日志：
- 看前端 console：
- 看网络请求：
- 看页面状态：

## Cleanup

1. 清理后台进程
2. 清理 PID 和临时文件
3. 归档证据
4. 回填经验教训
