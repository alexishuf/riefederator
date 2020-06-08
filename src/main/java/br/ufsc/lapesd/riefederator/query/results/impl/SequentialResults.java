package br.ufsc.lapesd.riefederator.query.results.impl;

import br.ufsc.lapesd.riefederator.query.results.Results;
import br.ufsc.lapesd.riefederator.query.results.ResultsCloseException;
import br.ufsc.lapesd.riefederator.query.results.ResultsList;
import br.ufsc.lapesd.riefederator.query.results.Solution;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Set;

class SequentialResults implements Results {
    private final @Nonnull Set<String> varNames;
    private final @Nonnull ResultsList results;
    private @Nullable String nodeName;
    private int idx = 0;

    public SequentialResults(@Nonnull Collection<Results> results, @Nonnull Set<String> varNames) {
        this.varNames = varNames;
        this.results = results instanceof ResultsList ? (ResultsList)results
                                                      : new ResultsList(results);
    }

    @Override
    public @Nullable String getNodeName() {
        return nodeName;
    }

    @Override
    public void setNodeName(@Nullable String nodeName) {
        this.nodeName = nodeName;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public int getReadyCount() {
        if (idx >= results.size()) return 0;
        return results.get(idx).getReadyCount();
    }

    @Override
    public boolean hasNext() {
        while (idx < results.size()) {
            if (results.get(idx).hasNext())
                return true;
            else
                ++idx;
        }
        return false;
    }

    @Override
    public @Nonnull Solution next() {
        if (!hasNext())
            throw new NoSuchElementException();
        return results.get(idx).next();
    }

    @Override
    public @Nonnull Set<String> getVarNames() {
        return varNames;
    }

    @Override
    public void close() throws ResultsCloseException {
        results.close();
    }
}
