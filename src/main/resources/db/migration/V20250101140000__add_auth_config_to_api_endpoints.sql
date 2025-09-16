-- 为API端点添加认证配置字段
ALTER TABLE api_endpoints ADD COLUMN auth_config TEXT;
