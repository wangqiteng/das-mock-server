package com.dbapp.dasmockserver.controller;

import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import com.dbapp.dasmockserver.service.MockServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mock-server")
@CrossOrigin(origins = "*")
public class MockServerController {
    
    @Autowired
    private MockServerService mockServerService;
    
    /**
     * 上传文档并生成Mock服务
     */
    @PostMapping("/generate")
    public ResponseEntity<MockService> generateMockService(
            @RequestParam("file") MultipartFile file,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "port", defaultValue = "8081") Integer port) {
        
        try {
            MockService mockService = mockServerService.generateMockServiceFromDocument(
                file, serviceName, description, port);
            return ResponseEntity.ok(mockService);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 获取所有Mock服务
     */
    @GetMapping("/services")
    public ResponseEntity<List<MockService>> getAllMockServices() {
        List<MockService> services = mockServerService.getAllMockServices();
        return ResponseEntity.ok(services);
    }
    
    /**
     * 根据ID获取Mock服务
     */
    @GetMapping("/services/{id}")
    public ResponseEntity<MockService> getMockServiceById(@PathVariable Long id) {
        return mockServerService.getMockServiceById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 获取Mock服务的所有端点
     */
    @GetMapping("/services/{id}/endpoints")
    public ResponseEntity<List<ApiEndpoint>> getEndpointsByServiceId(@PathVariable Long id) {
        List<ApiEndpoint> endpoints = mockServerService.getEndpointsByServiceId(id);
        return ResponseEntity.ok(endpoints);
    }
    
    /**
     * 更新API端点
     */
    @PutMapping("/endpoints/{id}")
    public ResponseEntity<ApiEndpoint> updateEndpoint(
            @PathVariable Long id,
            @RequestBody ApiEndpoint updatedEndpoint) {
        try {
            ApiEndpoint endpoint = mockServerService.updateEndpoint(id, updatedEndpoint);
            return ResponseEntity.ok(endpoint);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 删除Mock服务
     */
    @DeleteMapping("/services/{id}")
    public ResponseEntity<Void> deleteMockService(@PathVariable Long id) {
        mockServerService.deleteMockService(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 启动Mock服务
     */
    @PostMapping("/services/{id}/start")
    public ResponseEntity<Void> startMockService(@PathVariable Long id) {
        mockServerService.startMockService(id);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 停止Mock服务
     */
    @PostMapping("/services/{id}/stop")
    public ResponseEntity<Void> stopMockService(@PathVariable Long id) {
        mockServerService.stopMockService(id);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 重新生成Mock服务代码
     */
    @PostMapping("/services/{id}/regenerate")
    public ResponseEntity<Void> regenerateMockService(@PathVariable Long id) {
        try {
            mockServerService.regenerateMockService(id);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 创建新的Mock服务（手动）
     */
    @PostMapping("/services")
    public ResponseEntity<MockService> createMockService(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String description = (String) request.get("description");
        Integer port = (Integer) request.get("port");
        
        if (name == null || port == null) {
            return ResponseEntity.badRequest().build();
        }
        
        MockService mockService = mockServerService.createMockService(name, description, port);
        return ResponseEntity.status(HttpStatus.CREATED).body(mockService);
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "message", "Mock Server Generator is running"));
    }
} 