# QuickForm 后端详细设计文档（与当前代码一致）

更新时间：2026-03-06  
适用分支：`master`  
技术栈：Spring Boot 3 + MyBatis(XML) + PostgreSQL(JSONB)

## 1. 目标与范围

本项目后端只做三件事：
- 通用数据存储与查询（按 `pageCode` 分组，业务数据存 JSONB）
- 通用审批流程（模板配置、发起、审批、固定审批中心查询）
- 通用报表执行（按 `pageCode` 从数据库读取 SQL 并执行）

明确边界：
- 不存储页面 Schema，不提供页面管理接口。
- 前端页面渲染由低代码平台负责，后端只提供通用 API。
- 数据展示页面与工作流页面是两套职责，接口已拆分。
- 所有接口均为 `POST`。
- 当前不展开安全策略（按既定约定）。

## 2. 总体架构

## 2.1 分层
- Controller：HTTP 路由与参数接收
- Service：业务规则、校验、流程推进、返回结构封装
- Mapper(XML)：SQL 定义与数据库访问
- PostgreSQL：持久化 JSONB 数据、流程模板、流程实例、审批任务、报表 SQL

## 2.2 核心分域
- 数据域：`data_record`
- 流程域：`workflow_template` + `workflow_instance` + `workflow_task`
- 报表域：`report`

说明：流程域不再依赖 `data_record` 中的流程专用字段；流程状态统一在 `workflow_instance`。

## 3. 数据库设计

数据库初始化文件：`/Users/sailv/IdeaProjects/quickForm /src/main/resources/schema.sql`

## 3.1 表：`data_record`（业务数据）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK, 默认 `gen_random_uuid()` | 记录主键 |
| `page_code` | `TEXT` | NOT NULL | 业务集合标识（前端固定传入） |
| `data` | `JSONB` | NOT NULL, 默认 `{}` | 动态业务数据 |
| `status` | `TEXT` | NOT NULL, 默认 `draft`, CHECK | 业务状态（非流程运行态） |
| `created_at` | `TIMESTAMP` | NOT NULL, 默认 `NOW()` | 创建时间 |
| `updated_at` | `TIMESTAMP` | NOT NULL, 默认 `NOW()` | 更新时间 |
| `created_by` | `TEXT` | 可空 | 创建人 |
| `updated_by` | `TEXT` | 可空 | 更新人 |

`status` 枚举约束：`draft` / `submitted` / `approved` / `rejected`

## 3.2 表：`workflow_template`（流程模板）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | 模板主键 |
| `page_code` | `TEXT` | NOT NULL | 归属页面 |
| `template_code` | `TEXT` | NOT NULL | 模板编码 |
| `name` | `TEXT` | NOT NULL | 模板名称 |
| `config_json` | `JSONB` | NOT NULL, 默认 `{}` | 节点配置 |
| `enabled` | `BOOLEAN` | NOT NULL, 默认 `TRUE` | 是否启用 |
| `is_default` | `BOOLEAN` | NOT NULL, 默认 `FALSE` | 是否默认模板 |
| `updated_at` | `TIMESTAMP` | NOT NULL, 默认 `NOW()` | 更新时间 |

唯一约束：`UNIQUE(page_code, template_code)`

## 3.3 表：`workflow_instance`（流程实例）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | 流程实例 ID |
| `page_code` | `TEXT` | NOT NULL | 归属页面 |
| `template_id` | `BIGINT` | FK -> `workflow_template(id)` | 模板主键 |
| `record_id` | `UUID` | NOT NULL, FK -> `data_record(id)` | 业务记录 ID |
| `template_code` | `TEXT` | NOT NULL | 模板编码快照 |
| `status` | `TEXT` | NOT NULL, 默认 `submitted`, CHECK | 流程实例状态 |
| `current_node_code` | `TEXT` | 可空 | 当前节点编码 |
| `starter` | `TEXT` | 可空 | 发起人 |
| `started_at` | `TIMESTAMP` | NOT NULL, 默认 `NOW()` | 发起时间 |
| `finished_at` | `TIMESTAMP` | 可空 | 结束时间 |
| `updated_at` | `TIMESTAMP` | NOT NULL, 默认 `NOW()` | 更新时间 |

`status` 枚举约束：`submitted` / `approved` / `rejected`

