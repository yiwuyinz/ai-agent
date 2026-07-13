package com.example.aiagent.rag.rerank;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

public class RerankVectorStore implements VectorStore {

    private final VectorStore delegate;
    private final Reranker reranker;
    private final int recallK;
    private final int rerankTopK;

    public RerankVectorStore(VectorStore delegate, Reranker reranker, int recallK, int rerankTopK) {
        this.delegate = delegate;
        this.reranker = reranker;
        this.recallK = recallK;
        this.rerankTopK = rerankTopK;
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        SearchRequest recallRequest = SearchRequest.builder()
                .query(request.getQuery())
                .topK(Math.max(request.getTopK(), recallK))
                .similarityThreshold(request.getSimilarityThreshold())
                .build();

        List<Document> candidates = delegate.similaritySearch(recallRequest);
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        int finalTopK = Math.min(request.getTopK(), rerankTopK);
        return reranker.rerank(request.getQuery(), candidates, finalTopK);
    }

    @Override
    public void add(List<Document> documents) {
        delegate.add(documents);
    }

    @Override
    public void delete(List<String> idList) {
        delegate.delete(idList);
    }

    @Override
    public void delete(String id) {
        delegate.delete(id);
    }
    @Override
    public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
        delegate.delete(filterExpression);
    }
}
