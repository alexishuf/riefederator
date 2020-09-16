package br.ufsc.lapesd.riefederator.federation.planner.pre.steps;

import br.ufsc.lapesd.riefederator.algebra.InnerOp;
import br.ufsc.lapesd.riefederator.algebra.Op;
import br.ufsc.lapesd.riefederator.algebra.TakenChildren;
import br.ufsc.lapesd.riefederator.algebra.inner.PipeOp;
import br.ufsc.lapesd.riefederator.federation.planner.phased.PlannerStep;
import br.ufsc.lapesd.riefederator.query.modifiers.SPARQLFilter;
import br.ufsc.lapesd.riefederator.util.IdentityHashSet;
import br.ufsc.lapesd.riefederator.util.RefSet;
import org.apache.commons.lang3.tuple.ImmutablePair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class PushFiltersStep implements PlannerStep {

    /**
     * Walks the tree attempting to push any {@link SPARQLFilter} as deep as possible toward leaves.
     *
     * If necessary the {@link SPARQLFilter} will be placed on more than node (e.g., when it
     * is found on an UnionOp).
     *
     * @param root the input plan (or query)
     * @param locked a set of nodes (by reference) that should not be replaced or altered beyond
     *               addition of modifiers.
     * @return a replacement node for root or root itself if it could be modified in-place
     */
    @Override
    public @Nonnull Op plan(@Nonnull Op root, @Nonnull RefSet<Op> locked) {
        RefSet<Op> visited = null;
        ArrayDeque<ImmutablePair<Op, Integer>> stack = new ArrayDeque<>();
        stack.push(ImmutablePair.of(root, 0));
        while (!stack.isEmpty()) {
            ImmutablePair<Op, Integer> pair = stack.pop();
            if (pair.right == 1) {
                pushFilters(locked, pair.left);
            } else {
                assert pair.right == 0;
                if (locked.contains(pair.left)) {
                    if (visited == null)
                        visited = new IdentityHashSet<>(locked.size());
                    if (!visited.add(pair.left))
                        continue;
                }
                stack.push(ImmutablePair.of(pair.left, 1));
                for (Op child : pair.left.getChildren())
                    stack.push(ImmutablePair.of(child, 0));
            }
        }

        return root;
    }

    @Override
    public @Nonnull String toString() {
        return getClass().getSimpleName();
    }

    private void pushFilters(@Nonnull RefSet<Op> locked, @Nonnull Op root) {
        if (!(root instanceof InnerOp))
            return; //no children to push to
        List<SPARQLFilter> victims = null;
        try (TakenChildren children = ((InnerOp) root).takeChildren()) {
            for (SPARQLFilter filter : root.modifiers().filters()) {
                boolean pushed = false;
                for (ListIterator<Op> it = children.listIterator(); it.hasNext(); ) {
                    Op replacement = pushFilter(filter, locked, it.next());
                    if (replacement != null) {
                        pushed = true;
                        it.set(replacement);
                    }
                }
                if (pushed)
                    (victims == null ? victims = new ArrayList<>() : victims).add(filter);
            }
        }
        if (victims != null)
            root.modifiers().removeAll(victims);
    }

    /**
     * @return null if filter could not be pushed, else return op or a replacement
     */
    private @Nullable Op pushFilter(@Nonnull SPARQLFilter filter,
                                    @Nonnull RefSet<Op> locked, @Nonnull Op op) {
        if (!op.getAllVars().containsAll(filter.getVarTermNames()))
            return null; //op cannot evaluate filter being pushed down
        boolean pushed = false;
        if (op instanceof InnerOp) {
            try (TakenChildren children = ((InnerOp) op).takeChildren()) {
                for (ListIterator<Op> it = children.listIterator(); it.hasNext(); ) {
                    Op replacement = pushFilter(filter, locked, it.next());
                    if (replacement != null) {
                        it.set(replacement);
                        pushed = true;
                    }
                }
            }
        }
        if (!pushed) {
            if (locked.contains(op))
                op = new PipeOp(op);
            op.modifiers().add(filter);
        }
        return op;
    }
}
