package br.ufsc.lapesd.freqel.federation.planner.conjunctive.paths;

import br.ufsc.lapesd.freqel.TestContext;
import br.ufsc.lapesd.freqel.algebra.Op;
import br.ufsc.lapesd.freqel.algebra.leaf.EndpointQueryOp;
import br.ufsc.lapesd.freqel.algebra.util.TreeUtils;
import br.ufsc.lapesd.freqel.description.molecules.Atom;
import br.ufsc.lapesd.freqel.federation.planner.JoinOrderPlanner;
import br.ufsc.lapesd.freqel.federation.planner.conjunctive.ArbitraryJoinOrderPlanner;
import br.ufsc.lapesd.freqel.federation.planner.conjunctive.ArrayJoinGraph;
import br.ufsc.lapesd.freqel.federation.planner.conjunctive.JoinGraph;
import br.ufsc.lapesd.freqel.federation.planner.conjunctive.JoinOrderPlannerTest;
import br.ufsc.lapesd.freqel.model.Triple;
import br.ufsc.lapesd.freqel.model.term.Term;
import br.ufsc.lapesd.freqel.query.CQuery;
import br.ufsc.lapesd.freqel.query.MutableCQuery;
import br.ufsc.lapesd.freqel.query.endpoint.CQEndpoint;
import br.ufsc.lapesd.freqel.query.endpoint.impl.EmptyEndpoint;
import br.ufsc.lapesd.freqel.util.indexed.ref.RefIndexSet;
import br.ufsc.lapesd.freqel.util.indexed.subset.IndexSubset;
import br.ufsc.lapesd.freqel.description.molecules.annotations.AtomAnnotation;
import br.ufsc.lapesd.freqel.description.molecules.annotations.AtomInputAnnotation;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static br.ufsc.lapesd.freqel.algebra.util.TreeUtils.streamPreOrder;
import static br.ufsc.lapesd.freqel.model.Triple.Position.OBJ;
import static br.ufsc.lapesd.freqel.model.Triple.Position.SUBJ;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.collect.Sets.union;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.testng.Assert.*;

@Test(groups = {"fast"})
public class SubPathAggregationTest implements TestContext {
    public static final EmptyEndpoint e1 = new EmptyEndpoint(), e2 = new EmptyEndpoint();

    public static final Atom A1 = new Atom("Atom1"), A2 = new Atom("Atom2");

    public static @Nonnull EndpointQueryOp
    node(@Nonnull CQEndpoint endpoint, @Nonnull Term s, @Nonnull Term p, @Nonnull Term o) {
        return new EndpointQueryOp(endpoint, CQuery.from(new Triple(s, p, o)));
    }
    public static @Nonnull
    EndpointQueryOp node(@Nonnull CQEndpoint endpoint, @Nonnull Term s, @Nonnull Term p, @Nonnull Term o,
                         @Nonnull Triple.Position input) {
        MutableCQuery query = MutableCQuery.from(new Triple(s, p, o));
        if (input == SUBJ) {
            query.annotate(s, AtomInputAnnotation.asRequired(A1, "A1").get());
            query.annotate(o, AtomAnnotation.of(A2));
        } else if (input == OBJ) {
            query.annotate(s, AtomAnnotation.of(A1));
            query.annotate(o, AtomInputAnnotation.asRequired(A2, "A2").get());
        }
        return new EndpointQueryOp(endpoint, query);
    }

    public static EndpointQueryOp n1 = node(e1, Alice, p1, x), n2 = node(e1, x, p2, y),
                            n3 = node(e1, y, p3, z),     n4 = node(e1, z, p4, Bob);
    public static EndpointQueryOp o1 = node(e2, Alice, p1, x, SUBJ), o2 = node(e2, x, p2, y, SUBJ),
                            o3 = node(e2, y, p3, z, SUBJ),     o4 = node(e2, z, p4, Bob, SUBJ);
    public static EndpointQueryOp l1 = node(e2, Alice, p1, x, OBJ), l2 = node(e2, x, p2, y, OBJ),
                            l3 = node(e2, y, p3, z, OBJ),     l4 = node(e2, z, p4, Bob, OBJ);
    public static RefIndexSet<Op> allNodes = RefIndexSet.fromRefDistinct(
            asList(n1, n2, n3, n4, o1, o2, o3, o4, l1, l2, l3, l4));

