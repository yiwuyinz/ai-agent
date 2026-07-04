package com.example.aiagent.rag.selfcorrect;

public interface SelfCorrectingRAG {
    /**
     * 执行自纠错 RAG 查询
     * @param userQuery 用户原始提问
     * @param chatId 会话ID（用于多轮对话）
     * @return 包含纠错过程和最终答案的结果
     */
    SelfCorrectResult query(String userQuery, String chatId);
}
