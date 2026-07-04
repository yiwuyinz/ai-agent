package com.example.aiagent.rag;

import com.example.aiagent.app.AIapp;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 效果评估测试
 * 基于真实对话接口 aiapp.doChatWithRag(userPrompt, chatId) 进行端到端评估
 */
@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("local")
public class RagEffectivenessTest {

    @Resource
    private AIapp aiapp;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    private List<TestCase> testCases;
    private int chatIdCounter = 0;

    // 语义相似度阈值
    private static final double RELEVANCE_THRESHOLD = 0.55;
    private static final double CITATION_THRESHOLD = 0.60;

    @BeforeAll
    void setUp() {
        testCases = buildTestCases();
        log.info("=== RAG 测试环境准备完成，测试用例数: {} ===", testCases.size());
    }

    // ==================== 核心测试方法 ====================

    @Test
    void testRetrievalMetrics() {
        log.info("\n╔══════════════════════════════════════════════════════╗");
        log.info("║              检索阶段评估 (Retrieval)                ║");
        log.info("╚══════════════════════════════════════════════════════╝");

        int total = testCases.size();
        int[] kValues = {1, 3, 5};

        Map<Integer, MetricResult> results = new LinkedHashMap<>();

        for (int k : kValues) {
            double totalRecall = 0;
            double totalHit = 0;
            double mrrSum = 0;

            for (TestCase tc : testCases) {
                SearchRequest request = SearchRequest.builder()
                        .query(tc.getQuestion())
                        .topK(k)
                        .build();

                List<Document> retrieved = vectorStore.similaritySearch(request);

                double recall = calculateRecallAtK(retrieved, tc, k);
                boolean hit = isHit(retrieved, tc.getExpectedSources());
                double mrr = calculateMrr(retrieved, tc.getExpectedSources());

                totalRecall += recall;
                totalHit += hit ? 1 : 0;
                mrrSum += mrr;

                log.debug("[K={}] Q: {} | Recall: {:.2f} | Hit: {} | MRR: {:.4f}",
                        k, tc.getQuestion(), recall, hit, mrr);
            }

            results.put(k, MetricResult.builder()
                    .recallAtK(totalRecall / total)
                    .hitRateAtK(totalHit / total)
                    .mrrAtK(mrrSum / total)
                    .build());
        }

        // 输出结果
        log.info("\n┌─────────────────────────────────────────────────────┐");
        log.info("│              检索阶段评估结果                        │");
        log.info("├─────────────────────────────────────────────────────┤");
        for (int k : kValues) {
            MetricResult r = results.get(k);
            log.info("│  Recall@{}:     {:>6.2f}%                            │", k, r.getRecallAtK() * 100);
            log.info("│  HitRate@{}:    {:>6.2f}%                            │", k, r.getHitRateAtK() * 100);
            log.info("│  MRR@{}:        {:>6.4f}                              │", k, r.getMrrAtK());
            if (k < kValues[kValues.length - 1]) {
                log.info("├─────────────────────────────────────────────────────┤");
            }
        }
        log.info("└─────────────────────────────────────────────────────┘");

        // 断言
        MetricResult r3 = results.get(3);
        assertTrue(r3.getRecallAtK() >= 0.55, "Recall@3 应 >= 55%，当前: " + r3.getRecallAtK());
        assertTrue(r3.getHitRateAtK() >= 0.50, "HitRate@3 应 >= 50%，当前: " + r3.getHitRateAtK());
        assertTrue(results.get(5).getMrrAtK() >= 0.40, "MRR@5 应 >= 0.40，当前: " + results.get(5).getMrrAtK());
    }