## 3.4 表：`workflow_task`（审批任务）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | 任务 ID |
| `instance_id` | `BIGINT` | NOT NULL, FK -> `workflow_instance(id)` | 所属流程实例 |
| `page_code` | `TEXT` | NOT NULL | 归属页面 |
| `record_id` | `UUID` | NOT NULL, FK -> `data_record(id)` | 业务记录 ID |
| `template_id` | `BIGINT` | FK -> `workflow_template(id)` | 模板主键 |
| `template_code` | `TEXT` | NOT NULL | 模板编码快照 |
| `node_code` | `TEXT` | NOT NULL | 节点编码 |
| `assignee` | `TEXT` | 可空 | 审批人（可空表示待认领） |
| `status` | `TEXT` | NOT NULL, 默认 `pending`, CHECK | 任务状态 |
| `action` | `TEXT` | NOT NULL, 默认 `pending`, CHECK | 动作 |
| `comment` | `TEXT` | 可空 | 审批意见 |
| `operated_by` | `TEXT` | 可空 | 实际操作人 |
| `created_at` | `TIMESTAMP` | NOT NULL, 默认 `NOW()` | 创建时间 |
| `updated_at` | `TIMESTAMP` | NOT NULL, 默认 `NOW()` | 更新时间 |

`status` 枚举约束：`pending` / `done` / `cancelled`  
`action` 枚举约束：`pending` / `approve` / `reject` / `skip`

## 3.5 表：`report`（报表配置）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | 报表 ID |
| `page_code` | `TEXT` | NOT NULL | 页面编码 |
| `name` | `TEXT` | NOT NULL | 报表名称 |
| `sql_text` | `TEXT` | NOT NULL | 报表 SQL |
| `options` | `JSONB` | NOT NULL, 默认 `{}` | 扩展选项 |

## 3.6 索引与唯一约束说明

- `idx_data_page`：按 `page_code` 查询数据列表
- `idx_data_gin`：JSONB 检索加速
- `idx_data_status`：按状态过滤
- `idx_data_creator`：按创建人过滤
- `idx_workflow_template_page`：模板列表
- `idx_workflow_template_default`：默认模板定位
- `uk_workflow_template_default_page`：每个 `page_code` 只能有一个默认模板
- `idx_workflow_instance_record`：按记录定位实例
- `idx_workflow_instance_starter`：我发起列表
- `uk_workflow_instance_active_record`：同一 `page_code + record_id` 仅允许一个进行中的实例
- `idx_task_todo`：待办列表
- `idx_task_record_node_status`：节点完成度判断
- `idx_task_instance_node_status`：实例内节点推进
- `idx_task_template`：按模板定位任务
- `idx_task_operated`：已办列表
- `idx_report_page`：按页面读取报表
- `uk_report_page_code`：每个 `pageCode` 仅一条报表 SQL

## 4. 工作流配置模型

模型类：
- `/Users/sailv/IdeaProjects/quickForm /src/main/java/com/quickform/api/model/WorkflowConfig.java`
- `/Users/sailv/IdeaProjects/quickForm /src/main/java/com/quickform/api/model/WorkflowNode.java`

配置 JSON 示例：

```json
{
  "nodes": [
    {"code": "L1", "name": "第一层审批", "mode": "all", "assignees": ["A", "B"]},
    {"code": "L2", "name": "第二层审批", "mode": "any", "assignees": ["C", "D"]}
  ]
}
```

规则：
- `mode=all`：该节点全部待办处理完成后进入下一节点。
- `mode=any`：任意一人通过即可进入下一节点，其他待办自动 `cancelled + skip`。
- 任意节点 `reject`：实例结束为 `rejected`，剩余待办全部取消。

## 5. API 设计（全部 POST）

Controller 文件：
- `/Users/sailv/IdeaProjects/quickForm /src/main/java/com/quickform/api/controller/DataController.java`
- `/Users/sailv/IdeaProjects/quickForm /src/main/java/com/quickform/api/controller/WorkflowController.java`
- `/Users/sailv/IdeaProjects/quickForm /src/main/java/com/quickform/api/controller/ReportController.java`

统一响应结构：

```json
{
  "success": true,
  "message": null,
  "data": {}
}
```

## 5.1 数据接口

### 5.1.1 `POST /data/{pageCode}/query`

