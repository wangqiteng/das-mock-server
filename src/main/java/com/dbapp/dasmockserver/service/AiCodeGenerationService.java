package com.dbapp.dasmockserver.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.dbapp.dasmockserver.config.AiConfig;
import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.ApiEndpoint.HttpMethod;
import com.dbapp.dasmockserver.model.MockService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.dbapp.dasmockserver.config.AiConfig.DEFAULT_PROMPT;

@Service
@Slf4j
public class AiCodeGenerationService {
    
    @Autowired(required = false)
    private ChatClient chatClient;
    
    @Autowired
    private AiConfigService aiConfigService;

    @Value("${hengNao.switch:false}")
    private Boolean hengNaoSwitch;

    @Value("${hengNao.key:}")
    private String appKey;

    @Value("${hengNao.secret:}")
    private String secret;

    // json解析设置为宽松模式：允许不带引号的字段名，允许单引号
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    
    // 临时AI配置，用于单次生成
    public static ThreadLocal<AiConfig.AiModelConfig> temporaryConfig = new ThreadLocal<>();
    
    /**
     * 设置临时AI配置（仅用于当前线程的本次生成）
     */
    public void setTemporaryAiConfig(String modelName, Double temperature, Integer maxTokens, Double topP) {
        AiConfig.AiModelConfig config = new AiConfig.AiModelConfig(modelName, temperature, maxTokens, topP);
        temporaryConfig.set(config);
    }
    
    /**
     * 清除临时AI配置
     */
    public void clearTemporaryAiConfig() {
        temporaryConfig.remove();
    }
    
    /**
     * 使用AI分析文档并生成API端点信息
     */
    public List<ApiEndpoint> generateApiEndpoints(String documentContent, MockService mockService) {
        AiConfig.AiModelConfig config = temporaryConfig.get();
        // 如果有恒脑配置，则使用恒脑配置
        if (("hengNao".equals(config.getModelName())) && hengNaoSwitch) {
            String prompt = buildApiAnalysisPrompt(documentContent);
            String aiResponse = callHengNaoAi(prompt);
            return parseApiEndpointsFromHengNaoAiResponse(aiResponse, mockService);
        }

        // 如果没有AI相关配置，返回空
        if (chatClient == null) {
            return List.of();
        }
        
        String prompt = buildApiAnalysisPrompt(documentContent);
        
        // 使用动态配置调用AI
        String aiResponse = callAiWithDynamicConfig(prompt);
        
        return parseApiEndpointsFromAiResponse(aiResponse, mockService);
    }


