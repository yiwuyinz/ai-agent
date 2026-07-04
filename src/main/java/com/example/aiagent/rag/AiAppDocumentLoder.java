package com.example.aiagent.rag;

import com.example.aiagent.rag.document.DocumentLoadOptions;
import com.example.aiagent.rag.document.DocumentLoaderFactory;
import com.example.aiagent.rag.document.DocumentLoaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
class AiAppDocumentLoder {

    private final ResourcePatternResolver resourcePatternResolver;
    private final DocumentLoaderFactory loaderFactory;

    // 支持的路径配置（可从配置文件注入）
    @Value("${ai.document.paths:classpath:document/*}")
    private String[] documentPaths;

    @Value("${ai.document.supported-extensions:md,txt,pdf,docx,html,htm}")
    private String supportedExtensions;

    AiAppDocumentLoder(
            ResourcePatternResolver resourcePatternResolver,
            DocumentLoaderFactory loaderFactory) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.loaderFactory = loaderFactory;
    }

    /**
     * 加载所有配置的文档
     */
    public List<Document> loadAllDocuments() {
        List<Document> allDocuments = new ArrayList<>();

        for (String path : documentPaths) {
            allDocuments.addAll(loadFromPath(path));
        }

        log.info("共加载 {} 个文档", allDocuments.size());
        return allDocuments;
    }

    /**
     * 从指定路径加载文档
     */
    public List<Document> loadFromPath(String pathPattern) {
        List<Document> documents = new ArrayList<>();

        try {
            Resource[] resources = resourcePatternResolver.getResources(pathPattern);
            log.info("路径 {} 发现 {} 个资源", pathPattern, resources.length);

            for (Resource resource : resources) {
                if (!resource.exists() || !resource.isReadable()) {
                    log.warn("资源不可读: {}", resource.getFilename());
                    continue;
                }

                try {
                    List<Document> docs = loadResource(resource);
                    documents.addAll(docs);
                } catch (Exception e) {
                    log.error("加载资源失败: {}", resource.getFilename(), e);
                }
            }

        } catch (IOException e) {
            log.error("资源解析失败: {}", pathPattern, e);
        }

        return documents;
    }

    /**
     * 加载单个资源
     */
    public List<Document> loadResource(Resource resource) {
        if (!loaderFactory.isSupported(resource)) {
            log.warn("不支持的文档类型，跳过: {}", resource.getFilename());
            return new ArrayList<>();
        }

        DocumentLoaderStrategy strategy = loaderFactory.getStrategy(resource);
        DocumentLoadOptions options = DocumentLoadOptions.defaultOptions();

        return strategy.load(resource, options);
    }
}
