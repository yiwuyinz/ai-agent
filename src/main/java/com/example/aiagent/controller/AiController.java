package com.example.aiagent.controller;

import com.example.aiagent.agent.YuManus;
import com.example.aiagent.app.AIapp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AIapp aiapp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private ToolCallbackProvider toolCallbacks;

    @GetMapping("/ai_app/chat/sync")
    public String doChatWithAiAppSync(String message, String chatId){
        return aiapp.doChat(message, chatId);
    }

    @GetMapping(value = "/ai_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithAiAppSSE(String message, String chatId) {
        return aiapp.doChatByStream(message, chatId);
    }

    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message){
        YuManus yuManus = new YuManus(allTools, dashscopeChatModel, toolCallbacks);
        return yuManus.runStream(message);
    }
}
