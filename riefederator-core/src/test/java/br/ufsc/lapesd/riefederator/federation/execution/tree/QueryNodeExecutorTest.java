package br.ufsc.lapesd.riefederator.federation.execution.tree;

import br.ufsc.lapesd.riefederator.NamedSupplier;
import br.ufsc.lapesd.riefederator.TestContext;
import br.ufsc.lapesd.riefederator.federation.execution.PlanExecutor;
import br.ufsc.lapesd.riefederator.federation.execution.tree.impl.SimpleQueryNodeExecutor;
import br.ufsc.lapesd.riefederator.federation.tree.PlanNode;
import br.ufsc.lapesd.riefederator.federation.tree.QueryNode;
import br.ufsc.lapesd.riefederator.jena.query.ARQEndpoint;
import br.ufsc.lapesd.riefederator.query.CQuery;
import br.ufsc.lapesd.riefederator.query.endpoint.Capability;
import br.ufsc.lapesd.riefederator.query.endpoint.TPEndpoint;
import br.ufsc.lapesd.riefederator.query.modifiers.SPARQLFilter;
import br.ufsc.lapesd.riefederator.query.results.Results;
import br.ufsc.lapesd.riefederator.query.results.ResultsExecutor;
import br.ufsc.lapesd.riefederator.query.results.Solution;
import br.ufsc.lapesd.riefederator.query.results.impl.BufferedResultsExecutor;
import br.ufsc.lapesd.riefederator.query.results.impl.MapSolution;
import br.ufsc.lapesd.riefederator.query.results.impl.SequentialResultsExecutor;
import com.google.common.collect.Sets;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static br.ufsc.lapesd.riefederator.query.parse.CQueryContext.createQuery;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.testng.Assert.*;

@Test(groups = {"fast"})
public class QueryNodeExecutorTest implements TestContext {
    private static final PlanExecutor failExecutor = new PlanExecutor() {
        @Override
        public @Nonnull Results executePlan(@Nonnull PlanNode plan) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nonnull Results executeNode(@Nonnull PlanNode node) {
            throw new UnsupportedOperationException();
        }
    };

    private static @Nonnull ResultsExecutor saveExecutor(@Nonnull ResultsExecutor executor) {
        resultExecutors.add(executor);
        return executor;
    }

    private static final List<NamedSupplier<QueryNodeExecutor>> suppliers = asList(
            new NamedSupplier<>("SimpleQueryNodeExecutor+SequentialResultsExecutor",
                    () -> new SimpleQueryNodeExecutor(failExecutor,
                            saveExecutor(new SequentialResultsExecutor()))),
            new NamedSupplier<>("SimpleQueryNodeExecutor+BufferedResultsExecutor(single, 1)",
                    () -> new SimpleQueryNodeExecutor(failExecutor,
                            saveExecutor(new BufferedResultsExecutor(Executors.newSingleThreadExecutor(), 1)))),
            new NamedSupplier<>("SimpleQueryNodeExecutor+BufferedResultsExecutor(single, 10)",
                    () -> new SimpleQueryNodeExecutor(failExecutor,
                            saveExecutor(new BufferedResultsExecutor(Executors.newSingleThreadExecutor(), 10)))),
            new NamedSupplier<>("SimpleQueryNodeExecutor+BufferedResultsExecutor",
                    () -> new SimpleQueryNodeExecutor(failExecutor,
                            saveExecutor(new BufferedResultsExecutor())))
    );

    private static final @Nonnull Queue<ResultsExecutor> resultExecutors;
    private static final @Nonnull ARQEndpoint rdf1;
    private static final @Nonnull ARQEndpoint rdf1WithoutFilters;

