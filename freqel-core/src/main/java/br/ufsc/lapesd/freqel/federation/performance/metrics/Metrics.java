package br.ufsc.lapesd.freqel.federation.performance.metrics;

import br.ufsc.lapesd.freqel.algebra.Op;
import br.ufsc.lapesd.freqel.util.BenchmarkUtils;
import br.ufsc.lapesd.freqel.description.Description;
import br.ufsc.lapesd.freqel.federation.Federation;
import br.ufsc.lapesd.freqel.federation.performance.metrics.impl.SimpleMetric;
import br.ufsc.lapesd.freqel.federation.performance.metrics.impl.SimpleTimeMetric;
import br.ufsc.lapesd.freqel.federation.planner.PostPlanner;
import br.ufsc.lapesd.freqel.federation.planner.PrePlanner;
import br.ufsc.lapesd.freqel.query.CQuery;
import br.ufsc.lapesd.freqel.query.endpoint.TPEndpoint;
import br.ufsc.lapesd.freqel.query.results.Results;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class Metrics {
    /**
     * Number of distinct {@link TPEndpoint} selected for a query.
     */
    public static final @Nonnull SimpleMetric<Integer> SOURCES_COUNT
            = SimpleMetric.builder("SOURCES_COUNT").create(Integer.class);

    /**
     * Time spent executing {@link Federation#initAllSources(int, TimeUnit)}.
     *
     * Note that samples of this metric will be cropped to the timeout given to
     * {@link Federation#initAllSources(int, TimeUnit)}
     */
    public static final @Nonnull SimpleTimeMetric INIT_SOURCES_MS
            = new SimpleTimeMetric("INIT_SOURCES_MS");

    /**
     * Time spent executing {@link Description#match(CQuery)}.
     */
    public static final @Nonnull SimpleTimeMetric SELECTION_MS
            = new SimpleTimeMetric("SELECTION_MS");

    /**
     * Time spent agglutinating subqueries identified during source selection.
     */
    public static final @Nonnull SimpleTimeMetric AGGLUTINATION_MS
            = new SimpleTimeMetric("AGGLUTINATION_MS");

    /**
     * Double with milliseconds spent on the {@link PrePlanner} (not including
     * planning triggered for the leaves).
     */
    public static final @Nonnull SimpleTimeMetric PRE_PLAN_MS
            = new SimpleTimeMetric("PRE_PLAN_MS");

    /**
     * Double with milliseconds spent on the {@link PostPlanner}.
     */
    public static final @Nonnull SimpleTimeMetric POST_PLAN_MS
            = new SimpleTimeMetric("POST_PLAN_MS");

    /**
     * Double with number of milliseconds spent planning (Planner and
     * FilterPlacement) values <strong>do not</strong> include {@link Metrics#PRE_PLAN_MS},
     * but <strong>do include</strong> {@link Metrics#OPT_MS}.
     */
    public static @Nonnull SimpleTimeMetric PLAN_MS = new SimpleTimeMetric("PLAN_MS");

    /**
     * Double with number of milliseconds spent optimizing (join-order optmization & heuristics)
     */
    public static @Nonnull SimpleTimeMetric OPT_MS
            = SimpleTimeMetric.builder("OPT_MS").containedBy(PLAN_MS).create();

    /**
     * Double with the number of milliseconds spent converting a {@link CQuery} into a
     * {@link Op}. This metric <b>includes</b> any other metric.
     */
    public static @Nonnull SimpleTimeMetric FULL_PLAN_MS
            = SimpleTimeMetric.builder("FULL_PLAN_MS").containsAnything().create();

    /**
     * Time used for fetching all results. This is not measured from within the federation.
     * Instead this is measured by application code around the while that
     * calls {@link Results#next()}.
     */
    public static @Nonnull SimpleTimeMetric EXEC_MS
            = new SimpleTimeMetric("EXEC_MS");

    /**
     * Time within EXEC_MS until the first result is received by the outer iterator
     * returned by {@link Federation#execute(Op)}
     *
     * This metric is measured in milliseconds and is contained by {@link Metrics#EXEC_MS}.
     */
    public static @Nonnull SimpleTimeMetric FIRST_RESULT_EXEC_MS =
            SimpleTimeMetric.builder("FIRST_RESULT_EXEC_MS").containedBy(EXEC_MS).create();

    /**
     * Time spent in {@link BenchmarkUtils#preheatCooldown()} and equivalent tasks, in milliseconds.
     *
     * This is not measured from within the {@link Federation}, it must be manually measured.
     */
    public static @Nonnull SimpleTimeMetric COOLDOWN_MS
            = new SimpleTimeMetric("PREHEAT_COOLDOWN_MS");
}
