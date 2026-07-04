package com.example.aiagent.rag.selfcorrect;

import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class QueryCorrector {

    private final ChatClient chatClient;

    private ChatClient.Builder chatClientBuilder;

    public QueryCorrector(ChatModel dashscopeChatModel) {
        chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    /**
     * 查询纠错主入口
     */
    public CorrectedQuery correct(String userQuery) {
        List<CorrectionStep> steps = new ArrayList<>();

        //查询扩展
        List<String> expandedQueries = expandQuery(userQuery);

        return CorrectedQuery.builder()
                .originalQuery(userQuery)
                .expandedQueries(expandedQueries)
                .correctionSteps(steps)
                .build();
    }
    /**
     * 查询扩展：生成多个相关查询以提高召回
     */
    private List<String> expandQuery(String query) {
        String prompt = String.format("""
            基于以下医学查询，生成3-5个语义等价但表述不同的查询变体，
            用于提高检索召回率。

            原始查询：%s

            要求：
            1. 使用同义词替换（如"血压高"→"高血压"）
            2. 补充相关医学术语
            3. 每个变体一行，只输出查询文本

            输出格式（每行一个）：
            变体1
            变体2
            ...
            """, query);

        String result = chatClient.prompt(prompt).call().content();
//
        List<String> expanded = new ArrayList<>();
        expanded.add(query); // 保留原始查询

        for (String line : result.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("变体") && !trimmed.equals(query)) {
                expanded.add(trimmed);
            }
        }

        log.info("查询扩展: {} 个变体", expanded.size());
        return expanded;
    }

    @Data
    @Builder
    public static class CorrectedQuery {
        private String originalQuery;
        private List<String> expandedQueries;
        private List<CorrectionStep> correctionSteps;
    }
}
