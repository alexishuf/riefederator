package br.ufsc.lapesd.riefederator.federation.planner.pre.steps;

import br.ufsc.lapesd.riefederator.TestContext;
import br.ufsc.lapesd.riefederator.algebra.Op;
import br.ufsc.lapesd.riefederator.algebra.inner.CartesianOp;
import br.ufsc.lapesd.riefederator.algebra.inner.ConjunctionOp;
import br.ufsc.lapesd.riefederator.algebra.inner.UnionOp;
import br.ufsc.lapesd.riefederator.algebra.leaf.QueryOp;
import br.ufsc.lapesd.riefederator.query.modifiers.SPARQLFilter;
import br.ufsc.lapesd.riefederator.util.ref.EmptyRefSet;
import br.ufsc.lapesd.riefederator.util.ref.IdentityHashSet;
import br.ufsc.lapesd.riefederator.util.ref.RefSet;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Stream;

import static br.ufsc.lapesd.riefederator.query.parse.CQueryContext.createQuery;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

@Test(groups = {"fast"})
public class CartesianDistributionStepTest implements TestContext {

    @DataProvider
    public static Object[][] testData() {
        QueryOp q1 = new QueryOp(createQuery(Alice, knows, x));
        return Stream.of(
                asList(q1, EmptyRefSet.emptySet(), null),
                asList(q1, IdentityHashSet.of(q1), null),
                asList(UnionOp.builder()
                                .add(new QueryOp(createQuery(Alice, knows, x)))
                                .add(new QueryOp(createQuery(Bob, knows, x)))
                                .build(),
                       EmptyRefSet.emptySet(), null),
                // base case
                asList(ConjunctionOp.builder()
                        .add(CartesianOp.builder()
                                .add(new QueryOp(createQuery(Alice, knows, x)))
                                .add(new QueryOp(createQuery(
                                        y, age, v, SPARQLFilter.build("?v > 23"))))
                                .build())
                        .add(new QueryOp(createQuery(x, age, u, SPARQLFilter.build("?u < 23"))))
                        .build(),
                       IdentityHashSet.of(q1),
                       CartesianOp.builder()
                               .add(new QueryOp(createQuery(
                                       Alice, knows, x,
                                       x, age, u, SPARQLFilter.build("?u < 23"))))
                               .add(new QueryOp(createQuery(
                                       y, age, v, SPARQLFilter.build("?v > 23"))))
                               .build())
        ).map(List::toArray).toArray(Object[][]::new);
    }

    @Test(dataProvider = "testData")
    public void test(@Nonnull Op in, @Nonnull RefSet<Op> locked, @Nullable Op expected) {
        if (expected == null)
            expected = in;
        boolean expectSame = expected.equals(in);
        CartesianDistributionStep step = new CartesianDistributionStep();
        Op actual = step.visit(in, locked);
        assertEquals(actual, expected);
        if (expectSame)
            assertSame(actual, in);
    }
}