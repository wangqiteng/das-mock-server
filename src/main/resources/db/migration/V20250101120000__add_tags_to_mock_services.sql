-- 为mock_services表添加tags字段
ALTER TABLE mock_services ADD COLUMN tags TEXT;

-- 添加注释
COMMENT ON COLUMN mock_services.tags IS '服务标签，多个标签用逗号分隔';