    @DataProvider
    public static Object[][] stateStoreData() {
        return Stream.of(
                asList(emptyList(), singleton(n1), singleton(singleton(n1))),
                asList(emptyList(), asList(n2, o3), singleton(asList(n2, o3))),
                asList(singleton(singleton(n1)), asList(o2, o3),
                       asList(singleton(n1), asList(o2, o3))),
                asList(singleton(singleton(n1)), asList(n1, n2, n3),
                       asList(singleton(n1), asList(n2, n3))),
                asList(asList(singleton(n1), singleton(n2)), asList(n1, n2, o3, o4),
                       asList(singleton(n1), singleton(n2), asList(o3, o4))),
                asList(singleton(asList(n1, n2, n3)), asList(n2, n3, o4),
                       asList(singleton(n1), asList(n2, n3), singleton(o4))),
                asList(singleton(singleton(n1)), asList(o2, o4), null), // not join-connected
                asList(asList(asList(n1, n2), asList(n3, n4)), asList(n2, n3),
                       asList(singleton(n1), singleton(n2), singleton(n3), singleton(n4)))
        ).map(List::toArray).toArray(Object[][]::new);
    }

    @Test(dataProvider = "stateStoreData")
    public void testStateStore(@Nonnull Collection<Collection<Op>> components,
                               @Nonnull Collection<Op> component,
                               @Nullable Collection<Collection<Op>> expected) {
        //sanity
        assertTrue(components.stream().flatMap(Collection::stream).allMatch(allNodes::contains));
        if (expected != null) {
            assertTrue(expected.stream().flatMap(Collection::stream).allMatch(allNodes::contains));
            assertTrue(expected.stream().flatMap(Collection::stream)
                                        .collect(toSet()).containsAll(component));
        }

        for (int i = 0; i < 256; i++) {
            ArrayList<Op> permutation = new ArrayList<>(allNodes);
            Collections.shuffle(permutation);

            //setup
            JoinGraph joinGraph = new ArrayJoinGraph(RefIndexSet.fromRefDistinct(permutation));
            SubPathAggregation.State state = new SubPathAggregation.State(joinGraph);
            components.stream().map(allNodes::subset).forEach(state.getComponents()::add);

            // call & check
            if (expected == null) {
                expectThrows(IllegalArgumentException.class,
                        () -> state.store(allNodes.subset(component)));

            } else {
                state.store(allNodes.subset(component));

                //check
                Set<IndexSubset<Op>> expectedSet;
                expectedSet = expected.stream().map(allNodes::subset).collect(toSet());
                assertEquals(new HashSet<>(state.getComponents()), expectedSet);
            }
        }
    }

    @DataProvider
    public static Object[][] stateProcessPairData() {
        return Stream.of(
                asList(new JoinComponent(allNodes, n1, n2),
                       new JoinComponent(allNodes, n1, n2),
                       singleton(asList(n1, n2))),
                asList(new JoinComponent(allNodes, n1, n2),
                       new JoinComponent(allNodes, n3, n4),
                       emptySet()),
                asList(new JoinComponent(allNodes, n1, n2, o3),
                       new JoinComponent(allNodes, n1, n2, n3),
                       singleton(asList(n1, n2))),
                asList(new JoinComponent(allNodes, n1, o2, o3, n4),
                       new JoinComponent(allNodes, n4, l3, l2, n1),
                       asList(singleton(n1), singleton(n4))),
                asList(new JoinComponent(allNodes, n1, n2, o3, n4),
                       new JoinComponent(allNodes, n4, l3, n2, n1),
                       asList(asList(n1, n2), singleton(n4)))
        ).map(List::toArray).toArray(Object[][]::new);
    }

