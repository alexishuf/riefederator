package br.ufsc.lapesd.riefederator.query.endpoint;

import br.ufsc.lapesd.riefederator.algebra.Op;
import io.micrometer.core.lang.Nullable;

import javax.annotation.Nonnull;

public class UnrelatedEndpointException extends DQEndpointException {
    private final @Nullable Op op;
    private final @Nullable DQEndpoint targetEndpoint;

    public UnrelatedEndpointException(@Nonnull String message,
                                      @Nullable Op op, @Nullable DQEndpoint targetEndpoint) {
        super(message);
        this.op = op;
        this.targetEndpoint = targetEndpoint;
    }

    public UnrelatedEndpointException(@Nonnull Op op, @Nullable DQEndpoint targetEndpoint) {
        this("Endpoint != "+targetEndpoint+" on query "+op, op, targetEndpoint);
    }

    public @Nullable Op getOp() {
        return op;
    }

    public @Nullable DQEndpoint getTargetEndpoint() {
        return targetEndpoint;
    }
}