    @Test
    void testGenerationMetrics() {
        log.info("\n╔══════════════════════════════════════════════════════╗");
        log.info("║              生成阶段评估 (Generation)               ║");
        log.info("╚══════════════════════════════════════════════════════╝");

        int total = testCases.size();
        double totalRelevance = 0;
        double totalCitation = 0;
        int validCases = 0;

        List<GenerationResult> details = new ArrayList<>();

        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            int chatId = ++chatIdCounter;

            log.info("\n[{}/{}] 问题: {}", i + 1, total, tc.getQuestion());

            // 调用真实 RAG 对话接口
            String generatedAnswer = aiapp.doChatWithRag(tc.getQuestion(), String.valueOf(chatId));

            if (generatedAnswer == null || generatedAnswer.trim().isEmpty()) {
                log.warn("生成答案为空，跳过");
                continue;
            }

            log.info("生成答案: {}", generatedAnswer.substring(0, Math.min(150, generatedAnswer.length())));

            // 1. 回答相关性：生成答案与问题的语义相似度
            double relevance = calculateSemanticSimilarity(generatedAnswer, tc.getQuestion());
            totalRelevance += relevance;

            // 2. 引用准确率：生成答案中的事实是否能在向量库中找到支持
            double citationAcc = calculateCitationAccuracy(generatedAnswer, tc.getQuestion());
            totalCitation += citationAcc;

            validCases++;

            details.add(GenerationResult.builder()
                    .question(tc.getQuestion())
                    .generatedAnswer(generatedAnswer)
                    .relevance(relevance)
                    .citationAccuracy(citationAcc)
                    .build());

            log.info("  → 相关性: {:.4f} | 引用准确率: {:.4f}", relevance, citationAcc);
        }

        double avgRelevance = totalRelevance / validCases;
        double avgCitation = totalCitation / validCases;

        log.info("\n┌─────────────────────────────────────────────────────┐");
        log.info("│              生成阶段评估结果                        │");
        log.info("├─────────────────────────────────────────────────────┤");
        log.info("│  回答相关性:     {:>6.2f}%                          │", avgRelevance * 100);
        log.info("│  引用准确率:     {:>6.2f}%                          │", avgCitation * 100);
        log.info("│  有效测试数:     {:>3d}                             │", validCases);
        log.info("└─────────────────────────────────────────────────────┘");

        // 打印低质量案例
        details.stream()
                .filter(d -> d.getRelevance() < RELEVANCE_THRESHOLD || d.getCitationAccuracy() < CITATION_THRESHOLD)
                .forEach(d -> log.warn("⚠️ 低质量回答: {} | 相关性: {:.2f} | 引用: {:.2f}",
                        d.getQuestion(), d.getRelevance(), d.getCitationAccuracy()));

