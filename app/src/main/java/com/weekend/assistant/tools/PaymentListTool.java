package com.weekend.assistant.tools;

import com.weekend.assistant.workspace.PaymentService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Lists tracked payments (read-only; Weekend never moves money). */
@Component
public class PaymentListTool implements Tool {

    private final PaymentService payments;

    public PaymentListTool(PaymentService payments) {
        this.payments = payments;
    }

    @Override
    public String name() {
        return "payment_list";
    }

    @Override
    public String description() {
        return "List the owner's tracked payments with payee, amount in INR, due date and status.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of(), List.of());
    }

    @Override
    public boolean writes() {
        return false;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        String list = payments.all().stream().limit(100)
                .map(p -> "- " + p.payee() + ": INR " + p.amountInr().toPlainString() + " (" + p.status().name().toLowerCase(java.util.Locale.ROOT)
                        + (p.dueAt() == null ? "" : ", due " + p.dueAt()) + ")")
                .collect(Collectors.joining("\n"));
        return ToolOutput.ok(list.isEmpty() ? "No payments tracked." : list);
    }
}
