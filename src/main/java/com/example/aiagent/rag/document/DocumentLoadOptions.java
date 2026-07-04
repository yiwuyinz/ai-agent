package com.example.aiagent.rag.document;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class DocumentLoadOptions {
    private String filename;
    private String status;
    private boolean includeCodeBlock;
    private boolean includeBlockquote;
    private boolean horizontalRuleCreateDocument;
    private String charset;  // 编码，用于TXT等
    private Map<String, Object> additionalMetadata;

    public static DocumentLoadOptions defaultOptions() {
        return DocumentLoadOptions.builder()
                .includeCodeBlock(false)
                .includeBlockquote(false)
                .horizontalRuleCreateDocument(true)
                .charset("UTF-8")
                .additionalMetadata(new HashMap<>())
                .build();
    }
}
