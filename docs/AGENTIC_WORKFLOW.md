# Agentic development workflow — SmartLogix

SmartLogix is built and maintained using a **recursive, role-separated AI development team** implemented as [Claude Code](https://claude.com/claude-code) subagents. This document explains the methodology: why it's structured this way, what each role does, and how to run it.

## Why role separation instead of one agent doing everything

A single agent that designs, implements, reviews, and tests its own work has no adversarial check on its own output — it tends to rubber-stamp its own reasoning, the same failure mode that makes "self-code-review" weak for human engineers. This project mirrors how a real small engineering team works instead:

- **Architect** designs before code is written, and is not allowed to implement.
- **Developer** implements exactly what was designed, and cannot approve their own work.
- **Reviewer** checks the diff against the design and against a security/compliance checklist, is **read-only** (cannot edit code — separation of duties), and can send work back.
- **QA** tests against acceptance criteria end-to-end, independent of whether the reviewer already approved the code quality, and can route failures back to either developer (bug) or architect (design gap).

Each role runs as a fresh agent invocation with no shared memory of the others' reasoning — only the artifacts they produce (a design doc, a diff, a test report) are passed forward, via files in the repo. That forces every handoff to be explicit and inspectable, which is also what makes the process auditable for a hiring reviewer looking at this repo.

## The four roles

| Role | Defined in | Can edit code? | Produces |
|---|---|---|---|
| Architect | [`.claude/agents/architect.md`](../.claude/agents/architect.md) | No | `docs/designs/<slug>.md` — API contract, data model, design pattern fit, OWASP + [Chilean compliance](COMPLIANCE_CL.md) requirements, acceptance criteria |
| Developer | [`.claude/agents/developer.md`](../.claude/agents/developer.md) | Yes | Implementation + tests (AAA structure, JUnit 5/Mockito) |
| Reviewer | [`.claude/agents/reviewer.md`](../.claude/agents/reviewer.md) | No (read-only) | `VERDICT: APPROVED / CHANGES_REQUESTED` + severity-ranked findings against OWASP Top 10 |
| QA | [`.claude/agents/qa.md`](../.claude/agents/qa.md) | Tests only | `VERDICT: PASS / FAIL` against the architect's acceptance criteria, with bug routing (developer vs. architect) |

## The recursive loop

Orchestrated by [`.claude/skills/dev-cycle/SKILL.md`](../.claude/skills/dev-cycle/SKILL.md):

```
architect → developer → reviewer ─┬─ CHANGES_REQUESTED → back to developer (max 3x)
                                   └─ APPROVED → qa ─┬─ FAIL (bug)         → back to developer (max 3x)
                                                      ├─ FAIL (design gap) → back to architect (max 2x)
                                                      └─ PASS → done
```

Iteration caps prevent infinite loops; hitting a cap escalates to a human with the concrete artifact (design doc, findings, or failing test) rather than failing silently.

## Running it

```
/dev-cycle <describe the feature or bug fix>
```

Example: `/dev-cycle add a GET /api/usuarios/me/datos endpoint that aggregates a user's own data across auth-service, ms-pedidos and ms-envios for ARCO+ access-right compliance`

The orchestrator will design, implement, review, and test the change autonomously, reporting progress at each phase transition, and will stop to ask if the request is ambiguous or if an iteration cap is hit.

## Security and compliance baked into the loop, not bolted on

Every phase explicitly touches security and Chilean regulatory context rather than treating it as a separate audit pass:

- Architect maps every design to OWASP Top 10 (2021) categories and to [`docs/COMPLIANCE_CL.md`](COMPLIANCE_CL.md) (Ley 21.663 — Marco de Ciberseguridad; Ley 21.719 — Protección de Datos Personales, in force 2026-12-01).
- Reviewer's checklist is the same OWASP categories, applied to the actual diff.
- QA explicitly tests negative/abuse cases (invalid input, missing/forged JWT, wrong-owner access) as first-class acceptance criteria, not an afterthought.

This is also enforced independently in CI — see [`.github/workflows/`](../.github/workflows/) for CodeQL, dependency/secret/image scanning, which catch what the agentic review might miss.

## Context for reviewers of this repo

This project is built by a software engineering student as a portfolio piece to demonstrate fullstack + DevSecOps + AI-assisted engineering practice for internship/job applications. The agentic workflow above is itself part of that demonstration: it shows deliberate process design (role separation, auditable handoffs, security-by-design, regulatory awareness) rather than just "AI wrote the code."
