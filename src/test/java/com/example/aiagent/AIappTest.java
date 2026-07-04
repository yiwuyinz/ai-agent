package com.example.aiagent;

import com.example.aiagent.agent.YuManus;
import com.example.aiagent.app.AIapp;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;


@SpringBootTest
@ActiveProfiles("local")
class YuManusTest {

    @Resource
    private YuManus yuManus;

    @Resource
    private AIapp aiapp;
    @Test
    void run() {
        String chatId = UUID.randomUUID().toString();
        String userPrompt = """  
                糖尿病患者失眠时，睡前可以喝什么助眠？""";
        String answer = aiapp.doChatWithCorrectedRag(userPrompt,chatId);
        Assertions.assertNotNull(answer);
    }
}
