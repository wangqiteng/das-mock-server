package com.dbapp.dasmockserver.service;

import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.javapoet.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.validation.constraints.NotNull;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CodeGenerationService {
    
    @Value("${app.file.generated.path}")
    private String generatedPath;
    
    /**
     * 生成完整的Mock Server项目
     */
    public String generateMockServerProject(MockService mockService, List<ApiEndpoint> endpoints) throws IOException {
        String projectName = sanitizeProjectName(mockService.getName());
        String projectPath = generatedPath + "/" + projectName;
        
        // 创建项目目录
        createProjectDirectory(projectPath);
        
        // 生成各个文件
        generatePomXml(projectPath, mockService);
        generateApplicationClass(projectPath, mockService);
        generateControllerClass(projectPath, mockService, endpoints);
        generateDtoClasses(projectPath, endpoints);
        generateApplicationProperties(projectPath, mockService);
        generateReadme(projectPath, mockService);
        
        return projectPath;
    }
    
    /**
     * 创建项目目录结构
     */
    private void createProjectDirectory(String projectPath) throws IOException {
        Path path = Paths.get(projectPath);
        Files.createDirectories(path);
        
        // 创建Maven标准目录结构
        Files.createDirectories(path.resolve("src/main/java"));
        Files.createDirectories(path.resolve("src/main/resources"));
        Files.createDirectories(path.resolve("src/test/java"));
    }
    
    /**
     * 生成pom.xml文件
     */
    private void generatePomXml(String projectPath, MockService mockService) throws IOException {
        String pomContent = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.5.5</version>
                    <relativePath/>
                </parent>
                <groupId>com.dbapp</groupId>
                <artifactId>%s</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <name>%s</name>
                <description>%s</description>
                
                <properties>
                    <java.version>17</java.version>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                    <maven.compiler.encoding>UTF-8</maven.compiler.encoding>
                </properties>
                
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-validation</artifactId>
                    </dependency>
                    
                    <dependency>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-databind</artifactId>
                    </dependency>
                    
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-test</artifactId>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
                
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-maven-plugin</artifactId>
                            <configuration>
                                <fork>false</fork>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """, 
            sanitizeProjectName(mockService.getName()),
            mockService.getName(),
            mockService.getDescription() != null ? mockService.getDescription() : "Generated Mock Server"
        );
        
        Files.write(Paths.get(projectPath, "pom.xml"), pomContent.getBytes());
    }
    
    /**
     * 生成主应用类
     */
    private void generateApplicationClass(String projectPath, MockService mockService) throws IOException {
        String packageName = "com.dbapp." + sanitizeProjectName(mockService.getName()).toLowerCase();
        
        TypeSpec applicationClass = TypeSpec.classBuilder("Application")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassName.get("org.springframework.boot.autoconfigure", "SpringBootApplication"))
            .addMethod(MethodSpec.methodBuilder("main")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addParameter(String[].class, "args")
                .addStatement("$T.run($L.class, args)", 
                    ClassName.get("org.springframework.boot", "SpringApplication"),
                    "Application")
                .build())
            .build();
        
        JavaFile javaFile = JavaFile.builder(packageName, applicationClass).build();
        javaFile.writeTo(Paths.get(projectPath, "src/main/java"));
    }
    
    /**
     * 生成控制器类
     */
    private void generateControllerClass(String projectPath, MockService mockService, List<ApiEndpoint> endpoints) throws IOException {
        String packageName = "com.dbapp." + sanitizeProjectName(mockService.getName()).toLowerCase();
        
        TypeSpec.Builder controllerBuilder = TypeSpec.classBuilder("MockController")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassName.get("org.springframework.web.bind.annotation", "RestController"))
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "CrossOrigin"))
                .addMember("origins", "$S", "*")
                .build());
        
        // 用于跟踪已使用的方法名，确保不重复
        Set<String> usedMethodNames = new HashSet<>();
        
        // 为每个端点生成方法
        for (ApiEndpoint endpoint : endpoints) {
            MethodSpec method = generateEndpointMethod(endpoint, usedMethodNames);
            controllerBuilder.addMethod(method);
        }
        
        JavaFile javaFile = JavaFile.builder(packageName, controllerBuilder.build()).build();
        javaFile.writeTo(Paths.get(projectPath, "src/main/java"));
    }
    
    /**
     * 生成单个端点方法
     */
    private MethodSpec generateEndpointMethod(ApiEndpoint endpoint, Set<String> usedMethodNames) {
        String methodName = generateUniqueMethodName(endpoint.getPath(), usedMethodNames);
        
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .returns(Object.class);
        
        // 处理路径，移除重复的/api前缀
        String path = normalizePath(endpoint.getPath());
        
        // 解析参数信息
        ParameterInfo parameterInfo = parseParameters(endpoint);
        
        // 添加PathVariable参数到方法签名
        for (ParameterInfo.Parameter param : parameterInfo.getPathVariables()) {
            ClassName paramType = getParameterType(param.getType());
            methodBuilder.addParameter(
                ParameterSpec.builder(paramType, param.getName())
                    .addAnnotation(AnnotationSpec.builder(
                        ClassName.get("org.springframework.web.bind.annotation", "PathVariable"))
                        .addMember("value", "$S", param.getName())
                        .build())
                    .build()
            );
        }
        
        // 添加QueryParameter参数到方法签名
        for (ParameterInfo.Parameter param : parameterInfo.getQueryParameters()) {
            ClassName paramType = getParameterType(param.getType());
            methodBuilder.addParameter(
                ParameterSpec.builder(paramType, param.getName())
                    .addAnnotation(AnnotationSpec.builder(
                        ClassName.get("org.springframework.web.bind.annotation", "RequestParam"))
                        .addMember("value", "$S", param.getName())
                        .addMember("required", "$L", param.isRequired())
                        .build())
                    .build()
            );
        }
        
        // 添加RequestBody参数到方法签名
        if (parameterInfo.getRequestBody() != null && !parameterInfo.getRequestBody().getProperties().isEmpty()) {
            String requestClassName = generateClassName(endpoint.getName() + "Request");
            methodBuilder.addParameter(
                ParameterSpec.builder(ClassName.get("com.dbapp." + sanitizeProjectName(endpoint.getMockService().getName()).toLowerCase() + ".dto", requestClassName), "request")
                    .addAnnotation(ClassName.get("org.springframework.web.bind.annotation", "RequestBody"))
                    .build()
            );
        }
        
        // 添加HTTP方法注解
        String httpMethod = endpoint.getMethod().name().toLowerCase();
        methodBuilder.addAnnotation(AnnotationSpec.builder(
            ClassName.get("org.springframework.web.bind.annotation", httpMethod.substring(0, 1).toUpperCase() + httpMethod.substring(1) + "Mapping"))
            .addMember("value", "$S", path)
            .build());
        
        // 添加日志打印请求参数
        methodBuilder.addStatement("$T logger = $T.getLogger($T.class)", 
            ClassName.get("org.slf4j", "Logger"),
            ClassName.get("org.slf4j", "LoggerFactory"),
            ClassName.get("java.lang", "Class"));
        
        // 生成详细的参数日志
        generateDetailedParameterLog(methodBuilder, endpoint, parameterInfo);
        
        // 添加响应延迟
        if (endpoint.getResponseDelay() != null && endpoint.getResponseDelay() > 0) {
            methodBuilder.addStatement("try { $T.sleep($L); } catch ($T e) { }", 
                Thread.class, endpoint.getResponseDelay(), InterruptedException.class);
        }
        
        // 添加Mock响应
        if (endpoint.getMockResponse() != null && !endpoint.getMockResponse().isEmpty()) {
            CodeBlock tryBlock = CodeBlock.builder()
                .add("try {\n")
                .add("  return new $T().readValue($S, $T.class);\n", 
                    ClassName.get("com.fasterxml.jackson.databind", "ObjectMapper"),
                    endpoint.getMockResponse(),
                    ClassName.get("java.lang", "Object"))
                .add("} catch ($T e) {\n", ClassName.get("java.lang", "Exception"))
                .add("  return $T.of($S, $S);\n", 
                    ClassName.get("java.util", "Map"),
                    "error", "Failed to parse mock response")
                .add("}")
                .build();
            methodBuilder.addCode(tryBlock);
        } else {
            methodBuilder.addStatement("return $T.of($S, $S)", 
                ClassName.get("java.util", "Map"),
                "message", "Mock response for " + endpoint.getName());
        }
        
        return methodBuilder.build();
    }
    
    /**
     * 标准化路径，直接使用解析到的路径
     */
    public String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }
        
        // 直接使用解析到的路径，确保以/开头
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        
        return normalized;
    }
    
    /**
     * 提取路径中的PathVariable参数
     */
    public List<String> extractPathVariables(String path) {
        List<String> pathVariables = new ArrayList<>();
        
        if (path == null || path.trim().isEmpty()) {
            return pathVariables;
        }
        
        // 使用正则表达式匹配 {param} 格式的参数
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(path);
        
        while (matcher.find()) {
            String paramName = matcher.group(1);
            // 清理参数名，移除可能的特殊字符
            paramName = paramName.replaceAll("[^a-zA-Z0-9_]", "");
            if (!paramName.isEmpty()) {
                pathVariables.add(paramName);
            }
        }
        
        return pathVariables;
    }
    
    /**
     * 生成DTO类
     */
    private void generateDtoClasses(String projectPath, List<ApiEndpoint> endpoints) throws IOException {
        String packageName = "com.dbapp." + sanitizeProjectName(endpoints.get(0).getMockService().getName()).toLowerCase() + ".dto";
        
        for (ApiEndpoint endpoint : endpoints) {
            if (endpoint.getRequestSchema() != null && !endpoint.getRequestSchema().isEmpty()) {
                String requestClassName = generateClassName(endpoint.getName() + "Request");
                generateDtoClassFromAiSchema(projectPath, packageName, requestClassName, endpoint.getRequestSchema());
            }
            
            if (endpoint.getResponseSchema() != null && !endpoint.getResponseSchema().isEmpty()) {
                String responseClassName = generateClassName(endpoint.getName() + "Response");
                generateDtoClass(projectPath, packageName, responseClassName, endpoint.getResponseSchema());
            }
        }
    }
    
    /**
     * 生成单个DTO类
     */
    private void generateDtoClass(String projectPath, String packageName, String className, String schema) throws IOException {
        TypeSpec.Builder dtoClassBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonIgnoreProperties"))
                .addMember("ignoreUnknown", "$L", true)
                .build());
        
        // 解析JSON Schema并生成字段
        List<FieldSpec> fields = parseSchemaFields(schema);
        for (FieldSpec field : fields) {
            dtoClassBuilder.addField(field);
        }
        
        // 生成构造函数
        dtoClassBuilder.addMethod(generateConstructor(className, fields));
        
        // 生成getter和setter方法
        for (FieldSpec field : fields) {
            dtoClassBuilder.addMethod(generateGetter(field));
            dtoClassBuilder.addMethod(generateSetter(field));
        }
        
        JavaFile javaFile = JavaFile.builder(packageName, dtoClassBuilder.build()).build();
        javaFile.writeTo(Paths.get(projectPath, "src/main/java"));
    }
    
    /**
     * 从AI解析的Schema生成DTO类
     */
    private void generateDtoClassFromAiSchema(String projectPath, String packageName, String className, String schema) throws IOException {
        TypeSpec.Builder dtoClassBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonIgnoreProperties"))
                .addMember("ignoreUnknown", "$L", true)
                .build());
        
        // 解析AI生成的Schema并生成字段
        List<FieldSpec> fields = parseAiSchemaFields(schema);
        
        for (FieldSpec field : fields) {
            dtoClassBuilder.addField(field);
        }
        
        // 生成构造函数
        dtoClassBuilder.addMethod(generateConstructor(className, fields));
        
        // 生成getter和setter方法
        for (FieldSpec field : fields) {
            dtoClassBuilder.addMethod(generateGetter(field));
            dtoClassBuilder.addMethod(generateSetter(field));
        }
        
        JavaFile javaFile = JavaFile.builder(packageName, dtoClassBuilder.build()).build();
        javaFile.writeTo(Paths.get(projectPath, "src/main/java"));
    }
    
    /**
     * 解析JSON Schema并生成字段
     */
    private List<FieldSpec> parseSchemaFields(String schema) {
        List<FieldSpec> fields = new ArrayList<>();
        
        if (schema == null || schema.trim().isEmpty()) {
            return fields;
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(schema);
            
            if (jsonNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fieldsIterator = jsonNode.fields();
                while (fieldsIterator.hasNext()) {
                    Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
                    String fieldName = fieldEntry.getKey();
                    JsonNode fieldNode = fieldEntry.getValue();
                    
                    FieldSpec field = createFieldFromSchema(fieldName, fieldNode);
                    if (field != null) {
                        fields.add(field);
                    }
                }
            }
        } catch (Exception e) {
            // 如果JSON解析失败，尝试从表格数据格式解析
            fields.addAll(parseTableDataFields(schema));
        }
        
        return fields;
    }
    
    /**
     * 解析AI生成的Schema并生成字段
     */
    private List<FieldSpec> parseAiSchemaFields(String schema) {
        List<FieldSpec> fields = new ArrayList<>();
        
        if (schema == null || schema.trim().isEmpty()) {
            return fields;
        }
        
        try {
            // 预处理Schema，处理中文字符问题
            String processedSchema = schema
                .replace("、", ",")  // 中文顿号替换为英文逗号
                .replace("，", ",")  // 中文逗号替换为英文逗号
                .replace("：", ":")  // 中文冒号替换为英文冒号
                .replace("\"", "\"")  // 中文引号替换为英文引号
                .replace("\"", "\"")  // 中文引号替换为英文引号
                .replace("'", "'")   // 中文单引号替换为英文单引号
                .replace("'", "'")   // 中文单引号替换为英文单引号
                .replaceAll("\\s+", " ")  // 规范化空白字符
                .trim();  // 去除首尾空白
            
            // 尝试修复常见的JSON格式问题
            processedSchema = processedSchema
                .replaceAll(",\\s*}", "}")  // 移除对象末尾多余的逗号
                .replaceAll(",\\s*]", "]"); // 移除数组末尾多余的逗号
            
            // 如果预处理后仍然无法解析，尝试更激进的修复
            if (!isValidJson(processedSchema)) {
                processedSchema = fixJsonFormat(processedSchema);
            }
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(processedSchema);
            
            // 处理嵌套的requestBody结构
            if (jsonNode.has("requestBody")) {
                JsonNode requestBody = jsonNode.get("requestBody");
                
                // 检查requestBody的类型
                String requestBodyType = requestBody.has("type") ? requestBody.get("type").asText() : "object";
                
                if ("array".equals(requestBodyType) && requestBody.has("items")) {
                    // 处理array格式：requestBody.items.properties
                    JsonNode items = requestBody.get("items");
                    
                    if (items.has("properties")) {
                        JsonNode properties = items.get("properties");
                        Iterator<Map.Entry<String, JsonNode>> fieldsIterator = properties.fields();
                        
                        while (fieldsIterator.hasNext()) {
                            Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
                            String fieldName = fieldEntry.getKey();
                            JsonNode fieldNode = fieldEntry.getValue();
                            
                            FieldSpec field = createFieldFromAiSchema(fieldName, fieldNode);
                            if (field != null) {
                                fields.add(field);
                            }
                        }
                    }
                } else if ("object".equals(requestBodyType) && requestBody.has("properties")) {
                    // 处理object格式：requestBody.properties
                    JsonNode properties = requestBody.get("properties");
                    Iterator<Map.Entry<String, JsonNode>> fieldsIterator = properties.fields();
                    
                    while (fieldsIterator.hasNext()) {
                        Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
                        String fieldName = fieldEntry.getKey();
                        JsonNode fieldNode = fieldEntry.getValue();
                        
                        FieldSpec field = createFieldFromAiSchema(fieldName, fieldNode);
                        if (field != null) {
                            fields.add(field);
                        }
                    }
                } else {
                    // 兼容旧格式：直接检查是否有items.properties结构
                    if (requestBody.has("items")) {
                        JsonNode items = requestBody.get("items");
                        
                        if (items.has("properties")) {
                            JsonNode properties = items.get("properties");
                            Iterator<Map.Entry<String, JsonNode>> fieldsIterator = properties.fields();
                            
                            while (fieldsIterator.hasNext()) {
                                Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
                                String fieldName = fieldEntry.getKey();
                                JsonNode fieldNode = fieldEntry.getValue();
                                
                                FieldSpec field = createFieldFromAiSchema(fieldName, fieldNode);
                                if (field != null) {
                                    fields.add(field);
                                }
                            }
                        }
                    }
                }
            } else if (jsonNode.isObject()) {
                // 处理直接的字段结构
                Iterator<Map.Entry<String, JsonNode>> fieldsIterator = jsonNode.fields();
                while (fieldsIterator.hasNext()) {
                    Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
                    String fieldName = fieldEntry.getKey();
                    JsonNode fieldNode = fieldEntry.getValue();
                    
                    FieldSpec field = createFieldFromAiSchema(fieldName, fieldNode);
                    if (field != null) {
                        fields.add(field);
                    }
                }
            }
            
        } catch (Exception e) {
            // 如果解析失败，记录错误并返回空列表
            System.err.println("解析AI Schema失败: " + e.getMessage());
            System.err.println("原始Schema内容: " + schema);
            e.printStackTrace();
        }
        
        return fields;
    }
    
    /**
     * 检查JSON是否有效
     */
    private boolean isValidJson(String json) {
        try {
            new ObjectMapper().readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 修复JSON格式
     */
    private String fixJsonFormat(String json) {
        // 移除所有换行符和多余的空格
        String fixed = json.replaceAll("\\s+", " ");
        
        // 修复常见的格式问题
        fixed = fixed
            .replaceAll(",\\s*}", "}")
            .replaceAll(",\\s*]", "]")
            .replaceAll("\\{\\s*\\}", "{}")
            .replaceAll("\\[\\s*\\]", "[]");
        
        return fixed;
    }
    
    /**
     * 从表格数据格式解析字段
     */
    private List<FieldSpec> parseTableDataFields(String schema) {
        List<FieldSpec> fields = new ArrayList<>();
        
        if (schema == null || !schema.contains("===")) {
            return fields;
        }
        
        try {
            // 提取表格数据部分
            String tableData = extractTableData(schema);
            if (tableData == null) {
                return fields;
            }
            
            // 解析表格行
            String[] lines = tableData.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("编号") || line.startsWith("---")) {
                    continue;
                }
                
                // 解析表格行：编号 | 字段名称 | 字段编码 | 字段类型 | 是否必输 | 说明
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String fieldCode = parts[2].trim();
                    String fieldType = parts[3].trim();
                    String required = parts.length > 4 ? parts[4].trim() : "N";
                    
                    if (!fieldCode.isEmpty() && !fieldCode.equals("字段编码")) {
                        FieldSpec field = createFieldFromTableData(fieldCode, fieldType, required);
                        if (field != null) {
                            fields.add(field);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败时返回空列表
        }
        
        return fields;
    }
    
    /**
     * 提取表格数据部分
     */
    private String extractTableData(String schema) {
        int startIndex = schema.indexOf("=== 表格数据 ===");
        if (startIndex == -1) {
            return null;
        }
        
        int endIndex = schema.indexOf("=== 表格结束 ===");
        if (endIndex == -1) {
            endIndex = schema.length();
        }
        
        return schema.substring(startIndex + "=== 表格数据 ===".length(), endIndex).trim();
    }
    
    /**
     * 从JSON Schema创建字段
     */
    private FieldSpec createFieldFromSchema(String fieldName, JsonNode fieldNode) {
        // 清理字段名，确保符合Java标识符规范
        String cleanFieldName = generateVariableName(fieldName);
        
        // 确定字段类型
        TypeName fieldType = determineFieldType(fieldNode);
        
        // 创建字段
        FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, cleanFieldName, Modifier.PRIVATE);
        
        // 添加Jackson注解
        fieldBuilder.addAnnotation(AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonProperty"))
            .addMember("value", "$S", fieldName)
            .build());
        
        // 添加验证注解（如果字段是必需的）
        if (fieldNode.has("required") && fieldNode.get("required").asBoolean()) {
            fieldBuilder.addAnnotation(ClassName.get("jakarta.validation.constraints", "NotNull"));
        }
        
        return fieldBuilder.build();
    }
    
    /**
     * 从AI Schema创建字段
     */
    private FieldSpec createFieldFromAiSchema(String fieldName, JsonNode fieldNode) {
        // 清理字段名，确保符合Java标识符规范
        String cleanFieldName = generateVariableName(fieldName);
        
        // 确定字段类型
        TypeName fieldType = determineFieldTypeFromAiSchema(fieldNode);
        
        // 创建字段
        FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, cleanFieldName, Modifier.PRIVATE);
        
        // 添加Jackson注解
        fieldBuilder.addAnnotation(AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonProperty"))
            .addMember("value", "$S", fieldName)
            .build());
        
        // 添加验证注解（如果字段是必需的）
        if (fieldNode.has("required") && fieldNode.get("required").asBoolean()) {
            fieldBuilder.addAnnotation(ClassName.get("jakarta.validation.constraints", "NotNull"));
        }
        
        return fieldBuilder.build();
    }
    
    /**
     * 从表格数据创建字段
     */
    private FieldSpec createFieldFromTableData(String fieldCode, String fieldType, String required) {
        // 清理字段名，确保符合Java标识符规范
        String cleanFieldName = generateVariableName(fieldCode);
        
        // 确定字段类型
        TypeName javaType = mapTableTypeToJavaType(fieldType);
        
        // 创建字段
        FieldSpec.Builder fieldBuilder = FieldSpec.builder(javaType, cleanFieldName, Modifier.PRIVATE);
        
        // 添加Jackson注解
        fieldBuilder.addAnnotation(AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonProperty"))
            .addMember("value", "$S", fieldCode)
            .build());
        
        // 添加验证注解（如果字段是必需的）
        if ("Y".equalsIgnoreCase(required)) {
            fieldBuilder.addAnnotation(ClassName.get("jakarta.validation.constraints", "NotNull"));
        }
        
        return fieldBuilder.build();
    }
    
    /**
     * 确定字段类型
     */
    private TypeName determineFieldType(JsonNode fieldNode) {
        String type = fieldNode.has("type") ? fieldNode.get("type").asText() : "string";
        
        switch (type.toLowerCase()) {
            case "integer":
            case "int":
                return TypeName.INT;
            case "long":
                return TypeName.LONG;
            case "double":
            case "float":
                return TypeName.DOUBLE;
            case "boolean":
            case "bool":
                return TypeName.BOOLEAN;
            case "array":
                // 处理数组类型
                if (fieldNode.has("items")) {
                    TypeName itemType = determineFieldType(fieldNode.get("items"));
                    return ParameterizedTypeName.get(ClassName.get(List.class), itemType);
                }
                return ParameterizedTypeName.get(ClassName.get(List.class), TypeName.OBJECT);
            case "object":
                return TypeName.OBJECT;
            case "string":
            default:
                return ClassName.get(String.class);
        }
    }
    
    /**
     * 从AI Schema确定字段类型
     */
    private TypeName determineFieldTypeFromAiSchema(JsonNode fieldNode) {
        String type = fieldNode.has("type") ? fieldNode.get("type").asText() : "string";
        
        switch (type.toLowerCase()) {
            case "integer":
            case "int":
                return TypeName.INT;
            case "long":
                return TypeName.LONG;
            case "double":
            case "float":
                return TypeName.DOUBLE;
            case "boolean":
            case "bool":
                return TypeName.BOOLEAN;
            case "array":
                // 处理数组类型
                if (fieldNode.has("items")) {
                    TypeName itemType = determineFieldTypeFromAiSchema(fieldNode.get("items"));
                    return ParameterizedTypeName.get(ClassName.get(List.class), itemType);
                }
                return ParameterizedTypeName.get(ClassName.get(List.class), TypeName.OBJECT);
            case "object":
                return TypeName.OBJECT;
            case "string":
            default:
                return ClassName.get(String.class);
        }
    }
    
    /**
     * 将表格类型映射到Java类型
     */
    private TypeName mapTableTypeToJavaType(String tableType) {
        if (tableType == null) {
            return ClassName.get(String.class);
        }
        
        String type = tableType.toUpperCase();
        
        if (type.contains("VARCHAR") || type.contains("CHAR") || type.contains("TEXT")) {
            return ClassName.get(String.class);
        } else if (type.contains("INT") || type.contains("NUMBER")) {
            return TypeName.INT;
        } else if (type.contains("LONG") || type.contains("BIGINT")) {
            return TypeName.LONG;
        } else if (type.contains("DOUBLE") || type.contains("FLOAT") || type.contains("DECIMAL")) {
            return TypeName.DOUBLE;
        } else if (type.contains("BOOLEAN") || type.contains("BOOL")) {
            return TypeName.BOOLEAN;
        } else if (type.contains("DATE") || type.contains("TIMESTAMP")) {
            return ClassName.get(String.class); // 使用String表示日期
        } else {
            return ClassName.get(String.class); // 默认使用String
        }
    }
    
    /**
     * 生成构造函数
     */
    private MethodSpec generateConstructor(String className, List<FieldSpec> fields) {
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        
        // 添加参数
        for (FieldSpec field : fields) {
            constructorBuilder.addParameter(field.type, field.name);
        }
        
        // 添加字段赋值
        for (FieldSpec field : fields) {
            constructorBuilder.addStatement("this.$L = $L", field.name, field.name);
        }
        
        return constructorBuilder.build();
    }
    
    /**
     * 生成getter方法
     */
    private MethodSpec generateGetter(FieldSpec field) {
        String getterName = "get" + field.name.substring(0, 1).toUpperCase() + field.name.substring(1);
        
        return MethodSpec.methodBuilder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(field.type)
            .addStatement("return this.$L", field.name)
            .build();
    }
    
    /**
     * 生成setter方法
     */
    private MethodSpec generateSetter(FieldSpec field) {
        String setterName = "set" + field.name.substring(0, 1).toUpperCase() + field.name.substring(1);
        
        return MethodSpec.methodBuilder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(void.class)
            .addParameter(field.type, field.name)
            .addStatement("this.$L = $L", field.name, field.name)
            .build();
    }
    
    /**
     * 生成Java变量名，排除特殊符号
     */
    public String generateVariableName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "defaultVariable";
        }
        
        // 处理常见的命名模式
        String processed = name;
        
        // 处理连字符分隔的命名（如 user-name -> userName）
        if (processed.contains("-")) {
            String[] parts = processed.split("-");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (i == 0) {
                    result.append(part.toLowerCase());
                } else {
                    result.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
                }
            }
            processed = result.toString();
        }
        
        // 处理下划线分隔的命名（如 user_id -> userId）
        if (processed.contains("_")) {
            String[] parts = processed.split("_");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (i == 0) {
                    result.append(part.toLowerCase());
                } else {
                    result.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
                }
            }
            processed = result.toString();
        }
        
        // 移除特殊符号，只保留字母、数字和下划线
        String cleaned = processed.replaceAll("[^a-zA-Z0-9_]", "");
        
        // 如果清理后为空，使用默认名称
        if (cleaned.trim().isEmpty()) {
            return "defaultVariable";
        }
        
        // 确保首字母小写（Java变量名规范）
        String result = cleaned.substring(0, 1).toLowerCase() + cleaned.substring(1);
        
        // 如果以数字开头，添加前缀
        if (Character.isDigit(result.charAt(0))) {
            result = "var" + result;
        }
        
        // 限制长度，避免过长
        if (result.length() > 50) {
            result = result.substring(0, 50);
        }
        
        return result;
    }
    
    /**
     * 生成application.properties文件
     */
    private void generateApplicationProperties(String projectPath, MockService mockService) throws IOException {
        String propertiesContent = String.format("""
            # 服务器配置
            server.port=%d
            
            # 应用名称
            spring.application.name=%s
            
            # 日志配置
            logging.level.com.dbapp=DEBUG
            logging.pattern.console=%%d{yyyy-MM-dd HH:mm:ss.SSS} [%%thread] %%-5level %%logger{36} - %%msg%%n
            logging.charset.console=UTF-8
            logging.charset.file=UTF-8
            
            # Jackson配置
            spring.jackson.default-property-inclusion=non_null
            spring.jackson.encoding=UTF-8
            
            # 文件编码配置
            spring.http.encoding.charset=UTF-8
            spring.http.encoding.enabled=true
            spring.http.encoding.force=true
            """, 
            mockService.getPort(),
            mockService.getName()
        );
        
        Files.write(Paths.get(projectPath, "src/main/resources/application.properties"), 
            propertiesContent.getBytes());
    }
    
    /**
     * 生成README文件
     */
    private void generateReadme(String projectPath, MockService mockService) throws IOException {
        String readmeContent = String.format("""
            # %s
            
            %s
            
            ## 启动服务
            
            ```bash
            mvn spring-boot:run
            ```
            
            ## 访问地址
            
            - 服务地址: http://localhost:%d
            - API文档: http://localhost:%d/api
            
            ## 生成时间
            
            %s
            """, 
            mockService.getName(),
            mockService.getDescription() != null ? mockService.getDescription() : "自动生成的Mock Server",
            mockService.getPort(),
            mockService.getPort(),
            java.time.LocalDateTime.now()
        );
        
        Files.write(Paths.get(projectPath, "README.md"), readmeContent.getBytes());
    }
    
    /**
     * 清理项目名称，确保符合Java包名规范
     */
    public String sanitizeProjectName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "example";
        }
        
        // 移除特殊符号，只保留字母、数字
        String cleaned = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        
        // 如果清理后为空，使用默认名称
        if (cleaned.trim().isEmpty()) {
            return "example";
        }
        
        // 确保不以数字开头
        if (Character.isDigit(cleaned.charAt(0))) {
            cleaned = "pkg" + cleaned;
        }
        
        return cleaned;
    }

    /**
     * 生成Java类名，确保符合Java命名规范
     */
    public String generateClassName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "DefaultClass";
        }
        
        // 移除特殊符号，只保留字母、数字和下划线
        String cleaned = name.replaceAll("[^a-zA-Z0-9_]", "");
        
        // 如果清理后为空，使用默认名称
        if (cleaned.trim().isEmpty()) {
            return "DefaultClass";
        }
        
        // 确保首字母大写（Java类名规范）
        String result = cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);
        
        // 如果以数字开头，添加前缀
        if (Character.isDigit(result.charAt(0))) {
            result = "Class" + result;
        }
        
        // 限制长度，避免过长
        if (result.length() > 50) {
            result = result.substring(0, 50);
        }
        
        return result;
    }

    /**
     * 生成唯一的Java方法名，确保不重复
     */
    public String generateUniqueMethodName(String name, Set<String> usedMethodNames) {
        String baseMethodName = generateMethodName(name);
        String uniqueMethodName = baseMethodName;
        int counter = 1;
        
        // 如果方法名已存在，添加数字后缀直到找到唯一的方法名
        while (usedMethodNames.contains(uniqueMethodName)) {
            uniqueMethodName = baseMethodName + counter;
            counter++;
        }
        
        // 将使用的方法名添加到集合中
        usedMethodNames.add(uniqueMethodName);
        return uniqueMethodName;
    }
    
    /**
     * 生成Java方法名，排除特殊符号
     */
    public String generateMethodName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "defaultMethod";
        }

        // 处理常见的命名模式
        String processed = name;

        // 处理连字符分隔的命名（如 get-user-list -> getUserList）
        if (processed.contains("-")) {
            String[] parts = processed.split("-");
            StringBuilder result = new StringBuilder();
            int startIndex = 0;
            if(parts.length >= 2){
                startIndex = parts.length - 2 ;
            }
            for (int i = startIndex; i < parts.length; i++) {

                String part = parts[i];
                if (i == startIndex) {
                    result.append(part.toLowerCase());
                } else {
                    result.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
                }
            }
            processed = result.toString();
        }

        // 处理连字符分隔的命名（如 get-user-list -> getUserList）
        if (processed.contains("-")) {
            processed = convertToCamelCase(processed, "-");
        }

        // 处理路径分隔符（如 /user/list -> userList）
        if (processed.contains("/")) {
            processed = convertToCamelCase(processed, "/");
        }

        // 移除特殊符号，只保留字母、数字和下划线
        String cleaned = processed.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "");

        // 如果清理后为空，使用默认名称
        if (cleaned.trim().isEmpty()) {
            return "defaultMethod";
        }

        // 确保首字母小写（Java方法名规范）
        String result = cleaned.substring(0, 1).toLowerCase() + cleaned.substring(1);

        // 如果以数字开头，添加前缀
        if (Character.isDigit(result.charAt(0))) {
            result = "method" + result;
        }

        // 限制长度，避免过长
        if (result.length() > 50) {
            result = result.substring(0, 50);
        }

        return result;
    }

    /**
     * 将分隔符分隔的字符串转换为驼峰命名法
     * @param input 输入字符串
     * @param delimiter 分隔符
     * @return 驼峰命名法字符串
     */
    private String convertToCamelCase(String input, String delimiter) {
        String[] parts = input.split(java.util.regex.Pattern.quote(delimiter));
        StringBuilder result = new StringBuilder();

        // 只取最后两个部分，避免方法名过长
        int startIndex = Math.max(0, parts.length - 2);

        for (int i = startIndex; i < parts.length; i++) {
            String part = parts[i].trim();
            if (!part.isEmpty()) {
                if (result.length() == 0) {
                    result.append(part.toLowerCase());
                } else {
                    result.append(Character.toUpperCase(part.charAt(0)))
                            .append(part.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }
    
    /**
     * 解析查询参数
     */
    public List<String> parseQueryParameters(String requestSchema) {
        List<String> params = new ArrayList<>();
        
        if (requestSchema == null || requestSchema.trim().isEmpty()) {
            return params;
        }
        
        try {
            // 尝试解析JSON格式的请求参数
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(requestSchema);
            
            if (jsonNode.isObject()) {
                Iterator<String> fieldNames = jsonNode.fieldNames();
                while (fieldNames.hasNext()) {
                    String fieldName = fieldNames.next();
                    // 清理参数名，确保是有效的Java标识符
                    String cleanParamName = fieldName.replaceAll("[^a-zA-Z0-9_]", "");
                    if (!cleanParamName.isEmpty()) {
                        params.add(cleanParamName);
                    }
                }
            }
        } catch (Exception e) {
            // 如果JSON解析失败，尝试从文本中提取参数
            // 简单的参数提取逻辑
            String[] lines = requestSchema.split("\n");
            for (String line : lines) {
                if (line.contains(":") || line.contains("=")) {
                    String[] parts = line.split("[:=]");
                    if (parts.length > 0) {
                        String paramName = parts[0].trim().replaceAll("[^a-zA-Z0-9_]", "");
                        if (!paramName.isEmpty()) {
                            params.add(paramName);
                        }
                    }
                }
            }
        }
        
        return params;
    }
    
    /**
     * 生成参数日志字符串
     */
    private String generateParameterLogString(ApiEndpoint endpoint, List<String> pathVariables) {
        StringBuilder logString = new StringBuilder();
        
        // 添加路径变量
        if (!pathVariables.isEmpty()) {
            logString.append("pathVariables={");
            for (int i = 0; i < pathVariables.size(); i++) {
                if (i > 0) logString.append(", ");
                logString.append(pathVariables.get(i)).append(":").append("$").append(pathVariables.get(i));
            }
            logString.append("}");
        }
        
        // 添加请求体参数
        String httpMethod = endpoint.getMethod().name().toLowerCase();
        if ("post".equals(httpMethod) || "put".equals(httpMethod) || "patch".equals(httpMethod)) {
            if (logString.length() > 0) logString.append(", ");
            logString.append("requestBody=$request");
        }
        
        // 添加查询参数
        if ("get".equals(httpMethod) && endpoint.getRequestSchema() != null && !endpoint.getRequestSchema().isEmpty()) {
            List<String> queryParams = parseQueryParameters(endpoint.getRequestSchema());
            if (!queryParams.isEmpty()) {
                if (logString.length() > 0) logString.append(", ");
                logString.append("queryParams={");
                for (int i = 0; i < queryParams.size(); i++) {
                    if (i > 0) logString.append(", ");
                    logString.append(queryParams.get(i)).append(":").append("$").append(queryParams.get(i));
                }
                logString.append("}");
            }
        }
        
        return logString.toString();
    }
    
    /**
     * 生成详细的参数日志
     */
    private void generateDetailedParameterLog(MethodSpec.Builder methodBuilder, ApiEndpoint endpoint, ParameterInfo parameterInfo) {
        String httpMethod = endpoint.getMethod().name().toLowerCase();
        
        // 基础日志信息 - 使用英文避免编码问题
        methodBuilder.addStatement("logger.info($S)", 
            "Received request - Method: " + httpMethod.toUpperCase() + 
            ", Path: " + endpoint.getPath());
        
        // 打印路径变量
        if (!parameterInfo.getPathVariables().isEmpty()) {
            for (ParameterInfo.Parameter param : parameterInfo.getPathVariables()) {
                methodBuilder.addStatement("logger.info($S + $L)", 
                    "Path variable " + param.getName() + ": ", param.getName());
            }
        }
        
        // 打印查询参数
        if (!parameterInfo.getQueryParameters().isEmpty()) {
            for (ParameterInfo.Parameter param : parameterInfo.getQueryParameters()) {
                methodBuilder.addStatement("logger.info($S + $L)", 
                    "Query parameter " + param.getName() + ": ", param.getName());
            }
        }
        
        // 打印请求体参数
        if (parameterInfo.getRequestBody() != null && !parameterInfo.getRequestBody().getProperties().isEmpty()) {
            methodBuilder.addStatement("logger.info($S + $L)", "Request body: ", "request");
            
            // 使用try-catch包装JSON序列化，避免编译错误
            CodeBlock jsonLogBlock = CodeBlock.builder()
                .add("try {\n")
                .add("  logger.info($S + new $T().writeValueAsString($L));\n", 
                    "Request body JSON: ", 
                    ClassName.get("com.fasterxml.jackson.databind", "ObjectMapper"),
                    "request")
                .add("} catch ($T e) {\n", ClassName.get("com.fasterxml.jackson.core", "JsonProcessingException"))
                .add("  logger.warn($S + e.getMessage());\n", "Failed to serialize request body to JSON: ")
                .add("}")
                .build();
            methodBuilder.addCode(jsonLogBlock);
        }
    }
    
    /**
     * 解析参数信息
     */
    private ParameterInfo parseParameters(ApiEndpoint endpoint) {
        ParameterInfo parameterInfo = new ParameterInfo();
        parameterInfo.setPathVariables(new ArrayList<>());
        parameterInfo.setQueryParameters(new ArrayList<>());
        
        // 如果endpoint有parameters字段，优先使用
        if (endpoint.getParameters() != null && !endpoint.getParameters().isEmpty()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(endpoint.getParameters());
                
                // 解析pathVariables
                if (rootNode.has("pathVariables") && rootNode.get("pathVariables").isArray()) {
                    JsonNode pathVarsNode = rootNode.get("pathVariables");
                    for (JsonNode paramNode : pathVarsNode) {
                        ParameterInfo.Parameter param = new ParameterInfo.Parameter(
                            paramNode.get("name").asText(),
                            paramNode.get("type").asText(),
                            paramNode.get("required").asBoolean(),
                            paramNode.has("description") ? paramNode.get("description").asText() : ""
                        );
                        parameterInfo.getPathVariables().add(param);
                    }
                }
                
                // 解析queryParameters
                if (rootNode.has("queryParameters") && rootNode.get("queryParameters").isArray()) {
                    JsonNode queryParamsNode = rootNode.get("queryParameters");
                    for (JsonNode paramNode : queryParamsNode) {
                        ParameterInfo.Parameter param = new ParameterInfo.Parameter(
                            paramNode.get("name").asText(),
                            paramNode.get("type").asText(),
                            paramNode.get("required").asBoolean(),
                            paramNode.has("description") ? paramNode.get("description").asText() : ""
                        );
                        parameterInfo.getQueryParameters().add(param);
                    }
                }
                
                // 解析requestBody
                if (rootNode.has("requestBody") && rootNode.get("requestBody").isObject()) {
                    JsonNode requestBodyNode = rootNode.get("requestBody");
                    if (requestBodyNode.has("properties") && requestBodyNode.get("properties").isObject()) {
                        Map<String, ParameterInfo.Property> properties = new HashMap<>();
                        JsonNode propertiesNode = requestBodyNode.get("properties");
                        Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                        
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> field = fields.next();
                            String fieldName = field.getKey();
                            JsonNode fieldNode = field.getValue();
                            
                            ParameterInfo.Property property = new ParameterInfo.Property(
                                fieldNode.get("type").asText(),
                                fieldNode.get("required").asBoolean(),
                                fieldNode.has("description") ? fieldNode.get("description").asText() : ""
                            );
                            properties.put(fieldName, property);
                        }
                        
                        ParameterInfo.RequestBody requestBody = new ParameterInfo.RequestBody(
                            requestBodyNode.get("type").asText(),
                            properties
                        );
                        parameterInfo.setRequestBody(requestBody);
                    }
                }
                
                return parameterInfo;
            } catch (Exception e) {
                // 如果解析失败，回退到旧的方式
                System.err.println("解析参数信息失败，使用默认方式: " + e.getMessage());
            }
        }
        
        // 回退到旧的方式：从路径中提取pathVariables，从requestSchema中提取其他参数
        String path = endpoint.getPath();
        List<String> pathVars = extractPathVariables(path);
        for (String pathVar : pathVars) {
            ParameterInfo.Parameter param = new ParameterInfo.Parameter(pathVar, "string", true, "");
            parameterInfo.getPathVariables().add(param);
        }
        
        // 根据HTTP方法处理其他参数
        String httpMethod = endpoint.getMethod().name().toLowerCase();
        if ("get".equals(httpMethod) && endpoint.getRequestSchema() != null && !endpoint.getRequestSchema().isEmpty()) {
            List<String> queryParams = parseQueryParameters(endpoint.getRequestSchema());
            for (String param : queryParams) {
                ParameterInfo.Parameter queryParam = new ParameterInfo.Parameter(param, "string", false, "");
                parameterInfo.getQueryParameters().add(queryParam);
            }
        } else if (("post".equals(httpMethod) || "put".equals(httpMethod) || "patch".equals(httpMethod)) 
                   && endpoint.getRequestSchema() != null && !endpoint.getRequestSchema().isEmpty()) {
            // 创建requestBody
            Map<String, ParameterInfo.Property> properties = new HashMap<>();
            List<FieldSpec> fields = parseSchemaFields(endpoint.getRequestSchema());
            for (FieldSpec field : fields) {
                ParameterInfo.Property property = new ParameterInfo.Property(
                    getFieldTypeString(field.type),
                    field.annotations.stream().anyMatch(ann -> ann.type.toString().contains("NotNull")),
                    ""
                );
                properties.put(field.name, property);
            }
            
            if (!properties.isEmpty()) {
                ParameterInfo.RequestBody requestBody = new ParameterInfo.RequestBody("object", properties);
                parameterInfo.setRequestBody(requestBody);
            }
        }
        
        return parameterInfo;
    }
    
    /**
     * 获取参数类型对应的ClassName
     */
    private ClassName getParameterType(String type) {
        switch (type.toLowerCase()) {
            case "integer":
            case "int":
                return ClassName.get(Integer.class);
            case "long":
                return ClassName.get(Long.class);
            case "double":
                return ClassName.get(Double.class);
            case "float":
                return ClassName.get(Float.class);
            case "boolean":
                return ClassName.get(Boolean.class);
            case "string":
            default:
                return ClassName.get(String.class);
        }
    }
    
    /**
     * 获取字段类型字符串
     */
    private String getFieldTypeString(TypeName typeName) {
        String typeString = typeName.toString();
        if (typeString.contains("String")) return "string";
        if (typeString.contains("Integer") || typeString.contains("int")) return "integer";
        if (typeString.contains("Long") || typeString.contains("long")) return "long";
        if (typeString.contains("Double") || typeString.contains("double")) return "double";
        if (typeString.contains("Float") || typeString.contains("float")) return "float";
        if (typeString.contains("Boolean") || typeString.contains("boolean")) return "boolean";
        return "string";
    }
} 