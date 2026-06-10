package com.dbapp.dasmockserver.service;

import com.dbapp.dasmockserver.config.AiConfig;
import com.dbapp.dasmockserver.model.CustomModelConfig;
import com.dbapp.dasmockserver.repository.CustomModelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(AiConfigService.class);
    
    @Autowired
    private AiConfig aiConfig;
    
    @Autowired
    private CustomModelConfigRepository customModelConfigRepository;
    
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
     * 获取可用的模型列表（从数据库读取）
     */
    public List<ModelInfo> getAvailableModels() {
        List<CustomModelConfig> configs = customModelConfigRepository.findAll();
        List<ModelInfo> models = new ArrayList<>();
        for (CustomModelConfig config : configs) {
            models.add(new ModelInfo(config.getModelId(), config.getDisplayName(), config.getDescription()));
        }
        // 如果数据库为空，返回默认列表
        if (models.isEmpty()) {
            return getDefaultModelList();
        }
        return models;
    }
    
    /**
     * 获取默认模型列表（兜底使用）
     */
    private List<ModelInfo> getDefaultModelList() {
        return List.of(
            new ModelInfo("qwen-turbo", "通义千问Turbo", "快速响应，适合一般对话"),
            new ModelInfo("qwen-plus", "通义千问Plus", "平衡性能和效果"),
            new ModelInfo("qwen-max", "通义千问Max", "最高性能，适合复杂任务"),
            new ModelInfo("qwen-max-longcontext", "通义千问Max长文本", "支持超长文本处理"),
            new ModelInfo("hengNao", "恒脑", "恒脑AI模型")
        );
    }
    
    /**
     * 获取所有自定义模型配置（完整实体）
     */
    public List<CustomModelConfig> getAllCustomModels() {
        return customModelConfigRepository.findAll();
    }
    
    /**
     * 根据ID获取自定义模型
     */
    public Optional<CustomModelConfig> getCustomModelById(Long id) {
        return customModelConfigRepository.findById(id);
    }
    
    /**
     * 根据模型ID查找自定义模型配置
     */
    public Optional<CustomModelConfig> getCustomModelByModelId(String modelId) {
        return customModelConfigRepository.findByModelId(modelId);
    }
    
    /**
     * 新增自定义模型
     */
    public CustomModelConfig addCustomModel(CustomModelConfig modelConfig) {
        if (modelConfig.getModelId() == null || modelConfig.getModelId().trim().isEmpty()) {
            throw new IllegalArgumentException("模型ID不能为空");
        }
        if (modelConfig.getDisplayName() == null || modelConfig.getDisplayName().trim().isEmpty()) {
            throw new IllegalArgumentException("模型显示名称不能为空");
        }
        if (customModelConfigRepository.existsByModelId(modelConfig.getModelId())) {
            throw new IllegalArgumentException("模型ID '" + modelConfig.getModelId() + "' 已存在");
        }
        log.info("新增自定义模型: modelId={}, displayName={}", modelConfig.getModelId(), modelConfig.getDisplayName());
        return customModelConfigRepository.save(modelConfig);
    }
    
    /**
     * 更新自定义模型
     */
    public CustomModelConfig updateCustomModel(Long id, CustomModelConfig updated) {
        CustomModelConfig existing = customModelConfigRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("模型配置不存在，ID: " + id));
        
        // 如果修改了 modelId，检查是否与其他记录冲突
        if (updated.getModelId() != null && !updated.getModelId().equals(existing.getModelId())) {
            if (customModelConfigRepository.existsByModelId(updated.getModelId())) {
                throw new IllegalArgumentException("模型ID '" + updated.getModelId() + "' 已存在");
            }
            existing.setModelId(updated.getModelId());
        }
        
        if (updated.getDisplayName() != null) {
            existing.setDisplayName(updated.getDisplayName());
        }
        if (updated.getDescription() != null) {
            existing.setDescription(updated.getDescription());
        }
        if (updated.getIsDefault() != null) {
            existing.setIsDefault(updated.getIsDefault());
        }
        if (updated.getApiUrl() != null) {
            existing.setApiUrl(updated.getApiUrl());
        }
        if (updated.getApiKey() != null) {
            existing.setApiKey(updated.getApiKey());
        }
        if (updated.getApiType() != null) {
            existing.setApiType(updated.getApiType());
        }
        
        log.info("更新自定义模型: id={}, modelId={}", id, existing.getModelId());
        return customModelConfigRepository.save(existing);
    }
    
    /**
     * 删除自定义模型
     */
    public void deleteCustomModel(Long id) {
        if (!customModelConfigRepository.existsById(id)) {
            throw new IllegalArgumentException("模型配置不存在，ID: " + id);
        }
        log.info("删除自定义模型: id={}", id);
        customModelConfigRepository.deleteById(id);
    }
    
    /**
     * 获取默认配置
     */
    public AiConfig.AiModelConfig getDefaultConfig() {
        // 尝试从数据库获取标记为默认的模型
        List<CustomModelConfig> defaultModels = customModelConfigRepository.findByIsDefaultTrue();
        String defaultModelId = defaultModels.isEmpty() ? "qwen-turbo" : defaultModels.get(0).getModelId();
        return new AiConfig.AiModelConfig(defaultModelId, 0.1, 4000, 0.7);
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
     * 模型信息类（用于 API 响应）
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