请求体（可空）：`DataQueryRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `filters` | `Filter[]` | 否 | AND 条件 |
| `orFilters` | `Filter[]` | 否 | OR 条件组（整体与 AND 条件再 AND） |
| `sorts` | `Sort[]` | 否 | 排序 |
| `page` | `Integer` | 否 | 页码，默认 1 |
| `pageSize` | `Integer` | 否 | 页大小，默认 20 |
| `keywords` | `String` | 否 | 全文匹配 `data::text` |

`Filter.op` 支持：
- `eq` `ne` `like` `gt` `gte` `lt` `lte` `in` `contains`

排序字段支持：
- 固定字段：`status` `createdAt` `updatedAt` `createdBy` `updatedBy`
- 动态字段：JSON 键名（正则 `^[a-zA-Z0-9_]+$`）

返回：`PageResult<Map>`，`items` 中每项字段：
- `id` `status` `createdAt` `updatedAt` `createdBy` `updatedBy` `data`

### 5.1.2 `POST /data/{pageCode}/create`

请求体：`DataWriteRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `data` | `Map<String,Object>` | 是 | 业务 JSON |
| `status` | `String` | 否 | 默认 `draft` |
| `operator` | `String` | 否 | 写入 `created_by/updated_by` |

返回：新记录 `UUID`

### 5.1.3 `POST /data/{pageCode}/{id}/update`

请求体：`DataWriteRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `data` | `Map<String,Object>` | 是 | 全量替换 JSONB |
| `status` | `String` | 否 | 非空时更新状态 |
| `operator` | `String` | 否 | 写入 `updated_by` |

返回：影响行数 `int`

### 5.1.4 `POST /data/{pageCode}/{id}/delete`

请求体：无  
返回：影响行数 `int`

### 5.1.5 `POST /data/{pageCode}/{id}/get`

请求体：无  
返回字段：`id` `status` `createdAt` `updatedAt` `createdBy` `updatedBy` `data`

## 5.2 流程模板接口

### 5.2.1 `POST /workflow/template/list`
请求：`WorkflowTemplateListRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageCode` | `String` | 是 | 页面编码 |
| `enabled` | `Boolean` | 否 | 启用状态过滤 |
| `keyword` | `String` | 否 | 名称/编码模糊匹配 |
| `page` | `Integer` | 否 | 默认 1 |
| `pageSize` | `Integer` | 否 | 默认 20，最大 200 |

返回 item 字段：`id` `pageCode` `templateCode` `name` `enabled` `isDefault` `updatedAt` `nodeCount`

### 5.2.2 `POST /workflow/template/get`
请求：`WorkflowTemplateGetRequest`（`pageCode` + `templateCode`）  
返回字段：`id` `pageCode` `templateCode` `name` `enabled` `isDefault` `updatedAt` `config` `nodeCount`

### 5.2.3 `POST /workflow/template/save`
请求：`WorkflowTemplateSaveRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageCode` | `String` | 是 | 页面编码 |
| `templateCode` | `String` | 是 | 模板编码 |
| `name` | `String` | 是 | 模板名称 |
| `enabled` | `Boolean` | 否 | 默认沿用已有值或 `true` |
| `isDefault` | `Boolean` | 否 | 是否默认模板 |
| `config` | `Object` | 是 | 工作流配置 JSON |

返回：模板 ID（新增或更新后的 ID）

### 5.2.4 `POST /workflow/template/delete`
请求：`WorkflowTemplateDeleteRequest`（`pageCode` + `templateCode`）  
返回：影响行数 `int`

限制：若该模板仍有 `pending` 任务，不允许删除。

### 5.2.5 `POST /workflow/template/set-default`
请求：`WorkflowTemplateSetDefaultRequest`（`pageCode` + `templateCode`）  
返回：影响行数 `int`

限制：禁用模板不能设为默认。

## 5.3 流程动作接口

### 5.3.1 `POST /workflow/{pageCode}/{id}/submit`
请求：`WorkflowActionRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `operator` | `String` | 是 | 发起人（写入实例 `starter`） |
| `templateCode` | `String` | 否 | 不传时取默认模板 |
| `assignee` | `String` | 否 | 首节点无配置审批人时作为兜底审批人 |
| `comment` | `String` | 否 | 预留 |
| `nodeCode` | `String` | 否 | 忽略 |