        assertTrue(avgRelevance >= 0.55, "回答相关性应 >= 55%，当前: " + avgRelevance);
        assertTrue(avgCitation >= 0.50, "引用准确率应 >= 50%，当前: " + avgCitation);
    }

    @Test
    void testFullPipeline() {
        log.info("\n╔══════════════════════════════════════════════════════╗");
        log.info("║           端到端综合评估 (End-to-End)                ║");
        log.info("╚══════════════════════════════════════════════════════╝");

        int total = testCases.size();
        double totalRecall = 0;
        double totalHit = 0;
        double totalMrr = 0;
        double totalRelevance = 0;
        double totalCitation = 0;
        int validCases = 0;

        List<FullResult> details = new ArrayList<>();

        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            int chatId = ++chatIdCounter;

            // 检索阶段
            SearchRequest request = SearchRequest.builder()
                    .query(tc.getQuestion())
                    .topK(5)
                    .build();

            List<Document> retrieved = vectorStore.similaritySearch(request);

            double recall = calculateRecallAtK(retrieved, tc, 5);
            boolean hit = isHit(retrieved, tc.getExpectedSources());
            double mrr = calculateMrr(retrieved, tc.getExpectedSources());

            // 生成阶段
            String answer = aiapp.doChatWithRag(tc.getQuestion(), String.valueOf(chatId));
            double relevance = (answer != null) ? calculateSemanticSimilarity(answer, tc.getQuestion()) : 0;
            double citation = (answer != null) ? calculateCitationAccuracy(answer, tc.getQuestion()) : 0;

            totalRecall += recall;
            totalHit += hit ? 1 : 0;
            totalMrr += mrr;
            totalRelevance += relevance;
            totalCitation += citation;
            validCases++;

            details.add(FullResult.builder()
                    .question(tc.getQuestion())
                    .recall(recall)
                    .hit(hit)
                    .mrr(mrr)
                    .relevance(relevance)
                    .citationAccuracy(citation)
                    .build());
        }

        double avgRecall = totalRecall / validCases;
        double avgHit = totalHit / validCases;
        double avgMrr = totalMrr / validCases;
        double avgRelevance = totalRelevance / validCases;
        double avgCitation = totalCitation / validCases;

        double overallScore = avgRecall * 0.20 + avgHit * 0.15 + avgMrr * 0.10
                + avgRelevance * 0.30 + avgCitation * 0.25;

        log.info("\n{}", formatFullReport(total, avgRecall, avgHit, avgMrr, avgRelevance, avgCitation, overallScore));

        // 打印每个问题的详细结果
        log.info("\n========== 逐题详细结果 ==========");
        for (int i = 0; i < details.size(); i++) {
            FullResult r = details.get(i);
            log.info("[{:>2}] {} | Recall: {:.2f} | Hit: {} | MRR: {:.4f} | Rel: {:.2f} | Cit: {:.2f}",
                    i + 1,
                    r.getQuestion().length() > 30 ? r.getQuestion().substring(0, 30) + "..." : r.getQuestion(),
                    r.getRecall(), r.isHit() ? "✓" : "✗", r.getMrr(),
                    r.getRelevance(), r.getCitationAccuracy());
        }

        assertTrue(overallScore >= 0.55, "综合评分应 >= 0.55，当前: " + overallScore);
    }

    // ==================== 指标计算工具方法 ====================

    /**
     * Recall@K: 期望来源文档在 TopK 结果中的召回比例
     */
    private double calculateRecallAtK(List<Document> retrieved, TestCase tc, int k) {
        if (tc.getExpectedSources() == null || tc.getExpectedSources().isEmpty()) return 1.0;

        Set<String> expected = tc.getExpectedSources().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        long found = retrieved.stream()
                .limit(k)
                .map(d -> d.getMetadata().getOrDefault("filename", "").toString().toLowerCase())
                .filter(name -> expected.stream().anyMatch(name::contains))
                .distinct()
                .count();

        return Math.min(1.0, (double) found / expected.size());
    }

    /**
     * TopK 命中率：至少一个期望来源出现在 TopK 中
     */
    private boolean isHit(List<Document> retrieved, List<String> expectedSources) {
        if (expectedSources == null || expectedSources.isEmpty()) return true;

        return retrieved.stream()
                .map(d -> d.getMetadata().getOrDefault("filename", "").toString().toLowerCase())
                .anyMatch(name -> expectedSources.stream()
                        .anyMatch(exp -> name.contains(exp.toLowerCase())));
    }

    /**
     * MRR@K: 第一个正确答案排名的倒数
     */
    private double calculateMrr(List<Document> retrieved, List<String> expectedSources) {
        if (expectedSources == null || expectedSources.isEmpty()) return 1.0;

        for (int i = 0; i < retrieved.size(); i++) {
            String name = retrieved.get(i).getMetadata()
                    .getOrDefault("filename", "").toString().toLowerCase();
            for (String expected : expectedSources) {
                if (name.contains(expected.toLowerCase())) {
                    return 1.0 / (i + 1);
                }
            }
        }
        return 0;
    }

    /**
     * 语义相似度（余弦相似度）
     */
    private double calculateSemanticSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) return 0;

        try {
            float[] e1 = embeddingModel.embed(text1);
            float[] e2 = embeddingModel.embed(text2);
            return cosineSimilarity(e1, e2);
        } catch (Exception e) {
            log.warn("Embedding 失败，降级为 Jaccard 相似度: {}", e.getMessage());
            return jaccardSimilarity(text1, text2);
        }
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

    private double jaccardSimilarity(String s1, String s2) {
        Set<String> set1 = tokenize(s1);
        Set<String> set2 = tokenize(s2);
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String text) {
        // 中文按字，英文按词
        Set<String> tokens = new HashSet<>();
        // 提取中文字符
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                tokens.add(String.valueOf(c));
            }
        }
        // 提取英文单词和数字
        Matcher matcher = Pattern.compile("[a-zA-Z]+|\\d+").matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group().toLowerCase());
        }
        return tokens;
    }

    /**
     * 引用准确率：生成答案中的关键断言是否能在向量库中找到支持
     * 方法：提取答案中的数值/实体，在向量库中搜索验证
     */
    private double calculateCitationAccuracy(String answer, String question) {
        if (answer == null || answer.isEmpty()) return 0;

        // 提取答案中的关键断言（含数字、度量的句子）
        List<String> claims = extractClaims(answer);
        if (claims.isEmpty()) return 0.5; // 无明确断言，给中等分

        int supported = 0;
        for (String claim : claims) {
            // 在向量库中搜索该断言的相关内容
            SearchRequest evidenceRequest = SearchRequest.builder()
                    .query(claim)
                    .topK(3)
                    .build();

            List<Document> evidence = vectorStore.similaritySearch(evidenceRequest);

            if (evidence.isEmpty()) continue;

            // 检查是否有证据支持该断言
            String evidenceText = evidence.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));

            double sim = calculateSemanticSimilarity(claim, evidenceText);
            if (sim > CITATION_THRESHOLD) {
                supported++;
            }
        }

        return (double) supported / claims.size();
    }

    /**
     * 提取答案中的关键断言
     */
    private List<String> extractClaims(String answer) {
        List<String> claims = new ArrayList<>();

        // 模式1: 包含数字的句子（如 "1500-2000毫升", "38.5度"）
        Pattern numberPattern = Pattern.compile("[^。！？\n]*\\d+[\\d\\-–—.%]*[^。！？\n]*[。！？\n]");
        Matcher m1 = numberPattern.matcher(answer);
        while (m1.find()) {
            String claim = m1.group().trim();
            if (claim.length() >= 8) claims.add(claim);
        }

        // 模式2: "是/否" 判断句
        Pattern yesNoPattern = Pattern.compile("[^。！？\n]*(是|否|有效|无效|可以|不可以)[^。！？\n]*[。！？\n]");
        Matcher m2 = yesNoPattern.matcher(answer);
        while (m2.find()) {
            String claim = m2.group().trim();
            if (claim.length() >= 8 && !claims.contains(claim)) {
                claims.add(claim);
            }
        }

        // 模式3: 列表项（如 "1. xxx", "• xxx"）
        Pattern listPattern = Pattern.compile("(?:^|\\n)\\s*(?:\\d+[.、]|[-•])\\s*([^\\n]{10,})");
        Matcher m3 = listPattern.matcher(answer);
        while (m3.find()) {
            String claim = m3.group(1).trim();
            if (!claims.contains(claim)) claims.add(claim);
        }

        // 去重并限制数量
        return claims.stream().distinct().limit(5).collect(Collectors.toList());
    }

    // ==================== 报告格式化 ====================

    private String formatFullReport(int total, double recall, double hit, double mrr,
                                    double relevance, double citation, double overall) {
        return String.format("""
            
            ╔══════════════════════════════════════════════════════════════╗
            ║              医疗健康 RAG 端到端评估报告                       ║
            ╠══════════════════════════════════════════════════════════════╣
            ║  测试用例总数:           %3d                                  ║
            ╠══════════════════════════════════════════════════════════════╣
            ║  【检索阶段】                                                 ║
            ║    Recall@5:           %6.2f%%                              ║
            ║    HitRate@5:          %6.2f%%                              ║
            ║    MRR@5:              %6.4f                                ║
            ╠══════════════════════════════════════════════════════════════╣
            ║  【生成阶段】                                                 ║
            ║    回答相关性:         %6.2f%%                              ║
            ║    引用准确率:         %6.2f%%                              ║
            ╠══════════════════════════════════════════════════════════════╣
            ║  综合评分:             %6.2f%%                              ║
            ║  评级: %s
            ╚══════════════════════════════════════════════════════════════╝
            """,
                total,
                recall * 100, hit * 100, mrr,
                relevance * 100, citation * 100,
                overall * 100,
                getGrade(overall)
        );
    }

    private String getGrade(double score) {
        if (score >= 0.80) return "🌟 优秀 (Excellent)  ";
        if (score >= 0.65) return "✅ 良好 (Good)       ";
        if (score >= 0.50) return "⚠️ 一般 (Fair)       ";
        return "❌ 需改进 (Poor)      ";
    }

    // ==================== 测试用例构建 ====================

    private List<TestCase> buildTestCases() {
        return List.of(
                // === 常见感冒症状与护理.pdf ===
                TestCase.builder()
                        .question("普通感冒最常见的致病病毒是什么？")
                        .expectedAnswer("鼻病毒")
                        .expectedSources(List.of("常见感冒症状与护理"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("感冒患者每日建议饮水量是多少？")
                        .expectedAnswer("1500-2000 毫升温水")
                        .expectedSources(List.of("常见感冒症状与护理"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("体温达到多少度时建议进行退热处理？")
                        .expectedAnswer("38.5")
                        .expectedSources(List.of("常见感冒症状与护理"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("感冒出现哪些情况需要立即就医？")
                        .expectedAnswer("高热持续3天以上、呼吸困难、剧烈头痛")
                        .expectedSources(List.of("常见感冒症状与护理"))
                        .difficulty("中等").build(),

                // === 高血压基础知识.docx ===
                TestCase.builder()
                        .question("成人正常血压的标准是多少？")
                        .expectedAnswer("收缩压 < 120 mmHg，舒张压 < 80 mmHg")
                        .expectedSources(List.of("高血压基础知识"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("高血压的诊断标准是什么？")
                        .expectedAnswer("收缩压 ≥ 140 mmHg 和/或舒张压 ≥ 90 mmHg")
                        .expectedSources(List.of("高血压基础知识"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("高血压患者每日食盐摄入量应控制在多少？")
                        .expectedAnswer("5 克以内")
                        .expectedSources(List.of("高血压基础知识"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("高血压患者每周至少应进行多少分钟运动？")
                        .expectedAnswer("150 分钟")
                        .expectedSources(List.of("高血压基础知识"))
                        .difficulty("简单").build(),

                // === 合理用药安全须知.md ===
                TestCase.builder()
                        .question("全球约多少比例的药物使用不当？")
                        .expectedAnswer("50%")
                        .expectedSources(List.of("合理用药安全须知"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("儿童用药应按什么计算剂量？")
                        .expectedAnswer("体重")
                        .expectedSources(List.of("合理用药安全须知"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("抗生素对病毒感冒是否有效？")
                        .expectedAnswer("无效，仅对细菌感染有效")
                        .expectedSources(List.of("合理用药安全须知"))
                        .difficulty("中等").build(),

                // === 口腔健康与龋齿预防.docx ===
                TestCase.builder()
                        .question("龋齿形成的关键因素有哪些？")
                        .expectedAnswer("细菌、糖分、宿主（牙齿状况）和时间")
                        .expectedSources(List.of("口腔健康与龋齿预防"))
                        .difficulty("中等").build(),
                TestCase.builder()
                        .question("建议多久进行一次口腔检查？")
                        .expectedAnswer("每 6-12 个月")
                        .expectedSources(List.of("口腔健康与龋齿预防"))
                        .difficulty("简单").build(),

                // === 烧伤与烫伤急救处理.pdf ===
                TestCase.builder()
                        .question("烧伤急救的五步原则是什么？")
                        .expectedAnswer("冲、脱、泡、盖、送")
                        .expectedSources(List.of("烧伤与烫伤急救处理"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("烫伤后应冲洗多长时间？")
                        .expectedAnswer("15-30 分钟")
                        .expectedSources(List.of("烧伤与烫伤急救处理"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("烫伤后禁止涂抹哪些东西？")
                        .expectedAnswer("牙膏、酱油、香油等偏方")
                        .expectedSources(List.of("烧伤与烫伤急救处理"))
                        .difficulty("中等").build(),

                // === 失眠的自我调节方法.txt ===
                TestCase.builder()
                        .question("成年人推荐每晚睡眠几小时？")
                        .expectedAnswer("7-9 小时")
                        .expectedSources(List.of("失眠的自我调节方法"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("4-7-8 呼吸法具体怎么做？")
                        .expectedAnswer("吸气 4 秒 → 屏气 7 秒 → 呼气 8 秒")
                        .expectedSources(List.of("失眠的自我调节方法"))
                        .difficulty("中等").build(),
                TestCase.builder()
                        .question("卧室温度应保持在多少度有助于睡眠？")
                        .expectedAnswer("18-22°C")
                        .expectedSources(List.of("失眠的自我调节方法"))
                        .difficulty("简单").build(),

                // === 糖尿病饮食指南.md ===
                TestCase.builder()
                        .question("糖尿病患者每日蔬菜摄入量应达到多少？")
                        .expectedAnswer("500 克")
                        .expectedSources(List.of("糖尿病饮食指南"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("糖尿病患者碳水化合物的推荐比例是多少？")
                        .expectedAnswer("50%–60%")
                        .expectedSources(List.of("糖尿病饮食指南"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("血糖控制良好时，糖尿病患者可以吃哪些水果？")
                        .expectedAnswer("苹果、梨、柚子、草莓")
                        .expectedSources(List.of("糖尿病饮食指南"))
                        .difficulty("中等").build(),

                // === 心肺复苏操作步骤.txt ===
                TestCase.builder()
                        .question("成人心肺复苏的按压深度是多少？")
                        .expectedAnswer("5-6 厘米")
                        .expectedSources(List.of("心肺复苏操作步骤"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("心肺复苏时按压与通气的比例是多少？")
                        .expectedAnswer("30:2")
                        .expectedSources(List.of("心肺复苏操作步骤"))
                        .difficulty("简单").build(),
                TestCase.builder()
                        .question("AED 电极片应贴在什么位置？")
                        .expectedAnswer("右锁骨下、左腋中线")
                        .expectedSources(List.of("心肺复苏操作步骤"))
                        .difficulty("中等").build(),
                TestCase.builder()
                        .question("心肺复苏时，按压频率应该是多少？")
                        .expectedAnswer("100-120 次/分钟")
                        .expectedSources(List.of("心肺复苏操作步骤"))
                        .difficulty("简单").build(),

                // === 跨文档推理（高难度） ===
                TestCase.builder()
                        .question("高血压患者如果感冒发热，体温超过多少度需要处理？")
                        .expectedAnswer("38.5")
                        .expectedSources(List.of("高血压基础知识", "常见感冒症状与护理"))
                        .difficulty("困难").build(),
                TestCase.builder()
                        .question("糖尿病患者失眠时，睡前可以喝什么助眠？")
                        .expectedAnswer("温牛奶")
                        .expectedSources(List.of("糖尿病饮食指南", "失眠的自我调节方法"))
                        .difficulty("困难").build()
        );
    }

    // ==================== 数据类 ====================

    @Data
    @Builder
    static class TestCase {
        private String question;
        private String expectedAnswer;
        private List<String> expectedSources;
        private String difficulty;
    }

    @Data
    @Builder
    static class MetricResult {
        private double recallAtK;
        private double hitRateAtK;
        private double mrrAtK;
    }

    @Data
    @Builder
    static class GenerationResult {
        private String question;
        private String generatedAnswer;
        private double relevance;
        private double citationAccuracy;
    }

    @Data
    @Builder
    static class FullResult {
        private String question;
        private double recall;
        private boolean hit;
        private double mrr;
        private double relevance;
        private double citationAccuracy;
    }
}
