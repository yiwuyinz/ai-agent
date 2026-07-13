package com.example.aiagent.app;

import com.example.aiagent.advisor.MyLoggerAdvisor;
import com.example.aiagent.chatmemory.FileBasedChatMemory;
import com.example.aiagent.rag.AiAppRagCustomAdvisorFactory;
import com.example.aiagent.rag.QueryRewriter;
import com.example.aiagent.rag.selfcorrect.SelfCorrectResult;
import com.example.aiagent.rag.selfcorrect.SelfCorrectingRAGService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.DefaultChatClientBuilder;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;


import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;


@Component
@Slf4j
public class AIapp {

    private final ChatClient chatClient;

    public static final String SYSTEM_PROMPT = "扮演专业健康管理助手。开场向用户表明身份，告知用户可咨询健康、饮食、运动、睡眠及常见身体不适等问题。"
            + "根据用户情况进行针对性提问：身体不适询问年龄、症状表现、持续时间及严重程度；"
            + "慢病管理询问既往病史、用药情况及近期变化；"
            + "健康管理询问饮食、运动、睡眠等生活习惯。"
            + "引导用户详细描述情况、检查结果及关注的问题，以便提供个性化健康建议。"
            + "不直接作出医疗诊断，不推荐处方药使用方案；对于胸痛、呼吸困难、意识障碍、大量出血等紧急情况，立即建议就医或呼叫急救服务。";

    public AIapp(ChatModel dashscopeChatModel) {
//        String fileDir = System.getProperty("user.dir") + "/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)  // 设置最大消息数量
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content:{}", content);
        return content;
    }

    @Resource
    private VectorStore aiAppVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private ChatClient.Builder chatClientBuilder;

    public String doChatWithRag(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
//                .advisors(QuestionAnswerAdvisor.builder(aiAppVectorStore).build())
                .advisors(
                        AiAppRagCustomAdvisorFactory.createRetrievalAugmentationAdvisor(
                                aiAppVectorStore, chatClientBuilder
                        )
                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}", content);
        return content;
    }

    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content:{}", content);
        return content;
    }

    @Resource
    private ToolCallbackProvider toolCallbacks;

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbacks)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content:{}", content);
        return content;
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();

    }

    @Autowired
    private SelfCorrectingRAGService selfCorrectingRAG;

    public String doChatWithCorrectedRag(String userPrompt, String chatId) {
        // 替换原有实现为自纠错版本
        SelfCorrectResult result = selfCorrectingRAG.query(userPrompt, chatId);
        log.info("content:{}", result.getFinalAnswer());
        return result.getFinalAnswer();
    }
}
