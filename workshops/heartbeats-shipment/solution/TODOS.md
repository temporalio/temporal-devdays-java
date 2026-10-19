# TODOs

One TODO in three parts, all in Challenge 1. The Track's Check fails until all
three are done, and names the first part still missing.

| TODO | Challenge | File | What to do |
|---|---|---|---|
| 1a | 1 | `ShipmentWorkflowImpl.java`, the `ActivityOptions` | Add `.setHeartbeatTimeout(Duration.ofSeconds(2))`. |
| 1b | 1 | `ShipmentActivitiesImpl.java`, `packShipment` loop | Call `context.heartbeat(packed)` after each item. |
| 1c | 1 | `ShipmentActivitiesImpl.java`, top of `packShipment` | Start from `context.getHeartbeatDetails(Integer.class)` when there is one, else 0. |

Each part sits between a `// TODO 1x start` and a `// TODO 1x end` comment.
Change only the lines between them.
