CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS data_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  page_code TEXT NOT NULL,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'draft',
  workflow_template_code TEXT,
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
  UNIQUE (page_code, template_code),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workflow_task (
  id BIGSERIAL PRIMARY KEY,
  record_id UUID NOT NULL REFERENCES data_record(id),
  page_code TEXT NOT NULL,
  template_id BIGINT REFERENCES workflow_template(id),
  template_code TEXT NOT NULL,
  node_code TEXT NOT NULL,
  assignee TEXT,
  status TEXT NOT NULL,
  action TEXT NOT NULL,
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

ALTER TABLE data_record ADD COLUMN IF NOT EXISTS workflow_template_code TEXT;
ALTER TABLE workflow_task ADD COLUMN IF NOT EXISTS template_id BIGINT;
ALTER TABLE workflow_task ADD COLUMN IF NOT EXISTS template_code TEXT;
ALTER TABLE workflow_task ADD COLUMN IF NOT EXISTS operated_by TEXT;
ALTER TABLE workflow_task ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
UPDATE workflow_task SET template_code = COALESCE(template_code, 'legacy') WHERE template_code IS NULL;
ALTER TABLE workflow_task ALTER COLUMN template_code SET NOT NULL;
ALTER TABLE report DROP CONSTRAINT IF EXISTS report_page_code_name_key;

CREATE INDEX IF NOT EXISTS idx_data_page ON data_record(page_code);
CREATE INDEX IF NOT EXISTS idx_data_gin ON data_record USING GIN (data);
CREATE INDEX IF NOT EXISTS idx_data_status ON data_record(status);
CREATE INDEX IF NOT EXISTS idx_data_creator ON data_record(created_by);
CREATE INDEX IF NOT EXISTS idx_data_template ON data_record(page_code, workflow_template_code);
CREATE INDEX IF NOT EXISTS idx_workflow_template_page ON workflow_template(page_code);
CREATE INDEX IF NOT EXISTS idx_workflow_template_default ON workflow_template(page_code, is_default);
CREATE INDEX IF NOT EXISTS idx_report_page ON report(page_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_report_page_code ON report(page_code);
CREATE INDEX IF NOT EXISTS idx_task_todo ON workflow_task(assignee, status, page_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_record_node_status ON workflow_task(record_id, node_code, status);
CREATE INDEX IF NOT EXISTS idx_task_template ON workflow_task(page_code, template_code);
CREATE INDEX IF NOT EXISTS idx_task_operated ON workflow_task(operated_by, action, updated_at DESC);
