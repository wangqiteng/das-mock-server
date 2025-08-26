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
    public ResponseEntity<Map<String, Object>> updateEndpoint(
            @PathVariable Long id,
            @RequestBody ApiEndpoint updatedEndpoint) {
        try {
            ApiEndpoint endpoint = mockServerService.updateApiEndpoint(id, updatedEndpoint);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "endpoint", endpoint,
                "mockServiceId", endpoint.getMockService().getId()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
    public ResponseEntity<Map<String, Object>> startMockService(@PathVariable Long id) {
        boolean started = mockServerService.startMockService(id);
        if (started) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Mock服务启动成功",
                "serviceId", id
            ));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "message", "Mock服务启动失败",
                    "serviceId", id
                ));
        }
    }
    
    /**
     * 停止Mock服务
     */
    @PostMapping("/services/{id}/stop")
    public ResponseEntity<Map<String, Object>> stopMockService(@PathVariable Long id) {
        boolean stopped = mockServerService.stopMockService(id);
        if (stopped) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Mock服务停止成功",
                "serviceId", id
            ));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "message", "Mock服务停止失败",
                    "serviceId", id
                ));
        }
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
     * 检查服务运行状态
     */
    @GetMapping("/services/{id}/status")
    public ResponseEntity<Map<String, Object>> getServiceStatus(@PathVariable Long id) {
        boolean isRunning = mockServerService.isServiceRunning(id);
        Long processId = mockServerService.getServiceProcessId(id);
        
        return ResponseEntity.ok(Map.of(
            "serviceId", id,
            "isRunning", isRunning,
            "processId", processId != null ? processId : -1
        ));
    }
    
    /**
     * 获取服务日志
     */
    @GetMapping("/services/{id}/logs")
    public ResponseEntity<Map<String, Object>> getServiceLogs(@PathVariable Long id) {
        String logs = mockServerService.getServiceLog(id);
        return ResponseEntity.ok(Map.of(
            "serviceId", id,
            "logs", logs
        ));
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "message", "Mock Server Generator is running"));
    }
} 