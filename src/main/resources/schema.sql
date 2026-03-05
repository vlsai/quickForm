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
  UNIQUE (page_code, template_code),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
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

CREATE INDEX IF NOT EXISTS idx_data_page ON data_record(page_code);
CREATE INDEX IF NOT EXISTS idx_data_gin ON data_record USING GIN (data);
CREATE INDEX IF NOT EXISTS idx_data_status ON data_record(status);
CREATE INDEX IF NOT EXISTS idx_data_creator ON data_record(created_by);
CREATE INDEX IF NOT EXISTS idx_workflow_template_page ON workflow_template(page_code);
CREATE INDEX IF NOT EXISTS idx_workflow_template_default ON workflow_template(page_code, is_default);
CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_template_default_page ON workflow_template(page_code) WHERE is_default = TRUE;
CREATE INDEX IF NOT EXISTS idx_workflow_instance_record ON workflow_instance(record_id, page_code);
CREATE INDEX IF NOT EXISTS idx_workflow_instance_starter ON workflow_instance(starter, page_code, updated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_instance_active_record ON workflow_instance(page_code, record_id) WHERE status = 'submitted';
CREATE INDEX IF NOT EXISTS idx_report_page ON report(page_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_report_page_code ON report(page_code);
CREATE INDEX IF NOT EXISTS idx_task_todo ON workflow_task(assignee, status, page_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_record_node_status ON workflow_task(record_id, node_code, status);
CREATE INDEX IF NOT EXISTS idx_task_instance_node_status ON workflow_task(instance_id, node_code, status);
CREATE INDEX IF NOT EXISTS idx_task_template ON workflow_task(page_code, template_code);
CREATE INDEX IF NOT EXISTS idx_task_operated ON workflow_task(operated_by, action, updated_at DESC);
