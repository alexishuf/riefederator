package br.ufsc.lapesd.riefederator.query.endpoint.impl;

import br.ufsc.lapesd.riefederator.algebra.Cardinality;
import br.ufsc.lapesd.riefederator.query.CQuery;
import br.ufsc.lapesd.riefederator.query.endpoint.AbstractTPEndpoint;
import br.ufsc.lapesd.riefederator.query.endpoint.CQEndpoint;
import br.ufsc.lapesd.riefederator.query.endpoint.Capability;
import br.ufsc.lapesd.riefederator.query.results.Results;
import br.ufsc.lapesd.riefederator.query.results.impl.CollectionResults;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;

public class EmptyEndpoint extends AbstractTPEndpoint implements CQEndpoint {
    private @Nullable String name;

    public EmptyEndpoint() {
        this.name = null;
    }

    public EmptyEndpoint(@Nonnull String name) {
        this.name = name;
    }

    @Override
    public @Nonnull Results query(@Nonnull CQuery query) {
        return new CollectionResults(Collections.emptyList(), query.attr().allVarNames());
    }

    @Override
    public @Nonnull Cardinality estimate(@Nonnull CQuery query, int policy) {
        return Cardinality.UNSUPPORTED;
    }

    @Override
    public boolean hasRemoteCapability(@Nonnull Capability capability) {
        return true;
    }

    @Override
    public @Nonnull String toString() {
        if (name != null)
            return "EmptyEndpoint@"+name;
        return String.format("EmptyEndpoint@%x", System.identityHashCode(this));
    }
}
