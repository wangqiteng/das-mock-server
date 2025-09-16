package com.dbapp.dasmockserver.model;

/**
 * 认证类型枚举
 */
public enum AuthType {
    /**
     * API Key认证
     */
    API_KEY("API Key认证"),
    
    /**
     * Bearer Token认证
     */
    BEARER_TOKEN("Bearer Token认证"),
    
    /**
     * Basic Auth认证
     */
    BASIC_AUTH("Basic Auth认证"),
    
    /**
     * OAuth2认证
     */
    OAUTH2("OAuth2认证"),
    
    /**
     * 无认证
     */
    NONE("无认证");
    
    private final String description;
    
    AuthType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据字符串解析认证类型
     */
    public static AuthType fromString(String authType) {
        if (authType == null || authType.trim().isEmpty()) {
            return NONE;
        }
        
        String lowerType = authType.toLowerCase().trim();
        
        if (lowerType.contains("api") && lowerType.contains("key")) {
            return API_KEY;
        } else if (lowerType.contains("bearer") || lowerType.contains("token")) {
            return BEARER_TOKEN;
        } else if (lowerType.contains("basic")) {
            return BASIC_AUTH;
        } else if (lowerType.contains("oauth") || lowerType.contains("oauth2")) {
            return OAUTH2;
        } else {
            return NONE;
        }
    }
}
