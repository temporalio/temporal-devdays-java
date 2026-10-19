package io.temporal.devdays.childworkflows;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.ChildWorkflowFailure;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Starts one parent Workflow and prints what came back. Run with:
 * mvn -q compile exec:java@starter -Dexec.args="chain --fail"
 */
public class Starter {
    private static final String USAGE = """
            Usage: run-starter <scenario> [flags]

              child               one parent, one child
              chain --fail        a chain 5 levels deep that fails at the bottom
              chain --depth 3     a shorter chain that succeeds
              pack                fan out one child per item, then fan in""";

    public static void main(String[] args) throws InterruptedException {
        String scenario = args.length > 0 ? args[0] : "";
        List<String> flags = List.of(args).subList(Math.min(1, args.length), args.length);
        if (!List.of("child", "chain", "pack").contains(scenario)) {
            System.err.println(USAGE);
            System.exit(2);
        }

        String target = System.getenv().getOrDefault("TEMPORAL_ADDRESS", "127.0.0.1:7233");
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        WorkflowClient client = WorkflowClient.newInstance(service);

        switch (scenario) {
            case "child" -> runChild(client);
            case "chain" -> runChain(client, depth(flags), flags.contains("--fail"));
            default -> runPack(client);
        }
        System.exit(0);
    }

    private static void runChild(WorkflowClient client) throws InterruptedException {
        Order order = new Order(newId("ORD"), List.of("mug"));
        String tracking = resultOf(client, "OrderWorkflow", "order-" + order.orderId(), String.class, order);
        System.out.println("Parent  order-" + order.orderId() + "  completed");
        System.out.println("Child   shipment-" + order.orderId() + "  returned " + tracking);
    }

    private static void runChain(WorkflowClient client, int depth, boolean fail) throws InterruptedException {
        String root = newId("chain");
        try {
            int levels = resultOf(client, "ChainWorkflow", root, Integer.class, new ChainRequest(depth, fail));
            System.out.println("Chain " + root + " completed: " + levels + " levels of children below the top");
        } catch (WorkflowFailedException e) {
            // Unwrap one level at a time. Every parent wraps its child's failure
            // in a ChildWorkflowFailure, so the real error is at the very bottom.
            System.out.println("Chain " + root + " failed. The failure, one wrapper per level:\n");
            Throwable cause = e.getCause();
            while (cause instanceof ChildWorkflowFailure child) {
                System.out.println("  ChildWorkflowFailure  from " + child.getExecution().getWorkflowId());
                cause = child.getCause();
            }
            if (cause instanceof ApplicationFailure app) {
                System.out.println("  ApplicationFailure: " + app.getType() + ": " + app.getOriginalMessage());
            } else {
                System.out.println("  " + cause);
            }
        }
    }

    private static void runPack(WorkflowClient client) throws InterruptedException {
        Order order = new Order(newId("ORD"), List.of("mug", "book", "lamp", "plant", "clock"));
        long started = System.nanoTime();
        List<?> boxes = resultOf(client, "PackWorkflow", "pack-" + order.orderId(), List.class, order);
        double took = (System.nanoTime() - started) / 1e9;
        System.out.printf(Locale.ROOT, "Packed %d of %d items in %.1fs: %s%n",
                boxes.size(), order.items().size(), took, boxes);
    }

    /**
     * Starts a Workflow and waits for its result, saying so if no Worker picks it
     * up. Without a Worker the Workflow just sits on the Task Queue, so the
     * starter would otherwise wait forever with nothing on screen.
     */
    private static <R> R resultOf(WorkflowClient client, String type, String workflowId, Class<R> resultType,
            Object arg) throws InterruptedException {
        WorkflowStub stub = client.newUntypedWorkflowStub(type,
                WorkflowOptions.newBuilder().setTaskQueue(Shared.TASK_QUEUE).setWorkflowId(workflowId).build());
        stub.start(arg);
        CompletableFuture<R> result = stub.getResultAsync(resultType);
        try {
            try {
                return result.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                System.out.println("Still waiting on " + workflowId
                        + ". Is the Worker running? In the Worker tab, run:\n  run-worker");
                return result.get();
            }
        } catch (ExecutionException e) {
            if (e.getCause() instanceof WorkflowException failure) {
                throw failure;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    private static int depth(List<String> flags) {
        int at = flags.indexOf("--depth");
        return at >= 0 && at + 1 < flags.size() ? Integer.parseInt(flags.get(at + 1)) : 5;
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
