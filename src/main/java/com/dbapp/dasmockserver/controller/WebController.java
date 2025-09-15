package com.dbapp.dasmockserver.controller;

import com.dbapp.dasmockserver.config.AiConfig;
import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import com.dbapp.dasmockserver.service.AiConfigService;
import com.dbapp.dasmockserver.service.MockServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Controller
public class WebController {
    
    @Autowired
    private MockServerService mockServerService;
    
    @Autowired
    private AiConfigService aiConfigService;
    
    /**
     * 首页
     */
    @GetMapping("/")
    public String index(Model model) {
        // 获取前10个服务用于首页显示
        Page<MockService> servicesPage = mockServerService.getMockServicesWithFilters(
            null, null, null, org.springframework.data.domain.PageRequest.of(0, 10));
        model.addAttribute("services", servicesPage.getContent());
        return "index";
    }
    
    /**
     * 上传文档页面
     */
    @GetMapping("/upload")
    public String uploadPage() {
        return "upload";
    }
    
    /**
     * 服务详情页面
     */
    @GetMapping("/service/{id}")
    public String serviceDetail(@PathVariable Long id, Model model) {
        return mockServerService.getMockServiceById(id)
                .map(service -> {
                    List<ApiEndpoint> endpoints = mockServerService.getEndpointsByServiceId(id);
                    model.addAttribute("service", service);
                    model.addAttribute("endpoints", endpoints);
                    return "service-detail";
                })
                .orElse("redirect:/");
    }
    
    /**
     * 端点编辑页面
     */
    @GetMapping("/endpoint/{id}/edit")
    public String editEndpoint(@PathVariable Long id, Model model) {
        try {
            // 根据端点ID获取端点信息
            ApiEndpoint endpoint = mockServerService.getApiEndpointById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Endpoint not found"));
            
            model.addAttribute("endpoint", endpoint);
            return "edit-endpoint";
        } catch (Exception e) {
            // 如果端点不存在，重定向到首页
            return "redirect:/";
        }
    }
    
    /**
     * 服务管理页面
     */
    @GetMapping("/manage")
    public String manageServices(Model model) {
        // 获取所有服务用于管理页面显示
        Page<MockService> servicesPage = mockServerService.getMockServicesWithFilters(
            null, null, null, org.springframework.data.domain.PageRequest.of(0, 1000));
        model.addAttribute("services", servicesPage.getContent());
        return "manage";
    }
    

    
    /**
     * 下载Mock服务代码
     */
    @GetMapping("/service/{id}/download")
    public ResponseEntity<Resource> downloadServiceCode(@PathVariable Long id) {
        try {
            MockService mockService = mockServerService.getMockServiceById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Service not found"));
            
            String projectPath = mockService.getProjectPath();
            if (projectPath == null || projectPath.trim().isEmpty()) {
                throw new IllegalArgumentException("Project path not found");
            }
            
            Path projectDir = Paths.get(projectPath);
            if (!Files.exists(projectDir)) {
                throw new IllegalArgumentException("Project directory not found");
            }
            
            // 创建ZIP文件
            Path zipPath = createZipFile(projectDir, mockService.getName());
            
            // 读取ZIP文件作为Resource
            Resource resource = new org.springframework.core.io.FileSystemResource(zipPath.toFile());
            
                    // 处理中文字符编码问题
        String fileName = mockService.getName() + ".zip";
        String encodedFileName;
        try {
            encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8")
                    .replaceAll("\\+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
                    
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 创建ZIP文件
     */
    private Path createZipFile(Path projectDir, String serviceName) throws IOException {
        Path zipPath = Paths.get(System.getProperty("java.io.tmpdir"), serviceName + ".zip");
        
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(projectDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            String zipEntryName = projectDir.relativize(path).toString();
                            java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(zipEntryName);
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Error creating ZIP file", e);
                        }
                    });
        }
        
        return zipPath;
    }
    
    /**
     * 获取当前AI模型配置
     */
    @GetMapping("/api/ai-config")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAiConfig() {
        try {
            AiConfig.AiModelConfig currentConfig = aiConfigService.getCurrentConfig();
            List<AiConfigService.ModelInfo> availableModels = aiConfigService.getAvailableModels();
            
            Map<String, Object> response = Map.of(
                "currentConfig", currentConfig,
                "availableModels", availableModels,
                "configSource", aiConfigService.getConfigSource(),
                "isDynamic", aiConfigService.isUsingDynamicConfig()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 更新AI模型配置
     */
    @PostMapping("/api/ai-config")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateAiConfig(@RequestBody AiConfig.AiModelConfig config) {
        try {
            // 验证配置参数
            if (!aiConfigService.validateConfig(config)) {
                return ResponseEntity.badRequest().body(Map.of("error", "配置参数无效"));
            }
            
            // 实时更新配置
            boolean success = aiConfigService.updateConfig(config);
            
            if (success) {
                Map<String, Object> response = Map.of(
                    "message", "配置已实时更新，立即生效",
                    "config", config,
                    "source", aiConfigService.getConfigSource()
                );
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "配置更新失败"));
            }
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 重置AI配置为默认值
     */
    @PostMapping("/api/ai-config/reset")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetAiConfig() {
        try {
            aiConfigService.resetToDefault();
            
            AiConfig.AiModelConfig currentConfig = aiConfigService.getCurrentConfig();
            
            Map<String, Object> response = Map.of(
                "message", "配置已重置为默认值",
                "config", currentConfig,
                "source", aiConfigService.getConfigSource()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 获取默认AI配置
     */
    @GetMapping("/api/ai-config/default")
    @ResponseBody
    public ResponseEntity<AiConfig.AiModelConfig> getDefaultAiConfig() {
        try {
            AiConfig.AiModelConfig defaultConfig = aiConfigService.getDefaultConfig();
            return ResponseEntity.ok(defaultConfig);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
} 