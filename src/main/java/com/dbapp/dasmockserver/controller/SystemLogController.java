package com.dbapp.dasmockserver.controller;

import com.dbapp.dasmockserver.util.logging.SseLogBroadcaster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class SystemLogController {

    @Autowired
    private SseLogBroadcaster broadcaster;

    @GetMapping("/system-logs")
    public String page() {
        return "system-logs";
    }

    @GetMapping(path = "/api/system-logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter stream() {
        SseEmitter emitter = broadcaster.register();
        // 先回放最近日志
        broadcaster.replayRecent(emitter);
        return emitter;
    }
}


