package br.ufsc.lapesd.freqel.linkedator.strategies;

import br.ufsc.lapesd.freqel.linkedator.LinkedatorResult;
import br.ufsc.lapesd.freqel.query.endpoint.TPEndpoint;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.util.Collection;

public interface LinkedatorStrategy {
    /**
     * Generate suggestions of links from the given sources.
     *
     * @param sources sources to evaluate.
     * @return a non-null set of {@link LinkedatorResult} instances.
     */
    @CheckReturnValue
    @Nonnull Collection<LinkedatorResult> getSuggestions(@Nonnull Collection<TPEndpoint> sources);
}
