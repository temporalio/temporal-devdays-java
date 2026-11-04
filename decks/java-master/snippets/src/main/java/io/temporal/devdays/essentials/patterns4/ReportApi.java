package io.temporal.devdays.essentials.patterns4;

/** The reporting service the two-hour Activity calls. Not on a slide. */
public interface ReportApi {
  void compileAnnual(String projectId);
}
