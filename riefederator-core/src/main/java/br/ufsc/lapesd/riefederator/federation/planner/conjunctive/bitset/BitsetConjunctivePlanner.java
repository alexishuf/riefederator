package br.ufsc.lapesd.riefederator.federation.planner.conjunctive.bitset;

import br.ufsc.lapesd.riefederator.algebra.JoinInterface;
import br.ufsc.lapesd.riefederator.algebra.Op;
import br.ufsc.lapesd.riefederator.algebra.inner.UnionOp;
import br.ufsc.lapesd.riefederator.algebra.leaf.EmptyOp;
import br.ufsc.lapesd.riefederator.federation.cardinality.InnerCardinalityComputer;
import br.ufsc.lapesd.riefederator.federation.planner.JoinOrderPlanner;
import br.ufsc.lapesd.riefederator.federation.planner.conjunctive.bitset.priv.BitJoinGraph;
import br.ufsc.lapesd.riefederator.federation.planner.conjunctive.bitset.priv.InputsBitJoinGraph;
import br.ufsc.lapesd.riefederator.model.Triple;
import br.ufsc.lapesd.riefederator.query.CQuery;
import br.ufsc.lapesd.riefederator.util.Bitset;
import br.ufsc.lapesd.riefederator.util.RawAlignedBitSet;
import br.ufsc.lapesd.riefederator.util.bitset.ArrayBitset;
import br.ufsc.lapesd.riefederator.util.bitset.Bitsets;
import br.ufsc.lapesd.riefederator.util.indexed.IndexSet;
import br.ufsc.lapesd.riefederator.util.indexed.ref.RefIndexSet;
import br.ufsc.lapesd.riefederator.util.indexed.subset.IndexSubset;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

import static br.ufsc.lapesd.riefederator.federation.planner.conjunctive.bitset.AbstractStateHelper.NODES;

public class BitsetConjunctivePlanner extends AbstractBitsetConjunctivePlanner  {
    private static final Logger logger = LoggerFactory.getLogger(BitsetConjunctivePlanner.class);

    @Inject
    public BitsetConjunctivePlanner(@Nonnull JoinOrderPlanner joinOrderPlanner,
                                    @Nonnull InnerCardinalityComputer innerCardComputer) {
        super(joinOrderPlanner, innerCardComputer);
    }

    @Override protected @Nonnull BitJoinGraph createJoinGraph(RefIndexSet<Op> fragments) {
        return new InputsBitJoinGraph(fragments);
    }

    @Override @Nonnull RefIndexSet<Op> groupNodes(@Nonnull List<Op> nodes) {
        if (nodes.size() <= 1)
            return RefIndexSet.fromRefDistinct(nodes);
        ListMultimap<JoinInterface, Op> if2op;
        if2op = MultimapBuilder.hashKeys(nodes.size()).arrayListValues().build();

        for (Op op : nodes)
            if2op.put(new JoinInterface(op), op);
        RefIndexSet<Op> set = new RefIndexSet<>(nodes.size());
        for (Map.Entry<JoinInterface, Collection<Op>> e : if2op.asMap().entrySet()) {
            Collection<Op> children = e.getValue();
            set.add(children.size() > 2 ? UnionOp.build(children) : children.iterator().next());
        }
        return set;
    }

    @Override
    protected @Nonnull IndexSubset<Op> componentToSubset(@Nonnull RefIndexSet<Op> nodes,
                                                         @Nonnull Object component) {
        return nodes.subset((Bitset)component);
    }

    @Override @Nonnull Set<Bitset> findComponents(@Nonnull CQuery query,
                                                  @Nonnull BitJoinGraph graph) {
        int nNodes = graph.size();
        HashSet<Bitset> results = new HashSet<>(nNodes);
        InputStateHelper helper;
        helper = new InputStateHelper(graph, query.attr().varNamesUniverseOffer(),
                                      (IndexSubset<Triple>) query.attr().getSet());

        ArrayDeque<long[]> stack = new ArrayDeque<>(nNodes);
        for (int i = 0; i < nNodes; i++) {
            stack.push(helper.createState(i));
            while (!stack.isEmpty()) {
                long[] state = stack.pop();
                if (helper.isFinal(state)) {
                    assert validComponent(helper.bs, state, graph);
                    results.add(Bitsets.copyFixed(state, helper.bs.componentBegin(0),
                                                         helper.bs.componentEnd(0)));
                } else {
                    helper.forEachNeighbor(state, stack);
                    helper.bs.dealloc(state);
                }
            }
        }
        for (Bitset component : results)
            assert validComponent(component, graph);
        return results;
    }

    private boolean validComponent(@Nonnull Bitset subset, @Nonnull BitJoinGraph graph) {
        assert subset.length()-1 <= graph.size();
        RawAlignedBitSet bs = new RawAlignedBitSet(graph.size());
        long[] state = bs.alloc();
        long[] tmp = subset.toLongArray();
        System.arraycopy(tmp, 0, state, 0, tmp.length);
        assert RawAlignedBitSet.cardinality(state) == subset.cardinality();

        return validComponent(bs, state, graph);
    }

