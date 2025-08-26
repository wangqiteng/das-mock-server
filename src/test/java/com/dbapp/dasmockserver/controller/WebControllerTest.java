package com.dbapp.dasmockserver.controller;

import com.dbapp.dasmockserver.model.MockService;
import com.dbapp.dasmockserver.service.MockServerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebController.class)
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MockServerService mockServerService;

    private MockService mockService;

    @BeforeEach
    void setUp() {
        mockService = new MockService();
        mockService.setId(1L);
        mockService.setName("TestService");
        mockService.setDescription("Test Description");
        mockService.setPort(8081);
        mockService.setStatus(MockService.ServiceStatus.CREATED);
        mockService.setProjectPath("/tmp/test-project");
    }

    @Test
    void testIndexPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void testUploadPage() throws Exception {
        mockMvc.perform(get("/upload"))
                .andExpect(status().isOk())
                .andExpect(view().name("upload"));
    }

    @Test
    void testServiceDetailPage() throws Exception {
        when(mockServerService.getMockServiceById(1L)).thenReturn(Optional.of(mockService));
        when(mockServerService.getEndpointsByServiceId(1L)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/service/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("service-detail"));
    }

    @Test
    void testServiceDetailPageNotFound() throws Exception {
        when(mockServerService.getMockServiceById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/service/999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void testManagePage() throws Exception {
        mockMvc.perform(get("/manage"))
                .andExpect(status().isOk())
                .andExpect(view().name("manage"));
    }

    @Test
    void testDownloadServiceCode() throws Exception {
        // 创建临时测试目录
        Path testProjectDir = Files.createTempDirectory("test-project");
        Path testFile = testProjectDir.resolve("test.txt");
        Files.write(testFile, "test content".getBytes());
        
        mockService.setProjectPath(testProjectDir.toString());
        when(mockServerService.getMockServiceById(1L)).thenReturn(Optional.of(mockService));

        mockMvc.perform(get("/service/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", 
                    "attachment; filename=\"TestService.zip\"; filename*=UTF-8''TestService.zip"))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));

        // 清理测试文件
        Files.deleteIfExists(testFile);
        Files.deleteIfExists(testProjectDir);
    }

    @Test
    void testDownloadServiceCodeWithChineseName() throws Exception {
        // 创建临时测试目录
        Path testProjectDir = Files.createTempDirectory("test-project");
        Path testFile = testProjectDir.resolve("test.txt");
        Files.write(testFile, "test content".getBytes());
        
        // 设置中文服务名称
        mockService.setName("海峡银行事件中心接口文档");
        mockService.setProjectPath(testProjectDir.toString());
        when(mockServerService.getMockServiceById(1L)).thenReturn(Optional.of(mockService));

        mockMvc.perform(get("/service/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", 
                    "attachment; filename=\"%E6%B5%B7%E5%B3%A1%E9%93%B6%E8%A1%8C%E4%BA%8B%E4%BB%B6%E4%B8%AD%E5%BF%83%E6%8E%A5%E5%8F%A3%E6%96%87%E6%A1%A3.zip\"; filename*=UTF-8''%E6%B5%B7%E5%B3%A1%E9%93%B6%E8%A1%8C%E4%BA%8B%E4%BB%B6%E4%B8%AD%E5%BF%83%E6%8E%A5%E5%8F%A3%E6%96%87%E6%A1%A3.zip"))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));

        // 清理测试文件
        Files.deleteIfExists(testFile);
        Files.deleteIfExists(testProjectDir);
    }

    @Test
    void testDownloadServiceCodeNotFound() throws Exception {
        when(mockServerService.getMockServiceById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/service/999/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDownloadServiceCodeNoProjectPath() throws Exception {
        mockService.setProjectPath(null);
        when(mockServerService.getMockServiceById(1L)).thenReturn(Optional.of(mockService));

        mockMvc.perform(get("/service/1/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDownloadServiceCodeProjectPathNotExists() throws Exception {
        mockService.setProjectPath("/non-existent-path");
        when(mockServerService.getMockServiceById(1L)).thenReturn(Optional.of(mockService));

        mockMvc.perform(get("/service/1/download"))
                .andExpect(status().isNotFound());
    }
} 