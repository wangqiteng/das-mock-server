package com.dbapp.dasmockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 认证配置类
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthConfig {
    
    /**
     * 认证类型
     */
    @JsonProperty("type")
    private AuthType type;
    
    /**
     * 认证名称
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * 认证描述
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * API Key认证配置
     */
    @JsonProperty("apiKey")
    private ApiKeyConfig apiKey;
    
    /**
     * Bearer Token认证配置
     */
    @JsonProperty("bearerToken")
    private BearerTokenConfig bearerToken;
    
    /**
     * Basic Auth认证配置
     */
    @JsonProperty("basicAuth")
    private BasicAuthConfig basicAuth;
    
    /**
     * OAuth2认证配置
     */
    @JsonProperty("oauth2")
    private OAuth2Config oauth2;
    
    // 构造函数
    public AuthConfig() {
        this.type = AuthType.NONE;
    }
    
    public AuthConfig(AuthType type) {
        this.type = type;
    }
    
    // Getters and Setters
    public AuthType getType() {
        return type;
    }
    
    public void setType(AuthType type) {
        this.type = type;
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
    
    public ApiKeyConfig getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(ApiKeyConfig apiKey) {
        this.apiKey = apiKey;
    }
    
    public BearerTokenConfig getBearerToken() {
        return bearerToken;
    }
    
    public void setBearerToken(BearerTokenConfig bearerToken) {
        this.bearerToken = bearerToken;
    }
    
    public BasicAuthConfig getBasicAuth() {
        return basicAuth;
    }
    
    public void setBasicAuth(BasicAuthConfig basicAuth) {
        this.basicAuth = basicAuth;
    }
    
    public OAuth2Config getOauth2() {
        return oauth2;
    }
    
    public void setOauth2(OAuth2Config oauth2) {
        this.oauth2 = oauth2;
    }
    
    /**
     * API Key认证配置
     */
    public static class ApiKeyConfig {
        @JsonProperty("keyName")
        private String keyName;
        
        @JsonProperty("location")
        private String location; // header, query
        
        @JsonProperty("description")
        private String description;
        
        public ApiKeyConfig() {}
        
        public ApiKeyConfig(String keyName, String location) {
            this.keyName = keyName;
            this.location = location;
        }
        
        public String getKeyName() {
            return keyName;
        }
        
        public void setKeyName(String keyName) {
            this.keyName = keyName;
        }
        
        public String getLocation() {
            return location;
        }
        
        public void setLocation(String location) {
            this.location = location;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
    
    /**
     * Bearer Token认证配置
     */
    public static class BearerTokenConfig {
        @JsonProperty("headerName")
        private String headerName;
        
        @JsonProperty("description")
        private String description;
        
        public BearerTokenConfig() {
            this.headerName = "Authorization";
        }
        
        public BearerTokenConfig(String headerName) {
            this.headerName = headerName;
        }
        
        public String getHeaderName() {
            return headerName;
        }
        
        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
    
    /**
     * Basic Auth认证配置
     */
    public static class BasicAuthConfig {
        @JsonProperty("username")
        private String username;
        
        @JsonProperty("password")
        private String password;
        
        @JsonProperty("description")
        private String description;
        
        public BasicAuthConfig() {}
        
        public BasicAuthConfig(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
    
    /**
     * OAuth2认证配置
     */
    public static class OAuth2Config {
        @JsonProperty("authorizationUrl")
        private String authorizationUrl;
        
        @JsonProperty("tokenUrl")
        private String tokenUrl;
        
        @JsonProperty("scopes")
        private String[] scopes;
        
        @JsonProperty("flow")
        private String flow; // authorizationCode, clientCredentials, password, implicit
        
        @JsonProperty("description")
        private String description;
        
        public OAuth2Config() {}
        
        public OAuth2Config(String authorizationUrl, String tokenUrl, String flow) {
            this.authorizationUrl = authorizationUrl;
            this.tokenUrl = tokenUrl;
            this.flow = flow;
        }
        
        public String getAuthorizationUrl() {
            return authorizationUrl;
        }
        
        public void setAuthorizationUrl(String authorizationUrl) {
            this.authorizationUrl = authorizationUrl;
        }
        
        public String getTokenUrl() {
            return tokenUrl;
        }
        
        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }
        
        public String[] getScopes() {
            return scopes;
        }
        
        public void setScopes(String[] scopes) {
            this.scopes = scopes;
        }
        
        public String getFlow() {
            return flow;
        }
        
        public void setFlow(String flow) {
            this.flow = flow;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
}
