package com.example.aiagent.rag.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Word 文档加载器（DOCX）
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.poi.xwpf.usermodel.XWPFDocument")
public class WordLoaderStrategy implements DocumentLoaderStrategy {

    @Override
    public boolean supports(Resource resource) {
        String filename = resource.getFilename();
        return filename != null && (
                filename.toLowerCase().endsWith(".docx")
        );
    }

    @Override
    public List<Document> load(Resource resource, DocumentLoadOptions options) {
        try (InputStream is = resource.getInputStream()) {
            StringBuilder content = new StringBuilder();

            if (resource.getFilename().toLowerCase().endsWith(".docx")) {
                XWPFDocument document = new XWPFDocument(is);
                for (XWPFParagraph para : document.getParagraphs()) {
                    content.append(para.getText()).append("\n");
                }
            }
            Document doc = new Document(content.toString());
            doc.getMetadata().put("filename", resource.getFilename());
//            doc.getMetadata().put("type", "word");
//            doc.getMetadata().put("status", "unknown");

            return List.of(doc);

        } catch (IOException e) {
            log.error("Word文档加载失败: {}", resource.getFilename(), e);
            return new ArrayList<>();
        }
    }
}
