# QuickForm Backend (Spring Boot + MyBatis XML)

后端只存两类内容：
- 业务数据 JSONB（`data_record` 表）
- 审核配置/任务与报表配置（均按 `page_code` 归类）

工作流与报表与 `page_code` 绑定。

## Run

环境变量（或修改 `application.yml`）：
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`

启动：

```bash
mvn spring-boot:run
```

`schema.sql` 会在启动时自动建表。

## API（全部 POST）

### Data
- `/data/{pageCode}/query`
- `/data/{pageCode}/create`
- `/data/{pageCode}/{id}/update`
- `/data/{pageCode}/{id}/delete`

### Workflow
- `/workflow/config/get`
- `/workflow/config/save`
- `/workflow/{pageCode}/{id}/submit`
- `/workflow/{pageCode}/{id}/approve`
- `/workflow/{pageCode}/{id}/reject`
- `/workflow/tasks`

### Report
- `/report/run`

## Example Payloads

### Query Data (AND + OR)

```json
{
  "filters": [
    {"field": "age", "op": "gte", "value": 18}
  ],
  "orFilters": [
    {"field": "status", "op": "eq", "value": "approved"},
    {"field": "status", "op": "eq", "value": "submitted"}
  ],
  "sorts": [
    {"field": "updatedAt", "order": "desc"}
  ],
  "page": 1,
  "pageSize": 20
}
```

### Workflow Config Save

```json
{
  "pageCode": "customer",
  "name": "customer-approval",
  "config": {
    "nodes": [
      {"code": "dept", "name": "部门审核", "mode": "all", "assignees": ["u1", "u2"]},
      {"code": "finance", "name": "财务审核", "mode": "any", "assignees": ["u3", "u4"]}
    ]
  }
}
```

### Run Report

```json
{
  "sql": "SELECT data->>'name' AS name, COUNT(1) AS cnt FROM data_record WHERE page_code = :pageCode GROUP BY name",
  "params": {"pageCode": "customer"}
}
```

说明：报表 SQL 支持 `:param` 命名参数（自动转换为 MyBatis 绑定）。

说明：后端不维护页面信息，`pageCode` 由前端固定传入并作为数据/流程/报表分组键。
