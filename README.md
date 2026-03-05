# QuickForm Backend (Spring Boot + MyBatis XML)

后端只存两类内容：
- 业务数据 JSONB（`data_record` 表）
- 审核模板/任务与报表配置（均按 `page_code` 归类）

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
- `/data/{pageCode}/{id}/get`

### Workflow
- `/workflow/template/list`
- `/workflow/template/get`
- `/workflow/template/save`
- `/workflow/template/delete`
- `/workflow/template/set-default`
- `/workflow/{pageCode}/{id}/submit`
- `/workflow/{pageCode}/{id}/approve`
- `/workflow/{pageCode}/{id}/reject`
- `/workflow/todo/query`
- `/workflow/done/query`
- `/workflow/my-apply/query`
- `/workflow/record/timeline`

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

### Workflow Template Save

```json
{
  "pageCode": "change_apply",
  "templateCode": "normal_change_v1",
  "name": "change-approval",
  "enabled": true,
  "isDefault": true,
  "config": {
    "nodes": [
      {"code": "L1", "name": "第一层审批", "mode": "all", "assignees": ["A", "B"]},
      {"code": "L2", "name": "第二层审批", "mode": "any", "assignees": ["C", "D"]}
    ]
  }
}
```

### Submit Workflow

```json
{
  "operator": "u1001",
  "templateCode": "normal_change_v1",
  "comment": "发起审批"
}
```

### Run Report

```json
{
  "pageCode": "customer",
  "params": {"status": "approved"}
}
```

说明：
- 前端不传 SQL，后端按 `pageCode` 从 `report` 表读取 `sql_text` 后执行。
- 每个 `pageCode` 仅允许一条报表 SQL（数据库唯一约束）。
- 报表 SQL 里的 `:param` 会自动转换为 MyBatis 绑定参数。

说明：后端不维护页面信息，`pageCode` 由前端固定传入并作为数据/流程/报表分组键。

补充：
- 工作流状态已从 `data_record` 解耦，流程运行状态存放在 `workflow_instance`。
- 工作流待办/已办/我发起接口只返回流程域字段；业务 JSON 数据通过 `Data` 接口单独查询展示。
