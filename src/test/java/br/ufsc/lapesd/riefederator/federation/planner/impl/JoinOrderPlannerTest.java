package br.ufsc.lapesd.riefederator.federation.planner.impl;

import br.ufsc.lapesd.riefederator.NamedSupplier;
import br.ufsc.lapesd.riefederator.federation.planner.impl.paths.JoinGraph;
import br.ufsc.lapesd.riefederator.federation.tree.JoinNode;
import br.ufsc.lapesd.riefederator.federation.tree.PlanNode;
import br.ufsc.lapesd.riefederator.federation.tree.QueryNode;
import br.ufsc.lapesd.riefederator.federation.tree.TreeUtils;
import br.ufsc.lapesd.riefederator.model.Triple;
import br.ufsc.lapesd.riefederator.model.term.URI;
import br.ufsc.lapesd.riefederator.model.term.Var;
import br.ufsc.lapesd.riefederator.model.term.std.StdURI;
import br.ufsc.lapesd.riefederator.model.term.std.StdVar;
import br.ufsc.lapesd.riefederator.query.CQuery;
import br.ufsc.lapesd.riefederator.query.impl.EmptyEndpoint;
import br.ufsc.lapesd.riefederator.util.IndexedSet;
import com.google.common.collect.Collections2;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static br.ufsc.lapesd.riefederator.federation.planner.impl.JoinInfo.getPlainJoinability;
import static br.ufsc.lapesd.riefederator.federation.tree.TreeUtils.isTree;
import static br.ufsc.lapesd.riefederator.federation.tree.TreeUtils.streamPreOrder;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static org.testng.Assert.*;

public class JoinOrderPlannerTest {
    private static final URI Alice = new StdURI("http://example.org/Alice");
    private static final URI Bob = new StdURI("http://example.org/Bob");
    private static final URI p1 = new StdURI("http://example.org/p1");
    private static final URI p2 = new StdURI("http://example.org/p2");
    private static final URI p3 = new StdURI("http://example.org/p3");
    private static final URI p4 = new StdURI("http://example.org/p4");
    private static final URI p5 = new StdURI("http://example.org/p5");
    private static final URI p6 = new StdURI("http://example.org/p6");
    private static final URI p7 = new StdURI("http://example.org/p7");
    private static final Var x = new StdVar("x");
    private static final Var y = new StdVar("y");
    private static final Var z = new StdVar("z");
    private static final Var w = new StdVar("w");

    private static EmptyEndpoint ep = new EmptyEndpoint();

    public static final List<Supplier<JoinOrderPlanner>> suppliers =
            singletonList(new NamedSupplier<>(ArbitraryJoinOrderPlanner.class));

    private void checkPlan(PlanNode root, Set<PlanNode> expectedLeaves) {
        assertTrue(isTree(root));

        Set<PlanNode> leaves = streamPreOrder(root)
                .filter(n -> !(n instanceof JoinNode)).collect(toSet());
        assertEquals(leaves, expectedLeaves);

        Set<PlanNode> nonJoinInner = streamPreOrder(root).filter(n -> !leaves.contains(n))
                .filter(n -> !(n instanceof JoinNode)).collect(toSet());
        assertEquals(nonJoinInner, emptySet());

        Set<Triple> allTriples = expectedLeaves.stream().map(PlanNode::getMatchedTriples)
                .reduce(TreeUtils::union).orElse(emptySet());
        assertEquals(root.getMatchedTriples(), allTriples);
    }

    private @Nonnull Set<PlanNode> getPlanNodes(List<JoinInfo> list) {
        Set<PlanNode> expectedLeaves;
        expectedLeaves = list.stream().flatMap(i -> i.getNodes().stream()).collect(toSet());
        return expectedLeaves;
    }

    private boolean isBadPath(List<JoinInfo> list) {
        return list.stream().anyMatch(i -> !i.isValid());
    }


