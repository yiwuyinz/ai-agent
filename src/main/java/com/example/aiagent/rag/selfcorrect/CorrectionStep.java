package com.example.aiagent.rag.selfcorrect;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CorrectionStep {
    private String stage;       // 纠错阶段：QUERY_REWRITE / RETRIEVAL / GENERATION / FACT_CHECK
    private String issue;       // 发现的问题
    private String action;      // 采取的纠错动作
    private double beforeScore; // 纠错前分数
    private double afterScore;  // 纠错后分数
}