    /**
     * 调用恒脑接口
     * @param prompt
     * @return
     */
    private String callHengNaoAi(String prompt) {

        // 如果没有配置Spring Alibaba AI，使用恒脑配置

        // 生成签名
        String sign = getSign(appKey, secret);

        prompt = prompt.replaceAll("\\\\", "").replaceAll("\"","").replaceAll("\n","").replaceAll("\r","");
        String jsonBody = "{\"message\":[{\"role\":\"user\",\"content\":\"" + prompt + "\"}]}";

        // 构建HTTP请求
        // 创建HTTP客户端
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.das-ai.com/open/api/v1/chat"))
                .header("appKey", appKey)
                .header("sign", sign)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        // 发送请求并获取响应
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if(response.statusCode() == 200){
                log.info("恒脑响应结果：{}" ,response.body());
                return response.body();
            }else{
                log.error("恒脑AI调用失败，状态码:{}，响应内容: {}",response.statusCode(), response.body());
                return "";
            }

        } catch (Exception e) {
            log.error("恒脑AI调用失败: {}", e.getMessage(), e);
            return "";
        }
    }

    private List<ApiEndpoint> parseApiEndpointsFromHengNaoAiResponse(String aiResponse, MockService mockService) {
        List<ApiEndpoint> endpoints = new ArrayList<>();

        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            log.warn("恒脑AI响应为空，返回空端点列表");
            return endpoints;
        }
        String cleaned = aiResponse.trim();
        String jsonContent = "";
        try {
            // 清理AI响应，提取JSON部分
            jsonContent = parseHengNaoApiResponse(aiResponse);

            log.info("提取的JSON内容长度: {}", jsonContent.length());

            // 如果提取的内容为空或太短，尝试直接解析
            if (jsonContent.length() < 10) {
                log.warn("提取的JSON内容太短，尝试直接解析原始响应");
                jsonContent = aiResponse.trim();
            }

            // 尝试解析JSON数组
            List<JsonNode> endpointNodes = null;

            // 首先尝试解析为数组
            try {
                endpointNodes = objectMapper.readValue(jsonContent, new TypeReference<List<JsonNode>>() {});
                log.info("成功解析为JSON数组，节点数量: {}", endpointNodes.size());
            } catch (Exception e) {
                log.warn("解析JSON数组失败，尝试解析单个对象: {}", e.getMessage());

                // 如果解析数组失败，尝试解析单个对象并包装成数组
                try {
                    JsonNode singleNode = objectMapper.readTree(jsonContent);
                    endpointNodes = List.of(singleNode);
                    log.info("成功解析为单个对象，包装成数组");
                } catch (Exception e2) {
                    log.warn("解析单个对象也失败: {}", e2.getMessage());

                    return List.of();
                }
            }

            if (endpointNodes == null || endpointNodes.isEmpty()) {
                log.warn("解析到的端点节点为空");
                return endpoints;
            }

            for (int i = 0; i < endpointNodes.size(); i++) {
                JsonNode endpointNode = endpointNodes.get(i);
                try {
                    ApiEndpoint endpoint = new ApiEndpoint();
                    endpoint.setMockService(mockService);

                    // 解析基本信息
                    endpoint.setName(getStringValue(endpointNode, "name", "未命名端点_" + i));
                    endpoint.setPath(getStringValue(endpointNode, "path", "/api/unknown"));
                    endpoint.setDescription(getStringValue(endpointNode, "description", ""));

                    // 解析HTTP方法
                    String methodStr = getStringValue(endpointNode, "method", "GET");
                    endpoint.setMethod(parseHttpMethod(methodStr));

                    // 解析参数信息（统一使用requestSchema）
                    JsonNode requestSchema = endpointNode.get("requestSchema");
                    if (requestSchema != null) {
                        endpoint.setRequestSchema(requestSchema.toString());
                    } else {
                        // 如果没有requestSchema，尝试从其他字段获取
                        JsonNode parameters = endpointNode.get("parameters");
                        if (parameters != null) {
                            endpoint.setRequestSchema(parameters.toString());
                        } else {
                            endpoint.setRequestSchema("{}");
                        }
                    }

                    // 解析响应Schema
                    JsonNode responseSchema = endpointNode.get("responseSchema");
                    if (responseSchema != null) {
                        endpoint.setResponseSchema(responseSchema.toString());
                    } else {
                        endpoint.setResponseSchema("{}");
                    }

                    // 解析认证配置
                    JsonNode authConfig = endpointNode.get("authConfig");
                    if (authConfig != null) {
                        endpoint.setAuthConfig(authConfig.toString());
                    } else {
                        endpoint.setAuthConfig("{}");
                    }

                    // 设置默认值
                    endpoint.setMockResponse("{}");
                    endpoint.setResponseDelay(0);
                    endpoint.setStatusCode(200);
                    endpoint.setHeaders(Map.of()); // 使用空的Map而不是字符串

                    endpoints.add(endpoint);
                    log.info("成功解析端点: {}", endpoint.getName() + " - " + endpoint.getPath());

                } catch (Exception e) {
                    log.warn("解析第{}个端点时发生错误: {}", (i + 1), e.getMessage());
                    // 继续处理下一个端点，不中断整个流程
                }
            }

        } catch (Exception e) {
            // 处理所有异常
            log.error("解析API端点时发生错误: {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("总共解析到 {} 个端点", endpoints.size());
        return endpoints;
    }

    private String parseHengNaoApiResponse(String aiResponse) throws JsonProcessingException {
        String jsonContent = "";
        JsonNode responseNode = objectMapper.readTree(aiResponse);

        // data.message.content中提取
        if (responseNode.has("data") && responseNode.get("data").has("message")
                && (responseNode.get("data").get("message").has("content"))) {
            JsonNode outputNode = responseNode.get("data").get("message").get("content");
            String jsonText = outputNode.asText();
            if (jsonText != null && !jsonText.trim().isEmpty()) {
                log.info("从data.message.content中提取到JSON，长度: {}", jsonText.length());
                log.info("JSON内容预览: {}", jsonText.substring(0, Math.min(100, jsonText.length())));
            }
            jsonText = jsonText.replaceAll("`", "").replaceAll("\n", "")
                    .replaceAll("\\\\\"", "'").replaceAll("\\\\","");
            jsonContent = jsonText;
        }
        return jsonContent;
    }


    /**
     * 为单个端点生成Mock响应
     */
    public String generateMockResponse(ApiEndpoint endpoint) throws JsonProcessingException {
        // 如果有恒脑配置，则使用恒脑配置
        AiConfig.AiModelConfig config = temporaryConfig.get();
        if (("hengNao".equals(config.getModelName())) && hengNaoSwitch) {
            String prompt = buildMockResponsePrompt(endpoint);

            // 使用动态配置调用AI
            String response = callHengNaoAi(prompt);

            // 清理响应，移除markdown格式
            return parseHengNaoApiResponse(response);
        }

        // 如果没有AI相关配置，返回空
        if (chatClient == null) {
            // 如果没有配置AI，返回默认响应
            return generateDefaultMockResponse(endpoint);
        }

        
        String prompt = buildMockResponsePrompt(endpoint);
        
        // 使用动态配置调用AI
        String response = callAiWithDynamicConfig(prompt);
        
        // 清理响应，移除markdown格式
        return cleanMockResponse(response);
    }
    
    /**
     * 构建API分析提示词
     */
    private String buildApiAnalysisPrompt(String documentContent) {
        return String.format("""
            请分析以下API文档内容，提取所有API端点信息。无论存在几个端点，请均以JSON数组格式返回结果，多个端点通过逗号分隔。
            JSON格式如下：
            [{
                "name": "端点名称",
                "path": "/api/endpoint",
                "method": "GET|POST|PUT|DELETE|PATCH",
                "description": "端点描述",
                "requestSchema": {
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
                        // 根据实际API文档分析请求体结构
                        // 如果是对象类型，使用以下格式：
                        "type": "object",
                        "properties": {
                            "fieldName": {
                                "type": "string|integer|boolean|array|object",
                                "required": true|false,
                                "description": "字段描述",
                                "example": "示例值"
                            }
                        }
                        // 如果是数组类型，使用以下格式：
                        // "type": "array",
                        // "items": {
                        //     "type": "object",
                        //     "properties": {
                        //         "fieldName": {
                        //             "type": "string|integer|boolean|array|object",
                        //             "required": true|false,
                        //             "description": "字段描述"
                        //         }
                        //     }
                        // }
                        // 注意：请根据实际API文档内容分析，不要默认使用数组格式
                    }
                },
                "responseSchema": {
                    // 响应参数JSON Schema
                },
                "authConfig": {
                    "type": "API_KEY|BEARER_TOKEN|BASIC_AUTH|OAUTH2|NONE",
                    "name": "认证名称",
                    "description": "认证描述",
                    "apiKey": {
                        "keyName": "X-API-Key",
                        "location": "header|query",
                        "description": "API Key描述"
                    },
                    "bearerToken": {
                        "headerName": "Authorization",
                        "description": "Bearer Token描述"
                    },
                    "basicAuth": {
                        "username": "用户名",
                        "password": "密码",
                        "description": "Basic Auth描述"
                    },
                    "oauth2": {
                        "authorizationUrl": "授权URL",
                        "tokenUrl": "令牌URL",
                        "scopes": ["scope1", "scope2"],
                        "flow": "authorizationCode|clientCredentials|password|implicit",
                        "description": "OAuth2描述"
                    }
                }
            }]
            
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
            14. requestBody支持两种格式：
                - array格式：当请求体是数组时使用，包含type:"array"和items结构
                - object格式：当请求体是单个对象时使用，包含type:"object"和properties结构
            15. 根据文档内容判断使用哪种格式：
                - 如果文档明确提到"数组"、"列表"、"批量"等关键词，使用array格式
                - 如果文档提到单个对象、实体、记录或没有明确说明，使用object格式
                - 重要：不要默认使用array格式，大多数API的请求体都是object格式
                - 只有在文档明确说明是数组或批量操作时才使用array格式
            16. 请求体字段分析：
                - 仔细分析文档中的字段描述，理解每个字段的业务含义
                - 根据字段名称推断数据类型（如：name->string, age->integer, isActive->boolean）
                - 为每个字段提供合理的示例值
                - 确保必填字段标记为required: true
            17. 认证信息分析：
                - 如果文档中提到"API Key"、"X-API-Key"、"apikey"等，使用API_KEY类型
                - 如果文档中提到"Bearer Token"、"Authorization"、"JWT"等，使用BEARER_TOKEN类型
                - 如果文档中提到"Basic Auth"、"用户名密码"、"HTTP Basic"等，使用BASIC_AUTH类型
                - 如果文档中提到"OAuth"、"OAuth2"、"授权码"等，使用OAUTH2类型
                - 如果文档中没有明确的认证要求，使用NONE类型
                - 根据文档内容填写相应的认证配置参数
            
            请确保：
            1. 准确识别HTTP方法和路径
            2. 正确识别参数类型（pathVariables、queryParameters、requestBody）
            3. 从表格数据中正确提取所有字段信息
            4. 生成合理的参数结构，每个参数都要有type和required属性
            5. 只返回JSON格式，不要其他解释文字
            6. 确保参数信息包含从表格中提取的所有字段
            7. 字段名使用驼峰命名法
            8. 根据HTTP方法和文档内容合理分配参数类型
            9. 根据文档内容选择合适的requestBody格式（array或object）
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
            10. 重要：根据请求Schema类型正确生成参数类型：
                - 如果requestBody的type是"array"，控制器方法参数应该使用List<Request>类型
                - 如果requestBody的type是"object"，控制器方法参数应该使用单个Request类型
                - 参数名应该与类型匹配：List类型用"requests"，单个对象用"request"
            
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
     * 为API端点生成测试请求体
     */
    public String generateTestRequestBody(ApiEndpoint endpoint) throws JsonProcessingException {
        // 如果有恒脑配置，则使用恒脑配置
        AiConfig.AiModelConfig config = temporaryConfig.get();
        if (("hengNao".equals(config.getModelName())) && hengNaoSwitch) {
            String prompt = buildTestRequestBodyPrompt(endpoint);
            String response = callHengNaoAi(prompt);
            return parseHengNaoApiResponse(response);
        }

        // 如果没有AI相关配置，返回空
        if (chatClient == null) {
            return generateDefaultTestRequestBody(endpoint);
        }

        String prompt = buildTestRequestBodyPrompt(endpoint);
        
        // 使用动态配置调用AI
        String response = callAiWithDynamicConfig(prompt);

        // 清理响应，移除markdown格式
        return cleanMockResponse(response);
    }

    /**
     * 构建测试请求体生成提示词
     */
    private String buildTestRequestBodyPrompt(ApiEndpoint endpoint) {
        return String.format("""
            请为以下API端点生成一个真实的测试请求体数据：
            
            端点信息：
            - 名称: %s
            - 路径: %s
            - 方法: %s
            - 描述: %s
            - 请求Schema: %s
            
            重要要求：
            1. 仔细分析请求Schema，理解每个字段的含义和类型
            2. 根据字段名称和描述，生成符合实际业务场景的测试数据
            3. 不要使用固定的模板格式，要根据具体的API需求生成
            4. 如果是用户相关API，生成真实的用户信息（姓名、邮箱等）
            5. 如果是产品相关API，生成真实的产品信息（名称、价格、描述等）
            6. 如果是订单相关API，生成真实的订单信息（用户ID、产品ID、数量等）
            7. 如果是查询API，生成合理的查询条件
            8. 如果是更新API，生成要更新的具体字段值
            9. 确保数据类型完全匹配Schema要求
            10. 只返回JSON格式的请求体，不要任何其他文字
            11. 如果Schema是数组类型，生成1-3个元素的数组
            12. 如果Schema是对象类型，生成完整的对象结构
            13. 字段值要真实可信，避免使用"示例"、"测试"等占位符
            
            请根据以上要求生成测试数据：
            """,
            endpoint.getName(),
            endpoint.getPath(),
            endpoint.getMethod().name(),
            endpoint.getDescription() != null ? endpoint.getDescription() : "",
            endpoint.getRequestSchema() != null ? endpoint.getRequestSchema() : "{}"
        );
    }

    /**
     * 生成默认的测试请求体
     */
    private String generateDefaultTestRequestBody(ApiEndpoint endpoint) {
        // 根据端点名称、路径和描述生成更智能的测试数据
        String endpointName = endpoint.getName().toLowerCase();
        String path = endpoint.getPath().toLowerCase();
        String description = endpoint.getDescription() != null ? endpoint.getDescription().toLowerCase() : "";
        String method = endpoint.getMethod().name();
        
        // 分析请求Schema，尝试理解字段结构
        String requestSchema = endpoint.getRequestSchema();
        
        // 用户相关API
        if (endpointName.contains("user") || path.contains("user") || description.contains("用户")) {
            if (method.equals("POST")) {
                return """
                    {
                        "username": "john_doe",
                        "email": "john.doe@example.com",
                        "password": "SecurePass123!",
                        "firstName": "John",
                        "lastName": "Doe",
                        "phone": "+86-138-0013-8000",
                        "birthDate": "1990-05-15",
                        "gender": "male"
                    }
                    """;
            } else if (method.equals("PUT") || method.equals("PATCH")) {
                return """
                    {
                        "firstName": "John",
                        "lastName": "Smith",
                        "phone": "+86-138-0013-8001",
                        "email": "john.smith@example.com"
                    }
                    """;
            }
        }
        
        // 产品相关API
        if (endpointName.contains("product") || path.contains("product") || description.contains("产品")) {
            if (method.equals("POST")) {
                return """
                    {
                        "name": "iPhone 15 Pro",
                        "description": "最新款苹果手机，配备A17 Pro芯片",
                        "price": 7999.00,
                        "currency": "CNY",
                        "category": "electronics",
                        "brand": "Apple",
                        "stock": 50,
                        "sku": "IPH15PRO-256-BLK",
                        "weight": 187.0,
                        "dimensions": {
                            "length": 146.6,
                            "width": 70.6,
                            "height": 8.25
                        }
                    }
                    """;
            } else if (method.equals("PUT") || method.equals("PATCH")) {
                return """
                    {
                        "price": 7499.00,
                        "stock": 45,
                        "description": "iPhone 15 Pro - 限时优惠"
                    }
                    """;
            }
        }
        
        // 订单相关API
        if (endpointName.contains("order") || path.contains("order") || description.contains("订单")) {
            if (method.equals("POST")) {
                return """
                    {
                        "customerId": 12345,
                        "items": [
                            {
                                "productId": 1001,
                                "quantity": 2,
                                "unitPrice": 199.99
                            },
                            {
                                "productId": 1002,
                                "quantity": 1,
                                "unitPrice": 299.99
                            }
                        ],
                        "shippingAddress": {
                            "street": "北京市朝阳区建国路88号",
                            "city": "北京",
                            "state": "北京",
                            "postalCode": "100025",
                            "country": "中国"
                        },
                        "paymentMethod": "credit_card",
                        "notes": "请在工作日配送"
                    }
                    """;
            }
        }
        
        // 认证相关API
        if (endpointName.contains("login") || endpointName.contains("auth") || path.contains("login") || path.contains("auth")) {
            return """
                {
                    "username": "john_doe",
                    "password": "SecurePass123!",
                    "rememberMe": true
                }
                """;
        }
        
        // 搜索/查询相关API
        if (method.equals("POST") && (endpointName.contains("search") || endpointName.contains("query") || path.contains("search"))) {
            return """
                {
                    "keyword": "智能手机",
                    "category": "electronics",
                    "minPrice": 1000,
                    "maxPrice": 5000,
                    "brand": ["Apple", "Samsung", "Huawei"],
                    "sortBy": "price",
                    "sortOrder": "asc",
                    "page": 1,
                    "pageSize": 20
                }
                """;
        }
        
        // 文件上传相关API
        if (endpointName.contains("upload") || path.contains("upload") || description.contains("上传")) {
            return """
                {
                    "fileName": "document.pdf",
                    "fileType": "application/pdf",
                    "fileSize": 1024000,
                    "description": "重要文档",
                    "category": "documents"
                }
                """;
        }
        
        // 配置/设置相关API
        if (endpointName.contains("config") || endpointName.contains("setting") || path.contains("config")) {
            return """
                {
                    "theme": "dark",
                    "language": "zh-CN",
                    "notifications": {
                        "email": true,
                        "sms": false,
                        "push": true
                    },
                    "privacy": {
                        "profileVisible": true,
                        "dataSharing": false
                    }
                }
                """;
        }
        
        // 通用创建操作
        if (method.equals("POST")) {
            return """
                {
                    "name": "新项目",
                    "description": "这是一个新创建的项目",
                    "status": "active",
                    "priority": "medium",
                    "tags": ["重要", "紧急"],
                    "assignee": "john_doe",
                    "dueDate": "2024-12-31"
                }
                """;
        }
        
        // 通用更新操作
        if (method.equals("PUT") || method.equals("PATCH")) {
            return """
                {
                    "name": "更新后的项目",
                    "status": "completed",
                    "priority": "high",
                    "notes": "项目已完成更新"
                }
                """;
        }
        
        // 默认情况
        return """
            {
                "id": 1,
                "name": "测试数据",
                "description": "这是一个测试请求体",
                "status": "active",
                "createdAt": "2024-01-01T10:00:00Z"
            }
            """;
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
        
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            log.warn("AI响应为空，返回空端点列表");
            return endpoints;
        }
        
        try {
            // 清理AI响应，提取JSON部分
            String jsonContent = extractJsonFromResponse(aiResponse);
            log.info("提取的JSON内容长度: {}", jsonContent.length());
            
            // 如果提取的内容为空或太短，尝试直接解析
            if (jsonContent.length() < 10) {
                log.warn("提取的JSON内容太短，尝试直接解析原始响应");
                jsonContent = aiResponse.trim();
            }
            
            // 尝试解析JSON数组
            List<JsonNode> endpointNodes = null;
            
            // 首先尝试解析为数组
            try {
                endpointNodes = objectMapper.readValue(jsonContent, new TypeReference<List<JsonNode>>() {});
                log.info("成功解析为JSON数组，节点数量: {}", endpointNodes.size());
            } catch (Exception e) {
                log.warn("解析JSON数组失败，尝试解析单个对象: {}", e.getMessage());
                
                // 如果解析数组失败，尝试解析单个对象并包装成数组
                try {
                    JsonNode singleNode = objectMapper.readTree(jsonContent);
                    endpointNodes = List.of(singleNode);
                    log.info("成功解析为单个对象，包装成数组");
                } catch (Exception e2) {
                    log.warn("解析单个对象也失败: {}", e2.getMessage());
                    
                    // 如果所有解析都失败，尝试手动解析
                    log.warn("尝试手动解析JSON内容");
                    endpointNodes = manualParseJson(jsonContent);
                }
            }
            
            if (endpointNodes == null || endpointNodes.isEmpty()) {
                log.warn("解析到的端点节点为空");
                return endpoints;
            }
            
            for (int i = 0; i < endpointNodes.size(); i++) {
                JsonNode endpointNode = endpointNodes.get(i);
                try {
                    ApiEndpoint endpoint = new ApiEndpoint();
                    endpoint.setMockService(mockService);
                    
                    // 解析基本信息
                    endpoint.setName(getStringValue(endpointNode, "name", "未命名端点_" + i));
                    endpoint.setPath(getStringValue(endpointNode, "path", "/api/unknown"));
                    endpoint.setDescription(getStringValue(endpointNode, "description", ""));
                    
                    // 解析HTTP方法
                    String methodStr = getStringValue(endpointNode, "method", "GET");
                    endpoint.setMethod(parseHttpMethod(methodStr));
                    
                    // 解析参数信息（统一使用requestSchema）
                    JsonNode requestSchema = endpointNode.get("requestSchema");
                    if (requestSchema != null) {
                        endpoint.setRequestSchema(requestSchema.toString());
                    } else {
                        // 如果没有requestSchema，尝试从其他字段获取
                        JsonNode parameters = endpointNode.get("parameters");
                        if (parameters != null) {
                            endpoint.setRequestSchema(parameters.toString());
                        } else {
                            endpoint.setRequestSchema("{}");
                        }
                    }
                    
                    // 解析响应Schema
                    JsonNode responseSchema = endpointNode.get("responseSchema");
                    if (responseSchema != null) {
                        endpoint.setResponseSchema(responseSchema.toString());
                    } else {
                        endpoint.setResponseSchema("{}");
                    }

                    // 解析认证配置
                    JsonNode authConfig = endpointNode.get("authConfig");
                    if (authConfig != null) {
                        endpoint.setAuthConfig(authConfig.toString());
                    } else {
                        endpoint.setAuthConfig("{}");
                    }
                    
                    // 设置默认值
                    endpoint.setMockResponse("{}");
                    endpoint.setResponseDelay(0);
                    endpoint.setStatusCode(200);
                    endpoint.setHeaders(Map.of()); // 使用空的Map而不是字符串
                    
                    endpoints.add(endpoint);
                    log.info("成功解析端点: {}", endpoint.getName() + " - " + endpoint.getPath());
                    
                } catch (Exception e) {
                    log.warn("解析第{}个端点时发生错误: {}", (i + 1), e.getMessage());
                    // 继续处理下一个端点，不中断整个流程
                }
            }
            
        } catch (Exception e) {
            // 处理所有异常
            log.error("解析API端点时发生错误: {}", e.getMessage());
            e.printStackTrace();
        }
        
        log.info("总共解析到 {} 个端点", endpoints.size());
        return endpoints;
    }
    
    /**
     * 手动解析JSON内容
     */
    private List<JsonNode> manualParseJson(String jsonContent) {
        List<JsonNode> nodes = new ArrayList<>();
        
        try {
            // 尝试从JSON内容中提取基本的端点信息
            if (jsonContent.contains("\"name\"") && jsonContent.contains("\"path\"")) {
                log.info("检测到可能的端点信息，尝试手动解析");
                
                // 创建一个基本的端点节点
                ObjectNode endpointNode = objectMapper.createObjectNode();
                endpointNode.put("name", "手动解析端点");
                endpointNode.put("path", "/api/manual");
                endpointNode.put("method", "GET");
                endpointNode.put("description", "手动解析的端点");
                
                // 创建空的requestSchema和responseSchema
                ObjectNode requestSchema = objectMapper.createObjectNode();
                requestSchema.putArray("pathVariables");
                requestSchema.putArray("queryParameters");
                requestSchema.set("requestBody", objectMapper.createObjectNode());
                endpointNode.set("requestSchema", requestSchema);
                
                ObjectNode responseSchema = objectMapper.createObjectNode();
                endpointNode.set("responseSchema", responseSchema);
                
                nodes.add(endpointNode);
                log.info("手动解析成功，创建了1个基本端点");
            }
        } catch (Exception e) {
            log.error("手动解析失败: {}", e.getMessage());
        }
        
        return nodes;
    }
    
    /**
     * 从AI响应中提取JSON内容
     */
    private String extractJsonFromResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return "[]";
        }
        
        String cleaned = aiResponse.trim();
        log.info("原始AI响应长度: {}", cleaned.length());
        
        // 首先尝试从Spring AI响应格式中提取JSON
        // Spring AI响应格式通常是：{"result":{"output":{"text":"[JSON内容]"}}}
        try {
            JsonNode responseNode = objectMapper.readTree(cleaned);
            
            // 尝试从result.output.text中提取
            if (responseNode.has("result") && responseNode.get("result").has("output")) {
                JsonNode outputNode = responseNode.get("result").get("output");
                if (outputNode.has("text")) {
                    String jsonText = outputNode.get("text").asText();
                    if (jsonText != null && !jsonText.trim().isEmpty()) {
                        log.info("从result.output.text中提取到JSON，长度: {}", jsonText.length());
                        log.info("JSON内容预览: {}", jsonText.substring(0, Math.min(100, jsonText.length())));
                        return processJsonContent(jsonText);
                    }
                }
            }
            
            // 尝试从results[0].output.text中提取
            if (responseNode.has("results") && responseNode.get("results").isArray() && responseNode.get("results").size() > 0) {
                JsonNode firstResult = responseNode.get("results").get(0);
                if (firstResult.has("output") && firstResult.get("output").has("text")) {
                    String jsonText = firstResult.get("output").get("text").asText();
                    if (jsonText != null && !jsonText.trim().isEmpty()) {
                        log.info("从results[0].output.text中提取到JSON，长度: {}", jsonText.length());
                        log.info("JSON内容预览: {}", jsonText.substring(0, Math.min(100, jsonText.length())));
                        return processJsonContent(jsonText);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("解析Spring AI响应格式失败，尝试直接处理: {}", e.getMessage());
            log.error("错误类型: {}", e.getClass().getSimpleName());
        }
        
        // 如果不是Spring AI响应格式，按原来的方式处理
        return processJsonContent(cleaned);
    }
    
    /**
     * 处理JSON内容
     */
    private String processJsonContent(String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return "[]";
        }
        
        log.info("开始处理JSON内容，原始长度: {}", jsonContent.length());
        
        // 移除可能的markdown代码块标记
        String cleaned = jsonContent.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
            log.info("移除了```json标记");
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
            log.info("移除了```标记");
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
            log.info("移除了结尾的```标记");
        }
        
        cleaned = cleaned.trim();
        log.info("清理后长度: {}", cleaned.length());
        
        // 尝试修复JSON格式问题
        cleaned = fixJsonFormat(cleaned);
        log.info("格式修复后长度: {}", cleaned.length());
        
        // 验证JSON格式
        if (!isValidJson(cleaned)) {
            log.error("JSON格式验证失败，尝试进一步修复: {}", cleaned);
            log.error("失败内容预览: {}", cleaned.substring(0, Math.min(200, cleaned.length())));
            
            // 尝试更温和的修复
            String gentlyFixed = gentleJsonFix(cleaned);
            if (isValidJson(gentlyFixed)) {
                log.info("温和修复成功，长度: {}", gentlyFixed.length());
                return gentlyFixed;
            }
            
            // 如果温和修复失败，尝试激进修复
            cleaned = aggressiveJsonFix(cleaned);
            log.info("激进修复后长度: {}", cleaned.length());
        } else {
            log.info("JSON格式验证通过");
        }
        
        return cleaned;
    }
    
    /**
     * 温和的JSON修复
     */
    private String gentleJsonFix(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "[]";
        }
        
        String fixed = json.trim();
        log.info("开始温和修复，原始长度: {}", fixed.length());
        
        // 只进行最基本的修复，不改变结构
        fixed = fixed
            .replaceAll(",\\s*}", "}")  // 移除对象末尾多余的逗号
            .replaceAll(",\\s*]", "]")  // 移除数组末尾多余的逗号
            .replaceAll("\\s+", " ");   // 规范化空白字符
        
        // 修复中文标点符号
        fixed = fixed
            .replace("，", ",")  // 中文逗号替换为英文逗号
            .replace("：", ":")  // 中文冒号替换为英文冒号
            .replace("\"", "\"")  // 中文引号替换为英文引号
            .replace("\"", "\"")  // 中文引号替换为英文引号
            .replace("'", "'")   // 中文单引号替换为英文单引号
            .replace("'", "'");  // 中文单引号替换为英文单引号
        
        log.info("温和修复后长度: {}", fixed.length());
        return fixed;
    }
    
    /**
     * 修复JSON格式问题
     */
    private String fixJsonFormat(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "[]";
        }
        
        String fixed = json.trim();
        
        // 移除多余的空白字符
        fixed = fixed.replaceAll("\\s+", " ");
        
        // 修复常见的格式问题
        fixed = fixed
            .replaceAll(",\\s*}", "}")  // 移除对象末尾多余的逗号
            .replaceAll(",\\s*]", "]")  // 移除数组末尾多余的逗号
            .replaceAll("\\{\\s*\\}", "{}")  // 规范化空对象
            .replaceAll("\\[\\s*\\]", "[]"); // 规范化空数组
        
        // 修复中文标点符号
        fixed = fixed
            .replace("，", ",")  // 中文逗号替换为英文逗号
            .replace("：", ":")  // 中文冒号替换为英文冒号
            .replace("\"", "\"")  // 中文引号替换为英文引号
            .replace("\"", "\"")  // 中文引号替换为英文引号
            .replace("'", "'")   // 中文单引号替换为英文单引号
            .replace("'", "'");  // 中文单引号替换为英文单引号
        
        return fixed;
    }
    
    /**
     * 检查JSON是否有效
     */
    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 激进的JSON修复
     */
    private String aggressiveJsonFix(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "[]";
        }
        
        String fixed = json.trim();
        log.info("开始激进修复，原始长度: {}", fixed.length());
        
        // 如果内容看起来像JSON数组，但缺少括号，尝试补全
        if (fixed.startsWith("[") && !fixed.endsWith("]")) {
            log.info("检测到不完整的数组，尝试补全括号");
            // 计算括号平衡
            int openBrackets = 0;
            int openBraces = 0;
            boolean inString = false;
            boolean escaped = false;
            
            for (int i = 0; i < fixed.length(); i++) {
                char c = fixed.charAt(i);
                
                if (escaped) {
                    escaped = false;
                    continue;
                }
                
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                
                if (c == '"' && !escaped) {
                    inString = !inString;
                    continue;
                }
                
                if (!inString) {
                    if (c == '[') openBrackets++;
                    else if (c == ']') openBrackets--;
                    else if (c == '{') openBraces++;
                    else if (c == '}') openBraces--;
                }
            }
            
            log.info("括号统计: [={}, {}={}", openBrackets, openBraces);
            
            // 补全缺失的括号
            while (openBrackets > 0) {
                fixed += "]";
                openBrackets--;
            }
            while (openBraces > 0) {
                fixed += "}";
                openBraces--;
            }
            
            log.info("补全括号后长度: {}", fixed.length());
        }
        
        // 如果内容看起来像JSON对象，但期望数组，尝试包装成数组
        if (fixed.startsWith("{") && fixed.endsWith("}")) {
            log.info("检测到单个对象，包装成数组");
            fixed = "[" + fixed + "]";
        }
        
        // 如果内容不是以[或{开头，尝试包装成数组
        if (!fixed.startsWith("[") && !fixed.startsWith("{")) {
            log.info("检测到非标准JSON格式，尝试查找JSON结构");
            // 尝试找到第一个{或[的位置
            int firstBrace = fixed.indexOf('{');
            int firstBracket = fixed.indexOf('[');
            
            if (firstBrace >= 0 && (firstBracket < 0 || firstBrace < firstBracket)) {
                // 找到对象，包装成数组
                log.info("找到对象结构，包装成数组");
                fixed = "[" + fixed.substring(firstBrace) + "]";
            } else if (firstBracket >= 0) {
                // 找到数组，包装
                log.info("找到数组结构，包装");
                fixed = "[" + fixed.substring(firstBracket) + "]";
            } else {
                // 没有找到有效的JSON结构，返回空数组
                log.info("未找到有效JSON结构，返回空数组");
                return "[]";
            }
        }
        
        // 尝试修复转义字符问题
        fixed = fixEscapeCharacters(fixed);
        
        // 尝试修复引号问题
        fixed = fixQuoteIssues(fixed);
        
        // 最终验证
        if (!isValidJson(fixed)) {
            log.error("激进修复后仍然无效，返回空数组");
            log.error("最终内容预览: {}", fixed.substring(0, Math.min(200, fixed.length())));
            
            // 如果激进修复失败，尝试返回原始内容（如果看起来像JSON）
            if (json.trim().startsWith("[") || json.trim().startsWith("{")) {
                log.info("激进修复失败，但原始内容看起来像JSON，尝试返回原始内容");
                return json.trim();
            }
            
            return "[]";
        }
        
        log.info("激进修复成功，最终长度: {}", fixed.length());
        return fixed;
    }
    
    /**
     * 修复转义字符问题
     */
    private String fixEscapeCharacters(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        
        String fixed = json;
        
        // 修复常见的转义字符问题
        fixed = fixed
            .replace("\\\"", "\"")  // 修复过度转义的引号
            .replace("\\\\", "\\")  // 修复过度转义的反斜杠
            .replace("\\n", "\n")   // 修复换行符
            .replace("\\t", "\t")   // 修复制表符
            .replace("\\r", "\r");  // 修复回车符
        
        return fixed;
    }
    
    /**
     * 修复引号问题
     */
    private String fixQuoteIssues(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        
        String fixed = json;
        
        // 修复不匹配的引号
        int quoteCount = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < fixed.length(); i++) {
            char c = fixed.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                quoteCount++;
                inString = !inString;
            }
        }
        
        // 如果引号数量是奇数，尝试修复
        if (quoteCount % 2 != 0) {
            log.info("检测到不匹配的引号，尝试修复");
            // 在末尾添加缺失的引号
            if (!fixed.endsWith("\"")) {
                fixed += "\"";
            }
        }
        
        return fixed;
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
            log.warn("无法解析HTTP方法: {}, 使用默认值GET", methodStr);
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
     * 使用动态配置调用AI
     */
    private String callAiWithDynamicConfig(String prompt) {
        try {
            // 优先使用临时配置，然后是动态配置，最后是静态配置
            AiConfig.AiModelConfig config = temporaryConfig.get();
            String configSource = "临时配置";
            
            if (config == null) {
                config = aiConfigService.getCurrentConfig();
                configSource = aiConfigService.getConfigSource();
            }
            
            // 记录配置信息
            log.info("使用AI配置: model={}, temperature={}, maxTokens={}, topP={}, source={}", 
                config.getModelName(), config.getTemperature(), config.getMaxTokens(), 
                config.getTopP(), configSource);
            
            // 调用AI并返回结果
            DashScopeChatOptions customOptions = DashScopeChatOptions.builder()
                    .withTopP(config.getTopP())
                    .withTemperature(config.getTemperature())
                    .withModel(config.getModelName())
                    .withMaxToken(config.getMaxTokens())
                    .build();
            String result = chatClient.prompt(new Prompt(DEFAULT_PROMPT,customOptions)).user(prompt).call().content();
            
            return result;
            
        } catch (Exception e) {
            log.error("AI调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI调用失败: " + e.getMessage(), e);
        }
    }

    public static String getSign(String key, String secret) {
        try {
            long timestamp = System.currentTimeMillis();
            String data = String.format("%d\n%s\n%s", timestamp, secret, key);
            Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSHA256.init(secretKeySpec);
            byte[] sign = hmacSHA256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return timestamp + java.util.Base64.getEncoder().encodeToString(sign);
        }catch (Exception e) {
            log.error("生成恒脑签名异常: {}", e.getMessage(), e);
            return "";
        }
    }
} 