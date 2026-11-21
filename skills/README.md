# TideStack 应用开发 Skills

公共套件包含 **17 个独立 Skill**。平台内部的 operate-ouroboros、run-tests、publish-ouroboros 另计 3 个，总计 20 个。生成模板分发公共套件的副本，不重复计数。

TideStack 是公共 Skill 名称；运行时包名、Maven 坐标、SDK、镜像与资源路径继续使用 Ouroboros。整合基线为 `dev-next@c3ff6111937767f373392c24fda9f698bc48c068`，各快照保留自身来源依据；实际 SNAPSHOT 依赖仍需核对。English: [README.en.md](README.en.md)。

## 使用与安装

在 monorepo 根目录发现公共套件：

```bash
npx skills add ./skills --list
npx skills add ./skills --all
```

独立发布仓库以本目录内容为根时，可使用 `npx skills add <owner>/<repo> --all`。仅需要个别任务时选择 `--skill <name>`，同时安装其实际引用的同级任务；metadata 工具随 metadata Skill 自包含分发。生成的后端项目在 `.agents/skills/` 已包含公共套件。

`tidestack-howto` 回答“这个需求，用 TideStack 应该怎么做”：给出能力组合、开发路线、资源清单与验证建议，推荐后续 Skill，**不自动实施**。明确实施任务时直接使用专项 Skill；不因写一个方法就重新建立扩展工程。

公开套件面向使用平台开发业务应用的开发者。常规流程复用已有模块、服务和资源；主键生成器、插件、装饰器、权限解析器等按需扩展。平台内部加载、授权决策、投递设施及模板生成器维护不作为应用开发步骤。

专家随包携带[能力地图及检索说明](tidestack-howto/references/capability-map.md)、精简索引和完整目录，当前收录 107 项能力。按需先检索、再读取候选条目，输出稳定能力 ID；目录记录源码核对状态，不表示部署已启用或已完成运行验收。

## 完整清单

| Skill | 用途 |
| --- | --- |
| [tidestack-howto](tidestack-howto/SKILL.md) | 只读需求评估、平台能力选型与资源规划 |
| [tidestack-backend-extension-development](tidestack-backend-extension-development/SKILL.md) | 后端宿主/扩展工程、装配、注册与交付 |
| [tidestack-frontend-extension-development](tidestack-frontend-extension-development/SKILL.md) | 自定义组件、微前端、布局、登录、SDK 与生命周期 |
| [tidestack-business-entry-definition](tidestack-business-entry-definition/SKILL.md) | 模块、菜单、权限与路由的资源归属和引用 |
| [tidestack-metadata-definition](tidestack-metadata-definition/SKILL.md) | 32 类资源契约、加载条件、统一校验与支撑资源 |
| [tidestack-metadata-migration](tidestack-metadata-migration/SKILL.md) | 旧目录迁移、冲突与有效构建核验 |
| [tidestack-local-debugging](tidestack-local-debugging/SKILL.md) | 复用已有环境、按需启动基座并验证应用关键路径 |
| [tidestack-ui-schema-development](tidestack-ui-schema-development/SKILL.md) | AMIS 页面、表单、动作、数据域与用户文案 |
| [tidestack-business-model-development](tidestack-business-model-development/SKILL.md) | 业务模型、关系、视图、Action 和 Handler |
| [tidestack-data-model-development](tidestack-data-model-development/SKILL.md) | Java 模型、主键、约束、查询、事务、插件与装饰器 |
| [tidestack-web-authorization](tidestack-web-authorization/SKILL.md) | 高代码 Controller 方法授权与 HTTP 验证 |
| [tidestack-access-control-development](tidestack-access-control-development/SKILL.md) | 行列范围定义、解析扩展和业务执行中的消费 |
| [tidestack-business-event-development](tidestack-business-event-development/SKILL.md) | 事件发布与订阅、执行身份、业务幂等和失败恢复 |
| [tidestack-logicflow-development](tidestack-logicflow-development/SKILL.md) | 自动流程、脚本、节点、正式执行和失败传播 |
| [tidestack-approval-integration](tidestack-approval-integration/SKILL.md) | 人工审批的业务绑定、发起、表单与结果回写 |
| [tidestack-rpc-integration](tidestack-rpc-integration/SKILL.md) | 出站 RPC 模型、认证、映射与失败处理 |
| [tidestack-notification-development](tidestack-notification-development/SKILL.md) | 通知模板、接收者、渠道与发送验证 |

## 旧名称迁移

| 旧名称 | 新入口 |
| --- | --- |
| tidestack | tidestack-howto，开发指导与只读选型 |
| tidestack-platform-development | tidestack-howto，只读选型；执行任务按上表另选 |
| tidestack-backend-development | tidestack-backend-extension-development |
| tidestack-project-initialization | backend-extension 的内嵌/标准基座初始化参考；类型化编译归 data-model |
| tidestack-frontend-development | tidestack-frontend-extension-development |
| ouroboros-ui-schema-definition | tidestack-ui-schema-development |
| ouroboros-app-module-definition / ouroboros-menu-definition / ouroboros-ui-model-definition | metadata 与 business-entry |
| ouroboros-authority-definition | metadata、web-authorization、access-control |
| ouroboros-configuration-definition | metadata 的 supporting-resources 参考 |
| ouroboros-resource-bundle-definition | business-entry |
| ouroboros-metadata-migration | metadata-migration |
| ouroboros-start-web / start-ouroboros-frontend | local-debugging |

更新已有项目时先安装新入口、修改调用引用，再移除旧副本；不会自动删除用户安装目录。模板使用公共源生成自包含副本，禁止只手改生成副本。

## 契约与维护

每个目录有 name/description frontmatter 与聚焦 SKILL.md，按需读取 references，工具放 scripts。所有元数据 Schema 从公共 `metadata-schema/` 同源分发到手册、IDE、Skills 与模板，并记录源路径和 SHA-256。UI 资源包装与组件内容各有公共契约。能力目录的三个 JSON 从手册同源同步到 tidestack-howto，记录来源状态与 SHA-256；不另维护能力定义。

平台维护命令：

```bash
python3 scripts/skills/sync_distribution.py
python3 scripts/skills/sync_distribution.py --check
python3 -m unittest discover -s scripts/skills/tests -v
python3 -m unittest discover -s skills/tidestack-metadata-definition/scripts/tests -v
python3 -m unittest discover -s skills/tidestack-local-debugging/scripts/tests -v
```

完整 Draft 2020-12 校验依赖 metadata Skill 的 scripts/requirements.txt，在隔离环境安装。子集模式必须显式选择，不作为完整验收。脚本测试使用临时目录与受控替身，不操作真实容器或外部渠道。

自定义审批表单描述：`approval-form` / `.approval-form.json`；维护源为 `metadata-schema/approval-form.schema.json`，由现有 Skill 分发工具同步到公共引用与项目模板。