    @DataProvider
    public static Object[][] planData() {
        QueryNode n1 = new QueryNode(ep, CQuery.from(new Triple(Alice, p1, x)));
        QueryNode n2 = new QueryNode(ep, CQuery.from(new Triple(x, p2, y)));
        QueryNode n3 = new QueryNode(ep, CQuery.from(new Triple(y, p3, z)));
        QueryNode n4 = new QueryNode(ep, CQuery.from(new Triple(z, p4, w)));
        QueryNode n5 = new QueryNode(ep, CQuery.from(new Triple(w, p5, Bob)));
        QueryNode n6 = new QueryNode(ep, CQuery.from(new Triple(w, p6, Bob)));
        QueryNode n7 = new QueryNode(ep, CQuery.from(new Triple(z, p7, x)));

        return suppliers.stream().flatMap(s -> Stream.of(
                asList(s, singletonList(getPlainJoinability(n1, n2))),
                asList(s, singletonList(getPlainJoinability(n2, n4))),
                asList(s, asList(getPlainJoinability(n1, n2), getPlainJoinability(n2, n3))),
                asList(s, asList(getPlainJoinability(n4, n2), getPlainJoinability(n2, n1))),
                asList(s, asList(getPlainJoinability(n2, n3), getPlainJoinability(n1, n2))),
                asList(s, asList(getPlainJoinability(n1, n2), getPlainJoinability(n2, n3), getPlainJoinability(n3, n4))),
                asList(s, asList(getPlainJoinability(n4, n3), getPlainJoinability(n3, n2), getPlainJoinability(n2, n1))),
                asList(s, asList(getPlainJoinability(n3, n4), getPlainJoinability(n2, n3), getPlainJoinability(n1, n2))),
                asList(s, asList(getPlainJoinability(n1, n2), getPlainJoinability(n2, n3), getPlainJoinability(n3, n4), getPlainJoinability(n4, n5))),
                asList(s, asList(getPlainJoinability(n5, n4), getPlainJoinability(n4, n3), getPlainJoinability(n3, n2), getPlainJoinability(n2, n1))),
                asList(s, asList(getPlainJoinability(n4, n5), getPlainJoinability(n3, n4), getPlainJoinability(n2, n3), getPlainJoinability(n1, n2))),
                asList(s, asList(getPlainJoinability(n6, n5), getPlainJoinability(n4, n5), getPlainJoinability(n4, n7), getPlainJoinability(n3, n4), getPlainJoinability(n2, n3), getPlainJoinability(n1, n2))),
                asList(s, asList(getPlainJoinability(n1, n2), getPlainJoinability(n2, n3), getPlainJoinability(n3, n4), getPlainJoinability(n4, n7), getPlainJoinability(n4, n5), getPlainJoinability(n5, n6)))
                )).map(List::toArray).toArray(Object[][]::new);
    }

    @Test(dataProvider = "planData")
    public void testPlan(Supplier<JoinOrderPlanner> supplier, List<JoinInfo> list) {
        JoinOrderPlanner planner = supplier.get();
        if (isBadPath(list))
            expectThrows(IllegalArgumentException.class, () -> planner.plan(list));
        else
            checkPlan(planner.plan(list), getPlanNodes(list));
    }

    @Test(dataProvider = "planData")
    public void testPlanGivenGraph(Supplier<JoinOrderPlanner> supplier, List<JoinInfo> list) {
        JoinOrderPlanner planner = supplier.get();
        Set<PlanNode> leavesSet = getPlanNodes(list);
        ArrayList<PlanNode> nodesList = new ArrayList<>(leavesSet);
        int rounds = 0;
        //noinspection UnstableApiUsage
        for (List<PlanNode> permutation : Collections2.permutations(nodesList)) {
            JoinGraph joinGraph = new JoinGraph(IndexedSet.from(permutation));
            if (isBadPath(list))
                expectThrows(IllegalArgumentException.class, () -> planner.plan(list, joinGraph));
            else
                checkPlan(planner.plan(list, joinGraph), leavesSet);
            if (++rounds > 1024)
                 break;
        }
    }

    @Test(dataProvider = "planData")
    public void testPlanGivenNodes(Supplier<JoinOrderPlanner> supplier, List<JoinInfo> list) {
        JoinOrderPlanner planner = supplier.get();
        Set<PlanNode> leavesSet = getPlanNodes(list);
        JoinGraph joinGraph = new JoinGraph(IndexedSet.from(leavesSet));
        int rounds = 0;
        //noinspection UnstableApiUsage
        for (List<PlanNode> permutation : Collections2.permutations(new ArrayList<>(leavesSet))) {
            if (isBadPath(list)) {
                expectThrows(IllegalArgumentException.class,
                        () -> planner.plan(joinGraph, permutation));
            } else {
                checkPlan(planner.plan(joinGraph, permutation), leavesSet);
            }
            if (++rounds > 1024)
                break;
        }
    }

}