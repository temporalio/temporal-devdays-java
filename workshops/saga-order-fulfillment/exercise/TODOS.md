# TODOs

Every TODO in this Workshop, in the order you do them. Each coding Challenge in
the Track covers one row, and its Check fails until that row is done.

| TODO | Challenge | File | What to do |
|---|---|---|---|
| 1 | 2. Unwind | `OrderFulfillmentWorkflowImpl.java`, `newSaga` and `unwind` | Build the Saga with `setContinueWithError(true)`, and call `saga.compensate()` in `unwind`. |
| 2a | 3. Register before you run | `OrderFulfillmentWorkflowImpl.java`, `fulfill` | Register the release with `saga.addCompensation` before `reserveInventory` runs. |
| 2b | 3. Register before you run | `OrderFulfillmentWorkflowImpl.java`, `fulfill` | Register the refund with `saga.addCompensation` before `chargePayment` runs. |
| 3a | 4. Make the undo safe to repeat | `OrderActivitiesImpl.java`, `releaseInventoryIfReserved` | Return early when nothing is reserved. |
| 3b | 4. Make the undo safe to repeat | `OrderActivitiesImpl.java`, `refundPaymentIfCharged` | Return early when nothing is charged. |

Challenge 1 changes no code. Each TODO sits between a `// TODO N start` and a
`// TODO N end` comment. Change only the lines between them.
