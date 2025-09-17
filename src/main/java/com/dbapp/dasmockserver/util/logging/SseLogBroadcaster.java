package com.dbapp.dasmockserver.util.logging;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 负责将日志消息广播到所有活跃的SSE连接，同时维护一个内存环形缓冲供新连接快速回放最近日志。
 */
public class SseLogBroadcaster {

    private static final long SSE_TIMEOUT_MS = 0L; // 不超时，由客户端断开
    private static final int RING_BUFFER_SIZE = 500;

    private final List<SseEmitter> activeEmitters = new CopyOnWriteArrayList<>();
    private final String[] ringBuffer = new String[RING_BUFFER_SIZE];
    private int writeIndex = 0;
    private int totalWritten = 0;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        activeEmitters.add(emitter);
        emitter.onCompletion(() -> activeEmitters.remove(emitter));
        emitter.onTimeout(() -> activeEmitters.remove(emitter));
        emitter.onError(throwable -> activeEmitters.remove(emitter));
        return emitter;
    }

    public void replayRecent(SseEmitter emitter) {
        int count = Math.min(totalWritten, RING_BUFFER_SIZE);
        int start = (writeIndex - count + RING_BUFFER_SIZE) % RING_BUFFER_SIZE;
        for (int i = 0; i < count; i++) {
            String line = ringBuffer[(start + i) % RING_BUFFER_SIZE];
            if (line != null) {
                trySend(emitter, line);
            }
        }
    }

    public void broadcast(String level, String loggerName, String message) {
        String ts = LocalDateTime.now().format(TS);
        String payload = "[" + ts + "][" + level + "][" + loggerName + "] " + message;

        // 写入环形缓冲
        ringBuffer[writeIndex] = payload;
        writeIndex = (writeIndex + 1) % RING_BUFFER_SIZE;
        totalWritten++;

        // 广播到所有Emitter
        for (SseEmitter emitter : activeEmitters) {
            trySend(emitter, payload);
        }
    }

    private void trySend(SseEmitter emitter, String payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(payload));
        } catch (IOException e) {
            activeEmitters.remove(emitter);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
    }
}


