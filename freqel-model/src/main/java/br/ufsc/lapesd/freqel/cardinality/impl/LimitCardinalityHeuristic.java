package br.ufsc.lapesd.freqel.cardinality.impl;

import br.ufsc.lapesd.freqel.algebra.Cardinality;
import br.ufsc.lapesd.freqel.cardinality.CardinalityHeuristic;
import br.ufsc.lapesd.freqel.query.CQuery;
import br.ufsc.lapesd.freqel.query.endpoint.TPEndpoint;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Estimates cardinality by delegation to {@link TPEndpoint#estimate(CQuery, int)}.
 *
 * This will issue ASK and LIMIT queries to the endpoint as allowed by the estimatePolicy.
 */
public class LimitCardinalityHeuristic implements CardinalityHeuristic {
    private final int policy;

    @Inject public LimitCardinalityHeuristic(@Named("estimatePolicy") int policy) {
        this.policy = policy;
    }

    public int getPolicy() {
        return policy;
    }

    @Override
    public @Nonnull Cardinality estimate(@Nonnull CQuery query, @Nullable TPEndpoint endpoint) {
        if (endpoint == null) return Cardinality.UNSUPPORTED;
        return endpoint.estimate(query, policy);
    }
}
