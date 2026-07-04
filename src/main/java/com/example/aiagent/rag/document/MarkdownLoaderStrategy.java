package com.example.aiagent.rag.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MarkdownLoaderStrategy implements DocumentLoaderStrategy {

    @Override
    public boolean supports(Resource resource) {
        String filename = resource.getFilename();
        return filename != null && filename.toLowerCase().endsWith(".md");
    }

    @Override
    public List<Document> load(Resource resource, DocumentLoadOptions options) {
        try {
            String fileName = resource.getFilename();
            String status = extractStatus(fileName);

            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(options.isHorizontalRuleCreateDocument())
                    .withIncludeCodeBlock(options.isIncludeCodeBlock())
                    .withIncludeBlockquote(options.isIncludeBlockquote())
                    .withAdditionalMetadata("filename", fileName)
//                    .withAdditionalMetadata("status", status)
//                    .withAdditionalMetadata("type", "markdown")
                    .build();

            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
            return reader.get();

        } catch (Exception e) {
            log.error("Markdown文档加载失败: {}", resource.getFilename(), e);
            return new ArrayList<>();
        }
    }

    private String extractStatus(String fileName) {
        // 从文件名提取状态，如 doc-01-draft.md -> draft
        if (fileName == null || fileName.length() < 7) return "unknown";
        // 支持多种命名规则
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 6) {
            String name = fileName.substring(0, lastDot);
            if (name.length() >= 2) {
                return name.substring(name.length() - 2);
            }
        }
        return "unknown";
    }
}