    private boolean validComponent(@Nonnull RawAlignedBitSet bs, long[] state,
                                   @Nonnull BitJoinGraph graph) {
        Set<String> nonIn = new HashSet<>(), in = new HashSet<>();
        int i = bs.nextSet(state, NODES, 0);
        for (; i >=0; i = bs.nextSet(state, NODES, i+1)) {
            nonIn.addAll(graph.getNodes().get(i).getStrictResultVars());
            in.addAll(graph.getNodes().get(i).getRequiredInputVars());
        }
        if (!nonIn.containsAll(in))
            return false;

        IndexSubset<Op> subset = graph.getNodes().subset(bs.asBitset(state, NODES));
        Op plan = joinOrderPlanner.plan(graph, subset);
        return !(plan instanceof EmptyOp);
    }

    @Override
    @Nonnull List<Bitset> findCommonSubsets(@Nonnull Collection<?> componentsColl,
                                            @Nonnull BitJoinGraph graph) {
        //noinspection unchecked
        List<Bitset> comps = componentsColl instanceof List ? (List<Bitset>)componentsColl
                                : new ArrayList<Bitset>((Collection<Bitset>) componentsColl);
        List<Bitset> results = new ArrayList<>(comps.size());
        Intersection shared = new Intersection(), notNovel = new Intersection();
        for (int i = 0, nComponents = comps.size(); i < nComponents; i++) {
            for (int j = i+1; j < nComponents; j++) {
                if (!shared.intersect(comps.get(i), comps.get(j)) || !isConnected(shared, graph))
                    continue;
                for (Bitset oldResult : results) {
                    if (oldResult.equals(shared)) {
                        shared.clear();
                        break;
                    }
                    if (!notNovel.intersect(shared, oldResult))
                        continue;
                    int sharedCard = shared.cardinality(), oldCard = oldResult.cardinality();
                    boolean handled = false;
                    if (sharedCard > oldCard) {
                        Bitset reduced = oldResult.copy();
                        reduced.andNot(notNovel);
                        if (isConnected(reduced, graph)) {
                            oldResult.andNot(notNovel);
                            handled = true;
                        }
                    }
                    if (!handled) {
                        Bitset reduced = shared.copy();
                        reduced.andNot(notNovel);
                        if (!isConnected(reduced, graph)) {
                            if (sharedCard > oldCard) { //keep only shared
                                oldResult.clear();
                            } else { //keep only old
                                shared.clear();
                                break;
                            }
                        }// else: keep one and reduced version  of other
                    }
                }
                if (!shared.isEmpty())
                    results.add(shared.take());
            }
        }

        ArrayList<Bitset> cleanResults = new ArrayList<>(results.size());
        for (Bitset set : results) {
            if (!set.isEmpty()) cleanResults.add(set);
        }
        return cleanResults;
    }

    private static class Intersection extends ArrayBitset {
        public boolean intersect(@Nonnull Bitset a, @Nonnull Bitset b) {
            clear();
            or(a);
            and(b);
            return !isEmpty();
        }
        public @Nonnull Bitset take() {
            Bitset copy = copy();
            clear();
            return copy;
        }
    }

    private boolean isConnected(@Nonnull Bitset subset, @Nonnull BitJoinGraph graph) {
        IndexSubset<Op> ss = graph.getNodes().subset(subset);
        IndexSubset<Triple> queryTriples = null;
        for (Op op : ss) {
            if (queryTriples == null)
                queryTriples = ((IndexSubset<Triple>)op.getMatchedTriples()).copy();
            else
                queryTriples.addAll(op.getMatchedTriples());
        }
        IndexSet<String> vars = ((IndexSubset<String>)ss.iterator().next().getAllVars()).getParent();
        InputStateHelper helper = new InputStateHelper(graph, vars, queryTriples);

        ArrayDeque<long[]> stack = new ArrayDeque<>(subset.cardinality()*2);
        stack.push(helper.createState(subset.nextSetBit(0)));
        while (!stack.isEmpty()) {
            long[] state = stack.pop();
            if (helper.isFinal(state)) {
                assert validComponent(helper.bs, state, graph);
                return true;
            } else {
                helper.forEachNeighbor(state, i -> {
                    if (subset.get(i)) {
                        long[] next = helper.addNode(state, i);
                        if (next != null) stack.push(next);
                    }
                });
            }
        }
        return false;
    }

    @Override
    @Nonnull List<IndexSubset<Op>> replaceShared(@Nonnull Collection<?> inComponents,
                                                 @Nonnull List<Bitset> sharedSubsets,
                                                 @Nonnull BitJoinGraph joinGraph) {
        RefIndexSet<Op> nodes = joinGraph.getNodes();
        List<IndexSubset<Op>> components = new ArrayList<>(inComponents.size());
        for (Object inComponent : inComponents)
            components.add(nodes.subset((Bitset) inComponent));

        for (Bitset shared : sharedSubsets) {
            Op plan = joinOrderPlanner.plan(joinGraph, nodes.subset(shared));
            int planIdx = nodes.size();
            boolean changed = nodes.add(plan);
            assert changed;
            assert nodes.indexOf(plan) == planIdx;
            Bitset sharedCopy = shared.copy();
            for (IndexSubset<Op> component : components) {
                sharedCopy.or(shared); // restore bits removed by andNot below
                Bitset componentBitset = component.getBitset();
                sharedCopy.andNot(componentBitset);
                if (sharedCopy.isEmpty()) { // component.containsAll(shared)
                    componentBitset.andNot(shared); // remove shared
                    componentBitset.set(planIdx);   // add the plan
                }
            }
        }
        joinGraph.notifyAddedNodes(); //compute new JoinInfos
        return components; //now with shared subsets replaced
    }
}
