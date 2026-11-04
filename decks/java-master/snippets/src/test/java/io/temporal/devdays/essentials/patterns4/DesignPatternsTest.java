package io.temporal.devdays.essentials.patterns4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.ClaimWorkflow;
import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.FollowUpWorkflow;
import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.LoanWorkflow;
import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.RiskWorkflow;
import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.SignupWorkflow;
import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.TriageWorkflow;
import io.temporal.devdays.essentials.patterns4.RfiWorkflows.AnnualReportWorkflow;
import io.temporal.devdays.essentials.patterns4.RfiWorkflows.EligibilityWorkflow;
import io.temporal.devdays.essentials.patterns4.RfiWorkflows.ReviewWorkflow;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** 04-design-patterns: each slide's code runs, and the claims its notes make about it hold. */
@Timeout(30)
class DesignPatternsTest {
  private static final String QUEUE = "patterns";

  /** An email service that fails the first {@code failuresLeft} sends with an SMTP timeout. */
  static class FlakyEmail implements EmailService {
    final AtomicInteger failuresLeft = new AtomicInteger();
    final List<String> sent = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void send(String to, String body) {
      if (failuresLeft.getAndDecrement() > 0) throw new IllegalStateException("SMTP timeout");
      sent.add(to + ": " + body);
    }
  }

  private TestWorkflowEnvironment env;
  private WorkflowClient client;
  private FlakyEmail email;
  private List<String> reports;
  private UserDb db;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    client = env.getWorkflowClient();
    email = new FlakyEmail();
    reports = Collections.synchronizedList(new ArrayList<>());
    db = new UserDb();
    Worker worker = env.newWorker(QUEUE);
    worker.registerWorkflowImplementationTypes(
        EligibilityWorkflowImpl.class,
        ReviewWorkflowImpl.class,
        AnnualReportWorkflowImpl.class,
        TriageWorkflowImpl.class,
        LoanWorkflowImpl.class,
        FollowUpWorkflowImpl.class,
        RiskWorkflowImpl.class,
        ClaimWorkflowImpl.class,
        SignupWorkflowImpl.class,
        IdsWorkflow.Impl.class);
    worker.registerActivitiesImplementations(
        new RfiActivitiesImpl(email, reports::add),
        new ClaimActivitiesImpl(),
        new LoanActivitiesImpl(),
        new UserActivitiesImpl(db, email));
    env.start();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private <T> T stub(Class<T> type, String id) {
    return client.newWorkflowStub(
        type, WorkflowOptions.newBuilder().setTaskQueue(QUEUE).setWorkflowId(id).build());
  }

  private <T> T stub(Class<T> type) {
    return stub(type, UUID.randomUUID().toString());
  }

  private List<HistoryEvent> history(String workflowId) {
    return client.fetchHistory(workflowId).getEvents();
  }

  private final RfiRequest rfi = new RfiRequest("RFI-0042", "p-7", Instant.now(), 12_000);

  @Test
  void workflowSafeIdsReplayButJdkOnesDoNot() {
    String id = "ids-" + UUID.randomUUID();
    IdsWorkflow wf = stub(IdsWorkflow.class, id);
    List<String> firstRun = wf.ids();
    // A Query on a closed Workflow replays its history on a fresh Workflow instance.
    List<String> replayed = client.newWorkflowStub(IdsWorkflow.class, id).idsAfterReplay();
    assertEquals(
        firstRun.get(0),
        replayed.get(0),
        "04-design-patterns.md (Make it an Activity if it…): the notes say Workflow.randomUUID()"
            + " gives the same value on replay; IdsWorkflow.java's replay produced another");
    assertNotEquals(
        firstRun.get(1),
        replayed.get(1),
        "04-design-patterns.md (Make it an Activity if it…): the notes say UUID.randomUUID() in a"
            + " Workflow differs on replay; IdsWorkflow.java's replay produced the same value");
  }

  @Test
  void validateEligibilityChecksAllThreeRules() {
    assertTrue(
        stub(EligibilityWorkflow.class).check(rfi),
        "RfiActivitiesImpl.validateEligibility should pass RFI-0042, submitted now, cost 12000");
    RfiRequest stale = new RfiRequest("RFI-0042", "p-7", Instant.parse("2020-01-01T00:00:00Z"), 1);
    assertTrue(
        !stub(EligibilityWorkflow.class).check(stale),
        "RfiActivitiesImpl.validateEligibility should reject an RFI submitted in 2020");
  }

  @Test
  void aFailedSendIsRetriedAndTheWorkflowNeverSeesIt() {
    email.failuresLeft.set(2);
    stub(ReviewWorkflow.class).review(rfi, "rita@example.com");
    assertEquals(
        List.of("rita@example.com: RFI RFI-0042 requires your review"),
        email.sent,
        "04-design-patterns.md (can fail on its own): notifyReviewer failed twice, so Temporal"
            + " should have retried it until the email went out, once");
  }

