package com.dbapp.dasmockserver.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DocumentParserService {
    
    /**
     * 解析上传的文档文件
     */
    public String parseDocument(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        
        String fileExtension = getFileExtension(fileName).toLowerCase();

        try{
            switch (fileExtension) {
                case "pdf":
                    return parsePdfFile(file.getInputStream());
                case "docx":
                    return parseWordDocxFile(file.getInputStream());
                case "doc":
                    return parseWordDocFile(file.getInputStream());
                case "md":
                case "markdown":
                    return parseMarkdownFile(file.getInputStream());
                case "txt":
                    return parseTextFile(file.getInputStream());
                default:
                    throw new IllegalArgumentException("不支持的文件格式: " + fileExtension);
            }
        }catch (Exception e){
            log.error("文件解析异常: ", e);
            throw new IllegalArgumentException("文件解析异常: " + e);
        }
    }
    
    /**
     * 解析PDF文件
     */
    private String parsePdfFile(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
    
    /**
     * 解析Word .docx 文件
     */
    private String parseWordDocxFile(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder content = new StringBuilder();
            
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                content.append(paragraph.getText()).append("\n");
            }
            
            return content.toString();
        }
    }

    /**
     * 解析Word .doc 文件
     */
    private String parseWordDocFile(InputStream inputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream)) {
            StringBuilder content = new StringBuilder();

            Range range = document.getRange();
            for (int i = 0; i < range.numParagraphs(); i++) {
                Paragraph paragraph = range.getParagraph(i);
                content.append(paragraph.text()).append("\n");
            }

            return content.toString();
        }
    }
    
    /**
     * 解析Markdown文件
     */
    private String parseMarkdownFile(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes());
    }
    
    /**
     * 解析文本文件
     */
    private String parseTextFile(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes());
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
    
    /**
     * 验证文件格式是否支持
     */
    public boolean isSupportedFormat(String fileName) {
        if (fileName == null) {
            return false;
        }
        
        String extension = getFileExtension(fileName).toLowerCase();
        return extension.matches("(pdf|docx|doc|md|markdown|txt)");
    }
    
    /**
     * 从文档内容中提取API信息
     */
    public List<String> extractApiSections(String documentContent) {
        List<String> apiSections = new ArrayList<>();
        
        // 按行分割文档
        String[] lines = documentContent.split("\n");
        StringBuilder currentSection = new StringBuilder();
        boolean inApiSection = false;
        
        for (String line : lines) {
            // 检测API相关的关键词
            if (isApiSectionStart(line)) {
                if (inApiSection && currentSection.length() > 0) {
                    apiSections.add(currentSection.toString().trim());
                    currentSection = new StringBuilder();
                }
                inApiSection = true;
            }
            
            if (inApiSection) {
                currentSection.append(line).append("\n");
            }
            
            // 检测API部分结束
            if (inApiSection && isApiSectionEnd(line)) {
                apiSections.add(currentSection.toString().trim());
                currentSection = new StringBuilder();
                inApiSection = false;
            }
        }
        
        // 添加最后一个部分
        if (inApiSection && currentSection.length() > 0) {
            apiSections.add(currentSection.toString().trim());
        }
        
        return apiSections;
    }
    
    /**
     * 判断是否为API部分开始
     */
    private boolean isApiSectionStart(String line) {
        String lowerLine = line.toLowerCase().trim();
        return lowerLine.contains("api") || 
               lowerLine.contains("endpoint") || 
               lowerLine.contains("接口") ||
               lowerLine.contains("路径") ||
               lowerLine.contains("url") ||
               lowerLine.matches(".*(get|post|put|delete|patch)\\s+.*");
    }
    
    /**
     * 判断是否为API部分结束
     */
    private boolean isApiSectionEnd(String line) {
        String lowerLine = line.toLowerCase().trim();
        return lowerLine.isEmpty() || 
               lowerLine.startsWith("#") || 
               lowerLine.startsWith("##") ||
               lowerLine.startsWith("###");
    }
}
