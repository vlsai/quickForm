# QuickForm 设计文档（多流程模板审批 + 固定审批中心）

日期：2026-03-06  
适用基线：B（后端不存页面；前端固定 `page_code`）

## 1. 背景与目标

### 1.1 背景
- 前端通过低代码平台快速生成业务页面，但不负责流程引擎逻辑。
- 后端统一承接：
  - 业务数据（JSONB）
  - 审批流程（与 `page_code` 绑定）
  - 报表能力（与 `page_code` 绑定）

### 1.2 目标
- 一个 `page_code` 支持多个流程模板，发起时可选择模板。
- 固定提供审批中心页面能力（待办/已办/我发起），不依赖报表实现审批页面。
- 所有接口使用 `POST`。
- 后端接口通用复用，前端页面尽量少写、可模板化。

### 1.3 非目标
- 本期不实现页面 schema 存储/渲染。
- 不处理复杂权限与安全策略（按当前约定不展开）。

## 2. 总体方案

### 2.1 架构
- 前端：
  - 固定 4 个页面模板：发起、我的待办、我的已办、我发起的。
  - 每个页面按 `page_code` 传参复用。
- 后端：
  - Spring Boot + MyBatis XML
  - 提供通用数据接口 + 审批模板接口 + 审批中心查询接口
- 数据库：
  - PostgreSQL
  - 业务数据 `JSONB`
  - 流程模板 JSON 配置

### 2.2 关键设计原则
- `page_code` 是业务集合分组键。
- 流程模板按 `page_code + template_code` 唯一标识。
- 发起时写入选择的模板，后续审批沿该模板推进。
- 审批中心查询由后端专用接口输出，前端仅展示。

## 3. 数据模型设计（PostgreSQL）

> 当前实现采用“数据域”和“流程域”彻底分离：
> - 业务数据在 `data_record`（JSONB）
> - 流程运行状态在 `workflow_instance + workflow_task`
> - 不再把流程状态写入 `data_record` 的流程专用字段

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS data_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  page_code TEXT NOT NULL,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'submitted', 'approved', 'rejected')),
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  created_by TEXT,
  updated_by TEXT
);

CREATE TABLE IF NOT EXISTS workflow_template (
  id BIGSERIAL PRIMARY KEY,
  page_code TEXT NOT NULL,
  template_code TEXT NOT NULL,
  name TEXT NOT NULL,
  config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE (page_code, template_code)
);

CREATE TABLE IF NOT EXISTS workflow_instance (
  id BIGSERIAL PRIMARY KEY,
  page_code TEXT NOT NULL,
  template_id BIGINT REFERENCES workflow_template(id),
  record_id UUID NOT NULL REFERENCES data_record(id),
  template_code TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'submitted' CHECK (status IN ('submitted', 'approved', 'rejected')),
  current_node_code TEXT,
  starter TEXT,
  started_at TIMESTAMP NOT NULL DEFAULT NOW(),
  finished_at TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workflow_task (
  id BIGSERIAL PRIMARY KEY,
  instance_id BIGINT NOT NULL REFERENCES workflow_instance(id),
  page_code TEXT NOT NULL,
  record_id UUID NOT NULL REFERENCES data_record(id),
  template_id BIGINT REFERENCES workflow_template(id),
  template_code TEXT NOT NULL,
  node_code TEXT NOT NULL,
  assignee TEXT,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'done', 'cancelled')),
  action TEXT NOT NULL DEFAULT 'pending' CHECK (action IN ('pending', 'approve', 'reject', 'skip')),
  comment TEXT,
  operated_by TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS report (
  id BIGSERIAL PRIMARY KEY,
  page_code TEXT NOT NULL,
  name TEXT NOT NULL,
  sql_text TEXT NOT NULL,
  options JSONB NOT NULL DEFAULT '{}'::jsonb
);
```

### 3.1 索引与唯一约束

```sql
CREATE INDEX IF NOT EXISTS idx_data_page ON data_record(page_code);
CREATE INDEX IF NOT EXISTS idx_data_status ON data_record(status);
CREATE INDEX IF NOT EXISTS idx_data_creator ON data_record(created_by);
CREATE INDEX IF NOT EXISTS idx_data_gin ON data_record USING GIN (data);

CREATE INDEX IF NOT EXISTS idx_workflow_template_page ON workflow_template(page_code);
CREATE INDEX IF NOT EXISTS idx_workflow_template_default ON workflow_template(page_code, is_default);
CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_template_default_page
  ON workflow_template(page_code) WHERE is_default = TRUE;