    @Test(dataProvider = "stateProcessPairData")
    public void testStateProcessPair(JoinComponent left, JoinComponent right,
                                     Collection<Collection<Op>> expected) {
        for (int i = 0; i < 256; i++) {
            ArrayList<Op> permutation = new ArrayList<>(allNodes);
            Collections.shuffle(permutation);
            JoinGraph graph = new ArrayJoinGraph(RefIndexSet.fromRefDistinct(permutation));
            Set<IndexSubset<Op>> stored = new HashSet<>();
            SubPathAggregation.State state = new SubPathAggregation.State(graph) {
                @Override
                public void store(@Nonnull IndexSubset<Op> component) {
                    stored.add(component);
                }
            };

            state.processPair(left, right);

            Set<IndexSubset<Op>> expectedSet;
            expectedSet = expected.stream().map(allNodes::subset).collect(toSet());
            assertEquals(stored, expectedSet);
        }
    }

    @Test
    public void testReducedJoinGraphNoComponents() {
        SubPathAggregation.State state = new SubPathAggregation.State(new ArrayJoinGraph(allNodes));
        state.planComponents(new ArbitraryJoinOrderPlanner());
        assertEquals(state.getPlanned().size(), 0);

        JoinGraph reduced = state.createReducedJoinGraph(singleton(
                new JoinComponent(allNodes, n1, n2, n3)));
        assertEquals(reduced.getNodes(), newHashSet(n1, n2, n3));
    }

    @Test
    public void testReducedJoinGraphOneComponent() {
        SubPathAggregation.State state = new SubPathAggregation.State(new ArrayJoinGraph(allNodes));
        state.getComponents().add(allNodes.subset(asList(n1, n2)));
        state.planComponents(new ArbitraryJoinOrderPlanner());
        assertEquals(state.getPlanned().size(), 1);

        JoinGraph reduced = state.createReducedJoinGraph(singleton(
                new JoinComponent(allNodes, n1, n2, n2, n3)));
        assertEquals(reduced.getNodes(),
                     newHashSet(n3, state.getPlanned().iterator().next().node));
    }

    @Test
    public void testReducedJoinGraphThreeComponents() {
        SubPathAggregation.State state = new SubPathAggregation.State(new ArrayJoinGraph(allNodes));
        state.getComponents().add(allNodes.subset(asList(o1, o2)));
        state.getComponents().add(allNodes.subset(asList(n3, n4)));
        state.getComponents().add(allNodes.subset(asList(l1, l2)));
        state.planComponents(new ArbitraryJoinOrderPlanner());
        assertEquals(state.getPlanned().size(), 3);

        JoinGraph reduced = state.createReducedJoinGraph(asList(
                new JoinComponent(allNodes, o1, o2, n3, n4),
                new JoinComponent(allNodes, l1, l2, n3, n4)));
        assertEquals(reduced.getNodes(),
                     state.getPlanned().stream().map(pc -> pc.node).collect(toSet()));
    }

    @Test
    public void testReducePath() {
        SubPathAggregation.State state = new SubPathAggregation.State(new ArrayJoinGraph(allNodes));
        state.getComponents().add(allNodes.subset(n3));             // 0
        state.getComponents().add(allNodes.subset(asList(o1, o2))); // 1
        state.getComponents().add(allNodes.subset(asList(l1, l2))); // 2
        state.getComponents().add(allNodes.subset(o4));             // 3
        state.getComponents().add(allNodes.subset(l4));             // 4
        state.planComponents(new ArbitraryJoinOrderPlanner());
        List<Op> planned = state.getPlanned().stream().map(pc -> pc.node).collect(toList());
        assertEquals(planned.size(), 5);
        assertTrue(planned.stream().allMatch(TreeUtils::isTree));

        List<JoinComponent> paths = asList(
                new JoinComponent(allNodes, o1, o2, n3, o4),
                new JoinComponent(allNodes, o1, o2, n3, l4),
                new JoinComponent(allNodes, l1, l2, n3, o4),
                new JoinComponent(allNodes, l1, l2, n3, l4)
        );

        JoinGraph reducedGraph = state.createReducedJoinGraph(paths);
        assertEquals(reducedGraph.getNodes(), new HashSet<>(planned));

        List<JoinComponent> reducedPaths = paths.stream().map(state::reducePath).collect(toList());
        assertEquals(reducedPaths.stream().map(JoinComponent::getNodes).collect(toList()),
                     asList(
                             newHashSet(planned.get(1), planned.get(0), planned.get(3)),
                             newHashSet(planned.get(1), planned.get(0), planned.get(4)),
                             newHashSet(planned.get(2), planned.get(0), planned.get(3)),
                             newHashSet(planned.get(2), planned.get(0), planned.get(4))
                     )
        );
    }


