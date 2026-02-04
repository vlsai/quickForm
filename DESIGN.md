# QuickForm 后端设计文档（Spring Boot + PostgreSQL + MyBatis XML）

> 目标：后端一次开发，前端（TinyEngine）通过元数据驱动动态页面，实现动态字段/增删列、通用 CRUD、流程审批、报表 SQL。

## 1. 总体架构

- **前端**：TinyEngine 低代码搭建页面、表单、列表、报表。
- **后端**：Spring Boot + MyBatis XML，提供统一元数据、通用数据、流程、报表接口（全部 POST）。
- **数据库**：PostgreSQL，业务数据以 JSONB 存储，字段动态可扩展。

## 2. 数据库表结构（schema.sql）

### 2.1 元数据表

```sql
CREATE TABLE IF NOT EXISTS app (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  code TEXT NOT NULL UNIQUE,
  options JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS dataset (
  id BIGSERIAL PRIMARY KEY,
  app_id BIGINT REFERENCES app(id),
  name TEXT NOT NULL,
  code TEXT NOT NULL UNIQUE,
  primary_key TEXT NOT NULL DEFAULT 'id',
  options JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS field (
  id BIGSERIAL PRIMARY KEY,
  dataset_id BIGINT NOT NULL REFERENCES dataset(id),
  name TEXT NOT NULL,
  code TEXT NOT NULL,
  type TEXT NOT NULL,
  required BOOLEAN NOT NULL DEFAULT FALSE,
  default_value TEXT,
  options JSONB NOT NULL DEFAULT '{}'::jsonb,
  order_no INT NOT NULL DEFAULT 0,
  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE(dataset_id, code)
);

CREATE TABLE IF NOT EXISTS page (
  id BIGSERIAL PRIMARY KEY,
  app_id BIGINT REFERENCES app(id),
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  schema_json JSONB NOT NULL DEFAULT '{}'::jsonb
);
```

### 2.2 工作流与报表

```sql
CREATE TABLE IF NOT EXISTS workflow (
  id BIGSERIAL PRIMARY KEY,
  dataset_id BIGINT REFERENCES dataset(id),
  name TEXT NOT NULL,
  config_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS report (
  id BIGSERIAL PRIMARY KEY,
  app_id BIGINT REFERENCES app(id),
  name TEXT NOT NULL,
  sql_text TEXT NOT NULL,
  options JSONB NOT NULL DEFAULT '{}'::jsonb
);
```

### 2.3 业务数据（JSONB 动态字段）

```sql
CREATE TABLE IF NOT EXISTS data_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  dataset_id BIGINT NOT NULL REFERENCES dataset(id),
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'draft',
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  created_by TEXT,
  updated_by TEXT
);
```

### 2.4 审批任务

```sql
CREATE TABLE IF NOT EXISTS workflow_task (
  id BIGSERIAL PRIMARY KEY,
  record_id UUID NOT NULL REFERENCES data_record(id),
  dataset_id BIGINT NOT NULL REFERENCES dataset(id),
  node_code TEXT NOT NULL,
  assignee TEXT,
  status TEXT NOT NULL,
  action TEXT NOT NULL,
  comment TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### 2.5 索引

```sql
CREATE INDEX IF NOT EXISTS idx_dataset_code ON dataset(code);
CREATE INDEX IF NOT EXISTS idx_field_dataset ON field(dataset_id);
CREATE INDEX IF NOT EXISTS idx_data_dataset ON data_record(dataset_id);
CREATE INDEX IF NOT EXISTS idx_data_gin ON data_record USING GIN (data);
CREATE INDEX IF NOT EXISTS idx_task_assignee ON workflow_task(assignee);
CREATE INDEX IF NOT EXISTS idx_task_record_node_status ON workflow_task(record_id, node_code, status);
```

## 3. 动态字段与“Excel 式增删列”实现

- **增列**：向 `field` 表新增字段定义，不改业务数据。
- **删列**：字段软删 `is_deleted = TRUE`。
- **读取**：根据字段元数据动态渲染表头/表单。
- **数据存储**：所有业务数据存入 `data_record.data (jsonb)`。

## 4. 后端接口设计（全部 POST）

### 4.1 元数据接口

| 接口 | 功能 |
|---|---|
| `/meta/datasets/list` | 获取 dataset 列表 |
| `/meta/datasets/get` | 获取单个 dataset |
| `/meta/datasets/create` | 创建 dataset |
| `/meta/fields/list` | 获取字段列表 |
| `/meta/fields/create` | 创建字段 |
| `/meta/fields/update` | 更新字段 |
| `/meta/fields/delete` | 删除字段（软删） |

### 4.2 通用数据接口

| 接口 | 功能 |
|---|---|
| `/data/{datasetCode}/query` | 查询数据（支持 AND + OR） |
| `/data/{datasetCode}/create` | 新增记录 |
| `/data/{datasetCode}/{id}/update` | 更新记录 |
| `/data/{datasetCode}/{id}/delete` | 删除记录 |

### 4.3 工作流接口

| 接口 | 功能 |
|---|---|
| `/workflow/config/get` | 获取流程配置 |
| `/workflow/config/save` | 保存流程配置 |
| `/workflow/{datasetCode}/{id}/submit` | 提交流程 |
| `/workflow/{datasetCode}/{id}/approve` | 审批通过 |
| `/workflow/{datasetCode}/{id}/reject` | 审批驳回 |
| `/workflow/tasks` | 查询任务 |

### 4.4 报表接口

| 接口 | 功能 |
|---|---|
| `/report/run` | 直接执行 SQL 报表 |

> 报表 SQL 支持 `:param` 命名参数（后端会转换为 MyBatis 绑定）。

## 5. 查询表达式（AND + OR）

- `filters`：AND 条件
- `orFilters`：OR 条件（同一层级）
- 支持操作符：`eq/ne/gt/gte/lt/lte/like/in/contains`

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

## 6. 工作流设计（多节点/多审批人）

### 6.1 配置结构

```json
{
  "nodes": [
    {"code": "dept", "name": "部门审核", "mode": "all", "assignees": ["u1", "u2"]},
    {"code": "finance", "name": "财务审核", "mode": "any", "assignees": ["u3", "u4"]}
  ]
}
```

### 6.2 执行规则

- **顺序节点**：按 `nodes` 顺序推进
- **节点模式**：
  - `all`：所有审批人完成后流转下一节点
  - `any`：任意人审批后流转，其他待办取消
- **驳回**：结束流程，状态变为 `rejected`，未完成任务取消

## 7. MyBatis XML 结构

- Mapper 接口仅定义方法签名
- SQL 统一放在 `src/main/resources/mapper/*.xml`
- `application.yml` 配置：

```yaml
mybatis:
  mapper-locations: classpath*:mapper/*.xml
  type-aliases-package: com.quickform.api.model
  configuration:
    map-underscore-to-camel-case: true
```

## 8. 模块与目录结构

```
src/main/java/com/quickform/api
  controller/   REST 接口
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

## 9. 运行方式

```bash
mvn spring-boot:run
```

默认 DB 配置可通过环境变量设置：
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`

## 10. 约束与说明

- 所有接口均为 POST。
- 报表接口不做安全限制（按需求）。
- 动态字段全部落 JSONB，不修改物理列。
- 查询时仅允许元数据中存在的字段参与筛选/排序。

---

如需补充：
- 更复杂查询 DSL
- 工作流并行/条件分支
- OpenAPI 文档
- TinyEngine 前端映射示例

直接告诉我需要哪部分。
