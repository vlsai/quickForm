# QuickForm 后端设计文档（精简版：固定 page_code + JSONB 数据）

> 目标：后端不存页面，仅按前端固定 `page_code` 归类数据、流程、报表。

## 1. 架构概览

- **前端**：低代码平台生成页面并维护固定 `page_code`
- **后端**：Spring Boot + MyBatis XML，仅提供存储与查询
- **数据库**：PostgreSQL，业务数据 JSONB

## 2. 数据模型（PostgreSQL）

### 2.1 数据表（JSONB）

```sql
CREATE TABLE IF NOT EXISTS data_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  page_code TEXT NOT NULL,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'draft',
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  created_by TEXT,
  updated_by TEXT
);
```

- `data`：业务数据 JSONB
- 与 `page_code` 绑定，实现“一个页面一套数据集合”

### 2.3 工作流

```sql
CREATE TABLE IF NOT EXISTS workflow (
  id BIGSERIAL PRIMARY KEY,
  page_code TEXT NOT NULL,
  name TEXT NOT NULL,
  config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workflow_task (
  id BIGSERIAL PRIMARY KEY,
  record_id UUID NOT NULL REFERENCES data_record(id),
  page_code TEXT NOT NULL,
  node_code TEXT NOT NULL,
  assignee TEXT,
  status TEXT NOT NULL,
  action TEXT NOT NULL,
  comment TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### 2.4 报表

```sql
CREATE TABLE IF NOT EXISTS report (
  id BIGSERIAL PRIMARY KEY,
  page_code TEXT,
  name TEXT NOT NULL,
  sql_text TEXT NOT NULL,
  options JSONB NOT NULL DEFAULT '{}'::jsonb
);
```

### 2.5 索引

```sql
CREATE INDEX IF NOT EXISTS idx_data_page ON data_record(page_code);
CREATE INDEX IF NOT EXISTS idx_data_gin ON data_record USING GIN (data);
CREATE INDEX IF NOT EXISTS idx_workflow_page ON workflow(page_code);
CREATE INDEX IF NOT EXISTS idx_report_page ON report(page_code);
CREATE INDEX IF NOT EXISTS idx_task_page ON workflow_task(page_code);
CREATE INDEX IF NOT EXISTS idx_task_assignee ON workflow_task(assignee);
CREATE INDEX IF NOT EXISTS idx_task_record_node_status ON workflow_task(record_id, node_code, status);
```

## 3. 核心原则

1. `page_code` 由前端固定并透传
2. 后端不维护页面定义
3. 业务数据全部存 JSONB
4. 流程/报表按 `page_code` 隔离

## 4. 接口设计（全部 POST）

### 4.1 Data

| 接口 | 功能 |
|---|---|
| `/data/{pageCode}/query` | 查询数据（支持 AND + OR + 分页排序） |
| `/data/{pageCode}/create` | 新增数据 |
| `/data/{pageCode}/{id}/update` | 更新数据 |
| `/data/{pageCode}/{id}/delete` | 删除数据 |

### 4.2 Workflow

| 接口 | 功能 |
|---|---|
| `/workflow/config/get` | 获取流程配置 |
| `/workflow/config/save` | 保存流程配置 |
| `/workflow/{pageCode}/{id}/submit` | 提交 |
| `/workflow/{pageCode}/{id}/approve` | 审批通过 |
| `/workflow/{pageCode}/{id}/reject` | 审批驳回 |
| `/workflow/tasks` | 查询任务 |

### 4.3 Report

| 接口 | 功能 |
|---|---|
| `/report/run` | 按 `page_code` 读取 SQL 并执行报表 |

## 5. 查询表达式

- `filters`：AND
- `orFilters`：OR
- 运算符：`eq/ne/gt/gte/lt/lte/like/in/contains`
- 字段白名单：只允许 `[a-zA-Z0-9_]` 字段名

示例：

```json
{
  "filters": [
    {"field": "age", "op": "gte", "value": 18}
  ],
  "orFilters": [
    {"field": "status", "op": "eq", "value": "approved"},
    {"field": "status", "op": "eq", "value": "submitted"}
  ],
  "page": 1,
  "pageSize": 20
}
```

## 6. 工作流（多节点/多审批人）

配置结构：

```json
{
  "nodes": [
    {"code": "dept", "name": "部门审核", "mode": "all", "assignees": ["u1", "u2"]},
    {"code": "finance", "name": "财务审核", "mode": "any", "assignees": ["u3", "u4"]}
  ]
}
```

规则：
- 顺序节点推进
- `all`：所有审批人完成才进入下一节点
- `any`：任一审批人完成即流转，其他待办取消
- `reject`：结束流程，状态置为 `rejected`

## 7. MyBatis XML

- Mapper 仅定义方法签名
- SQL 放在 `src/main/resources/mapper/*.xml`

```yaml
mybatis:
  mapper-locations: classpath*:mapper/*.xml
  type-aliases-package: com.quickform.api.model
  configuration:
    map-underscore-to-camel-case: true
```

## 8. 目录结构

```
src/main/java/com/quickform/api
  controller/   数据/流程/报表接口
  dto/          请求/响应结构
  mapper/       MyBatis Mapper 接口
  model/        业务模型
  service/      业务逻辑
  exception/    异常处理
src/main/resources
  mapper/       XML SQL
  schema.sql    初始化表
  application.yml
```

## 9. page_code 约定

- 不提供 `/page/*` 管理接口。
- 后端不会校验 `page_code` 是否存在于页面表。
- 仅校验格式：`[a-zA-Z0-9_-]+`。

## 10. 报表执行约定

- 前端调用 `/report/run` 只需传 `pageCode`（可选 `reportName` 或 `reportId`）。
- 后端从 `report` 表查询 `sql_text` 再执行。
- 查询优先级：
  1. `reportId`
  2. `pageCode + reportName`
  3. `pageCode` 最新一条

---

如果需要更复杂的查询 DSL、工作流并行/条件分支、或 OpenAPI 文档，可以继续扩展。
