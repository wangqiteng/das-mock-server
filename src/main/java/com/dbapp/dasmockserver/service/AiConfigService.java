package com.dbapp.dasmockserver.service;

import com.dbapp.dasmockserver.config.AiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiConfigService {
    
    @Autowired
    private AiConfig aiConfig;
    
    // 动态配置存储，支持实时切换
    private final AtomicReference<AiConfig.AiModelConfig> dynamicConfig = new AtomicReference<>();
    
    /**
     * 获取当前AI模型配置（优先使用动态配置）
     */
    public AiConfig.AiModelConfig getCurrentConfig() {
        AiConfig.AiModelConfig dynamic = dynamicConfig.get();
        if (dynamic != null) {
            return dynamic;
        }
        return aiConfig.getCurrentConfig();
    }
    
    /**
     * 更新AI模型配置（实时生效）
     */
    public boolean updateConfig(AiConfig.AiModelConfig newConfig) {
        if (!validateConfig(newConfig)) {
            return false;
        }
        
        // 更新动态配置
        dynamicConfig.set(newConfig);
        return true;
    }
    
    /**
     * 重置为默认配置
     */
    public void resetToDefault() {
        dynamicConfig.set(null);
    }
    
    /**
     * 获取可用的模型列表
     */
    public List<ModelInfo> getAvailableModels() {
        return Arrays.asList(
            new ModelInfo("qwen-turbo", "通义千问Turbo", "快速响应，适合一般对话"),
            new ModelInfo("qwen-plus", "通义千问Plus", "平衡性能和效果"),
            new ModelInfo("qwen-max", "通义千问Max", "最高性能，适合复杂任务"),
            new ModelInfo("qwen-max-longcontext", "通义千问Max长文本", "支持超长文本处理")
        );
    }
    
    /**
     * 获取默认配置
     */
    public AiConfig.AiModelConfig getDefaultConfig() {
        return new AiConfig.AiModelConfig(
            "qwen-turbo",
            0.1,
            4000,
            0.7
        );
    }
    
    /**
     * 验证配置参数
     */
    public boolean validateConfig(AiConfig.AiModelConfig config) {
        if (config == null) return false;
        
        // 验证模型名称
        if (config.getModelName() == null || config.getModelName().trim().isEmpty()) {
            return false;
        }
        
        // 验证temperature (0.0 - 2.0)
        if (config.getTemperature() == null || 
            config.getTemperature() < 0.0 || 
            config.getTemperature() > 2.0) {
            return false;
        }
        
        // 验证maxTokens (1 - 8000)
        if (config.getMaxTokens() == null || 
            config.getMaxTokens() < 1 || 
            config.getMaxTokens() > 8000) {
            return false;
        }
        
        // 验证topP (0.0 - 1.0)
        if (config.getTopP() == null || 
            config.getTopP() < 0.0 || 
            config.getTopP() > 1.0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查是否为动态配置
     */
    public boolean isUsingDynamicConfig() {
        return dynamicConfig.get() != null;
    }
    
    /**
     * 获取配置来源信息
     */
    public String getConfigSource() {
        return isUsingDynamicConfig() ? "动态配置" : "静态配置";
    }
    
    /**
     * 模型信息类
     */
    public static class ModelInfo {
        private String modelId;
        private String modelName;
        private String description;
        
        public ModelInfo(String modelId, String modelName, String description) {
            this.modelId = modelId;
            this.modelName = modelName;
            this.description = description;
        }
        
        // Getters
        public String getModelId() { return modelId; }
        public String getModelName() { return modelName; }
        public String getDescription() { return description; }
    }
} 