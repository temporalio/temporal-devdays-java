package io.temporal.devdays.essentials.client;

/**
 * Not on a slide, and never on the Client's classpath in a real system: the Client only needs the
 * interface. TransferClientTest registers it on a test Worker.
 */
public class TransferWorkflowImpl implements TransferWorkflow {
  @Override
  public Receipt transfer(Transfer transfer) {
    return new Receipt(transfer.transferId(), "COMPLETED");
  }
}