CREATE INDEX IF NOT EXISTS idx_workflow_instance_record ON workflow_instance(record_id, page_code);
CREATE INDEX IF NOT EXISTS idx_workflow_instance_starter ON workflow_instance(starter, page_code, updated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_instance_active_record
  ON workflow_instance(page_code, record_id) WHERE status = 'submitted';

CREATE INDEX IF NOT EXISTS idx_task_todo ON workflow_task(assignee, status, page_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_record_node_status ON workflow_task(record_id, node_code, status);
CREATE INDEX IF NOT EXISTS idx_task_instance_node_status ON workflow_task(instance_id, node_code, status);
CREATE INDEX IF NOT EXISTS idx_task_template ON workflow_task(page_code, template_code);
CREATE INDEX IF NOT EXISTS idx_task_operated ON workflow_task(operated_by, action, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_report_page ON report(page_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_report_page_code ON report(page_code);
```

## 4. 流程配置模型

```json
{
  "nodes": [
    {
      "code": "L1",
      "name": "第一层审批",
      "mode": "all",
      "assignees": ["A", "B"]
    },
    {
      "code": "L2",
      "name": "第二层审批",
      "mode": "any",
      "assignees": ["C", "D"]
    }
  ]
}
```

规则：
- `mode=all`：该节点全部审批人通过后流转。
- `mode=any`：任一审批人通过即流转，其他同节点待办置 `cancelled/skip`。
- 任一 `reject`：流程结束，单据状态置 `rejected`，其余待办取消。

## 5. 接口设计（全部 POST）

## 5.1 流程模板管理
- `POST /workflow/template/list`
- `POST /workflow/template/get`
- `POST /workflow/template/save`
- `POST /workflow/template/delete`
- `POST /workflow/template/set-default`

`/workflow/template/save` 示例：

```json
{
  "pageCode": "change_apply",
  "templateCode": "normal_change_v1",
  "name": "常规变更流程",
  "enabled": true,
  "isDefault": false,
  "config": {
    "nodes": [
      {"code":"L1","name":"第一层审批","mode":"all","assignees":["A","B"]},
      {"code":"L2","name":"第二层审批","mode":"any","assignees":["C","D"]}
    ]
  }
}
```

## 5.2 发起与审批动作
- `POST /workflow/{pageCode}/{id}/submit`
- `POST /workflow/{pageCode}/{id}/approve`
- `POST /workflow/{pageCode}/{id}/reject`

`submit` 请求体：

```json
{
  "operator": "user_001",
  "templateCode": "normal_change_v1",
  "comment": "发起审批"
}
```

说明：
- `templateCode` 不传时，后端取默认模板。
- 若无默认模板且未传 `templateCode`，返回 400。

## 5.3 固定审批中心查询（不使用报表接口）
- `POST /workflow/todo/query`
- `POST /workflow/done/query`
- `POST /workflow/my-apply/query`
- `POST /workflow/record/timeline`

`todo/query` 示例：

```json
{
  "assignee": "A",
  "pageCode": "change_apply",
  "page": 1,
  "pageSize": 20
}
```

返回字段建议：
- `taskId`
- `instanceId`
- `recordId`
- `pageCode`
- `templateCode`
- `templateName`
- `nodeCode`
- `taskStatus`
- `workflowStatus`
- `currentNodeCode`
- `createdAt`

## 5.4 通用数据接口（保持）
- `POST /data/{pageCode}/query`
- `POST /data/{pageCode}/create`
- `POST /data/{pageCode}/{id}/update`
- `POST /data/{pageCode}/{id}/delete`
- `POST /data/{pageCode}/{id}/get`

## 6. 状态机设计

## 6.1 业务数据状态（`data_record.status`）
- `draft`
- `submitted`
- `approved`
- `rejected`

说明：
- 该字段属于业务数据域，后端通用数据接口可读写。
- 工作流不再强制驱动该字段流转（数据展示页面与工作流页面职责分离）。

## 6.2 流程实例状态（`workflow_instance.status`）
- `submitted`
- `approved`
- `rejected`

流转：
- `submitted -> approved|rejected`

## 6.3 审批任务状态（`workflow_task.status + action`）
- `pending + pending`
- `done + approve`
- `done + reject`
- `cancelled + skip`

## 7. 关键实现约束

### 7.1 事务性
- `submit/approve/reject` 必须加事务。
- 防止部分写成功导致流程状态不一致。

### 7.2 并发与幂等
- 审批更新必须附加 `status='pending'` 条件。
- 更新条数为 0 时返回“任务已处理或无权限”。
- `submit` 前检查是否已有有效待办，阻止重复提交。

### 7.3 参数与校验
- `pageCode`、`templateCode` 格式校验（字母数字下划线中划线）。
- 节点 `code` 在同一模板内唯一。
- `assignees` 允许多人，不能为空数组时应有明确兜底策略。

## 8. 前端最小化实现建议

固定 4 页即可：
- 发起页
- 我的待办
- 我的已办
- 我发起的

前端策略：
- 使用统一列表组件渲染后端返回结构。
- 审批动作按钮仅绑定通用接口。
- 页面层只传 `pageCode` 与用户标识，不写业务 SQL。

## 9. 典型时序（A/B + C/D）

1. 保存模板：L1(`all`: A,B) -> L2(`any`: C,D)
2. 发起人创建业务数据
3. 发起审批（选择模板）
4. 生成 L1 两条 `pending`
5. A approve（L1 未完成）
6. B approve（L1 完成，进入 L2）
7. 生成 C、D 两条 `pending`
8. C approve（D 自动 `cancelled/skip`）
9. `workflow_instance.status = approved`

## 10. 当前实现结果

- 数据与流程已解耦：流程状态落 `workflow_instance`，业务 JSON 仍在 `data_record`。
- 审批中心接口固定：`todo/done/my-apply/timeline`，不依赖报表 SQL。
- 支持多模板发起与 `all/any` 审批语义。
- 报表改为按 `pageCode` 从 `report.sql_text` 获取 SQL 执行。
- 每个 `pageCode` 限定 1 条报表 SQL（唯一约束）。

## 11. 验收标准

- 同一 `pageCode` 可绑定多个流程模板。
- 发起时可选择模板并正确生成首节点待办。
- `all/any` 语义严格生效。
- 固定审批中心不依赖报表接口。
- 所有接口均为 POST。
- 并发场景无重复审批成功问题。
