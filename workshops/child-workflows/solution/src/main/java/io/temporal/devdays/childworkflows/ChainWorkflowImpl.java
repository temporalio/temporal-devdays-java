package io.temporal.devdays.childworkflows;

import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Workflow;

public class ChainWorkflowImpl implements ChainWorkflow {
    @Override
    public int run(ChainRequest request) {
        if (request.depth() == 0) {
            if (request.failAtBottom()) {
                throw ApplicationFailure.newFailure("the bottom of the chain failed", "BottomOfChain");
            }
            return 0;
        }
        // Each child's ID is its parent's ID plus one segment, so the ID alone
        // tells you how deep you are: chain-ABC123/4/3/2/1/0.
        ChainWorkflow below = Workflow.newChildWorkflowStub(
                ChainWorkflow.class,
                ChildWorkflowOptions.newBuilder()
                        .setWorkflowId(Workflow.getInfo().getWorkflowId() + "/" + (request.depth() - 1))
                        .build());
        return below.run(new ChainRequest(request.depth() - 1, request.failAtBottom())) + 1;
    }
}
