package com.example.aiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.print.Doc;
import java.util.List;

@Configuration
public class AiAppVectorStoreConfig {

    @Resource
    private AiAppDocumentLoder aiAppDocumentLoder;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Bean
    VectorStore aiAppVectorStore(EmbeddingModel dashscopeEmbeddingModel){
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
        List<Document> documents = aiAppDocumentLoder.loadAllDocuments();
        //自动补充关键词源信息
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documents);
        if (documents != null && !documents.isEmpty()) {
            simpleVectorStore.add(enrichedDocuments);
        }
        return  simpleVectorStore;
    }
}