    @Test
    public void testReducePathWithNoOp() {
        SubPathAggregation.State state = new SubPathAggregation.State(new ArrayJoinGraph(allNodes));
        state.getComponents().add(allNodes.subset(asList(n3, o4))); // 0
        state.getComponents().add(allNodes.subset(asList(n1, n2))); // 1
        state.getComponents().add(allNodes.subset(asList(o1, o2))); // 2
        state.planComponents(new ArbitraryJoinOrderPlanner());
        List<Op> planned = state.getPlanned().stream().map(pc -> pc.node).collect(toList());
        assertEquals(planned.size(), 3);
        assertTrue(planned.stream().allMatch(TreeUtils::isTree));

        List<JoinComponent> paths = asList(
                new JoinComponent(allNodes, n1, n2, n3, o4),
                new JoinComponent(allNodes, o1, o2, n3, o4),
                new JoinComponent(allNodes, l1, l2, l3, l4)
        );

        JoinGraph reducedGraph = state.createReducedJoinGraph(paths);
        assertEquals(reducedGraph.getNodes(),
                     union(new HashSet<>(planned), newHashSet(l1, l2, l3, l4)));

        List<JoinComponent> reducedPaths = paths.stream().map(state::reducePath).collect(toList());
        assertEquals(reducedPaths.stream().map(JoinComponent::getNodes).collect(toList()),
                asList(
                        newHashSet(planned.get(1), planned.get(0)),
                        newHashSet(planned.get(2), planned.get(0)),
                        newHashSet(l1, l2, l3, l4)
                )
        );
        assertSame(reducedPaths.get(2), paths.get(2)); //do not rebuild if there is no change
    }

    @DataProvider
    public static Object[][] joinOrderPlannerData() {
        return JoinOrderPlannerTest.suppliers.stream().map(s -> new Object[]{s})
                .toArray(Object[][]::new);
    }

    @Test
    public void testAggregateEmpty() {
        SubPathAggregation aggregation = SubPathAggregation.aggregate(new ArrayJoinGraph(allNodes),
                emptyList(), new ArbitraryJoinOrderPlanner());
        assertEquals(aggregation.getGraph().size(), 0);
        assertEquals(aggregation.getJoinComponents(), emptyList());
    }


    @Test(dataProvider = "joinOrderPlannerData")
    public void testAggregateSinglePath(Supplier<JoinOrderPlanner> supplier) {
        List<JoinComponent> paths = singletonList(new JoinComponent(allNodes, n1, n2, n3, n4));
        SubPathAggregation aggregation = SubPathAggregation.aggregate(new ArrayJoinGraph(allNodes),
                paths, supplier.get());
        assertEquals(aggregation.getJoinComponents().size(), 1);
        assertSame(aggregation.getJoinComponents().get(0), paths.get(0));
        assertEquals(aggregation.getGraph().getNodes(), allNodes.subset(asList(n1, n2, n3, n4)));
    }

    @Test(dataProvider = "joinOrderPlannerData")
    public void testAggregateCommonPrefix(Supplier<JoinOrderPlanner> supplier) {
        List<JoinComponent> paths = asList(
                new JoinComponent(allNodes, n1, n2, n3, n4),
                new JoinComponent(allNodes, n1, n2, o3, o4));
        SubPathAggregation a;
        a = SubPathAggregation.aggregate(new ArrayJoinGraph(allNodes), paths, supplier.get());

        assertTrue(a.getGraph().getNodes().containsAll(asList(n3, n4, o3, o4)));
        assertFalse(a.getGraph().getNodes().containsAll(asList(n1, n2)));

        IndexSubset<Op> novel;
        novel = a.getJoinComponents().get(0).getNodes().createMinus(allNodes);
        assertEquals(novel.size(), 1);
        Op common = novel.iterator().next();
        assertTrue(streamPreOrder(common).collect(toSet()).containsAll(newHashSet(n1, n2)));

        assertEquals(a.getJoinComponents().stream().map(JoinComponent::getNodes).collect(toList()),
                     asList(newHashSet(common, n3, n4), newHashSet(common, o3, o4)));
    }

