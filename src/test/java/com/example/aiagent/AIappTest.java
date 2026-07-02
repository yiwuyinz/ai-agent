package com.example.aiagent;

import com.example.aiagent.agent.YuManus;
import com.example.aiagent.app.AIapp;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;


@SpringBootTest
class YuManusTest {

    @Resource
    private YuManus yuManus;

    @Test
    void run() {
        String userPrompt = """  
                我的另一半居住在上海静安区，请帮我搜索静安区 5 公里内合适的约会地点，  
                并结合一些网络图片，制定一份详细的约会计划""";
        String answer = yuManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}
