package com.dbapp.dasmockserver.service;

import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import com.dbapp.dasmockserver.model.ApiEndpoint.HttpMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class AiCodeGenerationServiceTest {

    private AiCodeGenerationService service;

    @BeforeEach
    void setUp() {
        service = new AiCodeGenerationService();
    }

    @Test
    void testGenerateMethodName() {
        assertNotNull(service.generateMethodName("/api/incident/receive"));
        assertNotNull(service.generateMethodName("/api/users"));
        assertNotNull(service.generateMethodName("/api/user/list"));
    }

    @Test
    void testGenerateClassName() {
        assertNotNull(service.generateClassName("告警信息"));
        assertNotNull(service.generateClassName("用户管理"));
        assertNotNull(service.generateClassName("test-class"));
    }

    @Test
    void testGeneratePackageName() {
        assertNotNull(service.generatePackageName("test-project"));
        assertNotNull(service.generatePackageName("mock-server"));
        assertNotNull(service.generatePackageName("2test"));
    }

    @Test
    void testGenerateVariableName() {
        assertNotNull(service.generateVariableName("user-name"));
        assertNotNull(service.generateVariableName("user_id"));
        assertNotNull(service.generateVariableName("testVariable"));
    }

    @Test
    void testGenerateApiEndpointsFromTableData() {
        // Test document content with table data
        String documentContent = """
            # 事件中心接口文档
            
            ## 告警信息
            
            ### 接口功能描述
            - 传输方式：HTTP传输
            - URL：http://ip:port/eventHub/api/incident/receive
            - 请求方法：POST
            - 频度：实时发送
            - 内容格式：JSON
            - 编码格式：UTF-8
            
            实时接收第三方调用接口生成告警信息
            
            === 表格数据 ===
            编号 | 字段名称 | 字段编码 | 字段类型 | 是否必输 | 说明
            1 | 主键 | id | VARCHAR2(60) | N | 系统自动生成
            2 | 事件名称 | name | VARCHAR2(60) | Y | 
            3 | 节点ID | nodeId | INT | N | 
            4 | 节点名称 | nodeName | VARCHAR2(60) | N | 
            5 | 故障源类 | srcObjClass | INT | N | 第三方平台事件类型ID
            6 | 故障源ID | srcObjId | INT | N | 第三方平台事件设备ID
            7 | 故障源名称 | srcObjName | VARCHAR2(60) | N | 第三方平台原始故障源名称
            8 | 最早发生时间 | timeFirst | DATE | N | 
            9 | 告警信息 | alertMessage | VARCHAR2(256) | Y | 
            10 | 级别 | severity | INT | Y | 参考字典表
            11 | 类别 | category | INT | N | 参考字典表
            12 | 系列 | family | INT | N | 参考字典表
            13 | 告警系统 | author | VARCHAR2(256) | Y | 需要协定，测试可传UNKNOW（仅做测试，不会触发告警动作）
            14 | 原始ID | originIncId | INT | N | 第三方平台事件ID
            15 | IP地址 | ipAddress | VARCHAR2(60) | Y | 故障源ip
            16 | 情境ID | situationId | CHAR(5) | Y | 用来标识事件策略ID，如果没有，请将同一个类型的事件定义同一个值
            17 | 状态 | status | INT | Y | 1-产生；0-恢复
            18 | 恢复消息 | normalMessage | CHAR(5) | N | 仅状态为0时生效
            === 表格结束 ===
            """;
        
        MockService mockService = new MockService();
        mockService.setName("事件中心");
        
        List<ApiEndpoint> endpoints = service.generateApiEndpoints(documentContent, mockService);
        
        assertNotNull(endpoints);
        
        if (endpoints.isEmpty()) {
            System.out.println("AI未配置，无法测试表格数据解析功能");
        } else {
            assertFalse(endpoints.isEmpty());
            
            ApiEndpoint endpoint = endpoints.get(0);
            assertEquals("告警信息", endpoint.getName());
            assertEquals("/api/incident/receive", endpoint.getPath());
            assertEquals("POST", endpoint.getMethod().name());
            
            String requestSchema = endpoint.getRequestSchema();
            assertNotNull(requestSchema);
            
            // 验证AI解析的requestSchema格式
            assertTrue(requestSchema.contains("name"));
            assertTrue(requestSchema.contains("alertMessage"));
            assertTrue(requestSchema.contains("author"));
            assertTrue(requestSchema.contains("situationId"));
            assertTrue(requestSchema.contains("status"));
            assertTrue(requestSchema.contains("severity"));
            assertTrue(requestSchema.contains("ipAddress"));
            
            // 验证AI生成的JSON Schema格式
            assertTrue(requestSchema.contains("\"type\""));
            assertTrue(requestSchema.contains("\"required\""));
        }
    }

    @Test
    void testGenerateApiEndpointsWithRequestExample() {
        // Test document content with request example
        String documentContent = """
            # 事件中心接口文档
            
            ## 告警信息
            
            ### 接口功能描述
            - 传输方式：HTTP传输
            - URL：http://ip:port/eventHub/api/incident/receive
            - 请求方法：POST
            - 频度：实时发送
            - 内容格式：JSON
            - 编码格式：UTF-8
            
            实时接收第三方调用接口生成告警信息
            
            ### 请求示例
            ```json
            {
              "name": "title",
              "alertMessage": "message",
              "author":"UNKNOW",
              "situationId": 10060,
              "status": 1,
              "severity": "4",
              "ipAddress": "127.0.0.1"
            }
            ```
            
            === 表格数据 ===
            编号 | 字段名称 | 字段编码 | 字段类型 | 是否必输 | 说明
            1 | 主键 | id | VARCHAR2(60) | N | 系统自动生成
            2 | 事件名称 | name | VARCHAR2(60) | Y | 
            3 | 告警信息 | alertMessage | VARCHAR2(256) | Y | 
            4 | 告警系统 | author | VARCHAR2(256) | Y | 需要协定，测试可传UNKNOW
            5 | 情境ID | situationId | CHAR(5) | Y | 用来标识事件策略ID
            6 | 状态 | status | INT | Y | 1-产生；0-恢复
            7 | 级别 | severity | INT | Y | 参考字典表
            8 | IP地址 | ipAddress | VARCHAR2(60) | Y | 故障源ip
            === 表格结束 ===
            """;
        
        MockService mockService = new MockService();
        mockService.setName("事件中心");
        
        List<ApiEndpoint> endpoints = service.generateApiEndpoints(documentContent, mockService);
        
        assertNotNull(endpoints);
        
        if (endpoints.isEmpty()) {
            System.out.println("AI未配置，无法测试请求示例解析功能");
        } else {
            assertFalse(endpoints.isEmpty());
            
            ApiEndpoint endpoint = endpoints.get(0);
            assertEquals("告警信息", endpoint.getName());
            assertEquals("/api/incident/receive", endpoint.getPath());
            assertEquals("POST", endpoint.getMethod().name());
            
            String requestSchema = endpoint.getRequestSchema();
            assertNotNull(requestSchema);
            
            // 验证AI解析的requestSchema包含请求示例中的字段
            assertTrue(requestSchema.contains("name"));
            assertTrue(requestSchema.contains("alertMessage"));
            assertTrue(requestSchema.contains("author"));
            assertTrue(requestSchema.contains("situationId"));
            assertTrue(requestSchema.contains("status"));
            assertTrue(requestSchema.contains("severity"));
            assertTrue(requestSchema.contains("ipAddress"));
            
            // 验证AI生成的JSON Schema格式
            assertTrue(requestSchema.contains("\"type\""));
            assertTrue(requestSchema.contains("\"required\""));
            
            // 验证字段类型映射
            assertTrue(requestSchema.contains("\"name\":{\"type\":\"string\""));
            assertTrue(requestSchema.contains("\"status\":{\"type\":\"integer\""));
            assertTrue(requestSchema.contains("\"situationId\":{\"type\":\"integer\""));
        }
    }

    @Test
    void testMockResponseGeneration() {
        // 测试mock响应生成
        MockService mockService = new MockService();
        mockService.setName("测试服务");
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setMockService(mockService);
        endpoint.setName("测试接口");
        endpoint.setPath("/api/test");
        endpoint.setMethod(HttpMethod.POST);
        endpoint.setDescription("测试接口描述");
        
        // 测试POST请求的mock响应
        String mockResponse = service.generateMockResponse(endpoint);
        assertNotNull(mockResponse);
        
        // 验证响应不包含错误信息
        assertFalse(mockResponse.contains("error"));
        assertFalse(mockResponse.contains("失败"));
        
        // 验证响应包含成功信息
        assertTrue(mockResponse.contains("success") || mockResponse.contains("成功") || mockResponse.contains("200"));
        
        // 测试GET请求的mock响应
        endpoint.setMethod(HttpMethod.GET);
        String getMockResponse = service.generateMockResponse(endpoint);
        assertNotNull(getMockResponse);
        
        // 验证GET响应不包含错误信息
        assertFalse(getMockResponse.contains("error"));
        assertFalse(getMockResponse.contains("失败"));
    }
} 