返回：`1` 表示发起成功

关键校验：
- 记录必须存在。
- 当前记录不能有待处理任务。
- 当前记录不能有进行中的流程实例。
- 模板必须存在且启用。

### 5.3.2 `POST /workflow/{pageCode}/{id}/approve`
请求：`WorkflowActionRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `operator` | `String` | 是 | 审批人 |
| `nodeCode` | `String` | 否 | 指定节点，不传自动推断 |
| `comment` | `String` | 否 | 审批意见 |

返回：`1` 表示审批动作成功

审批权限判定：
- 仅允许处理当前节点中分配给 `operator` 的 `pending` 任务。
- 若存在无审批人任务（`assignee IS NULL`），允许当前 `operator` 处理。
- 不允许“代别人审批” fallback。

### 5.3.3 `POST /workflow/{pageCode}/{id}/reject`
请求与 `approve` 相同。  
返回：`1` 表示驳回成功。

行为：
- 当前任务置 `done + reject`
- 同实例其他 `pending` 任务全部置 `cancelled + skip`
- `workflow_instance.status` 置 `rejected`

## 5.4 固定审批中心接口

### 5.4.1 `POST /workflow/todo/query`
请求：`WorkflowTodoQueryRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `assignee` | `String` | 是 | 当前用户 |
| `pageCode` | `String` | 否 | 页面过滤 |
| `keywords` | `String` | 否 | 匹配 `recordId/templateName/nodeCode` |
| `page` | `Integer` | 否 | 默认 1 |
| `pageSize` | `Integer` | 否 | 默认 20，最大 200 |

返回 item 字段：
- `taskId` `instanceId` `recordId` `pageCode` `templateCode` `templateName`
- `nodeCode` `assignee` `taskStatus` `action` `comment`
- `workflowStatus` `currentNodeCode` `starter` `startedAt` `finishedAt`
- `createdAt` `updatedAt`

### 5.4.2 `POST /workflow/done/query`
请求：`WorkflowDoneQueryRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `operator` | `String` | 是 | 当前用户 |
| `pageCode` | `String` | 否 | 页面过滤 |
| `keywords` | `String` | 否 | 匹配 `recordId/templateName/nodeCode` |
| `page` | `Integer` | 否 | 默认 1 |
| `pageSize` | `Integer` | 否 | 默认 20，最大 200 |

返回字段与 `todo` 类似，额外包含 `operator`。

### 5.4.3 `POST /workflow/my-apply/query`
请求：`WorkflowMyApplyQueryRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `operator` | `String` | 是 | 发起人 |
| `pageCode` | `String` | 否 | 页面过滤 |
| `status` | `String` | 否 | 仅支持 `submitted/approved/rejected` |
| `keywords` | `String` | 否 | 匹配 `recordId/templateName/currentNodeCode` |
| `page` | `Integer` | 否 | 默认 1 |
| `pageSize` | `Integer` | 否 | 默认 20，最大 200 |

返回 item 字段：
- `instanceId` `recordId` `pageCode` `workflowStatus`
- `templateCode` `templateName` `currentNodeCode`
- `pendingAssignees`（数组）
- `starter` `startedAt` `finishedAt` `updatedAt`

### 5.4.4 `POST /workflow/record/timeline`
请求：`WorkflowRecordTimelineRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageCode` | `String` | 是 | 页面编码 |
| `recordId` | `UUID` | 是 | 业务记录 ID |

返回结构：
- `record`：`recordId` `pageCode`
- `instance`：`instanceId` `templateCode` `templateName` `workflowStatus` `currentNodeCode` `starter` `startedAt` `finishedAt` `updatedAt`
- `timeline`：任务列表，字段 `taskId` `instanceId` `recordId` `pageCode` `templateCode` `templateName` `nodeCode` `assignee` `taskStatus` `action` `comment` `operator` `createdAt` `updatedAt`

## 5.5 报表接口

### 5.5.1 `POST /report/run`
请求：`ReportRunRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageCode` | `String` | 是 | 页面编码 |
| `params` | `Map<String,Object>` | 否 | SQL 参数 |

返回：`List<Map<String,Object>>`

执行规则：
- 先按 `pageCode` 查询 `report.sql_text`。
- SQL 中 `:param` 自动转换为 MyBatis `#{params.param}`。
- 前端不直接传 SQL。