    @Test(dataProvider = "joinOrderPlannerData")
    public void testAggregateStarAtPrefix(Supplier<JoinOrderPlanner> supplier) {
        List<JoinComponent> paths = asList(
                new JoinComponent(allNodes, l1, l2, n3, l4),
                new JoinComponent(allNodes, l1, l2, n3, o4),
                new JoinComponent(allNodes, o1, o2, n3, l4),
                new JoinComponent(allNodes, o1, o2, n3, o4)
        );
        SubPathAggregation a;
        a = SubPathAggregation.aggregate(new ArrayJoinGraph(allNodes), paths, supplier.get());

        assertEquals(a.getGraph().getNodes().fullSubset().createIntersection(allNodes),
                     allNodes.subset(asList(n3, o4, l4)));

        assertEquals(a.getJoinComponents().stream().map(p -> p.getNodes().size()).collect(toList()),
                     asList(3, 3, 3, 3));

        IndexSubset<Op> novel;
        novel = a.getJoinComponents().get(0).getNodes().createMinus(allNodes);
        assertEquals(novel.size(), 1);
        Op common0 = novel.iterator().next();
        assertTrue(streamPreOrder(common0).collect(toSet()).containsAll(newHashSet(l1, l2)));

        novel = a.getJoinComponents().get(2).getNodes().createMinus(allNodes);
        assertEquals(novel.size(), 1);
        Op common2 = novel.iterator().next();
        assertTrue(streamPreOrder(common2).collect(toSet()).containsAll(newHashSet(o1, o2)));

        assertEquals(a.getJoinComponents().stream().map(JoinComponent::getNodes).collect(toList()),
                asList(newHashSet(common0, n3, l4), newHashSet(common0, n3, o4),
                       newHashSet(common2, n3, l4), newHashSet(common2, n3, o4)));
    }


    @Test(dataProvider = "joinOrderPlannerData")
    public void testAggregateStarAtSuffix(Supplier<JoinOrderPlanner> supplier) {
        List<JoinComponent> paths = asList(
                new JoinComponent(allNodes, l1, n2, l3, l4),
                new JoinComponent(allNodes, l1, n2, o3, o4),
                new JoinComponent(allNodes, o1, n2, l3, l4),
                new JoinComponent(allNodes, o1, n2, o3, o4)
        );
        SubPathAggregation a;
        a = SubPathAggregation.aggregate(new ArrayJoinGraph(allNodes), paths, supplier.get());

        assertEquals(a.getGraph().getNodes().fullSubset().createIntersection(allNodes),
                     allNodes.subset(asList(n2, l1, o1)));

        assertEquals(a.getJoinComponents().stream().map(p -> p.getNodes().size()).collect(toList()),
                     asList(3, 3, 3, 3));

        IndexSubset<Op> novel;
        novel = a.getJoinComponents().get(0).getNodes().createMinus(allNodes);
        assertEquals(novel.size(), 1);
        Op common0 = novel.iterator().next();
        assertTrue(streamPreOrder(common0).collect(toSet()).containsAll(newHashSet(l3, l4)));

        novel = a.getJoinComponents().get(1).getNodes().createMinus(allNodes);
        assertEquals(novel.size(), 1);
        Op common1 = novel.iterator().next();
        assertTrue(streamPreOrder(common1).collect(toSet()).containsAll(newHashSet(o3, o4)));

        assertEquals(a.getJoinComponents().stream().map(JoinComponent::getNodes).collect(toList()),
                asList(newHashSet(l1, n2, common0), newHashSet(l1, n2, common1),
                       newHashSet(o1, n2, common0), newHashSet(o1, n2, common1)));
    }
}