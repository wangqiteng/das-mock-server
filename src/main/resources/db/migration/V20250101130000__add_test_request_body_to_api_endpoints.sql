-- 为API端点表添加测试请求体字段
ALTER TABLE api_endpoints ADD COLUMN test_request_body TEXT;

-- 添加注释
COMMENT ON COLUMN api_endpoints.test_request_body IS '用于API测试的示例请求体数据';
