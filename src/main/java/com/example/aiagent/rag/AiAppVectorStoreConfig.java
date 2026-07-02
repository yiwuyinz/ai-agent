package com.example.aiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiAppVectorStoreConfig {

    @Resource
    private AiAppDocumentLoder aiAppDocumentLoder;

    @Bean
    VectorStore aiAppVectorStore(EmbeddingModel dashscopeEmbeddingModel){
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
        List<Document> documents = aiAppDocumentLoder.loadMarkdowns();
//        simpleVectorStore.add(documents);

        if (documents != null && !documents.isEmpty()) {
            simpleVectorStore.add(documents);
        }
        return  simpleVectorStore;
    }
}
