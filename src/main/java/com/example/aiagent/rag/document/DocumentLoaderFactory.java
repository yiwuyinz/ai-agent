package com.example.aiagent.rag.document;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentLoaderFactory {

    private final List<DocumentLoaderStrategy> strategies;

    public DocumentLoaderFactory(List<DocumentLoaderStrategy> strategies) {
        this.strategies = strategies;
    }

    public DocumentLoaderStrategy getStrategy(Resource resource) {
        return strategies.stream()
                .filter(s -> s.supports(resource))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "不支持的文档类型: " + resource.getFilename()
                ));
    }

    public boolean isSupported(Resource resource) {
        return strategies.stream().anyMatch(s -> s.supports(resource));
    }
}
