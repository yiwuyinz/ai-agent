package com.example.aiagent.rag.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯文本文档加载器
 */
@Slf4j
@Component
public class TextLoaderStrategy implements DocumentLoaderStrategy {

    @Override
    public boolean supports(Resource resource) {
        String filename = resource.getFilename();
        return filename != null && (
                filename.toLowerCase().endsWith(".txt") ||
                        filename.toLowerCase().endsWith(".text")
        );
    }

    @Override
    public List<Document> load(Resource resource, DocumentLoadOptions options) {
        try {
            String content = new String(
                    resource.getInputStream().readAllBytes(),
                    Charset.forName(options.getCharset())
            );

            Document document = new Document(content);
            document.getMetadata().put("filename", resource.getFilename());
//            document.getMetadata().put("type", "text");
//            document.getMetadata().put("status", "unknown");

            // 添加额外元数据
            if (options.getAdditionalMetadata() != null) {
                document.getMetadata().putAll(options.getAdditionalMetadata());
            }

            return List.of(document);

        } catch (IOException e) {
            log.error("文本文档加载失败: {}", resource.getFilename(), e);
            return new ArrayList<>();
        }
    }
}
