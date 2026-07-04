package com.example.aiagent.rag.selfcorrect;

import com.example.aiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 自纠错 RAG 主服务
 */
@Slf4j
@Service
public class SelfCorrectingRAGService implements SelfCorrectingRAG {

    private final QueryCorrector queryCorrector;
    private final RetrievalCorrector retrievalCorrector;
    private final GenerationCorrector generationCorrector;

    @Resource
    private QueryRewriter queryRewriter;

    public SelfCorrectingRAGService(
            QueryCorrector queryCorrector,
            RetrievalCorrector retrievalCorrector,
            GenerationCorrector generationCorrector) {
        this.queryCorrector = queryCorrector;
        this.retrievalCorrector = retrievalCorrector;
        this.generationCorrector = generationCorrector;
    }

    @Override
    public SelfCorrectResult query(String userQuery, String chatId) {
        log.info("========== 自纠错 RAG 开始 ==========");
        log.info("用户查询: {}", userQuery);

        List<CorrectionStep> allCorrections = new ArrayList<>();

        // ========== 第一层：查询纠错 ==========
        log.info("--- 第一层：查询纠错 ---");
        String rewrittenQuery = queryRewriter.doQueryRewrite(userQuery);
        QueryCorrector.CorrectedQuery correctedQuery = queryCorrector.correct(rewrittenQuery);
        allCorrections.addAll(correctedQuery.getCorrectionSteps());

        log.info("扩展查询数: {}", correctedQuery.getExpandedQueries().size());

        // ========== 第二层：检索纠错 ==========
        log.info("--- 第二层：检索纠错 ---");
        RetrievalCorrector.RetrievalResult retrievalResult =
                retrievalCorrector.retrieveWithCorrection(correctedQuery.getExpandedQueries());
        allCorrections.addAll(retrievalResult.getCorrectionSteps());

        log.info("检索文档数: {}", retrievalResult.getDocumentCount());
        log.info("检索质量分: {}", retrievalResult.getQualityScore());

        List<Document> contexts = retrievalResult.getDocuments();

        // ========== 第三层：生成纠错 ==========
        log.info("--- 第三层：生成纠错 ---");
        GenerationCorrector.GenerationResult generationResult =
                generationCorrector.generateWithCorrection(rewrittenQuery, contexts);
        allCorrections.addAll(generationResult.getCorrectionSteps());

        log.info("是否纠错: {}", generationResult.isWasCorrected());

        // ========== 组装结果 ==========
        double overallConfidence = calculateOverallConfidence(
                retrievalResult.getQualityScore(),
                allCorrections.size()
        );

        log.info("========== 自纠错 RAG 完成，总体置信度: {} ==========", overallConfidence);

        return SelfCorrectResult.builder()
                .finalAnswer(generationResult.getAnswer())
                .originalAnswer(generationResult.getOriginalDraft())
                .corrections(allCorrections)
                .wasCorrected(!allCorrections.isEmpty())
                .confidence(overallConfidence)
                .usedDocuments(contexts)
                .build();
    }

    private double calculateOverallConfidence(double retrievalScore, int correctionCount) {
        // 基础分
        double base = retrievalScore;
        // 纠错次数惩罚（纠错越多，置信度越低）
        double penalty = Math.min(correctionCount * 0.05, 0.2);

        return Math.max(0, base - penalty);
    }
}
