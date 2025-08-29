package com.dbapp.dasmockserver.service;

import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import com.dbapp.dasmockserver.repository.ApiEndpointRepository;
import com.dbapp.dasmockserver.repository.MockServiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class MockServerService {
    
    @Autowired
    private MockServiceRepository mockServiceRepository;
    
    @Autowired
    private ApiEndpointRepository apiEndpointRepository;
    
    @Autowired
    private DocumentParserService documentParserService;
    
    @Autowired
    private AiCodeGenerationService aiCodeGenerationService;
    
    @Autowired
    private CodeGenerationService codeGenerationService;
    
    @Autowired
    private ProcessManagementService processManagementService;
    
    /**
     * 创建新的Mock服务
     */
    public MockService createMockService(String name, String description, Integer port) {
        MockService mockService = new MockService();
        mockService.setName(name);
        mockService.setDescription(description);
        mockService.setPort(port);
        mockService.setStatus(MockService.ServiceStatus.CREATED);
        
        return mockServiceRepository.save(mockService);
    }
    
    /**
     * 上传文档并生成Mock服务
     */
    public MockService generateMockServiceFromDocument(MultipartFile file, String serviceName, 
                                                      String description, Integer port,
                                                      String aiModel, Double aiTemperature, 
                                                      Integer aiMaxTokens, Double aiTopP) throws IOException {
        // 验证文件格式
        if (!documentParserService.isSupportedFormat(file.getOriginalFilename())) {
            throw new IllegalArgumentException("不支持的文件格式");
        }
        
        // 解析文档内容
        String documentContent = documentParserService.parseDocument(file);
        
        // 创建Mock服务
        MockService mockService = createMockService(serviceName, description, port);
        mockService.setOriginalDocument(documentContent);
        mockService.setDocumentType(getFileExtension(file.getOriginalFilename()));
        mockService.setStatus(MockService.ServiceStatus.GENERATING);
        mockService = mockServiceRepository.save(mockService);
        
        try {
            // 临时设置AI配置用于本次生成
            aiCodeGenerationService.setTemporaryAiConfig(aiModel, aiTemperature, aiMaxTokens, aiTopP);
            
            // 使用AI分析文档并生成API端点
            List<ApiEndpoint> endpoints = aiCodeGenerationService.generateApiEndpoints(documentContent, mockService);
            
            // 保存API端点
            for (ApiEndpoint endpoint : endpoints) {
                endpoint.setMockService(mockService);
                apiEndpointRepository.save(endpoint);
            }
            
            // 生成Mock响应
            for (ApiEndpoint endpoint : endpoints) {
                String mockResponse = aiCodeGenerationService.generateMockResponse(endpoint);
                endpoint.setMockResponse(mockResponse);
                apiEndpointRepository.save(endpoint);
            }
            
            // 生成代码项目
            String projectPath = codeGenerationService.generateMockServerProject(mockService, endpoints);
            mockService.setProjectPath(projectPath);
            mockService.setBaseUrl("http://localhost:" + port);
            log.info("Mock服务生成成功，项目路径: {},访问路径: {}", projectPath,mockService.getBaseUrl());
            mockService.setStatus(MockService.ServiceStatus.CREATED);
            mockServiceRepository.save(mockService);
            
            return mockService;
            
        } catch (Exception e) {
            mockService.setStatus(MockService.ServiceStatus.ERROR);
            mockServiceRepository.save(mockService);
            throw new RuntimeException("生成Mock服务失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取所有Mock服务
     */
    public List<MockService> getAllMockServices() {
        return mockServiceRepository.findAll();
    }
    
    /**
     * 根据ID获取Mock服务
     */
    public Optional<MockService> getMockServiceById(Long id) {
        return mockServiceRepository.findById(id);
    }
    
    /**
     * 获取Mock服务的所有端点
     */
    public List<ApiEndpoint> getEndpointsByServiceId(Long serviceId) {
        return apiEndpointRepository.findByMockServiceId(serviceId);
    }
    
    /**
     * 更新API端点
     */
    public ApiEndpoint updateEndpoint(Long endpointId, ApiEndpoint updatedEndpoint) {
        Optional<ApiEndpoint> optional = apiEndpointRepository.findById(endpointId);
        if (optional.isPresent()) {
            ApiEndpoint existing = optional.get();
            
            if (updatedEndpoint.getName() != null) {
                existing.setName(updatedEndpoint.getName());
            }
            if (updatedEndpoint.getPath() != null) {
                existing.setPath(updatedEndpoint.getPath());
            }
            if (updatedEndpoint.getMethod() != null) {
                existing.setMethod(updatedEndpoint.getMethod());
            }
            if (updatedEndpoint.getDescription() != null) {
                existing.setDescription(updatedEndpoint.getDescription());
            }
            if (updatedEndpoint.getRequestSchema() != null) {
                existing.setRequestSchema(updatedEndpoint.getRequestSchema());
            }
            if (updatedEndpoint.getResponseSchema() != null) {
                existing.setResponseSchema(updatedEndpoint.getResponseSchema());
            }
            if (updatedEndpoint.getMockResponse() != null) {
                existing.setMockResponse(updatedEndpoint.getMockResponse());
            }
            if (updatedEndpoint.getResponseDelay() != null) {
                existing.setResponseDelay(updatedEndpoint.getResponseDelay());
            }
            if (updatedEndpoint.getStatusCode() != null) {
                existing.setStatusCode(updatedEndpoint.getStatusCode());
            }
            if (updatedEndpoint.getHeaders() != null) {
                existing.setHeaders(updatedEndpoint.getHeaders());
            }
            
            return apiEndpointRepository.save(existing);
        }
        throw new IllegalArgumentException("端点不存在: " + endpointId);
    }
    
    /**
     * 删除Mock服务
     */
    public void deleteMockService(Long serviceId) {
        mockServiceRepository.deleteById(serviceId);
    }
    
    /**
     * 启动Mock服务
     */
    public boolean startMockService(Long serviceId) {
        Optional<MockService> optional = mockServiceRepository.findById(serviceId);
        if (optional.isPresent()) {
            MockService mockService = optional.get();
            
            // 检查服务是否已经在运行
            if (processManagementService.isServiceRunning(serviceId)) {
                log.warn("服务 {} 已经在运行中", serviceId);
                mockService.setStatus(MockService.ServiceStatus.RUNNING);
                mockServiceRepository.save(mockService);
                return true;
            }
            
            // 从数据库获取项目路径
            String projectPath = mockService.getProjectPath();
            if (projectPath == null || projectPath.trim().isEmpty()) {
                log.error("服务 {} 的项目路径为空", serviceId);
                mockService.setStatus(MockService.ServiceStatus.ERROR);
                mockServiceRepository.save(mockService);
                return false;
            }
            
            // 启动进程
            boolean started = processManagementService.startMockServer(serviceId, projectPath, mockService.getPort());
            
            if (started) {
                mockService.setStatus(MockService.ServiceStatus.RUNNING);
                mockServiceRepository.save(mockService);
                log.info("Mock服务启动成功: serviceId={}, port={}", serviceId, mockService.getPort());
                return true;
            } else {
                mockService.setStatus(MockService.ServiceStatus.ERROR);
                mockServiceRepository.save(mockService);
                log.error("Mock服务启动失败: serviceId={}", serviceId);
                return false;
            }
        }
        return false;
    }
    
    /**
     * 停止Mock服务
     */
    public boolean stopMockService(Long serviceId) {
        Optional<MockService> optional = mockServiceRepository.findById(serviceId);
        if (optional.isPresent()) {
            MockService mockService = optional.get();
            
            // 停止进程
            boolean stopped = processManagementService.stopMockServer(serviceId);
            
            if (stopped) {
                mockService.setStatus(MockService.ServiceStatus.STOPPED);
                mockServiceRepository.save(mockService);
                log.info("Mock服务停止成功: serviceId={}", serviceId);
                return true;
            } else {
                log.error("Mock服务停止失败: serviceId={}", serviceId);
                return false;
            }
        }
        return false;
    }
    
    /**
     * 重新生成Mock服务代码
     */
    public void regenerateMockService(Long serviceId) throws IOException {
        Optional<MockService> optional = mockServiceRepository.findById(serviceId);
        if (optional.isPresent()) {
            MockService mockService = optional.get();
            List<ApiEndpoint> endpoints = apiEndpointRepository.findByMockServiceId(serviceId);
            
            // 重新生成代码项目
            String projectPath = codeGenerationService.generateMockServerProject(mockService, endpoints);
            mockService.setProjectPath(projectPath);
            mockServiceRepository.save(mockService);
        }
    }
    

    
    /**
     * 检查服务是否正在运行
     */
    public boolean isServiceRunning(Long serviceId) {
        return processManagementService.isServiceRunning(serviceId);
    }
    
    /**
     * 获取服务进程ID
     */
    public Long getServiceProcessId(Long serviceId) {
        return processManagementService.getProcessId(serviceId);
    }
    
    /**
     * 获取服务日志
     */
    public String getServiceLog(Long serviceId) {
        return processManagementService.getServiceLog(serviceId);
    }
    
    /**
     * 根据ID获取API端点
     */
    public Optional<ApiEndpoint> getApiEndpointById(Long id) {
        return apiEndpointRepository.findById(id);
    }
    
    /**
     * 更新API端点
     */
    public ApiEndpoint updateApiEndpoint(Long id, ApiEndpoint updatedEndpoint) {
        Optional<ApiEndpoint> optional = apiEndpointRepository.findById(id);
        if (optional.isPresent()) {
            ApiEndpoint endpoint = optional.get();
            endpoint.setName(updatedEndpoint.getName());
            endpoint.setPath(updatedEndpoint.getPath());
            endpoint.setMethod(updatedEndpoint.getMethod());
            endpoint.setDescription(updatedEndpoint.getDescription());
            endpoint.setStatusCode(updatedEndpoint.getStatusCode());
            endpoint.setResponseDelay(updatedEndpoint.getResponseDelay());
            endpoint.setMockResponse(updatedEndpoint.getMockResponse());
            return apiEndpointRepository.save(endpoint);
        }
        throw new IllegalArgumentException("Endpoint not found with id: " + id);
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
    
    /**
     * 获取可用端口
     */
    public Integer getAvailablePort() {
        // 获取所有已使用的端口
        List<Integer> usedPorts = mockServiceRepository.findAll().stream()
                .map(MockService::getPort)
                .toList();
        
        // 从8081开始查找可用端口
        int startPort = 8081;
        int maxPort = 65535;
        
        for (int port = startPort; port <= maxPort; port++) {
            if (!usedPorts.contains(port)) {
                return port;
            }
        }
        
        // 如果没有找到可用端口，返回null
        return null;
    }
} 