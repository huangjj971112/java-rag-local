package com.example.rag.controller;

import com.example.rag.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/rag/stream")
@RequiredArgsConstructor
public class StreamRagController {

    private final RagService ragService;

    @GetMapping(value = "/ask", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream(@RequestParam("question") String question,
                             @RequestParam(value = "sessionId", defaultValue = "default") String sessionId) {

        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);

        ragService.streamAsk(question, sessionId, emitter);

        return emitter;
    }

    @DeleteMapping("/memory")
    public String clearMemory(@RequestParam(value = "sessionId", defaultValue = "default") String sessionId) {
        ragService.clearMemory(sessionId);
        return "已清空会话：" + sessionId;
    }
}