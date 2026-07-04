package com.example.aiagent.rag.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PDF 文档加载器（使用 Apache PDFBox 或 Spring AI PDF Reader）
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.springframework.ai.reader.pdf.PagePdfDocumentReader")
public class PdfLoaderStrategy implements DocumentLoaderStrategy {

    @Override
    public boolean supports(Resource resource) {
        String filename = resource.getFilename();
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    @Override
    public List<Document> load(Resource resource, DocumentLoadOptions options) {
        try {
            // 使用 Spring AI 的 PDF Reader
            var pdfReader = new org.springframework.ai.reader.pdf.PagePdfDocumentReader(resource);
            List<Document> documents = pdfReader.get();

             //添加元数据
            documents.forEach(doc -> {
                doc.getMetadata().put("filename", resource.getFilename());
//                doc.getMetadata().put("type", "pdf");
//                doc.getMetadata().put("status", "unknown");
            });

            return documents;

        } catch (Exception e) {
            log.error("PDF文档加载失败: {}", resource.getFilename(), e);
            return new ArrayList<>();
        }
    }
}
