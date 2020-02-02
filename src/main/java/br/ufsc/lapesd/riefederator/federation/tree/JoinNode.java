package br.ufsc.lapesd.riefederator.federation.tree;

import br.ufsc.lapesd.riefederator.query.Solution;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static br.ufsc.lapesd.riefederator.federation.tree.TreeUtils.*;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Arrays.asList;

public class JoinNode extends PlanNode {
    private @Nonnull Set<String> joinVars;

    public static class Builder {
        private @Nonnull PlanNode left, right;
        private @Nullable Set<String> joinVars = null, resultVars = null, inputVars = null;
        private boolean projecting = true;

        public Builder(@Nonnull PlanNode left, @Nonnull PlanNode right) {
            this.left = left;
            this.right = right;
        }

        @Contract("_ -> this")  @CanIgnoreReturnValue
        public @Nonnull Builder addJoinVar(@Nonnull String name) {
            if (joinVars == null) joinVars = new HashSet<>();
            joinVars.add(name);
            return this;
        }
        @Contract("_ -> this")  @CanIgnoreReturnValue
        public @Nonnull Builder addJoinVars(@Nonnull Collection<String> names) {
            if (joinVars == null) joinVars = new HashSet<>();
            joinVars.addAll(names);
            return this;
        }

        @Contract("_ -> this")  @CanIgnoreReturnValue
        public @Nonnull Builder addResultVar(@Nonnull String name) {
            if (resultVars == null) resultVars = new HashSet<>();
            resultVars.add(name);
            return this;
        }
        @Contract("_ -> this")  @CanIgnoreReturnValue
        public @Nonnull Builder addResultVars(@Nonnull List<String> names) {
            if (resultVars == null) resultVars = new HashSet<>();
            resultVars.addAll(names);
            return this;
        }
        @Contract("_ -> this") @CanIgnoreReturnValue
        public @Nonnull Builder setResultVarsNoProjection(@Nonnull Collection<String> names) {
            resultVars = names instanceof Set ? (Set<String>) names : ImmutableSet.copyOf(names);
            projecting = false;
            return this;
        }

        @Contract("_ -> this") @CanIgnoreReturnValue
        public @Nonnull Builder setInputVars(@Nonnull Collection<String> names) {
            inputVars = names instanceof Set ? (Set<String>)names : ImmutableSet.copyOf(names);
            return this;
        }

        public JoinNode build() {
            if (joinVars == null) {
                if (inputVars == null) {
                    inputVars = new HashSet<>();
                    joinVars = TreeUtils.joinVars(left, right, inputVars);
                } else {
                    joinVars = TreeUtils.joinVars(left, right, null);
                    if (getClass().desiredAssertionStatus()) {
                        Set<String> all = unionInputs(asList(left, right));
                        checkArgument(all.containsAll(inputVars),
                                "Every inputVar must be a input from left or right or both");
                        checkArgument(inputVars.stream().noneMatch(joinVars::contains),
                                "An inputVar of the join node cannot also be a joinVar");
                    }
                }
            } else {
                if (getClass().desiredAssertionStatus()) {
                    checkArgument(joinVars.stream().allMatch(n -> left.getResultVars().contains(n)
                                    && right.getResultVars().contains(n)),
                            "There are join vars which do not occur in some side");
                }
                if (inputVars == null) {
                    inputVars = TreeUtils.unionInputs(asList(left, right));
                    inputVars.removeIf(n -> !joinVars.contains(n));
                }
            }
            checkArgument(!joinVars.isEmpty(),
                    "Cannot build a JoinNode with no Join vars. Use CartesianNode or EmptyNode");

            Set<String> all;
            if (JoinNode.class.desiredAssertionStatus() || resultVars == null) {
                all = unionResults(asList(left, right));
                checkArgument(all.containsAll(joinVars), "There are extra joinVars");
                if (resultVars != null) {
                    checkArgument(all.containsAll(resultVars), "There are extra resultVars");
                    checkArgument(projecting == !resultVars.containsAll(all),
                            "Mismatch between projecting and resultVars");
                }
            } else {
                all = null;
            }
            if (resultVars == null) {
                resultVars = all;
                projecting = false;
            }
            if (inputVars == null)
                inputVars = unionInputs(asList(left, right));
            return new JoinNode(left, right, joinVars, resultVars, projecting, inputVars);
        }
    }

    public static @Nonnull Builder builder(@Nonnull PlanNode left, @Nonnull PlanNode right) {
        return new Builder(left, right);
    }

    protected JoinNode(@Nonnull PlanNode left, @Nonnull PlanNode right,
                       @Nonnull Set<String> joinVars,
                       @Nonnull Set<String> resultVars, boolean projecting,
                       @Nonnull Set<String> inputVars) {
        super(resultVars, projecting, inputVars, asList(left, right));
        this.joinVars = joinVars;
    }

    public @Nonnull Set<String> getJoinVars() {
        return joinVars;
    }

    public @Nonnull PlanNode getLeft() {
        return getChildren().get(0);
    }

    public @Nonnull PlanNode getRight() {
        return getChildren().get(1);
    }

    @Override
    public @Nonnull PlanNode createBound(@Nonnull Solution solution) {
        PlanNode left = getLeft().createBound(solution);
        PlanNode right = getRight().createBound(solution);
        Set<String> all = unionResults(asList(left, right));
        Set<String> joinVars = TreeUtils.intersect(getJoinVars(), all);
        Set<String> resultVars = TreeUtils.intersect(getResultVars(), all);
        Set<String> inputVars = TreeUtils.intersect(getInputVars(), all);

        boolean projecting = resultVars.size() < all.size();
        return new JoinNode(left, right, joinVars, resultVars, projecting, inputVars);
    }

    @Override
    public@Nonnull JoinNode replacingChildren(@Nonnull Map<PlanNode, PlanNode> map)
            throws IllegalArgumentException {
        Preconditions.checkArgument(getChildren().containsAll(map.keySet()));
        if (map.isEmpty()) return this;

        PlanNode l = map.getOrDefault(getLeft(), getLeft());
        PlanNode r = map.getOrDefault(getRight(), getRight());

        List<PlanNode> list = asList(l, r);
        Set<String> joinVars = intersect(this.joinVars, intersectResults(list));
        Set<String> allResults = unionResults(list);
        Set<String> results = intersect(getResultVars(), allResults);
        boolean projecting = results.size() != allResults.size();
        Set<String> inputs = intersect(getInputVars(), unionInputs(list));

        return new JoinNode(l, r, joinVars, results, projecting, inputs);
    }

    @Override
    public @Nonnull StringBuilder toString(@Nonnull StringBuilder builder) {
        if (isProjecting())
            builder.append(getPiWithNames()).append('(');
        getRight().toString(getLeft().toString(builder).append(" ⋈ "));
        if (isProjecting())
            builder.append(')');
        return builder;
    }
}
