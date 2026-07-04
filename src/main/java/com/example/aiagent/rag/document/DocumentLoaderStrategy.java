package com.example.aiagent.rag.document;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.util.List;

public interface DocumentLoaderStrategy {
    /**
     * 是否支持该资源
     */
    boolean supports(Resource resource);

    /**
     * 加载文档
     */
    List<Document> load(Resource resource, DocumentLoadOptions options);

}
