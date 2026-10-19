package io.temporal.devdays.subscription;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A real `temporal server start-dev` on a free port, for the few tests the
 * in-memory TestWorkflowEnvironment cannot reproduce: dynamic config such as a
 * low history-count limit, or a heartbeat timeout firing in real time.
 *
 * Needs the `temporal` CLI on the PATH (it is in the Sandbox Image). Delete this
 * file from a Workshop that does not use it.
 *
 *   try (DevServer server = DevServer.start("limit.historyCount.suggestContinueAsNew=20")) {
 *       WorkflowClient client = server.client();
 *       ...
 *   }
 */
public final class DevServer implements AutoCloseable {
    private final Process process;
    private final File log;
    private final WorkflowServiceStubs service;
    private final WorkflowClient client;

    private DevServer(Process process, File log, WorkflowServiceStubs service) {
        this.process = process;
        this.log = log;
        this.service = service;
        this.client = WorkflowClient.newInstance(service);
    }

    /** Starts a dev server with each "key=value" as a --dynamic-config-value. */
    public static DevServer start(String... dynamicConfig) throws IOException {
        int port = freePort();
        List<String> cmd = new ArrayList<>(List.of(
                "temporal", "server", "start-dev",
                "--headless",
                "--ip", "127.0.0.1",
                "--port", String.valueOf(port),
                "--log-level", "error"));
        for (String kv : dynamicConfig) {
            cmd.add("--dynamic-config-value");
            cmd.add(kv);
        }
        File log = File.createTempFile("devserver-", ".log");
        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget("127.0.0.1:" + port).build());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            try {
                service.healthCheck();
                break;
            } catch (RuntimeException e) {
                if (!process.isAlive() || System.nanoTime() > deadline) {
                    process.destroyForcibly();
                    service.shutdownNow();
                    throw new IllegalStateException(
                            "The Temporal dev server did not start on port " + port + ". Its log: " + log, e);
                }
                sleep(Duration.ofMillis(200));
            }
        }
        return new DevServer(process, log, service);
    }

    public WorkflowClient client() {
        return client;
    }

    @Override
    public void close() {
        service.shutdownNow();
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        log.delete();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
