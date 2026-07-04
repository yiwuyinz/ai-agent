package com.example.aiagent.rag.selfcorrect;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 事实验证器
 * 功能：验证生成答案中的每个事实性声明
 */
@Slf4j
@Component
public class FactVerifier {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    // 验证阈值
    private static final double FACT_SIMILARITY_THRESHOLD = 0.30;
    private static final double OVERALL_CONFIDENCE_THRESHOLD = 0.75;

    public FactVerifier(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 事实验证主入口
     */
    public VerificationResult verify(String answer, String query, List<Document> sources) {
        List<FactCheck> factChecks = new ArrayList<>();
        List<CorrectionStep> steps = new ArrayList<>();

        // 提取答案中的事实性声明
        List<String> claims = extractFactualClaims(answer);

        int supportedCount = 0;
        int totalClaims = claims.size();

        for (String claim : claims) {
            FactCheck check = verifySingleClaim(claim, sources);
            factChecks.add(check);

            if (check.isSupported()) {
                supportedCount++;
            } else {
                log.warn("事实未通过验证: {} | 原因: {}", claim, check.getReason());
            }
        }

        double confidence = totalClaims > 0 ? (double) supportedCount / totalClaims : 0;

        // 如果置信度不足，尝试修正
        String correctedAnswer = answer;
        if (confidence < OVERALL_CONFIDENCE_THRESHOLD && !claims.isEmpty()) {
            log.warn("整体置信度不足 ({})，触发答案修正", confidence);

            // 过滤掉未验证通过的声明
            correctedAnswer = refineAnswer(answer, factChecks);

            // 如果修正后答案质量仍不足，标记为不确定
            if (correctedAnswer.length() < answer.length() * 0.5) {
                correctedAnswer = answer + "\n\n【系统提示】以上回答中的部分信息未能通过资料验证，建议您咨询专业医生。";
            }

            steps.add(CorrectionStep.builder()
                    .stage("FACT_CHECK")
                    .issue("部分事实声明未通过验证")
                    .action("过滤未验证内容并添加提示")
                    .beforeScore(confidence)
                    .afterScore((double) supportedCount / Math.max(1, totalClaims))
                    .build());
        }

        return VerificationResult.builder()
                .finalAnswer(correctedAnswer)
                .originalAnswer(answer)
                .confidence(confidence)
                .factChecks(factChecks)
                .wasCorrected(confidence < OVERALL_CONFIDENCE_THRESHOLD)
                .correctionSteps(steps)
                .build();
    }

    /**
     * 验证单个事实声明
     */
    private FactCheck verifySingleClaim(String claim, List<Document> sources) {
        // 方法1：在源文档中搜索相关段落
        String sourceText = sources.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        // 方法2：向量搜索获取最相关证据
        List<Document> evidence = vectorStore.similaritySearch(
                SearchRequest.builder().query(claim).topK(3).build()
        );

        String evidenceText = evidence.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        // 方法3：语义相似度验证
        double similarity = calculateSemanticSimilarity(claim, evidenceText);


        boolean supported = similarity > FACT_SIMILARITY_THRESHOLD;

        return FactCheck.builder()
                .claim(claim)
                .supported(supported)
                .similarityScore(similarity)
                .evidence(evidenceText.substring(0, Math.min(200, evidenceText.length())))
                .reason(supported ? "语义相似" : "语义相似度不足")
                .build();
    }


    /**
     * 提取事实性声明
     */
    private List<String> extractFactualClaims(String answer) {
        List<String> claims = new ArrayList<>();

        // 模式1: 包含数字的度量声明
        Pattern metricPattern = Pattern.compile(
                "[^。！？\n]*(?:为|是|应|需|推荐|建议|标准|正常|超过|低于|等于)[^。！？\n]*\\d+[\\w\\-%]*[^。！？\n]*[。！？]"
        );
        Matcher m1 = metricPattern.matcher(answer);
        while (m1.find()) {
            claims.add(m1.group().trim());
        }

        // 模式2: 因果关系声明
        Pattern causalPattern = Pattern.compile(
                "[^。！？\n]*(?:导致|引起|造成|预防|治疗|缓解)[^。！？\n]*[。！？]"
        );
        Matcher m2 = causalPattern.matcher(answer);
        while (m2.find()) {
            String claim = m2.group().trim();
            if (!claims.contains(claim)) claims.add(claim);
        }

        // 模式3: 禁止/必须声明
        Pattern mandatoryPattern = Pattern.compile(
                "[^。！？\n]*(?:禁止|必须|应当|不应|不能|避免)[^。！？\n]*[。！？]"
        );
        Matcher m3 = mandatoryPattern.matcher(answer);
        while (m3.find()) {
            String claim = m3.group().trim();
            if (!claims.contains(claim)) claims.add(claim);
        }

        return claims.stream().distinct().limit(10).collect(Collectors.toList());
    }

    /**
     * 修正答案：过滤未验证的声明
     */
    private String refineAnswer(String answer, List<FactCheck> factChecks) {
        String refined = answer;

        for (FactCheck check : factChecks) {
            if (!check.isSupported()) {
                // 将未验证的声明标记为[待核实]
                refined = refined.replace(check.getClaim(),
                        "[待核实] " + check.getClaim());
            }
        }

        return refined;
    }

    private double calculateSemanticSimilarity(String text1, String text2) {
        try {
            float[] e1 = embeddingModel.embed(text1);
            float[] e2 = embeddingModel.embed(text2);
            return cosineSimilarity(e1, e2);
        } catch (Exception e) {
            return 0;
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

    @Data
    @Builder
    public static class VerificationResult {
        private String finalAnswer;
        private String originalAnswer;
        private double confidence;
        private List<FactCheck> factChecks;
        private boolean wasCorrected;
        private List<CorrectionStep> correctionSteps;
    }

    @Data
    @Builder
    public static class FactCheck {
        private String claim;
        private boolean supported;
        private double similarityScore;
        private boolean llmVerified;
        private String evidence;
        private String reason;
    }
}
