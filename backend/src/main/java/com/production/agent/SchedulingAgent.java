package com.production.agent;

import com.production.domain.ProductionOrder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SchedulingAgent {

    private final ChatLanguageModel llm;
    private final boolean aiEnabled;

    public SchedulingAgent(@Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
                           @Value("${ollama.model:llama3.2}") String modelName,
                           @Value("${ollama.temperature:0.2}") double temperature) {
        ChatLanguageModel tmpLlm = null;
        boolean tmpEnabled = false;
        try {
            tmpLlm = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(temperature)
                    .build();
            tmpEnabled = true;
            log.info("AI agent enabled with Ollama model: {} at {}", modelName, baseUrl);
        } catch (Exception e) {
            log.warn("Failed to initialize Ollama: {}", e.getMessage());
        }
        this.llm = tmpLlm;
        this.aiEnabled = tmpEnabled;
    }

    public String analyseProductionOrders(List<ProductionOrder> orders) {
        if (!aiEnabled || llm == null) {
            return buildRuleBasedInsights(orders);
        }
        try {
            String summary = orders.stream()
                .map(po -> String.format("%s: qty=%d, status=%s, finish=%s",
                    po.getProductionOrderId(), po.getPlannedQuantity(), po.getStatus(), po.getActualFinishDate()))
                .collect(Collectors.joining("\n"));
            String prompt = "Analyze these production orders and give recommendations:\n" + summary;
            Response<AiMessage> response = llm.generate(List.of(
                SystemMessage.from("You are a production scheduler expert."),
                UserMessage.from(prompt)
            ));
            return response.content().text();
        } catch (Exception e) {
            return buildRuleBasedInsights(orders);
        }
    }

    private String buildRuleBasedInsights(List<ProductionOrder> orders) {
        long delayed = orders.stream().filter(po -> "DELAYED".equals(po.getStatus())).count();
        return String.format("Rule-based insights: %d out of %d production orders are delayed. Please check capacity constraints and due dates.", delayed, orders.size());
    }
}