示例：
- 库中 SQL：`SELECT * FROM data_record WHERE page_code = :pageCode AND status = :status`
- 请求 `params`：`{"pageCode":"change_apply","status":"approved"}`

## 6. 枚举与校验规则

## 6.1 编码格式校验
- `pageCode` 正则：`^[a-zA-Z0-9_-]+$`
- `templateCode` 正则：`^[a-zA-Z0-9_-]+$`
- 动态 JSON 字段名（过滤/排序）正则：`^[a-zA-Z0-9_]+$`

## 6.2 分页规则
- `page` 小于 1 时按 1
- `pageSize` 小于 1 时按 20
- `pageSize` 大于 200 时按 200

## 6.3 异常与返回

异常处理类：`/Users/sailv/IdeaProjects/quickForm /src/main/java/com/quickform/api/exception/GlobalExceptionHandler.java`

- `BadRequestException` -> HTTP 400
- `NotFoundException` -> HTTP 404
- 其他异常 -> HTTP 500

统一返回：`ApiResponse.error(message)`

## 7. 核心流程说明

## 7.1 发起流程
1. 校验 `pageCode`、记录存在。
2. 检查该记录无待处理任务且无进行中实例。
3. 解析模板（优先请求 `templateCode`，否则默认模板）。
4. 创建 `workflow_instance(status=submitted)`。
5. 按首节点生成 `workflow_task`。

## 7.2 审批通过
1. 找到记录当前 active instance。
2. 找到可操作任务（本人任务或无审批人任务）。
3. 将任务置 `done + approve`。
4. 若节点 `any`：同节点其余待办置 `cancelled + skip`。
5. 若节点完成：进入下一节点并生成任务；若无下一节点则实例置 `approved`。

## 7.3 审批驳回
1. 找到可操作任务并置 `done + reject`。
2. 同实例剩余 `pending` 任务全置 `cancelled + skip`。
3. 实例置 `rejected`。

## 8. 前端固定页面建议（最少页面）

前端固定开发 6 类页面即可，按 `pageCode` 复用：
- 数据列表页：调用 `/data/{pageCode}/query`
- 数据详情页：调用 `/data/{pageCode}/{id}/get`
- 数据编辑页：调用 `/data/{pageCode}/create`、`/data/{pageCode}/{id}/update`、`/delete`
- 发起审批页：模板列表 + `/workflow/{pageCode}/{id}/submit`
- 审批中心页：待办 `/workflow/todo/query`、已办 `/workflow/done/query`、我发起 `/workflow/my-apply/query`
- 流程轨迹弹窗页：`/workflow/record/timeline`

关键要求：
- 数据页面不依赖流程查询接口。
- 流程页面不依赖业务 `data` 字段。
- 报表页独立调用 `/report/run`。

## 9. 当前实现文件映射

- 数据服务：`/Users/sailv/IdeaProjects/quickForm /src/main/java/com/quickform/api/service/DataService.java`
- 流程服务：`/Users/sailv/IdeaProjects/quickForm /src/main/java/com/quickform/api/service/WorkflowService.java`
- 报表服务：`/Users/sailv/IdeaProjects/quickForm /src/main/java/com/quickform/api/service/ReportService.java`
- 数据 Mapper：`/Users/sailv/IdeaProjects/quickForm /src/main/resources/mapper/DataMapper.xml`
- 流程 Mapper：`/Users/sailv/IdeaProjects/quickForm /src/main/resources/mapper/WorkflowMapper.xml`
- 报表 Mapper：`/Users/sailv/IdeaProjects/quickForm /src/main/resources/mapper/ReportMapper.xml`

## 10. 部署与运行

配置文件：`/Users/sailv/IdeaProjects/quickForm /src/main/resources/application.yml`

环境变量：
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASS`

启动：

```bash
mvn spring-boot:run
```

编译验证：

```bash
mvn clean compile
```

`schema.sql` 会在启动时自动执行建表。

## 11. 已知约束

- 当前没有接入鉴权与数据权限。
- 报表 SQL 由后台直接执行，默认信任配置来源。
- 数据更新接口为“全量替换 JSONB”，不做 JSON Patch。
- `data_record.status` 为业务态，不自动与流程实例状态联动。
