package io.temporal.devdays.childworkflows;

import io.temporal.workflow.Workflow;
import java.time.Duration;

public class PackItemWorkflowImpl implements PackItemWorkflow {
    @Override
    public String pack(String item) {
        Workflow.sleep(Duration.ofSeconds(3));
        return "box:" + item;
    }
}
