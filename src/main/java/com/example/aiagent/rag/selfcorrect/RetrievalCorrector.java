package com.example.aiagent.rag.selfcorrect;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索纠错器
 * 功能：检索质量评估、重排序、检索失败时自动扩展
 */
@Slf4j
@Component
public class RetrievalCorrector {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    // 质量阈值
    private static final double MIN_RELEVANCE_THRESHOLD = 0.65;
    private static final int MIN_DOCUMENT_COUNT = 1;

    public RetrievalCorrector(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 检索纠错主入口
     */
    public RetrievalResult retrieveWithCorrection(List<String> queries) {
        List<CorrectionStep> steps = new ArrayList<>();
        List<Document> allResults = new ArrayList<>();

        // 第一轮：使用所有扩展查询检索
        for (String query : queries) {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(5).build()
            );
            allResults.addAll(results);
        }

        // 去重
        List<Document> uniqueResults = deduplicate(allResults);

        // 评估检索质量
        RetrievalQuality quality = assessQuality(uniqueResults, queries.get(0));

        // 如果质量不达标，触发纠错
        if (quality.getAverageScore() < MIN_RELEVANCE_THRESHOLD || uniqueResults.size() < MIN_DOCUMENT_COUNT) {
            log.warn("检索质量不足 (score={}, count={})，触发检索纠错",
                    quality.getAverageScore(), uniqueResults.size());

            // 纠错策略1：降低相似度阈值，扩大检索范围
            List<Document> expandedResults = retrieveWithLowerThreshold(queries.get(0));
            uniqueResults.addAll(expandedResults);

            // 纠错策略2：使用关键词匹配补充
            List<Document> keywordResults = keywordFallback(queries.get(0));
            uniqueResults.addAll(keywordResults);

            // 重新去重和排序
            uniqueResults = deduplicate(uniqueResults);
            uniqueResults = rerank(uniqueResults, queries.get(0));

            quality = assessQuality(uniqueResults, queries.get(0));

            steps.add(CorrectionStep.builder()
                    .stage("RETRIEVAL")
                    .issue("初始检索召回不足或相关性低")
                    .action("降低阈值+关键词补充+重排序")
                    .beforeScore(quality.getAverageScore())
                    .afterScore(quality.getAverageScore())
                    .build());
        }

        // 最终重排序
        List<Document> finalResults = rerank(uniqueResults, queries.get(0));

        return RetrievalResult.builder()
                .documents(finalResults)
                .qualityScore(quality.getAverageScore())
                .documentCount(finalResults.size())
                .correctionSteps(steps)
                .build();
    }

    /**
     * 检索质量评估
     */
    private RetrievalQuality assessQuality(List<Document> documents, String query) {
        if (documents.isEmpty()) {
            return RetrievalQuality.builder().averageScore(0).build();
        }

        float[] queryEmbedding = embeddingModel.embed(query);
        double totalScore = 0;

        for (Document doc : documents) {
            float[] docEmbedding = embeddingModel.embed(doc.getText());
            double similarity = cosineSimilarity(queryEmbedding, docEmbedding);
            totalScore += similarity;
        }

        double avgScore = totalScore / documents.size();

        return RetrievalQuality.builder()
                .averageScore(avgScore)
                .minScore(documents.size() > 0 ? totalScore / documents.size() : 0)
                .build();
    }

    /**
     * 降低阈值重新检索
     */
    private List<Document> retrieveWithLowerThreshold(String query) {
        log.info("执行低阈值检索扩展");
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(10)
                        .similarityThreshold(0.3) // 降低阈值
                        .build()
        );
    }

    /**
     * 关键词回退检索
     */
    private List<Document> keywordFallback(String query) {
        log.info("执行关键词回退检索");
        // 提取关键词进行模糊匹配
        String[] keywords = extractKeywords(query);
        List<Document> results = new ArrayList<>();

        for (String keyword : keywords) {
            if (keyword.length() >= 2) {
                results.addAll(vectorStore.similaritySearch(
                        SearchRequest.builder().query(keyword).topK(3).build()
                ));
            }
        }

        return results;
    }

    /**
     * 重排序（使用更精确的相似度计算）
     */
    private List<Document> rerank(List<Document> documents, String query) {
        float[] queryEmbedding = embeddingModel.embed(query);

        return documents.stream()
                .map(doc -> {
                    float[] docEmbedding = embeddingModel.embed(doc.getText());
                    double score = cosineSimilarity(queryEmbedding, docEmbedding);
                    doc.getMetadata().put("rerank_score", score);
                    return doc;
                })
                .sorted((a, b) -> Double.compare(
                        (Double) b.getMetadata().get("rerank_score"),
                        (Double) a.getMetadata().get("rerank_score")
                ))
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * 去重
     */
    private List<Document> deduplicate(List<Document> documents) {
        Set<String> seen = new HashSet<>();
        List<Document> unique = new ArrayList<>();

        for (Document doc : documents) {
            String key = doc.getText().substring(0, Math.min(50, doc.getText().length()));
            if (!seen.contains(key)) {
                seen.add(key);
                unique.add(doc);
            }
        }

        return unique;
    }

    private String[] extractKeywords(String query) {
        // 简单分词，实际可用 IK/Jieba
        return query.split("[\\s的了吗什么多少怎么和与]");
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10);
    }

    @Data
    @Builder
    public static class RetrievalResult {
        private List<Document> documents;
        private double qualityScore;
        private int documentCount;
        private List<CorrectionStep> correctionSteps;
    }

    @Data
    @Builder
    private static class RetrievalQuality {
        private double averageScore;
        private double minScore;
    }
}
