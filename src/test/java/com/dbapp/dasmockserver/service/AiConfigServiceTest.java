package com.dbapp.dasmockserver.service;

import com.dbapp.dasmockserver.config.AiConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AiConfigServiceTest {
    
    @Test
    public void testGetAvailableModels() {
        AiConfigService aiConfigService = new AiConfigService();
        var models = aiConfigService.getAvailableModels();
        
        assertNotNull(models);
        assertFalse(models.isEmpty());
        assertEquals(4, models.size());
        
        // 验证模型信息
        var firstModel = models.get(0);
        assertEquals("qwen-turbo", firstModel.getModelId());
        assertEquals("通义千问Turbo", firstModel.getModelName());
        assertNotNull(firstModel.getDescription());
        
        // 验证所有模型
        var modelIds = models.stream().map(AiConfigService.ModelInfo::getModelId).toList();
        assertTrue(modelIds.contains("qwen-turbo"));
        assertTrue(modelIds.contains("qwen-plus"));
        assertTrue(modelIds.contains("qwen-max"));
        assertTrue(modelIds.contains("qwen-max-longcontext"));
    }
    
    @Test
    public void testGetDefaultConfig() {
        AiConfigService aiConfigService = new AiConfigService();
        var defaultConfig = aiConfigService.getDefaultConfig();
        
        assertNotNull(defaultConfig);
        assertEquals("qwen-turbo", defaultConfig.getModelName());
        assertEquals(0.1, defaultConfig.getTemperature());
        assertEquals(4000, defaultConfig.getMaxTokens());
        assertEquals(0.7, defaultConfig.getTopP());
    }
    
    @Test
    public void testValidateConfig() {
        AiConfigService aiConfigService = new AiConfigService();
        
        // 测试有效配置
        var validConfig = new AiConfig.AiModelConfig("qwen-turbo", 0.1, 4000, 0.7);
        assertTrue(aiConfigService.validateConfig(validConfig));
        
        // 测试无效配置 - null
        assertFalse(aiConfigService.validateConfig(null));
        
        // 测试无效配置 - 空模型名
        var invalidConfig1 = new AiConfig.AiModelConfig("", 0.1, 4000, 0.7);
        assertFalse(aiConfigService.validateConfig(invalidConfig1));
        
        // 测试无效配置 - 温度超出范围
        var invalidConfig2 = new AiConfig.AiModelConfig("qwen-turbo", 3.0, 4000, 0.7);
        assertFalse(aiConfigService.validateConfig(invalidConfig2));
        
        // 测试无效配置 - maxTokens超出范围
        var invalidConfig3 = new AiConfig.AiModelConfig("qwen-turbo", 0.1, 10000, 0.7);
        assertFalse(aiConfigService.validateConfig(invalidConfig3));
        
        // 测试无效配置 - topP超出范围
        var invalidConfig4 = new AiConfig.AiModelConfig("qwen-turbo", 0.1, 4000, 1.5);
        assertFalse(aiConfigService.validateConfig(invalidConfig4));
    }
    
    @Test
    public void testModelInfo() {
        var modelInfo = new AiConfigService.ModelInfo("test-model", "测试模型", "这是一个测试模型");
        
        assertEquals("test-model", modelInfo.getModelId());
        assertEquals("测试模型", modelInfo.getModelName());
        assertEquals("这是一个测试模型", modelInfo.getDescription());
    }
    
    @Test
    public void testDynamicConfigUpdate() {
        AiConfigService aiConfigService = new AiConfigService();
        
        // 初始状态应该是静态配置
        assertFalse(aiConfigService.isUsingDynamicConfig());
        assertEquals("静态配置", aiConfigService.getConfigSource());
        
        // 更新配置
        var newConfig = new AiConfig.AiModelConfig("qwen-max", 0.8, 6000, 0.8);
        boolean success = aiConfigService.updateConfig(newConfig);
        
        assertTrue(success);
        assertTrue(aiConfigService.isUsingDynamicConfig());
        assertEquals("动态配置", aiConfigService.getConfigSource());
        
        // 验证配置已更新
        var currentConfig = aiConfigService.getCurrentConfig();
        assertEquals("qwen-max", currentConfig.getModelName());
        assertEquals(0.8, currentConfig.getTemperature());
        assertEquals(6000, currentConfig.getMaxTokens());
        assertEquals(0.8, currentConfig.getTopP());
    }
    
    @Test
    public void testResetToDefault() {
        AiConfigService aiConfigService = new AiConfigService();
        
        // 先设置动态配置
        var dynamicConfig = new AiConfig.AiModelConfig("qwen-max", 0.8, 6000, 0.8);
        aiConfigService.updateConfig(dynamicConfig);
        assertTrue(aiConfigService.isUsingDynamicConfig());
        
        // 重置为默认配置
        aiConfigService.resetToDefault();
        assertFalse(aiConfigService.isUsingDynamicConfig());
        assertEquals("静态配置", aiConfigService.getConfigSource());
    }
    
    @Test
    public void testInvalidConfigUpdate() {
        AiConfigService aiConfigService = new AiConfigService();
        
        // 尝试更新无效配置
        var invalidConfig = new AiConfig.AiModelConfig("qwen-turbo", 3.0, 4000, 0.7); // temperature超出范围
        boolean success = aiConfigService.updateConfig(invalidConfig);
        
        assertFalse(success);
        assertFalse(aiConfigService.isUsingDynamicConfig());
    }
} 