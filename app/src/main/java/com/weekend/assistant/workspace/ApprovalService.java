package com.weekend.assistant.workspace;

import com.weekend.assistant.agent.AgentService;
import com.weekend.assistant.domain.Payment;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** One queue for everything waiting on the owner's yes: agent tool calls and payment requests. */
@Service
public class ApprovalService {

    public enum Type { TOOL, PAYMENT }

    public record Approval(String id, Type type, String title, String detail, Instant createdAt) {}

    private final AgentService agent;
    private final PaymentService payments;

    public ApprovalService(AgentService agent, PaymentService payments) {
        this.agent = agent;
        this.payments = payments;
    }

    public List<Approval> pending() {
        Stream<Approval> tools = agent.pendingActions().stream()
                .map(p -> new Approval(p.id(), Type.TOOL, toolTitle(p.tool()), p.summary(), p.createdAt()));
        Stream<Approval> pays = payments.pendingApproval().stream()
                .map(p -> new Approval(p.id(), Type.PAYMENT, "Payment to " + p.payee(), paymentDetail(p), p.createdAt()));
        return Stream.concat(tools, pays).sorted(Comparator.comparing(Approval::createdAt).reversed()).toList();
    }

    /** Applies the owner's decision; returns a human-readable result, or empty when nothing was pending. */
    public Optional<String> decide(Type type, String id, boolean approved) {
        return switch (type) {
            case TOOL -> agent.confirm(id, approved);
            case PAYMENT -> payments.decide(id, approved).map(p -> approved
                    ? "Approved. Weekend does not pay; mark it paid once you have paid " + p.payee() + "."
                    : "Rejected. Nothing will be paid.");
        };
    }

    private static String toolTitle(String tool) {
        return switch (tool) {
            case "agent_delegate" -> "Send to another agent";
            case "reminder_create" -> "Create a reminder";
            case "task_create" -> "Add a task";
            default -> "Run " + tool;
        };
    }

    private static String paymentDetail(Payment p) {
        return "₹" + p.amountInr().toPlainString() + (p.note() == null ? "" : " · " + p.note());
    }
}
