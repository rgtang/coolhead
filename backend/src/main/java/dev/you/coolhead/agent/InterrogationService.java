package dev.you.coolhead.agent;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.you.coolhead.config.PolicyProperties;
import dev.you.coolhead.order.OrderRepository;
import dev.you.coolhead.order.PurchaseOrder;

/**
 * Layer 2 — a short multi-turn conversation whose only job is to separate real urgency from manufactured urgency.
 * <p>
 * The transcript lives on the order (JSON column) so the verdict step and the dashboard can show it.
 * The LLM never sees PII beyond what the user types; hard numbers come from {@link FactCollector}.
 */
@Service
public class InterrogationService {

    private static final Logger log = LoggerFactory.getLogger(InterrogationService.class);
    private static final TypeReference<List<Turn>> TURNS = new TypeReference<>() { };

    private final ChatClient chat;
    private final FactCollector facts;
    private final OrderRepository orders;
    private final ObjectMapper mapper;
    private final PolicyProperties policy;

    public InterrogationService(@Qualifier("interrogatorClient") ChatClient chat, FactCollector facts,
                                OrderRepository orders, ObjectMapper mapper, PolicyProperties policy) {
        this.chat = chat;
        this.facts = facts;
        this.orders = orders;
        this.mapper = mapper;
        this.policy = policy;
    }

    /** Start the conversation: feed facts, get the first question. */
    @Transactional
    public Reply open(PurchaseOrder order) {
        List<Turn> transcript = new ArrayList<>();
        transcript.add(Turn.system(facts.collect(order).render()
                + "\nThe user just authorized this purchase. Ask your first question."));
        return ask(order, transcript);
    }

    /** Append the user's answer; either ask the next question or signal that we're ready for a verdict. */
    @Transactional
    public Reply reply(PurchaseOrder order, String userText) {
        List<Turn> transcript = transcript(order);
        transcript.add(Turn.user(userText));

        long answered = transcript.stream().filter(t -> "user".equals(t.role())).count();
        if (answered >= policy.maxInterrogationTurns()) {
            save(order, transcript);
            return new Reply(null, true, "LIMIT", transcript);
        }
        return ask(order, transcript);
    }

    public List<Turn> transcript(PurchaseOrder order) {
        if (order.getTranscriptJson() == null || order.getTranscriptJson().isBlank()) return new ArrayList<>();
        try {
            return new ArrayList<>(mapper.readValue(order.getTranscriptJson(), TURNS));
        } catch (Exception e) {
            log.warn("corrupt transcript on order {}", order.getId(), e);
            return new ArrayList<>();
        }
    }

    /** Human-readable transcript for the judge prompt. */
    public String renderTranscript(PurchaseOrder order) {
        StringBuilder sb = new StringBuilder("TRANSCRIPT:\n");
        for (Turn t : transcript(order)) {
            if ("system".equals(t.role())) continue;
            sb.append("assistant".equals(t.role()) ? "Coolhead: " : "User: ").append(t.text()).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------

    private Reply ask(PurchaseOrder order, List<Turn> transcript) {
        try {
            LlmTurn out = chat.prompt()
                    .messages(toMessages(transcript))
                    .call()
                    .entity(LlmTurn.class);

            if (out == null || out.question() == null || out.question().isBlank()) {
                throw new IllegalStateException("empty interrogator output");
            }
            transcript.add(Turn.assistant(out.question()));
            save(order, transcript);
            return new Reply(out.question(), out.done(), out.urgencySignal(), transcript);
        } catch (Exception e) {
            log.warn("interrogator unavailable for order {}: {}", order.getId(), e.toString());
            save(order, transcript);
            return new Reply(null, true, "UNAVAILABLE", transcript);
        }
    }

    private List<Message> toMessages(List<Turn> transcript) {
        List<Message> out = new ArrayList<>();
        for (Turn t : transcript) {
            switch (t.role()) {
                case "assistant" -> out.add(new AssistantMessage(t.text()));
                default -> out.add(new UserMessage(t.text())); // "system" facts travel as a user message after the default system prompt
            }
        }
        return out;
    }

    private void save(PurchaseOrder order, List<Turn> transcript) {
        try {
            int turns = (int) transcript.stream().filter(t -> "user".equals(t.role())).count();
            order.recordTranscript(mapper.writeValueAsString(transcript), turns);
            orders.save(order);
        } catch (Exception e) {
            throw new IllegalStateException("cannot persist transcript", e);
        }
    }

    // ------------------------------------------------------------------------------------------------

    /** One line of the conversation. role ∈ {system, assistant, user}. */
    public record Turn(String role, String text) {
        static Turn system(String t) { return new Turn("system", t); }
        static Turn assistant(String t) { return new Turn("assistant", t); }
        static Turn user(String t) { return new Turn("user", t); }
    }

    /** Structured output the interrogator LLM must emit each turn. */
    public record LlmTurn(
            @JsonPropertyDescription("The single next question to ask, under 30 words.") String question,
            @JsonPropertyDescription("true when you have enough to judge, or the need is clearly concrete.") boolean done,
            @JsonPropertyDescription("LOW, MEDIUM or HIGH — how manufactured the urgency looks so far.") String urgencySignal) {
    }

    /** What the controller returns to the UI. {@code question} is null when {@code done}. */
    public record Reply(String question, boolean done, String urgencySignal, List<Turn> transcript) {
    }
}
