package com.example.aiagent.rag.document;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DocumentSplitter {

    // 分隔符优先级：段落 > 句子 > 单词 > 字符
    private static final List<String> SEPARATORS = List.of("\n\n", "\n", ". ","。", " ", "");

    /**
     * 递归切分文档
     * @param document 原始文档
     * @param maxTokens 最大 token 数（建议 400-512）
     */
    public List<Document> split(Document document, int maxTokens) {
        String text = document.getText();
        List<String> chunks = splitRecursive(text, SEPARATORS, maxTokens);

        // 处理重叠
        List<Document> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            // 附加元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", document.getMetadata().getOrDefault("source", "unknown"));
            metadata.put("chunk_index", String.valueOf(i));
            metadata.put("chunk_total", String.valueOf(chunks.size()));
            metadata.put("heading_path", extractHeadingPath(document));

            Document doc = new Document(chunk, metadata);
            result.add(doc);
        }
        return result;
    }

    private List<String> splitRecursive(String text, List<String> separators, int maxTokens) {
        if (countTokens(text) <= maxTokens || separators.isEmpty()) {
            return text.isBlank() ? List.of() : List.of(text.trim());
        }

        String sep = separators.get(0);
        List<String> parts = sep.equals("")
                ? List.of(text) // 最后一个兜底分隔符
                : Arrays.asList(text.split(sep, -1));

        List<String> chunks = new ArrayList<>();
        for (String part : parts) {
            if (countTokens(part) <= maxTokens) {
                if (!part.isBlank()) chunks.add(part.trim());
            } else {
                chunks.addAll(splitRecursive(part, separators.subList(1, separators.size()), maxTokens));
            }
        }
        return chunks;
    }

    private int countTokens(String text) {
        // 简单估算：中文 1 字 ≈ 1 token，英文 1 词 ≈ 1.3 token
        // 生产环境建议用 jtokkit 或调用 EmbeddingModel 的 tokenizer
        return text.length();
    }

    private String extractHeadingPath(Document doc) {
        // 从 metadata 或文本中提取标题层级
        return doc.getMetadata().getOrDefault("heading", "").toString();
    }
}
