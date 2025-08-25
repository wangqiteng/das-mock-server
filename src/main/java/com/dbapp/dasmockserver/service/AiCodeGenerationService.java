package com.dbapp.dasmockserver.service;

import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import com.dbapp.dasmockserver.model.ApiEndpoint.HttpMethod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiCodeGenerationService {
    
    @Autowired(required = false)
    private ChatClient chatClient;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 使用AI分析文档并生成API端点信息
     */
    public List<ApiEndpoint> generateApiEndpoints(String documentContent, MockService mockService) {
        if (chatClient == null) {
            // 如果没有配置AI，返回空列表
            return List.of();
        }
        
        String prompt = buildApiAnalysisPrompt(documentContent);
        
        String aiResponse = chatClient.prompt().user(prompt).call().content();
        
        return parseApiEndpointsFromAiResponse(aiResponse, mockService);
    }
    
    /**
     * 生成完整的Mock Server代码
     */
    public String generateMockServerCode(MockService mockService, List<ApiEndpoint> endpoints) {
        if (chatClient == null) {
            // 如果没有配置AI，返回默认代码
            return generateDefaultMockServerCode(mockService, endpoints);
        }
        
        String prompt = buildCodeGenerationPrompt(mockService, endpoints);
        
        return chatClient.prompt().user(prompt).call().content();
    }
    
    /**
     * 为单个端点生成Mock响应
     */
    public String generateMockResponse(ApiEndpoint endpoint) {
        if (chatClient == null) {
            // 如果没有配置AI，返回默认响应
            return generateDefaultMockResponse(endpoint);
        }
        
        String prompt = buildMockResponsePrompt(endpoint);
        
        return chatClient.prompt().user(prompt).call().content();
    }
    
    /**
     * 构建API分析提示词
     */
    private String buildApiAnalysisPrompt(String documentContent) {
        return String.format("""
            请分析以下API文档内容，提取所有API端点信息。请以JSON格式返回结果，格式如下：
            [
                {
                    "name": "端点名称",
                    "path": "/api/endpoint",
                    "method": "GET|POST|PUT|DELETE|PATCH",
                    "description": "端点描述",
                    "requestSchema": "请求参数JSON Schema",
                    "responseSchema": "响应参数JSON Schema"
                }
            ]
            
            文档内容：
            %s
            
            请确保：
            1. 准确识别HTTP方法和路径
            2. 提取请求和响应的参数结构
            3. 生成合理的JSON Schema
            4. 只返回JSON格式，不要其他解释文字
            """, documentContent);
    }
    
    /**
     * 构建代码生成提示词
     */
    private String buildCodeGenerationPrompt(MockService mockService, List<ApiEndpoint> endpoints) {
        StringBuilder endpointsJson = new StringBuilder();
        for (ApiEndpoint endpoint : endpoints) {
            endpointsJson.append(String.format("""
                {
                    "name": "%s",
                    "path": "%s",
                    "method": "%s",
                    "description": "%s",
                    "requestSchema": %s,
                    "responseSchema": %s
                },
                """, 
                endpoint.getName(),
                endpoint.getPath(),
                endpoint.getMethod().name(),
                endpoint.getDescription() != null ? endpoint.getDescription() : "",
                endpoint.getRequestSchema() != null ? endpoint.getRequestSchema() : "{}",
                endpoint.getResponseSchema() != null ? endpoint.getResponseSchema() : "{}"
            ));
        }
        
        return String.format("""
            请为以下API端点生成一个完整的Spring Boot Mock Server代码。
            
            服务信息：
            - 服务名称: %s
            - 服务描述: %s
            - 端口: %d
            
            API端点：
            %s
            
            请生成以下文件：
            1. 主应用类 (Application.java)
            2. 控制器类 (Controller.java)
            3. 数据模型类 (DTO.java)
            4. 配置文件 (application.properties)
            5. pom.xml
            
            要求：
            1. 使用Spring Boot 3.x
            2. 支持CORS
            3. 包含请求验证
            4. 生成合理的Mock数据
            5. 支持动态响应延迟
            6. 包含错误处理
            7. 代码要完整可运行
            
            请按文件分别返回，每个文件用```java开始，```结束。
            """, 
            mockService.getName(),
            mockService.getDescription() != null ? mockService.getDescription() : "",
            mockService.getPort(),
            endpointsJson.toString()
        );
    }
    
    /**
     * 构建Mock响应生成提示词
     */
    private String buildMockResponsePrompt(ApiEndpoint endpoint) {
        return String.format("""
            请为以下API端点生成一个合理的Mock响应数据：
            
            端点信息：
            - 名称: %s
            - 路径: %s
            - 方法: %s
            - 描述: %s
            - 响应Schema: %s
            
            请生成符合响应Schema的JSON数据，确保：
            1. 数据类型正确
            2. 包含合理的示例值
            3. 数据量适中（不要太多）
            4. 只返回JSON，不要其他文字
            """,
            endpoint.getName(),
            endpoint.getPath(),
            endpoint.getMethod().name(),
            endpoint.getDescription() != null ? endpoint.getDescription() : "",
            endpoint.getResponseSchema() != null ? endpoint.getResponseSchema() : "{}"
        );
    }
    
    /**
     * 从AI响应中解析API端点信息
     */
    private List<ApiEndpoint> parseApiEndpointsFromAiResponse(String aiResponse, MockService mockService) {
        List<ApiEndpoint> endpoints = new ArrayList<>();
        
        try {
            // 清理AI响应，提取JSON部分
            String jsonContent = extractJsonFromResponse(aiResponse);
            
            // 解析JSON数组
            List<JsonNode> endpointNodes = objectMapper.readValue(jsonContent, new TypeReference<List<JsonNode>>() {});
            
            for (JsonNode endpointNode : endpointNodes) {
                ApiEndpoint endpoint = new ApiEndpoint();
                endpoint.setMockService(mockService);
                
                // 解析基本信息
                endpoint.setName(getStringValue(endpointNode, "name", "未命名端点"));
                endpoint.setPath(getStringValue(endpointNode, "path", "/api/unknown"));
                endpoint.setDescription(getStringValue(endpointNode, "description", ""));
                
                // 解析HTTP方法
                String methodStr = getStringValue(endpointNode, "method", "GET");
                endpoint.setMethod(parseHttpMethod(methodStr));
                
                // 解析请求Schema
                JsonNode requestSchema = endpointNode.get("requestSchema");
                if (requestSchema != null) {
                    endpoint.setRequestSchema(requestSchema.toString());
                }
                
                // 解析响应Schema
                JsonNode responseSchema = endpointNode.get("responseSchema");
                if (responseSchema != null) {
                    endpoint.setResponseSchema(responseSchema.toString());
                }
                
                // 设置默认值
                endpoint.setMockResponse("{}");
                endpoint.setResponseDelay(0);
                endpoint.setStatusCode(200);
                endpoint.setHeaders(Map.of()); // 使用空的Map而不是字符串
                
                endpoints.add(endpoint);
            }
            
        } catch (JsonProcessingException e) {
            // 如果JSON解析失败，记录错误并返回空列表
            System.err.println("解析AI响应JSON失败: " + e.getMessage());
            System.err.println("AI响应内容: " + aiResponse);
        } catch (Exception e) {
            // 处理其他异常
            System.err.println("解析API端点时发生错误: " + e.getMessage());
        }
        
        return endpoints;
    }
    
    /**
     * 从AI响应中提取JSON内容
     */
    private String extractJsonFromResponse(String aiResponse) {
        // 移除可能的markdown代码块标记
        String cleaned = aiResponse.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        return cleaned.trim();
    }
    
    /**
     * 安全获取字符串值
     */
    private String getStringValue(JsonNode node, String fieldName, String defaultValue) {
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : defaultValue;
    }
    
    /**
     * 解析HTTP方法
     */
    private HttpMethod parseHttpMethod(String methodStr) {
        try {
            return HttpMethod.valueOf(methodStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 如果解析失败，返回GET作为默认值
            System.err.println("无法解析HTTP方法: " + methodStr + ", 使用默认值GET");
            return HttpMethod.GET;
        }
    }
    
    /**
     * 生成默认的Mock Server代码
     */
    private String generateDefaultMockServerCode(MockService mockService, List<ApiEndpoint> endpoints) {
        return String.format("""
            // 默认生成的Mock Server代码
            // 服务名称: %s
            // 端口: %d
            // 端点数量: %d
            
            // 这里应该包含完整的Spring Boot项目代码
            // 由于没有AI配置，返回默认代码模板
            """, 
            mockService.getName(),
            mockService.getPort(),
            endpoints.size()
        );
    }
    
    /**
     * 生成默认的Mock响应
     */
    private String generateDefaultMockResponse(ApiEndpoint endpoint) {
        return String.format("""
            {
                "message": "这是默认的Mock响应",
                "endpoint": "%s",
                "method": "%s",
                "timestamp": "%s"
            }
            """,
            endpoint.getPath(),
            endpoint.getMethod().name(),
            java.time.LocalDateTime.now()
        );
    }
    
    /**
     * 优化AI生成的代码
     */
    public String optimizeGeneratedCode(String generatedCode) {
        if (chatClient == null) {
            return generatedCode; // 如果没有AI，直接返回原代码
        }
        
        String prompt = String.format("""
            请优化以下Spring Boot代码，确保：
            1. 代码风格符合Java规范
            2. 添加必要的注释
            3. 优化性能
            4. 增强安全性
            5. 改进错误处理
            
            代码：
            %s
            
            请返回优化后的完整代码。
            """, generatedCode);
        
        return chatClient.prompt().user(prompt).call().content();
    }
    
    /**
     * 生成测试代码
     */
    public String generateTestCode(MockService mockService, List<ApiEndpoint> endpoints) {
        if (chatClient == null) {
            return String.format("""
                // 默认测试代码
                // 服务名称: %s
                // 端口: %d
                // 端点数量: %d
                """, 
                mockService.getName(),
                mockService.getPort(),
                endpoints.size()
            );
        }
        
        String prompt = String.format("""
            请为以下Mock Server生成完整的测试代码：
            
            服务信息：
            - 名称: %s
            - 端口: %d
            
            API端点：
            %s
            
            请生成：
            1. 集成测试类
            2. 单元测试类
            3. 测试配置文件
            
            要求：
            1. 使用JUnit 5和Spring Boot Test
            2. 测试所有端点
            3. 验证响应格式
            4. 包含边界条件测试
            """, 
            mockService.getName(),
            mockService.getPort(),
            endpoints.toString()
        );
        
        return chatClient.prompt().user(prompt).call().content();
    }
} 