package io.temporal.devdays.childworkflows;

import io.temporal.workflow.Async;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import java.util.ArrayList;
import java.util.List;

public class PackWorkflowImpl implements PackWorkflow {
    @Override
    public List<String> pack(Order order) {
        // Fan out: start one child per item, without waiting for any of them.
        List<Promise<String>> boxes = new ArrayList<>();
        for (int i = 0; i < order.items().size(); i++) {
            PackItemWorkflow child = Workflow.newChildWorkflowStub(
                    PackItemWorkflow.class,
                    ChildWorkflowOptions.newBuilder()
                            .setWorkflowId("pack-" + order.orderId() + "-" + i)
                            .build());
            boxes.add(Async.function(child::pack, order.items().get(i)));
            Workflow.getWorkflowExecution(child).get(); // waits for the start, not the result
        }

        // TODO 1 start
        // Fan in: wait for every child with Promise.allOf(boxes), then return
        // each child's box, in item order. Until you do, the parent returns at
        // once and the default Parent Close Policy terminates every child.
        return List.of();
        // TODO 1 end
    }
}
