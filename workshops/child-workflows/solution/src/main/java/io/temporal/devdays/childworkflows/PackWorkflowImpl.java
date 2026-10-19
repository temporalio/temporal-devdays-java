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
        Promise.allOf(boxes).get();
        return boxes.stream().map(Promise::get).toList();
        // TODO 1 end
    }
}
