package com.example.aiagent.rag.selfcorrect;

import lombok.Builder;
import lombok.Data;
import org.springframework.ai.document.Document;

import java.util.List;

@Data
@Builder
public class SelfCorrectResult {
    private String finalAnswer;           // 最终答案
    private String originalAnswer;        // 原始生成答案（未纠错）
    private List<CorrectionStep> corrections; // 纠错步骤记录
    private boolean wasCorrected;         // 是否经过纠错
    private double confidence;            // 置信度
    private List<Document> usedDocuments; // 最终使用的文档
}
