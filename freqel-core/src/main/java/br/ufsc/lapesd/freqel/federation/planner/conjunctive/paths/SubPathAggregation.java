package br.ufsc.lapesd.freqel.federation.planner.conjunctive.paths;

import br.ufsc.lapesd.freqel.algebra.Op;
import br.ufsc.lapesd.freqel.federation.planner.JoinOrderPlanner;
import br.ufsc.lapesd.freqel.federation.planner.conjunctive.ArrayJoinGraph;
import br.ufsc.lapesd.freqel.federation.planner.conjunctive.JoinGraph;
import br.ufsc.lapesd.freqel.model.Triple;
import br.ufsc.lapesd.freqel.util.indexed.ref.RefIndexSet;
import br.ufsc.lapesd.freqel.util.indexed.subset.IndexSubset;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;
import java.util.*;

public class SubPathAggregation {
    private final @Nonnull JoinGraph graph;
    private final @Nonnull List<JoinComponent> joinComponents;

    private SubPathAggregation(@Nonnull JoinGraph graph, @Nonnull List<JoinComponent> joinComponents) {
        this.graph = graph;
        this.joinComponents = joinComponents;
    }

    public static @Nonnull
    SubPathAggregation aggregate(@Nonnull JoinGraph g, @Nonnull Collection<JoinComponent> paths,
                                 @Nonnull JoinOrderPlanner joinOrderPlanner) {
        if (paths.isEmpty()) {
            return new SubPathAggregation(new ArrayJoinGraph(), Collections.emptyList());
        }
        List<JoinComponent> pathsList = paths instanceof List ? (List<JoinComponent>)paths
                                                         : new ArrayList<>(paths);
        State state = new State(g);
        int i = -1, size = pathsList.size();
        for (JoinComponent path : pathsList) {
            ++i;
            for (int j = i+1; j < size; j++)
                state.processPair(path, pathsList.get(j));
        }

        state.planComponents(joinOrderPlanner);
        JoinGraph reducedGraph = state.createReducedJoinGraph(pathsList);
        List<JoinComponent> reducedPaths = new ArrayList<>(pathsList.size());
        outer:
        for (JoinComponent path : pathsList) {
            JoinComponent reduced = state.reducePath(path);
            for (JoinComponent old : reducedPaths) {
                if (old.equals(reduced))
                    continue outer;
            }
            reducedPaths.add(reduced);
        }
        return new SubPathAggregation(reducedGraph, reducedPaths);
    }

    public @Nonnull JoinGraph getGraph() {
        return graph;
    }

    public @Nonnull List<JoinComponent> getJoinComponents() {
        return joinComponents;
    }

    @VisibleForTesting
    static class PlannedComponent {
        IndexSubset<Op> component;
        Op node;

        public PlannedComponent(IndexSubset<Op> component, Op node) {
            this.component = component;
            this.node = node;
        }
    }

    @VisibleForTesting
    static class State {
        private final @Nonnull JoinGraph graph;
        private final @Nonnull List<IndexSubset<Op>> components;
        private List<PlannedComponent> planned;
        private JoinGraph reducedGraph;

        public State(@Nonnull JoinGraph graph) {
            this.graph = graph;
            this.components = new ArrayList<>(graph.size());
        }

        @VisibleForTesting
        @Nonnull List<IndexSubset<Op>> getComponents() {
            return components;
        }

        @VisibleForTesting
        @Nonnull List<PlannedComponent> getPlanned() {
            return planned;
        }

        public void planComponents(@Nonnull JoinOrderPlanner planner) {
            assert components.stream().noneMatch(Objects::isNull);
            assert components.stream().noneMatch(Set::isEmpty);
            checkDisjointness();

            planned = new ArrayList<>(components.size());
            for (IndexSubset<Op> component : components) {
                if (component.size() == 1)
                    planned.add(new PlannedComponent(component, component.iterator().next()));
                else
                    planned.add(new PlannedComponent(component, planner.plan(graph, component)));
            }
        }

        public @Nonnull JoinGraph createReducedJoinGraph(@Nonnull Collection<JoinComponent> paths) {
            Preconditions.checkState(planned != null, "Call planComponents() before!");
            Preconditions.checkState(planned.size() == components.size());

            IndexSubset<Op> visited = graph.getNodes().emptySubset();
            List<Op> nodes = new ArrayList<>(graph.size());
            for (PlannedComponent pc : planned) {
                nodes.add(pc.node);
                visited.addAll(pc.component);
            }
            for (JoinComponent path : paths) {
                IndexSubset<Op> novel = path.getNodes().createMinus(visited);
                nodes.addAll(novel);
                visited.addAll(novel);
            }

            reducedGraph = new ArrayJoinGraph(RefIndexSet.fromRefDistinct(nodes));
            return reducedGraph;
        }

