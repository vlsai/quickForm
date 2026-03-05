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

> 说明：以下为目标结构；用于替代当前“单流程配置”模型。

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 业务数据
CREATE TABLE IF NOT EXISTS data_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  page_code TEXT NOT NULL,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'draft', -- draft/submitted/approved/rejected
  workflow_template_code TEXT,          -- 发起时选中的模板
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  created_by TEXT,
  updated_by TEXT
);

-- 流程模板（同一 page_code 多模板）
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

-- 审批任务
CREATE TABLE IF NOT EXISTS workflow_task (
  id BIGSERIAL PRIMARY KEY,
  record_id UUID NOT NULL REFERENCES data_record(id),
  page_code TEXT NOT NULL,
  template_id BIGINT REFERENCES workflow_template(id),
  template_code TEXT NOT NULL,
  node_code TEXT NOT NULL,
  assignee TEXT,
  status TEXT NOT NULL, -- pending/done/cancelled
  action TEXT NOT NULL, -- pending/approve/reject/skip
  comment TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  operated_by TEXT
);

-- 报表
CREATE TABLE IF NOT EXISTS report (
  id BIGSERIAL PRIMARY KEY,
  page_code TEXT NOT NULL,
  name TEXT NOT NULL,
  sql_text TEXT NOT NULL,
  options JSONB NOT NULL DEFAULT '{}'::jsonb
);
```

### 3.1 索引建议

```sql
CREATE INDEX IF NOT EXISTS idx_data_page ON data_record(page_code);
CREATE INDEX IF NOT EXISTS idx_data_status ON data_record(status);
CREATE INDEX IF NOT EXISTS idx_data_creator ON data_record(created_by);
CREATE INDEX IF NOT EXISTS idx_data_template ON data_record(page_code, workflow_template_code);
CREATE INDEX IF NOT EXISTS idx_data_gin ON data_record USING GIN (data);

CREATE INDEX IF NOT EXISTS idx_tpl_page_enabled ON workflow_template(page_code, enabled);
CREATE INDEX IF NOT EXISTS idx_tpl_page_default ON workflow_template(page_code, is_default);

CREATE INDEX IF NOT EXISTS idx_task_todo ON workflow_task(assignee, status, page_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_record ON workflow_task(record_id, node_code, status);
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
- `recordId`
- `pageCode`
- `templateCode`
- `templateName`
- `nodeCode`
- `nodeName`
- `taskStatus`
- `recordStatus`
- `data`
- `createdAt`

## 5.4 通用数据接口（保持）
- `POST /data/{pageCode}/query`
- `POST /data/{pageCode}/create`
- `POST /data/{pageCode}/{id}/update`
- `POST /data/{pageCode}/{id}/delete`

## 6. 状态机设计

## 6.1 业务单据状态（`data_record.status`）
- `draft`
- `submitted`
- `approved`
- `rejected`

流转：
- `draft|rejected -> submitted`
- `submitted -> approved|rejected`

## 6.2 审批任务状态（`workflow_task.status + action`）
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
9. 单据 `approved`

## 10. 与当前实现的差距与改造项

当前实现已具备：
- `page_code + JSONB` 数据模型
- 多节点/多审批人能力
- `mode=all/any` 推进逻辑
- 报表按 `pageCode` 从库取 SQL，且每个 `pageCode` 一条 SQL

需改造：
- 单流程表 `workflow` 改为多模板 `workflow_template`
- `submit` 增加模板选择（`templateCode`）
- 原 `/workflow/tasks` 下线
- 新增固定审批中心查询接口（`todo/done/my-apply/timeline`）
- 审批动作补事务与幂等保护

## 11. 验收标准

- 同一 `pageCode` 可绑定多个流程模板。
- 发起时可选择模板并正确生成首节点待办。
- `all/any` 语义严格生效。
- 固定审批中心不依赖报表接口。
- 所有接口均为 POST。
- 并发场景无重复审批成功问题。
