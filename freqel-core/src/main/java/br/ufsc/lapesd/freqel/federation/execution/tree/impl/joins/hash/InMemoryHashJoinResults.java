package br.ufsc.lapesd.freqel.federation.execution.tree.impl.joins.hash;

import br.ufsc.lapesd.freqel.federation.execution.tree.impl.joins.DefaultHashJoinOpExecutor;
import br.ufsc.lapesd.freqel.query.results.AbstractResults;
import br.ufsc.lapesd.freqel.query.results.Results;
import br.ufsc.lapesd.freqel.query.results.ResultsCloseException;
import br.ufsc.lapesd.freqel.query.results.Solution;
import br.ufsc.lapesd.freqel.query.results.impl.ArraySolution;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public class InMemoryHashJoinResults extends AbstractResults implements Results {
    private static final @Nonnull Logger logger = LoggerFactory.getLogger(DefaultHashJoinOpExecutor.class);

    private final @Nonnull Results smaller, larger;
    private final @Nonnull CrudeSolutionHashTable hashTable;
    private boolean stop = false, fetchedNonFetched = false;
    private final  @Nullable ExecutorService executorService;
    private final  @Nonnull Future<?> fetchTask;
    private final  @Nonnull ArrayDeque<Solution> queue;
    private final ArraySolution.ValueFactory factory;

    public static class Factory implements HashJoinResultsFactory {
        private boolean useThread = true;

        public @Nonnull Factory setUseThread(boolean useThread) {
            this.useThread = useThread;
            return this;
        }

        @Override
        public @Nonnull Results createResults(@Nonnull Results left, @Nonnull Results right,
                                              @Nonnull Collection<String> joinVars,
                                              @Nonnull Collection<String> resultVars) {
            return new InMemoryHashJoinResults(left, right, joinVars, resultVars, useThread);
        }
    }
    public static final @Nonnull Factory FACTORY = new Factory();

    public InMemoryHashJoinResults(@Nonnull Results smaller, @Nonnull Results larger,
                               @Nonnull Collection<String> joinVars,
                               @Nonnull Collection<String> resultVars) {
        this(smaller, larger, joinVars, resultVars, true);
    }

    public InMemoryHashJoinResults(@Nonnull Results smaller, @Nonnull Results larger,
                                   @Nonnull Collection<String> joinVars,
                                   @Nonnull Collection<String> resultVars,
                                   boolean useThread) {
        super(resultVars);
        Set<String> allVars = Stream.concat(smaller.getVarNames().stream(),
                                            larger.getVarNames().stream()).collect(toSet());
        Preconditions.checkArgument(allVars.containsAll(joinVars));

        this.hashTable = new CrudeSolutionHashTable(joinVars, 512);
        this.smaller = smaller;
        this.larger = larger;
        this.queue = new ArrayDeque<>();
        this.factory = ArraySolution.forVars(getVarNames());
        if (useThread) {
            this.executorService = Executors.newSingleThreadExecutor();
            this.fetchTask = executorService.submit(this::fetchAll);
        } else {
            this.executorService = null;
            CompletableFuture<Void> future = new CompletableFuture<>();
            this.fetchTask = future;
            fetchAll();
            future.complete(null);
        }
    }

    private void fetchAll() {
        try {
            while (!stop && smaller.hasNext()) {
                hashTable.add(smaller.next());
            }
            hashTable.recordFetches();
        } catch (Exception e) {
            logger.error("Fetch Task for {} dying with exception", smaller, e);
        }
    }

    private boolean advance() {
//        assert queue.isEmpty();
        /* await uninterruptibly for fetchTask */
        boolean interrupted = false;
        while (!fetchTask.isDone()) {
            try {
                fetchTask.get();
            } catch (InterruptedException e) {
                interrupted = true;
            } catch (ExecutionException ignored) {}
        }
        /* consume larger until we get some matches */
        try {
            while (larger.hasNext()) {
                if (tryJoin(larger.next()))
                    return true;
            }
            if (!fetchedNonFetched && larger.isOptional()) {
                fetchedNonFetched = true;
                hashTable.forEachNotFetched(s -> queue.add(factory.fromSolution(s)));
                return !queue.isEmpty();
            }
            return false;
        } finally {
            if (interrupted)
                Thread.currentThread().interrupt(); //restore interrupted flag
        }
    }

    private boolean tryJoin(@Nonnull Solution fromLarger) {
        Collection<Solution> leftSolutions = hashTable.getAll(fromLarger);
        boolean joined = !leftSolutions.isEmpty();
        if (!joined && smaller.isOptional()) {
            queue.add(factory.fromSolution(fromLarger));
            joined = true;
        }
        for (Solution fromSmaller : leftSolutions)
            queue.add(factory.fromSolutions(fromSmaller, fromLarger));
        return joined;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public boolean isDistinct() {
        return true;
    }

    @Override
    public int getReadyCount() {
        return queue.size();
    }

    @Override
    public boolean hasNext() {
        return !queue.isEmpty() || advance();
    }

    @Override
    public @Nonnull Solution next() {
        if (!hasNext())
            throw new NoSuchElementException("Results exhausted");
        return queue.remove();
    }

    @Override
    public void close() throws ResultsCloseException {
        stop = true;
        try {
            fetchTask.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.error("Fetch task is stuck, giving up on it");
        } catch (InterruptedException e) {
            logger.warn("Interrupted on close() while waiting for fetch task");
        } catch (ExecutionException e) {
            logger.error("Fetch task threw. Will proceed with close()", e);
        }
        if (executorService != null)
            executorService.shutdown();
        try {
            larger.close();
        } finally {
            smaller.close();
        }
    }

    @Override
    public @Nonnull String toString() {
        return String.format("InMemoryHashJoinResults@%x", System.identityHashCode(this));
    }
}
