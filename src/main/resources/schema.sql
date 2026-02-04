CREATE EXTENSION IF NOT EXISTS pgcrypto;

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

CREATE INDEX IF NOT EXISTS idx_dataset_code ON dataset(code);
CREATE INDEX IF NOT EXISTS idx_field_dataset ON field(dataset_id);
CREATE INDEX IF NOT EXISTS idx_data_dataset ON data_record(dataset_id);
CREATE INDEX IF NOT EXISTS idx_data_gin ON data_record USING GIN (data);
CREATE INDEX IF NOT EXISTS idx_task_assignee ON workflow_task(assignee);
CREATE INDEX IF NOT EXISTS idx_task_record_node_status ON workflow_task(record_id, node_code, status);