  @Test
  void theAnnualReportRunsUnderTheCallersTwoHourTimeout() {
    String id = "report-" + UUID.randomUUID();
    stub(AnnualReportWorkflow.class, id).run(rfi);
    assertEquals(List.of("p-7"), reports, "generateAnnualReport should compile project p-7");
    HistoryEvent scheduled =
        history(id).stream()
            .filter(e -> e.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED)
            .findFirst()
            .orElseThrow();
    assertEquals(
        Duration.ofHours(2).toSeconds(),
        scheduled.getActivityTaskScheduledEventAttributes().getStartToCloseTimeout().getSeconds(),
        "04-design-patterns.md (takes significant time): AnnualReportWorkflowImpl's stub sets a"
            + " two-hour Start-To-Close Timeout");
  }

  @Test
  void theBusinessDecisionIsAnIfInTheWorkflow() {
    TriageWorkflow big = stub(TriageWorkflow.class);
    assertEquals(
        ApprovalStatus.REQUIRES_REVIEW,
        big.triage(new Claim("c-1", 20_000, ClaimType.CHECKUP, List.of())),
        "TriageWorkflowImpl.triage: a claim over 10,000 requires review");
    String id = "triage-" + UUID.randomUUID();
    assertEquals(
        ApprovalStatus.REQUIRES_REVIEW,
        stub(TriageWorkflow.class, id).triage(new Claim("c-2", 500, ClaimType.SURGERY, List.of())),
        "TriageWorkflowImpl.triage: surgery requires review");
    assertTrue(
        history(id).stream()
            .noneMatch(e -> e.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED),
        "04-design-patterns.md (makes business decisions): the decision costs no Activity call");
    assertEquals(
        ApprovalStatus.APPROVED,
        stub(TriageWorkflow.class).triage(new Claim("c-3", 500, ClaimType.CHECKUP, List.of())),
        "TriageWorkflowImpl.triage: a small checkup continues to the Activities");
  }

  @Test
  void adjudicationChainsThreeActivities() {
    assertEquals(
        Decision.APPROVED,
        stub(LoanWorkflow.class).adjudicate(new Applicant("Ada", 90_000)),
        "LoanWorkflowImpl.adjudicate: a high income should be approved");
    assertEquals(
        Decision.DECLINED,
        stub(LoanWorkflow.class).adjudicate(new Applicant("Bob", 20_000)),
        "LoanWorkflowImpl.adjudicate: a low income should be declined");
  }

  @Test
  void aThirtyDaySleepIsADurableTimerAndAwaitWaitsForTheSignal() {
    String id = "followup-" + UUID.randomUUID();
    FollowUpWorkflow wf = stub(FollowUpWorkflow.class, id);
    WorkflowClient.start(wf::followUp);
    env.sleep(Duration.ofDays(31));
    wf.respond("yes, please");
    assertEquals(
        "yes, please",
        WorkflowStub.fromTyped(wf).getResult(String.class),
        "FollowUpWorkflowImpl: Workflow.await should return once the Signal sets response");
    HistoryEvent timer =
        history(id).stream()
            .filter(e -> e.getEventType() == EventType.EVENT_TYPE_TIMER_STARTED)
            .findFirst()
            .orElseThrow();
    assertEquals(
        Duration.ofDays(30).toSeconds(),
        timer.getTimerStartedEventAttributes().getStartToFireTimeout().getSeconds(),
        "04-design-patterns.md (manages timing): Workflow.sleep(30 days) is a Timer in history");
  }

  @Test
  void aPureCalculationGivesTheSameAnswer() {
    Claim claim = new Claim("c-1", 5_000, ClaimType.CHECKUP, List.of("a", "b"));
    assertEquals(
        7.0,
        stub(RiskWorkflow.class).score("ada@company.com", claim),
        "RiskWorkflowImpl: 5000 / 1000 + 2 claims of history is 7");
    assertEquals(
        -1.0,
        stub(RiskWorkflow.class).score("ada@elsewhere.com", claim),
        "RiskWorkflowImpl: an email outside company.com is not valid");
  }

  @Test
  void theActivityResultIsWrittenToHistory() {
    String id = "claim-" + UUID.randomUUID();
    assertEquals(
        new Approval("c-1", true),
        stub(ClaimWorkflow.class, id).process(new Claim("c-1", 500, ClaimType.CHECKUP, List.of())),
        "ClaimWorkflowImpl.process should return getApproval's result");
    assertTrue(
        history(id).stream()
            .anyMatch(e -> e.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_COMPLETED),
        "04-design-patterns.md (must survive restarts): the Activity result is in Event History");
  }

  @Test
  void aFailedEmailRetriesTheWholeActivityAndAddsTheUserTwice() {
    email.failuresLeft.set(1);
    User user = stub(SignupWorkflow.class).signUp(new UserData("ada@example.com"));
    assertEquals(
        new User(2, "ada@example.com"),
        user,
        "04-design-patterns.md (When to split an Activity): the retry of createUser inserts again");
    assertEquals(2, db.rows.size(), "UserActivitiesImpl.createUser should have added two rows");
  }
}
