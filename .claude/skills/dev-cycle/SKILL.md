---
name: dev-cycle
description: Runs a feature or bug fix through the full recursive agentic development loop — architect designs, developer implements, reviewer checks against OWASP/compliance, qa tests against acceptance criteria — looping back automatically on rejection until it passes or hits an iteration cap. Use when the user asks to build/fix something "through the team", "with the agent workflow", or names /dev-cycle.
---

# dev-cycle: recursive agentic development loop

This skill orchestrates four specialist subagents defined in `.claude/agents/`: **architect**, **developer**, **reviewer**, **qa**. You (the invoking Claude) are the orchestrator — you don't do the design/implementation/review/testing yourself, you drive the loop and make the routing decisions between agents, and you're the one who talks to the human.

## Why this exists

A single agent designing, coding, and reviewing its own work has no adversarial check — it tends to approve its own reasoning. Splitting into roles with separate agent invocations (fresh context each time, forced to read the artifact rather than remember writing it) plus a hard requirement that reviewer/QA never edit code themselves gives a real second opinion, the same reason human teams don't let one person design, build, review, and sign off on their own PR.

## Handoff mechanism

Agents don't share conversation context with each other — each `Agent` call starts fresh. The handoff between phases is **files in the repo**, not prose you relay:

- Architect writes `docs/designs/<slug>.md` (the design doc + acceptance criteria).
- Developer's changes live in the actual working tree (`git diff` is the artifact).
- Reviewer and QA read the design doc + `git diff` directly — don't paraphrase the diff into their prompt, tell them to read it themselves so they see the real thing.
- Feedback from reviewer/QA that routes back to developer or architect gets appended to the design doc under a `## Review feedback (iteration N)` or `## QA feedback (iteration N)` heading before re-invoking, so it's persisted and the next agent doesn't need you to relay it verbatim either.

## The loop

```
slug = kebab-case(feature description)
iteration = 0
MAX_DEV_ITERATIONS = 3   # developer <-> reviewer/qa loop
MAX_DESIGN_ITERATIONS = 2  # architect revisits

1. ARCHITECT
   Agent(subagent_type: "architect", prompt: "<feature description>. Write the design to docs/designs/<slug>.md.")
   -> design doc path

2. DEVELOP
   iteration += 1
   Agent(subagent_type: "developer", prompt: "Implement docs/designs/<slug>.md." [+ prior feedback context if looping])
   -> developer report (files changed, tests run)

3. REVIEW
   Agent(subagent_type: "reviewer", prompt: "Review the current git diff against docs/designs/<slug>.md.")
   -> VERDICT: APPROVED | CHANGES_REQUESTED (+ findings)

   if CHANGES_REQUESTED:
     if iteration < MAX_DEV_ITERATIONS:
       append findings to design doc as "## Review feedback (iteration <iteration>)"
       goto 2 (DEVELOP)
     else:
       STOP — escalate to human: "reviewer keeps rejecting after N tries, findings: ..."

4. QA
   Agent(subagent_type: "qa", prompt: "Test the current changes against docs/designs/<slug>.md.")
   -> VERDICT: PASS | FAIL (+ classification if FAIL)

   if FAIL and classification == IMPLEMENTATION BUG:
     if iteration < MAX_DEV_ITERATIONS:
       append QA feedback to design doc as "## QA feedback (iteration <iteration>)"
       goto 2 (DEVELOP)
     else:
       STOP — escalate to human

   if FAIL and classification == DESIGN GAP:
     design_iteration += 1
     if design_iteration <= MAX_DESIGN_ITERATIONS:
       append QA feedback to design doc as "## Design gap found in QA (revision <design_iteration>)"
       goto 1 (ARCHITECT) — this resets iteration to 0 for the new design
     else:
       STOP — escalate to human

   if PASS:
     DONE — summarize to human: design doc, files changed, test results, any MINOR findings from reviewer worth knowing about even though non-blocking.
```

## Orchestrator rules

- **Never skip a phase or short-circuit the loop yourself.** If you think the reviewer is being too strict, that's not your call — either let the loop run (developer gets another shot) or, if it hits the iteration cap, surface it to the human. Don't silently approve something reviewer/QA rejected.
- **Always pass file paths, not summaries, between agents.** Each agent should read the design doc and diff itself.
- **Report progress to the human at each phase transition** in one line ("Architect done: docs/designs/webhook-retry.md — handing to developer", "Reviewer: CHANGES_REQUESTED (2 findings) — looping back to developer, iteration 2/3"). Don't go silent for the whole loop.
- **On any STOP/escalation**, give the human the concrete artifact (design doc path, the actual findings, the actual test failures) — not a vague "it didn't work."
- **Don't invoke this loop for trivial changes** (typo fixes, a one-line config change) — use it for actual features/bug fixes where the design → build → review → test separation earns its overhead. Use judgment; ask the human if unsure whether a task warrants the full loop.
- If a subagent's final message doesn't end with the expected `VERDICT:` block, don't guess — re-invoke it (same subagent_type, new Agent call) asking it explicitly to end with the required verdict format.

## Starting a run

When the human invokes `/dev-cycle <description>`, or otherwise asks to build something through the team:
1. If the description is vague (no clear scope/service boundary), ask ONE clarifying question before starting architect — the whole loop is expensive to run on a misunderstood request.
2. Otherwise, start at phase 1 (ARCHITECT) immediately and drive the loop to completion or escalation, keeping the human updated as above.
