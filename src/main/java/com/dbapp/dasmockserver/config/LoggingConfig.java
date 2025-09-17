package com.dbapp.dasmockserver.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.dbapp.dasmockserver.util.logging.LogbackSseAppender;
import com.dbapp.dasmockserver.util.logging.SseLogBroadcaster;
import com.dbapp.dasmockserver.util.logging.SseLogBroadcasterHolder;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingConfig {

    @Bean
    public SseLogBroadcaster sseLogBroadcaster() {
        SseLogBroadcaster broadcaster = new SseLogBroadcaster();
        // 暴露给Logback Appender
        SseLogBroadcasterHolder.set(broadcaster);
        // 同时初始化并注册Appender
        attachAppender();
        return broadcaster;
    }

    private void attachAppender() {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            LogbackSseAppender appender = new LogbackSseAppender();
            appender.setContext(context);
            appender.setName("SSE_APPENDER");
            appender.start();

            // 绑定到root logger，捕获全局日志
            Logger root = context.getLogger("ROOT");
            boolean exists = root.iteratorForAppenders().hasNext();
            // 始终添加一个我们的appender（避免重复添加需要更复杂检查，这里简单处理）
            root.addAppender(appender);
        } catch (Exception ignored) {
        }
    }
}


