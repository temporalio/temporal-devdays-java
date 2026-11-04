---
layout: comparison
leftEyebrow: RECOMMENDED FOR SAFETY
leftTitle: Register before Activity Execution
leftFeatures:
  - "The compensation runs even if the Activity fails after partial completion"
  - "Example: a card is charged, but the Activity fails before returning success"
  - "The compensation must be idempotent and no-op when the forward Activity never executed"
  - "The safer default when side effects may occur before failure"
rightEyebrow: APPROPRIATE WHEN SAFE
rightTitle: Register after Activity Execution
rightFeatures:
  - "Only compensates Activities that completed successfully"
  - "Simpler: no need to check whether the forward action occurred"
  - "Appropriate when Activities are truly atomic (all-or-nothing)"
  - "Risk: partial completion without compensation"
---

The choice depends on your Activity's failure characteristics.

::right::

When in doubt, register **before** execution and make compensations idempotent.

<!--
Source: Temporal docs, Saga pattern page (When to register compensations).
Ported from the Python Deck.

Two minutes.

Ask the room which of the lab's steps is truly atomic. Neither. `chargePayment`
is the docs' own example: picture a charge that is recorded, and then the Activity
throws. Registered after, the refund is never added to the array and the
charge is stranded. Registered before, the refund runs, and its guard makes it
safe.

Registering after has one legitimate use: when the undo needs what the step
returned, such as a payment confirmation the refund must quote. Then the undo
cannot exist before the step, and the step had better be atomic.
-->
