-- 为 api_endpoints 表增加智能期望配置列
ALTER TABLE api_endpoints ADD COLUMN IF NOT EXISTS smart_expectations TEXT;

