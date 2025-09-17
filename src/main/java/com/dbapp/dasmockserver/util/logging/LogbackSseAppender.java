package com.dbapp.dasmockserver.util.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * 一个简单的Logback Appender，把日志事件推送到SSE广播器。
 */
public class LogbackSseAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent eventObject) {
        SseLogBroadcaster broadcaster = SseLogBroadcasterHolder.get();
        if (broadcaster == null) {
            return;
        }
        String level = levelToString(eventObject.getLevel());
        String loggerName = eventObject.getLoggerName();
        String formatted = eventObject.getFormattedMessage();
        broadcaster.broadcast(level, loggerName, formatted);
    }

    private String levelToString(Level level) {
        if (level == null) return "INFO";
        return level.toString();
    }
}


