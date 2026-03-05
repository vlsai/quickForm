# QuickForm 后端设计（当前实现）

详细方案请见：
- `docs/plans/2026-03-06-workflow-multi-template-design.md`

当前实现核心：
- 不存页面，仅使用前端固定 `page_code`
- 数据存储：`data_record.data (JSONB)`
- 流程：同一 `page_code` 支持多个 `workflow_template`，运行态在 `workflow_instance`
- 审批中心：固定接口 `todo/done/my-apply/timeline`，不依赖报表
- 报表：`/report/run` 按 `pageCode` 从 `report.sql_text` 取 SQL 执行
- 报表约束：每个 `page_code` 仅一条 SQL（`report.page_code` 唯一）
- 所有接口均为 `POST`
- 数据展示与审批中心解耦：流程接口不返回业务 JSON 数据，业务数据走 `Data` 接口

主要接口：
- Data：`/data/{pageCode}/query|create|update|delete|get`
- Workflow Template：`/workflow/template/list|get|save|delete|set-default`
- Workflow Action：`/workflow/{pageCode}/{id}/submit|approve|reject`
- Workflow Center：`/workflow/todo/query`、`/workflow/done/query`、`/workflow/my-apply/query`、`/workflow/record/timeline`
- Report：`/report/run`
