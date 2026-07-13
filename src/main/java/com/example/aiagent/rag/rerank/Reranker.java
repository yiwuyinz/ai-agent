package com.example.aiagent.rag.rerank;

import org.springframework.ai.document.Document;

import java.util.List;

public interface Reranker {
    List<Document> rerank(String query, List<Document> candidates, int topN);
}
