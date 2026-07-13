package com.example.aiagent.rag;

import com.example.aiagent.rag.document.DocumentSplitter;
import com.example.aiagent.rag.rerank.RerankVectorStore;
import com.example.aiagent.rag.rerank.Reranker;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.print.Doc;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class AiAppVectorStoreConfig {

    @Resource
    private AiAppDocumentLoder aiAppDocumentLoder;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Resource
    private DocumentSplitter documentSplitter;

    @Bean
    VectorStore aiAppVectorStore(EmbeddingModel dashscopeEmbeddingModel){
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
        List<Document> documents = aiAppDocumentLoder.loadAllDocuments();

        List<Document> chunkedDocuments = new ArrayList<>();
        for (Document doc : documents){
            chunkedDocuments.addAll(documentSplitter.split(doc, 512));
        }
        //自动补充关键词源信息
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(chunkedDocuments);
        if (documents != null && !documents.isEmpty()) {
            simpleVectorStore.add(enrichedDocuments);
        }
        return  simpleVectorStore;
    }

    @Bean
    @Primary
    public VectorStore aiAppVectorStore(
            @Qualifier("aiAppVectorStore") VectorStore delegate,
            Reranker reranker,
            @Value("${rag.rerank.recall-k:20}") int recallK,
            @Value("${rag.rerank.top-k:5}") int rerankTopK) {
        return new RerankVectorStore(delegate, reranker, recallK, rerankTopK);
    }
}
