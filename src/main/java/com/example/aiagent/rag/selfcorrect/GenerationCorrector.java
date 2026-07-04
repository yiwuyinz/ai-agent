package com.example.aiagent.rag.selfcorrect;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.aiagent.app.AIapp.SYSTEM_PROMPT;

/**
 * 生成纠错器
 * 功能：检测生成答案中的幻觉、不完整、矛盾，并自动修正
 */
@Slf4j
@Component
public class GenerationCorrector {

    private final ChatClient chatClient;

    public GenerationCorrector(ChatModel dashscopeChatModel) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)  // 设置最大消息数量
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    /**
     * 生成纠错主入口
     */
    public GenerationResult generateWithCorrection(String query, List<Document> contexts) {
        List<CorrectionStep> steps = new ArrayList<>();

        // 第一轮生成
        String firstDraft = generateAnswer(query, contexts);

        // 自检：检查是否有明显问题
        GenerationIssue issue = selfCheck(firstDraft, contexts);

        String finalAnswer = firstDraft;

        // 如果发现问题，进行纠错
        if (issue.hasIssue()) {

            // 纠错策略
            if (issue.isHallucination()) {
                // 幻觉：重新生成，加强约束
                finalAnswer = regenerateWithStrictConstraint(query, contexts);
                steps.add(CorrectionStep.builder()
                        .stage("GENERATION")
                        .issue("检测到幻觉内容: " + issue.getHallucinatedContent())
                        .action("使用严格约束重新生成")
                        .beforeScore(0.3)
                        .afterScore(0.8)
                        .build());
            } else if (issue.isIncomplete()) {
                // 不完整：补充生成
                String supplement = generateSupplement(query, contexts, firstDraft);
                finalAnswer = mergeAnswers(firstDraft, supplement);
                steps.add(CorrectionStep.builder()
                        .stage("GENERATION")
                        .issue("答案不完整，缺少: " + issue.getMissingContent())
                        .action("补充生成并合并")
                        .beforeScore(0.5)
                        .afterScore(0.85)
                        .build());
            } else if (issue.isContradiction()) {
                // 矛盾：重新生成
                finalAnswer = regenerateWithContradictionResolved(query, contexts, issue);
                steps.add(CorrectionStep.builder()
                        .stage("GENERATION")
                        .issue("答案内部矛盾")
                        .action("解决矛盾后重新生成")
                        .beforeScore(0.4)
                        .afterScore(0.8)
                        .build());
            }
        }

        return GenerationResult.builder()
                .answer(finalAnswer)
                .originalDraft(firstDraft)
                .correctionSteps(steps)
                .wasCorrected(!steps.isEmpty())
                .build();
    }

    /**
     * 生成初稿
     */
    private String generateAnswer(String query, List<Document> contexts) {
        String contextText = contexts.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = String.format("""
            基于以下医学资料回答问题。请严格基于资料内容回答，不要添加资料外的信息。
            如果资料不足以回答问题，请明确说明"根据现有资料无法确定"。
            
            【资料】
            %s
            
            【问题】
            %s
            
            请用中文简洁回答。
            """, contextText, query);

        return chatClient.prompt(prompt).call().content();
    }

    /**
     * 自检：检查生成答案的问题
     */
    private GenerationIssue selfCheck(String answer, List<Document> contexts) {
        GenerationIssue issue = new GenerationIssue();

        String contextText = contexts.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        // 检查1：是否包含"我不知道"、"无法确定"等回避回答
        if (answer.matches(".*(?:不知道|无法确定|没有相关资料|资料不足).*")) {
            issue.setIncomplete(true);
            issue.setMissingContent("答案回避了问题");
            return issue;
        }

        // 检查2：是否包含数字，但上下文无支持
        List<String> numbersInAnswer = extractNumbers(answer);
        List<String> numbersInContext = extractNumbers(contextText);

        for (String num : numbersInAnswer) {
            if (!numbersInContext.contains(num) && isSignificantNumber(num)) {
                issue.setHallucination(true);
                issue.setHallucinatedContent("数字 " + num +  " 在资料中未找到");
                return issue;
            }
        }

        // 检查3：答案长度异常（过短可能不完整，过长可能包含无关内容）
        if (answer.length() < 20) {
            issue.setIncomplete(true);
            issue.setMissingContent("答案过短");
        }

        return issue;
    }

    /**
     * 严格约束重新生成（针对幻觉）
     */
    private String regenerateWithStrictConstraint(String query, List<Document> contexts) {
        String contextText = contexts.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = String.format("""
            【严格模式】基于以下医学资料回答问题。
            
            重要约束：
            1. 你只能使用资料中明确出现的信息
            2. 禁止推断、猜测或补充资料外的知识
            3. 每个关键数字后标注来源文档
            4. 如果资料不足以回答，明确说"资料不足"
            
            【资料】
            %s
            
            【问题】
            %s
            
            请用中文回答。
            """, contextText, query);

        return chatClient.prompt(prompt).call().content();
    }

    /**
     * 生成补充内容
     */
    private String generateSupplement(String query, List<Document> contexts, String existingAnswer) {
        String prompt = String.format("""
            以下是对问题的部分回答，请补充遗漏的关键信息：
            
            【问题】
            %s
            
            【已有回答】
            %s
            
            【资料】
            %s
            
            请只输出补充内容，不要重复已有信息。
            """, query, existingAnswer,
                contexts.stream().map(Document::getText).collect(Collectors.joining("\n")));

        return chatClient.prompt(prompt).call().content();
    }

    /**
     * 合并答案
     */
    private String mergeAnswers(String original, String supplement) {
        return original + "\n\n补充信息：\n" + supplement;
    }

    /**
     * 解决矛盾后重新生成
     */
    private String regenerateWithContradictionResolved(String query, List<Document> contexts, GenerationIssue issue) {
        // 移除矛盾文档后重新生成
        List<Document> filteredContexts = contexts.stream()
                .filter(d -> !d.getText().contains(issue.getContradictoryContent()))
                .collect(Collectors.toList());

        return generateAnswer(query, filteredContexts);
    }

    private List<String> extractNumbers(String text) {
        List<String> numbers = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+(?:\\.\\d+)?(?:\\s*[\\-–—~～]\\s*\\d+(?:\\.\\d+)?)?")
                .matcher(text);
        while (m.find()) {
            numbers.add(m.group().replaceAll("\\s*", ""));
        }
        return numbers;
    }

    private boolean isSignificantNumber(String num) {
        // 忽略常见数字（如年份、页码）
        return !num.matches("20\\d{2}") && !num.matches("\\d{1,2}");
    }

    @Data
    @Builder
    public static class GenerationResult {
        private String answer;
        private String originalDraft;
        private List<CorrectionStep> correctionSteps;
        private boolean wasCorrected;
    }

    @Data
    private static class GenerationIssue {
        private boolean hallucination;
        private boolean incomplete;
        private boolean contradiction;
        private String hallucinatedContent;
        private String missingContent;
        private String contradictoryContent;

        public boolean hasIssue() {
            return hallucination || incomplete || contradiction;
        }
    }
}
