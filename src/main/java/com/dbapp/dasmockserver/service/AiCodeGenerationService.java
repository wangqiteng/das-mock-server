package com.dbapp.dasmockserver.service;

import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import com.dbapp.dasmockserver.model.ApiEndpoint.HttpMethod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
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
        
        String response = chatClient.prompt().user(prompt).call().content();
        
        // 清理响应，移除markdown格式
        return cleanMockResponse(response);
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
                    "parameters": {
                        "pathVariables": [
                            {
                                "name": "参数名",
                                "type": "string|integer|boolean",
                                "required": true|false,
                                "description": "参数描述"
                            }
                        ],
                        "queryParameters": [
                            {
                                "name": "参数名",
                                "type": "string|integer|boolean",
                                "required": true|false,
                                "description": "参数描述"
                            }
                        ],
                        "requestBody": {
                            "type": "object",
                            "properties": {
                                "fieldName": {
                                    "type": "string|integer|boolean|array|object",
                                    "required": true|false,
                                    "description": "字段描述"
                                }
                            }
                        }
                    },
                    "responseSchema": {
                        // 响应参数JSON Schema
                    }
                }
            ]
            
            文档内容：
            %s
            
            特别注意：
            1. 如果文档中包含表格数据（以"=== 表格数据 ==="开头），请从表格中提取字段信息
            2. 表格中的"字段名称"、"字段编码"、"字段类型"、"是否必输"等信息用于生成参数信息
            3. 优先使用表格中的"字段编码"作为参数名
            4. 根据"字段类型"确定参数的数据类型：
               - VARCHAR2 -> string
               - INT -> integer
               - DATE -> string (ISO格式)
               - CHAR -> string
               - NUMBER -> integer
               - BOOLEAN -> boolean
            5. 根据"是否必输"确定参数是否必需（Y=必需，N=可选）
            6. 忽略字典表（以"字典"开头的表格）
            7. 从URL中提取接口路径，识别路径中的{param}格式作为pathVariables
            8. 从请求方法中提取HTTP方法
            9. 根据HTTP方法确定参数类型：
               - GET: 通常使用queryParameters，如果有复杂对象则使用requestBody
               - POST/PUT/PATCH: 通常使用requestBody，简单参数可能使用queryParameters
               - DELETE: 通常使用pathVariables或queryParameters
            10. 如果文档中有请求示例，请参考示例中的字段名和类型
            11. 如果URL中包含{param}格式，这些参数应该放在pathVariables中
            12. 如果文档中明确提到查询参数，应该放在queryParameters中
            13. 如果文档中明确提到请求体，应该放在requestBody中
            
            请确保：
            1. 准确识别HTTP方法和路径
            2. 正确识别参数类型（pathVariables、queryParameters、requestBody）
            3. 从表格数据中正确提取所有字段信息
            4. 生成合理的参数结构，每个参数都要有type和required属性
            5. 只返回JSON格式，不要其他解释文字
            6. 确保参数信息包含从表格中提取的所有字段
            7. 字段名使用驼峰命名法
            8. 根据HTTP方法和文档内容合理分配参数类型
            """, documentContent);
    }
    
    /**
     * 构建代码生成提示词
     */
    private String buildCodeGenerationPrompt(MockService mockService, List<ApiEndpoint> endpoints) {
        StringBuilder endpointsJson = new StringBuilder();
        for (ApiEndpoint endpoint : endpoints) {
            // 生成安全的Java标识符
            String className = generateClassName(endpoint.getName());
            String methodName = generateMethodName(endpoint.getName());
            
            endpointsJson.append(String.format("""
                {
                    "name": "%s",
                    "className": "%s",
                    "methodName": "%s",
                    "path": "%s",
                    "method": "%s",
                    "description": "%s",
                    "requestSchema": %s,
                    "responseSchema": %s
                },
                """, 
                endpoint.getName(),
                className,
                methodName,
                endpoint.getPath(),
                endpoint.getMethod().name(),
                endpoint.getDescription() != null ? endpoint.getDescription() : "",
                endpoint.getRequestSchema() != null ? endpoint.getRequestSchema() : "{}",
                endpoint.getResponseSchema() != null ? endpoint.getResponseSchema() : "{}"
            ));
        }
        
        // 生成安全的服务名称
        String serviceClassName = generateClassName(mockService.getName());
        String packageName = generatePackageName(mockService.getName());
        
        return String.format("""
            请为以下API端点生成一个完整的Spring Boot Mock Server代码。
            
            服务信息：
            - 服务名称: %s
            - 服务类名: %s
            - 包名: %s
            - 服务描述: %s
            - 端口: %d
            
            API端点：
            %s
            
            请生成以下文件：
            1. 主应用类 (%sApplication.java)
            2. 控制器类 (%sController.java)
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
            8. 使用提供的类名和方法名
            9. 确保所有标识符符合Java命名规范
            
            请按文件分别返回，每个文件用```java开始，```结束。
            """, 
            mockService.getName(),
            serviceClassName,
            packageName,
            mockService.getDescription() != null ? mockService.getDescription() : "",
            mockService.getPort(),
            endpointsJson.toString(),
            serviceClassName,
            serviceClassName
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
            4. 只返回成功的响应结果，不要包含错误信息
            5. 如果是POST/PUT/PATCH请求，通常返回操作成功的响应
            6. 如果是GET请求，通常返回查询到的数据
            7. 响应格式应该简洁明了，符合RESTful API规范
            8. 只返回JSON，不要其他文字
            9. 不要同时返回成功和错误的结果
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
                
                // 解析参数信息（新格式）
                JsonNode parameters = endpointNode.get("parameters");
                if (parameters != null) {
                    endpoint.setParameters(parameters.toString());
                    
                    // 为了向后兼容，也设置requestSchema
                    if (parameters.has("requestBody") && parameters.get("requestBody").has("properties")) {
                        endpoint.setRequestSchema(parameters.get("requestBody").toString());
                    }
                } else {
                    // 兼容旧格式：解析requestSchema
                    JsonNode requestSchema = endpointNode.get("requestSchema");
                    if (requestSchema != null) {
                        endpoint.setRequestSchema(requestSchema.toString());
                    }
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
     * 生成Java类名，排除特殊符号
     */
    public String generateClassName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "DefaultClass";
        }
        
        // 移除特殊符号，只保留字母、数字和下划线
        String cleaned = name.replaceAll("[^a-zA-Z0-9_]", "");
        
        // 如果清理后为空，使用默认名称
        if (cleaned.trim().isEmpty()) {
            return "DefaultClass";
        }
        
        // 确保首字母大写（Java类名规范）
        String result = cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);
        
        // 如果以数字开头，添加前缀
        if (Character.isDigit(result.charAt(0))) {
            result = "Class" + result;
        }
        
        // 限制长度，避免过长
        if (result.length() > 50) {
            result = result.substring(0, 50);
        }
        
        return result;
    }
    
    /**
     * 生成Java方法名，从路径中提取最后两个单词，使用驼峰写法
     */
    public String generateMethodName(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "defaultMethod";
        }
        
        // 移除开头的斜杠
        String cleanPath = path.trim();
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        
        // 移除结尾的斜杠
        if (cleanPath.endsWith("/")) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }
        
        // 按斜杠分割路径
        String[] pathParts = cleanPath.split("/");
        
        // 过滤掉空字符串和版本号（如v1.0, v2等）
        List<String> meaningfulParts = new ArrayList<>();
        for (String part : pathParts) {
            if (!part.isEmpty() && !part.matches("^v\\d+(\\.\\d+)*$")) {
                meaningfulParts.add(part);
            }
        }
        
        // 如果路径部分太少，使用默认方法名
        if (meaningfulParts.size() < 1) {
            return "defaultMethod";
        }
        
        // 如果路径部分大于2，取最后两个单词
        List<String> selectedParts;
        if (meaningfulParts.size() > 2) {
            selectedParts = meaningfulParts.subList(meaningfulParts.size() - 2, meaningfulParts.size());
        } else {
            // 如果路径部分小于等于2，使用所有部分
            selectedParts = meaningfulParts;
        }
        
        // 构建方法名
        StringBuilder methodName = new StringBuilder();
        
        for (int i = 0; i < selectedParts.size(); i++) {
            String part = selectedParts.get(i);
            
            // 处理路径参数 {param}
            if (part.startsWith("{") && part.endsWith("}")) {
                // 提取参数名，移除大括号
                String paramName = part.substring(1, part.length() - 1);
                // 清理参数名，移除特殊字符
                paramName = paramName.replaceAll("[^a-zA-Z0-9_]", "");
                if (!paramName.isEmpty()) {
                    part = paramName;
                } else {
                    part = "param";
                }
            }
            
            // 移除特殊符号，只保留字母、数字和下划线
            String cleanedPart = part.replaceAll("[^a-zA-Z0-9_]", "");
            
            if (!cleanedPart.isEmpty()) {
                if (i == 0) {
                    // 第一个部分首字母小写
                    methodName.append(cleanedPart.toLowerCase());
                } else {
                    // 后续部分首字母大写（驼峰命名）
                    methodName.append(cleanedPart.substring(0, 1).toUpperCase())
                             .append(cleanedPart.substring(1).toLowerCase());
                }
            }
        }
        
        String result = methodName.toString();
        
        // 如果结果为空，使用默认名称
        if (result.trim().isEmpty()) {
            return "defaultMethod";
        }
        
        // 确保首字母小写（Java方法名规范）
        if (result.length() > 0) {
            result = result.substring(0, 1).toLowerCase() + result.substring(1);
        }
        
        // 如果以数字开头，添加前缀
        if (result.length() > 0 && Character.isDigit(result.charAt(0))) {
            result = "method" + result;
        }
        
        // 限制长度，避免过长
        if (result.length() > 50) {
            result = result.substring(0, 50);
        }
        
        return result;
    }
    
    /**
     * 生成Java变量名，排除特殊符号
     */
    public String generateVariableName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "defaultVariable";
        }
        
        // 处理常见的命名模式
        String processed = name;
        
        // 处理连字符分隔的命名（如 user-name -> userName）
        if (processed.contains("-")) {
            String[] parts = processed.split("-");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (i == 0) {
                    result.append(part.toLowerCase());
                } else {
                    result.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
                }
            }
            processed = result.toString();
        }
        
        // 处理下划线分隔的命名（如 user_id -> userId）
        if (processed.contains("_")) {
            String[] parts = processed.split("_");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (i == 0) {
                    result.append(part.toLowerCase());
                } else {
                    result.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
                }
            }
            processed = result.toString();
        }
        
        // 移除特殊符号，只保留字母、数字和下划线
        String cleaned = processed.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "");
        
        // 如果清理后为空，使用默认名称
        if (cleaned.trim().isEmpty()) {
            return "defaultVariable";
        }
        
        // 确保首字母小写（Java变量名规范）
        String result = cleaned.substring(0, 1).toLowerCase() + cleaned.substring(1);
        
        // 如果以数字开头，添加前缀
        if (Character.isDigit(result.charAt(0))) {
            result = "var" + result;
        }
        
        // 限制长度，避免过长
        if (result.length() > 50) {
            result = result.substring(0, 50);
        }
        
        return result;
    }
    
    /**
     * 生成Java包名，确保每个部分都符合Java命名规范
     */
    public String generatePackageName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "com.example";
        }
        
        // 处理连字符和下划线，转换为点分隔
        String processed = name.replaceAll("[-_]", ".");
        
        // 移除特殊符号，只保留字母、数字和点
        String cleaned = processed.replaceAll("[^a-zA-Z0-9.]", "");
        
        // 如果清理后为空，使用默认名称
        if (cleaned.trim().isEmpty()) {
            return "com.example";
        }
        
        // 按点分割包名
        String[] parts = cleaned.split("\\.");
        List<String> validParts = new ArrayList<>();
        
        for (String part : parts) {
            if (!part.isEmpty()) {
                // 确保每个部分不以数字开头
                String validPart = part;
                if (Character.isDigit(part.charAt(0))) {
                    validPart = "pkg" + part;
                }
                validParts.add(validPart.toLowerCase());
            }
        }
        
        // 如果没有有效部分，使用默认包名
        if (validParts.isEmpty()) {
            return "com.example";
        }
        
        // 重新组合包名
        String result = String.join(".", validParts);
        
        // 限制长度
        if (result.length() > 100) {
            result = result.substring(0, 100);
        }
        
        return result;
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
     * 清理Mock响应，移除markdown格式
     */
    private String cleanMockResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{}";
        }
        
        // 移除markdown代码块标记
        String cleaned = response.trim();
        
        // 移除开头的 ```json 或 ```
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        
        // 移除结尾的 ```
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        // 清理前后空白字符
        cleaned = cleaned.trim();
        
        // 验证是否为有效的JSON
        try {
            objectMapper.readTree(cleaned);
            return cleaned;
        } catch (Exception e) {
            log.warn("Invalid JSON in mock response, using default: {}", e.getMessage());
            return "{}";
        }
    }
    
    /**
     * 生成默认的Mock响应
     */
    private String generateDefaultMockResponse(ApiEndpoint endpoint) {
        String httpMethod = endpoint.getMethod().name().toLowerCase();
        
        switch (httpMethod) {
            case "post":
            case "put":
            case "patch":
                return String.format("""
                    {
                        "code": 200,
                        "message": "操作成功",
                        "data": {
                            "id": "mock-id-123",
                            "status": "success"
                        },
                        "timestamp": "%s"
                    }
                    """,
                    java.time.LocalDateTime.now()
                );
            case "get":
                return String.format("""
                    {
                        "code": 200,
                        "message": "查询成功",
                        "data": {
                            "items": [],
                            "total": 0,
                            "page": 1,
                            "size": 10
                        },
                        "timestamp": "%s"
                    }
                    """,
                    java.time.LocalDateTime.now()
                );
            case "delete":
                return String.format("""
                    {
                        "code": 200,
                        "message": "删除成功",
                        "data": null,
                        "timestamp": "%s"
                    }
                    """,
                    java.time.LocalDateTime.now()
                );
            default:
                return String.format("""
                    {
                        "code": 200,
                        "message": "请求成功",
                        "data": null,
                        "timestamp": "%s"
                    }
                    """,
                    java.time.LocalDateTime.now()
                );
        }
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