package com.dbapp.dasmockserver.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

class DocumentParserServiceTest {

    private DocumentParserService service;

    @BeforeEach
    void setUp() {
        service = new DocumentParserService();
    }

    @Test
    void testIsSupportedFormat() {
        assertTrue(service.isSupportedFormat("test.pdf"));
        assertTrue(service.isSupportedFormat("test.docx"));
        assertTrue(service.isSupportedFormat("test.doc"));
        assertTrue(service.isSupportedFormat("test.md"));
        assertTrue(service.isSupportedFormat("test.markdown"));
        assertTrue(service.isSupportedFormat("test.txt"));
        assertFalse(service.isSupportedFormat("test.jpg"));
        assertFalse(service.isSupportedFormat("test.exe"));
        assertFalse(service.isSupportedFormat(null));
    }

    @Test
    void testParseTextFile() throws IOException {
        String content = "这是一个测试文档\n包含API信息\nGET /api/users";
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test.txt", 
            "text/plain", 
            content.getBytes(StandardCharsets.UTF_8)
        );
        
        String result = service.parseDocument(file);
        assertNotNull(result);
        assertTrue(result.contains("这是一个测试文档"));
        assertTrue(result.contains("GET /api/users"));
    }

    @Test
    void testParseMarkdownFile() throws IOException {
        String content = "# API文档\n\n## 用户接口\n\nGET /api/users";
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test.md", 
            "text/markdown", 
            content.getBytes(StandardCharsets.UTF_8)
        );
        
        String result = service.parseDocument(file);
        assertNotNull(result);
        assertTrue(result.contains("# API文档"));
        assertTrue(result.contains("GET /api/users"));
    }

    @Test
    void testExtractApiSections() {
        String documentContent = """
            文档开始
            
            API接口列表：
            GET /api/users - 获取用户列表
            POST /api/users - 创建用户
            
            其他内容
            
            ## 另一个API部分
            PUT /api/users/{id} - 更新用户
            DELETE /api/users/{id} - 删除用户
            """;
        
        List<String> sections = service.extractApiSections(documentContent);
        assertNotNull(sections);
        assertTrue(sections.size() > 0);
        
        // 验证是否包含API信息
        boolean containsApiInfo = sections.stream()
            .anyMatch(section -> section.contains("GET /api/users") || 
                               section.contains("POST /api/users") ||
                               section.contains("PUT /api/users") ||
                               section.contains("DELETE /api/users"));
        assertTrue(containsApiInfo);
    }

    @Test
    void testExtractApiSectionsWithTableData() {
        String documentContent = """
            文档开始
            
            === 表格数据 ===
            API名称 | 请求方法 | 路径 | 描述
            获取用户 | GET | /api/users | 获取用户列表
            创建用户 | POST | /api/users | 创建新用户
            更新用户 | PUT | /api/users/{id} | 更新用户信息
            删除用户 | DELETE | /api/users/{id} | 删除用户
            === 表格结束 ===
            
            其他内容
            """;
        
        List<String> sections = service.extractApiSections(documentContent);
        assertNotNull(sections);
        assertTrue(sections.size() > 0);
        
        // 验证是否包含表格中的API信息
        boolean containsTableApiInfo = sections.stream()
            .anyMatch(section -> section.contains("GET | /api/users") || 
                               section.contains("POST | /api/users") ||
                               section.contains("PUT | /api/users") ||
                               section.contains("DELETE | /api/users"));
        assertTrue(containsTableApiInfo);
    }

    @Test
    void testUnsupportedFormat() {
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test.jpg", 
            "image/jpeg", 
            "test content".getBytes()
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.parseDocument(file);
        });
    }

    @Test
    void testNullFileName() {
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            null, 
            "text/plain", 
            "test content".getBytes()
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.parseDocument(file);
        });
    }
} 