package com.dbapp.dasmockserver.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "api_endpoints")
public class ApiEndpoint {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    private String path;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private HttpMethod method;
    
    @NotBlank
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(columnDefinition = "TEXT")
    private String requestSchema;
    
    @Column(columnDefinition = "TEXT")
    private String responseSchema;
    
    @Column(columnDefinition = "TEXT")
    private String mockResponse;
    
    private Integer responseDelay;
    
    private Integer statusCode;
    
    @ElementCollection
    @CollectionTable(name = "api_endpoint_headers", 
        joinColumns = @JoinColumn(name = "endpoint_id"))
    @MapKeyColumn(name = "header_name")
    @Column(name = "header_value")
    private Map<String, String> headers;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mock_service_id")
    private MockService mockService;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public HttpMethod getMethod() {
        return method;
    }
    
    public void setMethod(HttpMethod method) {
        this.method = method;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getRequestSchema() {
        return requestSchema;
    }
    
    public void setRequestSchema(String requestSchema) {
        this.requestSchema = requestSchema;
    }
    
    public String getResponseSchema() {
        return responseSchema;
    }
    
    public void setResponseSchema(String responseSchema) {
        this.responseSchema = responseSchema;
    }
    
    public String getMockResponse() {
        return mockResponse;
    }
    
    public void setMockResponse(String mockResponse) {
        this.mockResponse = mockResponse;
    }
    
    public Integer getResponseDelay() {
        return responseDelay;
    }
    
    public void setResponseDelay(Integer responseDelay) {
        this.responseDelay = responseDelay;
    }
    
    public Integer getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
    
    public MockService getMockService() {
        return mockService;
    }
    
    public void setMockService(MockService mockService) {
        this.mockService = mockService;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
    }
} 