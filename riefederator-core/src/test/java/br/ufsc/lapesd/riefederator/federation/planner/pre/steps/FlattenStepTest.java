package br.ufsc.lapesd.riefederator.federation.planner.pre.steps;

import br.ufsc.lapesd.riefederator.TestContext;
import br.ufsc.lapesd.riefederator.algebra.Op;
import br.ufsc.lapesd.riefederator.algebra.inner.CartesianOp;
import br.ufsc.lapesd.riefederator.algebra.inner.JoinOp;
import br.ufsc.lapesd.riefederator.algebra.inner.UnionOp;
import br.ufsc.lapesd.riefederator.algebra.leaf.QueryOp;
import br.ufsc.lapesd.riefederator.query.modifiers.SPARQLFilter;
import br.ufsc.lapesd.riefederator.util.RefEquals;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static br.ufsc.lapesd.riefederator.query.parse.CQueryContext.createQuery;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

@Test(groups = {"fast"})
public class FlattenStepTest implements TestContext {
    @DataProvider
    public static Object[][] flattenTestData() {
        QueryOp q1 = new QueryOp(createQuery(x, knows, Alice));
        JoinOp j1 = JoinOp.create(new QueryOp(createQuery(Alice, knows, x)),
                new QueryOp(createQuery(
                        x, knows, Bob,
                        x, age, u, SPARQLFilter.build("?u < 23"))));
        Op u1 = UnionOp.builder()
                .add(new QueryOp(createQuery(
                        x, knows, Alice,
                        x, age, u, SPARQLFilter.build("?u < 23"))))
                .add(new QueryOp(createQuery(x, knows, Bob)))
                .build();
        return Stream.of(
                asList(new QueryOp(createQuery(x, knows, Alice)),
                       emptySet(),
                       new QueryOp(createQuery(x, knows, Alice))),
                asList(q1, singleton(q1), q1),
                asList(j1, emptySet(), j1),
                asList(j1, singleton(q1), j1),
                asList(j1, newHashSet(q1, j1), j1),
                asList(UnionOp.builder()
                                .add(u1)
                                .add(new QueryOp(createQuery(
                                        x, knows, Charlie,
                                        x, age, u, SPARQLFilter.build("?u > 23"))))
                                .build(),
                       emptySet(),
                       UnionOp.builder()
                               .add(new QueryOp(createQuery(
                                       x, knows, Alice,
                                       x, age, u, SPARQLFilter.build("?u < 23"))))
                               .add(new QueryOp(createQuery(x, knows, Bob)))
                               .add(new QueryOp(createQuery(
                                       x, knows, Charlie,
                                       x, age, u, SPARQLFilter.build("?u > 23"))))
                               .build()),
                asList(UnionOp.builder()
                                .add(u1)
                                .add(new QueryOp(createQuery(
                                        x, knows, Charlie,
                                        x, age, u, SPARQLFilter.build("?u > 23"))))
                                .build(),
                        singleton(u1),
                        UnionOp.builder()
                                .add(u1)
                                .add(new QueryOp(createQuery(
                                        x, knows, Charlie,
                                        x, age, u, SPARQLFilter.build("?u > 23"))))
                                .build()),
                // do not undo cartesian decomposition
                asList(CartesianOp.builder()
                                .add(new QueryOp(createQuery(x, knows, Alice, x, age, u)))
                                .add(new QueryOp(createQuery(
                                        Alice, knows, y,
                                        y, age, v, SPARQLFilter.build("?v > 23"))))
                                .add(SPARQLFilter.build("?u < ?v"))
                                .build(),
                       emptySet(),
                       null)
        ).map(List::toArray).toArray(Object[][]::new);
    }

    @Test(dataProvider = "flattenTestData")
    public void testFlatten(@Nonnull Op in, @Nonnull Set<RefEquals<Op>> locked,
                            @Nullable Op expected) {
        FlattenStep step = new FlattenStep();
        if (expected == null)
            expected = in;
        Op actual = step.plan(in, locked);
        assertEquals(actual, expected);
        if (expected == in)
            assertSame(actual, expected);
    }
}