        public @Nonnull JoinComponent reducePath(@Nonnull JoinComponent path) {
            if (path.isWhole())
                return path;
            IndexSubset<Op> nodes = reducedGraph.getNodes().emptySubset();
            IndexSubset<Op> pending = path.getNodes().copy();
            boolean needsRebuild = false;
            for (PlannedComponent pc : planned) {
                if (path.getNodes().containsAll(pc.component)) {
                    nodes.add(pc.node);
                    pending.removeAll(pc.component);
                    needsRebuild |= pc.component.size() > 1;
                }
            }
            if (needsRebuild) {
                nodes.addAll(pending);
                assert hasNoSubsumedNodes(nodes);
                return new JoinComponent(reducedGraph, nodes);
            }
            return path; // no components or only singleton components
        }

        private boolean hasNoSubsumedNodes(IndexSubset<Op> nodes) {
            List<Op> subsumed = new ArrayList<>();
            for (Op i : nodes) {
                Set<Triple> iMatched = i.getMatchedTriples();
                for (Op j : nodes) {
                    if (i == j) continue;
                    Set<Triple> jMatched = j.getMatchedTriples();
                    if (iMatched.containsAll(jMatched))
                        subsumed.add(j);
                }
            }
            return subsumed.isEmpty();
        }

        public void processPair(@Nonnull JoinComponent left, @Nonnull JoinComponent right) {
            assert left.getNodes().getParent().containsAll(right.getNodes());
            assert right.getNodes().getParent().containsAll(left.getNodes());
            IndexSubset<Op> common = left.getNodes().createIntersection(right.getNodes());
            IndexSubset<Op> visited = common.copy();
            visited.clear();
            ArrayDeque<Op> stack = new ArrayDeque<>();
            for (Op start : common) {
                if (visited.contains(start))
                    continue;
                stack.push(start);
                IndexSubset<Op> component = common.copy();
                component.clear();
                while (!stack.isEmpty()) {
                    Op node = stack.pop();
                    if (component.add(node)) {
                        graph.forEachNeighbor(node, (i, n) -> {
                            if (common.contains(n)) stack.push(n);
                        });
                    }
                }
                visited.addAll(component);
                store(component);
            }
        }

        public void store(@Nonnull IndexSubset<Op> novel) {
            checkJoinConnected(novel);
            List<IndexSubset<Op>> dismembered = new ArrayList<>();
            for (IndexSubset<Op> c : components) {
                IndexSubset<Op> common = c.createIntersection(novel);
                if (!common.isEmpty()) {
                    if (common.size() != c.size()) {
                        c.removeAll(common);
                        dismembered.add(common);
                    }
                    novel.removeAll(common);
                }
            }
            components.addAll(dismembered);
            if (!novel.isEmpty())
                components.add(novel);
            checkDisjointness();
        }

        private void checkJoinConnected(@Nonnull IndexSubset<Op> component) {
            if (!SubPathAggregation.class.desiredAssertionStatus())
                return; //check is expensive
            if (component.isEmpty())
                return; //empty is always join-connected
            IndexSubset<Op> visited = component.copy();
            visited.clear();
            ArrayDeque<Op> stack = new ArrayDeque<>(component.size());
            stack.push(component.iterator().next());
            while (!stack.isEmpty()) {
                Op node = stack.pop();
                if (visited.add(node)) {
                    graph.forEachNeighbor(node, (i, n) -> {
                        if (component.contains(n))
                            stack.push(n);
                    });
                }
            }
            assert visited.size() <= component.size();
            if (!visited.equals(component)) {
                IndexSubset<Op> missing = component.copy();
                missing.removeAll(visited);
                throw new IllegalArgumentException("component "+component+" is disconnected. "+
                        missing+" are not reachable from the first member");
            }
        }

        private void checkDisjointness() {
            if (State.class.desiredAssertionStatus()) {
                for (int i = 0; i < components.size(); i++) {
                    for (int j = i+1; j < components.size(); j++) {
                        IndexSubset<Op> bad;
                        bad = components.get(i).createIntersection(components.get(j));
                        assert bad.isEmpty() : i+"-th and "+j+"-th components intersect: "+bad;
                    }
                }
            }
        }
    }
}
