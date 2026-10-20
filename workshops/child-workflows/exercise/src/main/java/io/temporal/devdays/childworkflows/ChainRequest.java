package io.temporal.devdays.childworkflows;

/**
 * The input of every level of a ChainWorkflow.
 *
 * @param depth        how many levels of children are still to start below this one
 * @param failAtBottom the Workflow at the bottom of the chain fails instead of returning
 */
public record ChainRequest(int depth, boolean failAtBottom) {}
