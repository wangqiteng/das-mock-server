package com.dbapp.dasmockserver.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    private static final String DEFAULT_PROMPT = "你是一个根据接口文档生成接口服务的助手，请根据用户提问生成相关代码！";
    
    @Value("${spring.ai.alibaba.dashscope.chat.options.model:qwen-turbo}")
    private String modelName;
    
    @Value("${spring.ai.alibaba.dashscope.chat.options.temperature:0.1}")
    private Double temperature;
    
    @Value("${spring.ai.alibaba.dashscope.chat.options.max-tokens:4000}")
    private Integer maxTokens;
    
    @Value("${spring.ai.alibaba.dashscope.chat.options.top-p:0.7}")
    private Double topP;
    
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultSystem(DEFAULT_PROMPT)
                // 实现 Chat Memory 的 Advisor
                // 在使用 Chat Memory 时，需要指定对话 ID，以便 Spring AI Alibaba 处理上下文。
//                .defaultAdvisors(
//                        new MessageChatMemoryAdvisor(new InMemoryChatMemory())
//                )
                // 实现 Logger 的 Advisor
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()
                )
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .withModel(modelName)
                                .withTemperature(temperature)
                                .withTopP(topP)
                                .build()
                )
                .build();
    }
    
    /**
     * 获取当前模型配置
     */
    public AiModelConfig getCurrentConfig() {
        return new AiModelConfig(modelName, temperature, maxTokens, topP);
    }
    
    /**
     * AI模型配置类
     */
    public static class AiModelConfig {
        private String modelName;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        
        public AiModelConfig(String modelName, Double temperature, Integer maxTokens, Double topP) {
            this.modelName = modelName;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.topP = topP;
        }
        
        // Getters and Setters
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        
        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }
        
        public Integer getMaxTokens() { return maxTokens; }
        public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
        
        public Double getTopP() { return topP; }
        public void setTopP(Double topP) { this.topP = topP; }
        
        @Override
        public String toString() {
            return String.format("AiModelConfig{modelName='%s', temperature=%.1f, maxTokens=%d, topP=%.1f}", 
                modelName, temperature, maxTokens, topP);
        }
    }
}
