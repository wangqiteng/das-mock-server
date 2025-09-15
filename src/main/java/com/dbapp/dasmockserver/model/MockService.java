package com.dbapp.dasmockserver.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mock_services")
public class MockService {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(columnDefinition = "TEXT")
    private String tags;
    
    private String baseUrl;
    
    private Integer port;
    
    @Enumerated(EnumType.STRING)
    private ServiceStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String originalDocument;
    
    private String documentType;
    
    @Column(columnDefinition = "TEXT")
    private String projectPath;
    
    @OneToMany(mappedBy = "mockService", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ApiEndpoint> endpoints = new ArrayList<>();
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = ServiceStatus.CREATED;
        }
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
    
    public String getTags() {
        return tags;
    }
    
    public void setTags(String tags) {
        this.tags = tags;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public Integer getPort() {
        return port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
    }
    
    public ServiceStatus getStatus() {
        return status;
    }
    
    public void setStatus(ServiceStatus status) {
        this.status = status;
    }
    
    public String getOriginalDocument() {
        return originalDocument;
    }
    
    public void setOriginalDocument(String originalDocument) {
        this.originalDocument = originalDocument;
    }
    
    public String getDocumentType() {
        return documentType;
    }
    
    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }
    
    public String getProjectPath() {
        return projectPath;
    }
    
    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }
    
    public List<ApiEndpoint> getEndpoints() {
        return endpoints;
    }
    
    public void setEndpoints(List<ApiEndpoint> endpoints) {
        this.endpoints = endpoints;
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
    
    public enum ServiceStatus {
        CREATED, GENERATING, RUNNING, STOPPED, ERROR
    }
} 