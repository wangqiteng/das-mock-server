package com.dbapp.dasmockserver.controller;

import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import com.dbapp.dasmockserver.service.MockServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class WebController {
    
    @Autowired
    private MockServerService mockServerService;
    
    /**
     * 首页
     */
    @GetMapping("/")
    public String index(Model model) {
        List<MockService> services = mockServerService.getAllMockServices();
        model.addAttribute("services", services);
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
        // 这里需要根据端点ID获取端点信息
        // 为了简化，暂时返回编辑页面
        model.addAttribute("endpointId", id);
        return "edit-endpoint";
    }
    
    /**
     * 服务管理页面
     */
    @GetMapping("/manage")
    public String manageServices(Model model) {
        List<MockService> services = mockServerService.getAllMockServices();
        model.addAttribute("services", services);
        return "manage";
    }
    
    /**
     * API文档页面
     */
    @GetMapping("/api-docs")
    public String apiDocs() {
        return "api-docs";
    }
} 