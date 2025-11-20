package com.dbapp.dasmockserver.util.logging;

/**
 * 在Logback Appender与Spring之间传递SseLogBroadcaster的静态持有者。
 */
public class SseLogBroadcasterHolder {
    private static volatile SseLogBroadcaster INSTANCE;

    public static void set(SseLogBroadcaster broadcaster) {
        INSTANCE = broadcaster;
    }

    public static SseLogBroadcaster get() {
        return INSTANCE;
    }
}




