package com.dbapp.dasmockserver.controller;

import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import com.dbapp.dasmockserver.service.MockServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/mock-server")
@CrossOrigin(origins = "*")
public class MockServerController {
    
    private static final Logger log = LoggerFactory.getLogger(MockServerController.class);
    
    @Autowired
    private MockServerService mockServerService;
    
    /**
     * 上传文档并生成Mock服务
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateMockService(
            @RequestParam("file") MultipartFile file,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "port", defaultValue = "8081") Integer port,
            @RequestParam(value = "aiModel", defaultValue = "qwen-turbo") String aiModel,
            @RequestParam(value = "aiTemperature", defaultValue = "0.1") Double aiTemperature,
            @RequestParam(value = "aiMaxTokens", defaultValue = "4000") Integer aiMaxTokens,
            @RequestParam(value = "aiTopP", defaultValue = "0.7") Double aiTopP) {
        
        try {
            MockService mockService = mockServerService.generateMockServiceFromDocument(
                file, serviceName, description, tags, port, aiModel, aiTemperature, aiMaxTokens, aiTopP);
            return ResponseEntity.ok(mockService);
        } catch (IllegalStateException e) {
            // 处理并发锁冲突的情况
            log.warn("文档生成服务繁忙: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "success", false,
                    "error", e.getMessage() != null ? e.getMessage() : "系统正在处理其他文档生成请求，请稍后再试",
                    "code", "SERVICE_BUSY"
            ));
        } catch (IllegalArgumentException e) {
            log.error("生成Mock服务参数错误: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage() != null ? e.getMessage() : "请求参数错误"
            ));
        } catch (Exception e) {
            log.error("生成Mock服务失败: {}", e.getMessage(), e);
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = "生成Mock服务时发生未知错误";
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", errorMessage
            ));
        }
    }
    
    /**
     * 获取所有Mock服务（分页查询）
     */
    @GetMapping("/services")
    public ResponseEntity<Page<MockService>> getAllMockServices(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "tag", required = false) String tag) {
        
        Pageable pageable = PageRequest.of(page, size);
        MockService.ServiceStatus serviceStatus = null;
        if (status != null && !status.isEmpty()) {
            try {
                serviceStatus = MockService.ServiceStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                // 忽略无效的状态值
            }
        }
        
        Page<MockService> services = mockServerService.getMockServicesWithFilters(
            keyword, serviceStatus, tag, pageable);
        return ResponseEntity.ok(services);
    }
    
    /**
     * 获取所有标签
     */
    @GetMapping("/tags")
    public ResponseEntity<Map<String, Object>> getAllTags() {
        try {
            List<String> tags = mockServerService.getAllTags();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "tags", tags
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "获取标签失败: " + e.getMessage()
            ));
        }
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
            log.info("开始更新端点: id={}, name={}, path={}", id, updatedEndpoint.getName(), updatedEndpoint.getPath());
            
            // 验证输入参数

            if (id == null) {
                log.error("端点ID为空");
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "端点ID不能为空"
                ));
            }
            
            ApiEndpoint endpoint = mockServerService.updateApiEndpoint(id, updatedEndpoint);
            
            log.info("端点更新成功: id={}, name={}", id, endpoint.getName());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "endpoint", endpoint,
                "mockServiceId", endpoint.getMockService().getId()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("端点不存在: id={}, error={}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("更新端点时发生错误: id={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "服务器内部错误: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 删除Mock服务
     * 先停止后删除
     */
    @DeleteMapping("/services/{id}")
    public ResponseEntity<Void> deleteMockService(@PathVariable Long id) {
        boolean stopped = mockServerService.stopMockService(id);
        if(stopped){
            mockServerService.deleteMockService(id);
            log.error("删除服务成功: id={}", id);
        }
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 启动Mock服务
     * 端口冲突时后端会自动递增寻找可用端口，响应中会包含 portChanged/actualPort 提示
     */
    @PostMapping("/services/{id}/start")
    public ResponseEntity<Map<String, Object>> startMockService(@PathVariable Long id) {
        // 启动前先读取首选端口，以便在响应中报告端口是否被自动重选
        Integer preferredPort = mockServerService.getMockServiceById(id)
                .map(MockService::getPort).orElse(null);
        MockService started = mockServerService.startMockService(id);
        if (started != null) {
            Integer actualPort = started.getPort();
            boolean portChanged = preferredPort != null && !preferredPort.equals(actualPort);
            String message = portChanged
                    ? "Mock服务启动成功（首选端口 " + preferredPort + " 被占用，已自动切换到 " + actualPort + "）"
                    : "Mock服务启动成功";
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", message,
                    "serviceId", id,
                    "port", actualPort != null ? actualPort : -1,
                    "portChanged", portChanged,
                    "preferredPort", preferredPort != null ? preferredPort : -1
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
        String tags = (String) request.get("tags");
        Integer port = (Integer) request.get("port");
        
        if (name == null || port == null) {
            return ResponseEntity.badRequest().build();
        }
        
        MockService mockService = mockServerService.createMockService(name, description, tags, port);
        return ResponseEntity.status(HttpStatus.CREATED).body(mockService);
    }
    
    /**
     * 一键停止所有Mock服务
     */
    @PostMapping("/services/stop-all")
    public ResponseEntity<Map<String, Object>> stopAllMockServices() {
        try {
            int stoppedCount = mockServerService.stopAllServices();
            String message = stoppedCount > 0
                    ? "已成功停止 " + stoppedCount + " 个Mock服务"
                    : "当前没有运行中的Mock服务";
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message,
                "stoppedCount", stoppedCount
            ));
        } catch (Exception e) {
            log.error("一键停止Mock服务失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "message", "一键停止服务时发生错误: " + e.getMessage()
                ));
        }
    }

    /**
     * 清理所有Mock服务进程
     */
    @PostMapping("/services/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupAllServices() {
        // cleanup 语义与 stop-all 一致：停止所有运行中的Mock服务
        return stopAllMockServices();
    }
    
    /**
     * 获取所有运行中的服务信息（调试用）
     */
    @GetMapping("/services/running")
    public ResponseEntity<Map<String, Object>> getRunningServices() {
        try {
            Map<String, Object> runningServices = mockServerService.getAllRunningServices();
            return ResponseEntity.ok(runningServices);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "获取运行中服务信息时发生错误: " + e.getMessage()
                ));
        }
    }
    
    /**
     * 检查服务运行状态
     */
    @GetMapping("/services/{id}/status")
    public ResponseEntity<Map<String, Object>> getServiceStatus(@PathVariable Long id) {
        try {
            Optional<MockService> serviceOpt = mockServerService.getMockServiceById(id);
            if (serviceOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            boolean isRunning = mockServerService.isServiceRunning(id);
            Long processId = mockServerService.getServiceProcessId(id);
            
            // 根据运行状态确定服务状态
            String status = isRunning ? "RUNNING" : "STOPPED";
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "serviceId", id,
                "status", status,
                "isRunning", isRunning,
                "processId", processId != null ? processId : -1
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "serviceId", id,
                "status", "ERROR",
                "message", "检查服务状态时发生错误: " + e.getMessage()
            ));
        }
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
        return ResponseEntity.ok(Map.of("status", "UP", "message", "Das Mock Server is running"));
    }
    
    /**
     * 获取可用端口
     */
    @GetMapping("/available-port")
    public ResponseEntity<Map<String, Object>> getAvailablePort() {
        try {
            Integer availablePort = mockServerService.getAvailablePort();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "availablePort", availablePort
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "获取可用端口失败: " + e.getMessage()
            ));
        }
    }
} 