    static {
        resultExecutors = new ConcurrentLinkedQueue<>();

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Model model = ModelFactory.createDefaultModel();
        String resourcePath = "br/ufsc/lapesd/riefederator/rdf-1.nt";
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Resource " + resourcePath + "not found");
            RDFDataMgr.read(model, in, Lang.NT);
        } catch (IOException e) {
            fail("Unexpected IOException", e);
        }
        rdf1 = ARQEndpoint.forModel(model, "rdf-1.nt");
        rdf1WithoutFilters = new ARQEndpoint("rdf-1.nt (no FILTER)",
                q -> QueryExecutionFactory.create(q, model), null,
                () -> {}, false) {
            @Override
            public @Nonnull Results query(@Nonnull CQuery query) {
                if (query.getModifiers().stream().anyMatch(SPARQLFilter.class::isInstance))
                    fail("This endpoint does not support FILTER! QueryNodeExecutor is bugged");
                return super.query(query);
            }

            @Override
            public boolean hasRemoteCapability(@Nonnull Capability capability) {
                if (capability == Capability.SPARQL_FILTER) return false;
                return super.hasRemoteCapability(capability);
            }
        };
    }

    @AfterMethod
    public void tearDown() {
        for (ResultsExecutor e = resultExecutors.poll(); e != null; e = resultExecutors.poll())
            e.close();
    }

    @DataProvider
    public static Object[][] testData() {
        return suppliers.stream()
                .flatMap(s -> Stream.of(rdf1, rdf1WithoutFilters).map(e -> new Object[]{s, e}))
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "testData")
    public void testExecuteQuery(Supplier<QueryNodeExecutor> supplier, TPEndpoint ep) {
        QueryNodeExecutor executor = supplier.get();
        Results results = executor.execute(new QueryNode(ep, createQuery(x, knows, Bob)));
        Set<Solution> actual = new HashSet<>();
        results.forEachRemainingThenClose(actual::add);

        assertEquals(actual, singleton(MapSolution.build(x, Alice)));
    }

    @Test(dataProvider = "testData")
    public void testExecuteQueryWithFilter(Supplier<QueryNodeExecutor> supplier, TPEndpoint ep) {
        QueryNodeExecutor executor = supplier.get();
        CQuery qry = createQuery(x, name, y, SPARQLFilter.build("REGEX(?y, \"^b.*\")"));

        Results results = executor.execute(new QueryNode(ep, qry));
        Set<Solution> actual = new HashSet<>();
        results.forEachRemainingThenClose(actual::add);
        assertEquals(actual, Sets.newHashSet(
                MapSolution.builder().put(x, Bob).put(y, lit("bob", "en")).build(),
                MapSolution.builder().put(x, Bob).put(y, lit("beto", "pt")).build()
        ));
    }

    @Test(dataProvider = "testData")
    public void testPushesNodeFilterToQuery(Supplier<QueryNodeExecutor> supplier, TPEndpoint ep) {
        QueryNodeExecutor executor = supplier.get();
        QueryNode node = new QueryNode(ep, createQuery(x, name, y));
        node.addFilter(SPARQLFilter.build("REGEX(?y, \"^b.*\")"));

        Results results = executor.execute(node);
        Set<Solution> actual = new HashSet<>();
        results.forEachRemainingThenClose(actual::add);
        assertEquals(actual, Sets.newHashSet(
                MapSolution.builder().put(x, Bob).put(y, lit("bob", "en")).build(),
                MapSolution.builder().put(x, Bob).put(y, lit("beto", "pt")).build()
        ));
    }

    @Test(dataProvider = "testData")
    public void testFiltersInBothPlaces(Supplier<QueryNodeExecutor> supplier, TPEndpoint ep) {
        QueryNodeExecutor executor = supplier.get();
        CQuery qry = createQuery(x, name, y, SPARQLFilter.build("REGEX(?y, \"^b.*$\")"));
        QueryNode node = new QueryNode(ep, qry);
        node.addFilter(SPARQLFilter.build("REGEX(?y, \".*o$\")"));

        Results results = executor.execute(node);
        Set<Solution> actual = new HashSet<>();
        results.forEachRemainingThenClose(actual::add);
        assertEquals(actual, singleton(
                MapSolution.builder().put(x, Bob).put(y, lit("beto", "pt")).build()
        ));
    }

}