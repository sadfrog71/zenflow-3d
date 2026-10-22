package com.deepdimen.matrix.assistant.run;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelChunk;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import org.reactivestreams.Publisher;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A business adapter runs its governed execution inside a real claimed Engine lease. */
public interface AssistantGovernedTextExecutor {
    boolean supports(IdentityEnvelope identity);
    /** Emits only native deltas. Return only after governed durable result validation succeeds. */
    Outcome execute(AssistantRunRecord run,Lease lease,Consumer<ModelChunk> observer);
    /** Hold business authorization locks while the Engine atomically persists its final exchange. */
    boolean complete(AssistantRunRecord run,Lease lease,Supplier<Boolean> engineCompletion);
    record Outcome(ResolvedAssistantModel model,AssistantExecutionTrace trace) {
        public Outcome {java.util.Objects.requireNonNull(model);java.util.Objects.requireNonNull(trace);}
    }
    interface Lease {
        /** Reloads and verifies current owner, expiry, identity and cancellation. */
        AssistantRunRecord current();
        /** Called once, at the real native invocation boundary. */
        void beforeInvocation(ResolvedAssistantModel model);
        boolean cancelled();
        Publisher<?> cancellation();
    }
}
