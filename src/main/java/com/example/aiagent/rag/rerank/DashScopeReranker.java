package com.example.aiagent.rag.rerank;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DashScopeReranker implements Reranker {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<Document> rerank(String query, List<Document> candidates, int topN) {
        if (candidates == null || candidates.size() <= topN) {
            return candidates;
        }

        // 构建 documents 参数
        List<Map<String, String>> docs = new ArrayList<>();
        for (Document doc : candidates) {
            Map<String, String> m = new HashMap<>();
            m.put("text", doc.getText());
            docs.add(m);
        }

        // 构建 query 参数
        Map<String, String> queryMap = new HashMap<>();
        queryMap.put("text", query);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gte-rerank");
        requestBody.put("query", queryMap);
        requestBody.put("documents", docs);
        requestBody.put("top_n", topN);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://dashscope.aliyuncs.com/api/v1/services/rerank/rerank-1",
                    entity,
                    Map.class
            );

            Map<String, Object> output = (Map<String, Object>) response.getBody().get("output");
            List<Map<String, Object>> results = (List<Map<String, Object>>) output.get("results");

            List<Document> reranked = new ArrayList<>();
            for (Map<String, Object> result : results) {
                int index = Integer.parseInt(result.get("doc_index").toString());
                Document original = candidates.get(index);

                // 将 Rerank 分数写入 metadata
                Map<String, Object> newMeta = new HashMap<>(original.getMetadata());
                newMeta.put("rerank_score", result.get("relevance_score"));

                reranked.add(new Document(original.getText(), newMeta));
            }
            return reranked;

        } catch (Exception e) {
            // 降级：Rerank 失败时直接返回原候选前 topN 条
            return candidates.subList(0, Math.min(topN, candidates.size()));
        }
    }
}
