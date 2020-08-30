package br.ufsc.lapesd.riefederator.federation.execution.tree.impl.joins;

import br.ufsc.lapesd.riefederator.algebra.Op;
import br.ufsc.lapesd.riefederator.algebra.inner.JoinOp;
import br.ufsc.lapesd.riefederator.federation.execution.PlanExecutor;
import br.ufsc.lapesd.riefederator.query.modifiers.Optional;
import br.ufsc.lapesd.riefederator.query.results.Results;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class AbstractBindJoinOpExecutor extends AbstractSimpleJoinOpExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AbstractBindJoinOpExecutor.class);

    protected AbstractBindJoinOpExecutor(Provider<PlanExecutor> planExecutorProvider) {
        super(planExecutorProvider);
    }

    protected AbstractBindJoinOpExecutor(PlanExecutor planExecutor) {
        super(planExecutor);
    }

    @Override
    protected  @Nonnull Results innerExecute(@Nonnull JoinOp node) {
        PlanExecutor planExecutor = getPlanExecutor();
        Results leftResults = null;
        Op[] nodes = orderForBind(node);
        try {
            leftResults = planExecutor.executeNode(nodes[0]);
            Results results = createResults(leftResults, nodes[1], node);
            leftResults = null; // ownership transferred
            return results;
        } finally {
            if (leftResults != null)
                leftResults.close();
        }
    }

    protected abstract @Nonnull  Results createResults(@Nonnull Results left,
                                                       @Nonnull Op right,
                                                       @Nonnull JoinOp node);

    protected @Nonnull  Op[] orderForBind(@Nonnull JoinOp node) {
        Op[] nodes = new Op[] {node.getLeft(), node.getRight()};

        int leftWeight  = (nodes[0].hasInputs() ? 1 : 0) + (nodes[0].hasRequiredInputs() ? 1 : 0);
        int rightWeight = (nodes[1].hasInputs() ? 1 : 0) + (nodes[1].hasRequiredInputs() ? 1 : 0);
        if (leftWeight > rightWeight) { //send node with inputs to the right
            Op tmp = nodes[0];
            nodes[0] = nodes[1];
            nodes[1] = tmp;
        }
        checkArgument(!nodes[0].hasRequiredInputs(), "Both left and right children have" +
                                                     " required inputs. Cannot bind join "+node);
        if (nodes[0].modifiers().optional() != null) {
            boolean hasReq = nodes[1].hasRequiredInputs();
            boolean hasOpt = nodes[1].modifiers().optional() != null;
            if (hasReq || hasOpt) {
                logger.error("Left operand of bind join is optional, but cannot be moved to " +
                             "the right since the right operand {}{}{}. Will " +
                             "ignore the optional clause on the left operand. join: {}",
                             hasReq ? "has required inputs " : "",
                             hasReq && hasOpt ? "and " : "",
                             hasOpt ? "is optional" : "",
                             node.prettyPrint());
                nodes[0] = nodes[0].flatCopy();
                nodes[0].modifiers().remove(Optional.INSTANCE);
            } else {
                Op tmp = nodes[1];
                nodes[1] = nodes[0];
                nodes[0] = tmp;
            }
        }

        return nodes;
    }
}