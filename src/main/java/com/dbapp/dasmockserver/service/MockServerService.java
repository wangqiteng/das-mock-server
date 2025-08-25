package com.dbapp.dasmockserver.service;

import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import com.dbapp.dasmockserver.repository.ApiEndpointRepository;
import com.dbapp.dasmockserver.repository.MockServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
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
                                                      String description, Integer port) throws IOException {
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
            mockService.setBaseUrl("http://localhost:" + port);
            
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
    public void startMockService(Long serviceId) {
        Optional<MockService> optional = mockServiceRepository.findById(serviceId);
        if (optional.isPresent()) {
            MockService mockService = optional.get();
            mockService.setStatus(MockService.ServiceStatus.RUNNING);
            mockServiceRepository.save(mockService);
        }
    }
    
    /**
     * 停止Mock服务
     */
    public void stopMockService(Long serviceId) {
        Optional<MockService> optional = mockServiceRepository.findById(serviceId);
        if (optional.isPresent()) {
            MockService mockService = optional.get();
            mockService.setStatus(MockService.ServiceStatus.STOPPED);
            mockServiceRepository.save(mockService);
        }
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
            codeGenerationService.generateMockServerProject(mockService, endpoints);
        }
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
} 