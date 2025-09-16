package com.dbapp.dasmockserver.service;

import java.util.List;
import java.util.Map;

/**
 * 参数信息数据类
 */
public class ParameterInfo {
    private List<Parameter> pathVariables;
    private List<Parameter> queryParameters;
    private RequestBody requestBody;
    
    public ParameterInfo() {
    }
    
    public ParameterInfo(List<Parameter> pathVariables, List<Parameter> queryParameters, RequestBody requestBody) {
        this.pathVariables = pathVariables;
        this.queryParameters = queryParameters;
        this.requestBody = requestBody;
    }
    
    public List<Parameter> getPathVariables() {
        return pathVariables;
    }
    
    public void setPathVariables(List<Parameter> pathVariables) {
        this.pathVariables = pathVariables;
    }
    
    public List<Parameter> getQueryParameters() {
        return queryParameters;
    }
    
    public void setQueryParameters(List<Parameter> queryParameters) {
        this.queryParameters = queryParameters;
    }
    
    public RequestBody getRequestBody() {
        return requestBody;
    }
    
    public void setRequestBody(RequestBody requestBody) {
        this.requestBody = requestBody;
    }
    
    /**
     * 参数类
     */
    public static class Parameter {
        private String name;
        private String type;
        private boolean required;
        private String description;
        private String originalName; // 原始变量名，用于@PathVariable注解的value属性
        
        public Parameter() {
        }
        
        public Parameter(String name, String type, boolean required, String description) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.description = description;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public boolean isRequired() {
            return required;
        }
        
        public void setRequired(boolean required) {
            this.required = required;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getOriginalName() {
            return originalName;
        }
        
        public void setOriginalName(String originalName) {
            this.originalName = originalName;
        }
    }
    
    /**
     * 请求体类
     */
    public static class RequestBody {
        private String type;
        private Map<String, Property> properties;
        
        public RequestBody() {
        }
        
        public RequestBody(String type, Map<String, Property> properties) {
            this.type = type;
            this.properties = properties;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public Map<String, Property> getProperties() {
            return properties;
        }
        
        public void setProperties(Map<String, Property> properties) {
            this.properties = properties;
        }
    }
    
    /**
     * 属性类
     */
    public static class Property {
        private String type;
        private boolean required;
        private String description;
        
        public Property() {
        }
        
        public Property(String type, boolean required, String description) {
            this.type = type;
            this.required = required;
            this.description = description;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public boolean isRequired() {
            return required;
        }
        
        public void setRequired(boolean required) {
            this.required = required;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
} 