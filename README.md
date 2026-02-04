# QuickForm Backend (Spring Boot + MyBatis XML)

All APIs use POST.

## Run

Set environment variables (or edit `application.yml`):
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`

Start:

```bash
mvn spring-boot:run
```

`schema.sql` will auto-create tables on startup.

## API (POST)

### Meta
- `/meta/datasets/list`
- `/meta/datasets/get`
- `/meta/datasets/create`
- `/meta/fields/list`
- `/meta/fields/create`
- `/meta/fields/update`
- `/meta/fields/delete`

### Data
- `/data/{datasetCode}/query`
- `/data/{datasetCode}/create`
- `/data/{datasetCode}/{id}/update`
- `/data/{datasetCode}/{id}/delete`

### Workflow
- `/workflow/{datasetCode}/{id}/submit`
- `/workflow/{datasetCode}/{id}/approve`
- `/workflow/{datasetCode}/{id}/reject`
- `/workflow/tasks`
- `/workflow/config/get`
- `/workflow/config/save`

### Report
- `/report/run`

## Example Payloads

### Create Dataset

```json
{
  "name": "Customer",
  "code": "customer",
  "primaryKey": "id",
  "options": {
    "icon": "user"
  }
}
```

### Create Field

```json
{
  "datasetCode": "customer",
  "name": "Name",
  "code": "name",
  "type": "text",
  "required": true,
  "orderNo": 1
}
```

### Create Data

```json
{
  "status": "draft",
  "operator": "alice",
  "data": {
    "name": "张三",
    "age": 30
  }
}
```

### Query Data

```json
{
  "filters": [
    {"field": "name", "op": "like", "value": "张"},
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
  "datasetCode": "customer",
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
  "sql": "SELECT data->>'name' AS name, COUNT(1) AS cnt FROM data_record WHERE dataset_id = :datasetId GROUP BY name",
  "params": {"datasetId": 1}
}
```

说明：报表 SQL 支持 `:param` 命名参数（会自动转换为 MyBatis 绑定）。
