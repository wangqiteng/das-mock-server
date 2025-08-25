package com.dbapp.dasmockserver.service;

import com.dbapp.dasmockserver.model.ApiEndpoint;
import com.dbapp.dasmockserver.model.MockService;
import com.squareup.javapoet.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

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
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RequestMapping"))
                .addMember("value", "$S", "/api")
                .build())
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "CrossOrigin"))
                .addMember("origins", "$S", "*")
                .build());
        
        // 为每个端点生成方法
        for (ApiEndpoint endpoint : endpoints) {
            controllerBuilder.addMethod(generateEndpointMethod(endpoint));
        }
        
        JavaFile javaFile = JavaFile.builder(packageName, controllerBuilder.build()).build();
        javaFile.writeTo(Paths.get(projectPath, "src/main/java"));
    }
    
    /**
     * 生成单个端点方法
     */
    private MethodSpec generateEndpointMethod(ApiEndpoint endpoint) {
        String methodName = generateMethodName(endpoint.getPath(), endpoint.getMethod().name());
        
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .returns(Object.class);
        
        // 添加HTTP方法注解
        String httpMethod = endpoint.getMethod().name().toLowerCase();
        methodBuilder.addAnnotation(AnnotationSpec.builder(
            ClassName.get("org.springframework.web.bind.annotation", httpMethod.substring(0, 1).toUpperCase() + httpMethod.substring(1) + "Mapping"))
            .addMember("value", "$S", endpoint.getPath())
            .build());
        
        // 添加响应延迟
        if (endpoint.getResponseDelay() != null && endpoint.getResponseDelay() > 0) {
            methodBuilder.addStatement("try { $T.sleep($L); } catch ($T e) { }", 
                Thread.class, endpoint.getResponseDelay(), InterruptedException.class);
        }
        
        // 添加Mock响应
        if (endpoint.getMockResponse() != null && !endpoint.getMockResponse().isEmpty()) {
            methodBuilder.addStatement("return $T.parse($S)", 
                ClassName.get("com.fasterxml.jackson.databind", "ObjectMapper"),
                endpoint.getMockResponse());
        } else {
            methodBuilder.addStatement("return $T.of($S, $S)", 
                ClassName.get("java.util", "Map"),
                "message", "Mock response for " + endpoint.getName());
        }
        
        return methodBuilder.build();
    }
    
    /**
     * 生成DTO类
     */
    private void generateDtoClasses(String projectPath, List<ApiEndpoint> endpoints) throws IOException {
        String packageName = "com.dbapp." + sanitizeProjectName(endpoints.get(0).getMockService().getName()).toLowerCase() + ".dto";
        
        for (ApiEndpoint endpoint : endpoints) {
            if (endpoint.getRequestSchema() != null && !endpoint.getRequestSchema().isEmpty()) {
                generateDtoClass(projectPath, packageName, endpoint.getName() + "Request", endpoint.getRequestSchema());
            }
            if (endpoint.getResponseSchema() != null && !endpoint.getResponseSchema().isEmpty()) {
                generateDtoClass(projectPath, packageName, endpoint.getName() + "Response", endpoint.getResponseSchema());
            }
        }
    }
    
    /**
     * 生成单个DTO类
     */
    private void generateDtoClass(String projectPath, String packageName, String className, String schema) throws IOException {
        // 这里简化处理，实际应该解析JSON Schema
        TypeSpec dtoClass = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassName.get("com.fasterxml.jackson.annotation", "JsonIgnoreProperties"))
            .addAnnotation(AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonIgnoreProperties"))
                .addMember("ignoreUnknown", "$L", true)
                .build())
            .build();
        
        JavaFile javaFile = JavaFile.builder(packageName, dtoClass).build();
        javaFile.writeTo(Paths.get(projectPath, "src/main/java"));
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
            
            # Jackson配置
            spring.jackson.default-property-inclusion=non_null
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
     * 清理项目名称
     */
    private String sanitizeProjectName(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }
    
    /**
     * 生成方法名
     */
    private String generateMethodName(String path, String httpMethod) {
        String[] parts = path.split("/");
        StringBuilder methodName = new StringBuilder(httpMethod.toLowerCase());
        
        for (String part : parts) {
            if (!part.isEmpty()) {
                methodName.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
            }
        }
        
        return methodName.toString();
    }
} 