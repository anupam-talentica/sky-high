---
description: High-level Copilot usage guidelines and constraints for the Sky-High project
---

## Purpose

These instructions guide how GitHub Copilot and similar AI assistants should be used in the **Sky-High** repo (backend, frontend, deployment, and docs). They complement the existing `.cursor/rules` files and **do not override** security, architecture, or coding standards defined elsewhere.

- **Scope**: Applies to all AI-assisted code, tests, docs, and refactors in this repository.
- **Priority**: If there is any conflict, **project rules and PRD.md requirements win over Copilot suggestions**.

---

## General Usage Principles

- **Use Copilot as a pair programmer, not an all-in-one generator.**
  - Accept suggestions only after you’ve **read and understood** them.
  - Prefer **small, incremental completions** over large, opaque dumps of code.
- **Keep changes aligned with existing conventions.**
  - Follow package / folder structure from `PROJECT_STRUCTURE.md` and `.cursor/rules/`.
  - For backend Java, follow `backend-java-standards.mdc` and `database-patterns.mdc`.
  - For docs, follow `documentation-standards.mdc`.
- **Always verify behavior.**
  - Add / update tests when Copilot changes business logic.
  - Run relevant test suites or linters before committing.

---

## Security & Secrets

- **Never accept or introduce real secrets** from Copilot suggestions.
  - No API keys, passwords, tokens, certificates, or connection strings in code, config, or docs.
  - Especially **do not** hardcode anything sensitive in `backend/src/main/resources/application.yml`.
- **Use environment variables and placeholders.**
  - Use patterns like `${SOME_ENV_VAR:}` as described in `backend-config-secrets.mdc`.
  - If Copilot proposes real-looking keys or credentials, **reject and replace** with safe placeholders.
- **Do not log sensitive data.**
  - Reject suggestions that log tokens, passwords, PII, or payment details.
  - Logs must follow the logging standards in `backend-java-standards.mdc`.

---

## Backend (Java / Spring Boot) Guidelines

- **Package and naming rules**
  - All packages must start with `com.skyhigh.*`.
  - Controllers, services, repositories, entities, DTOs, exceptions, and configs must follow the patterns from `backend-java-standards.mdc`.
- **Architecture and patterns**
  - Use **constructor injection**, not field injection.
  - Keep controllers thin; move business logic into `service` layer.
  - For persistence, prefer Spring Data JPA repositories following `database-patterns.mdc`.
- **Concurrency & seat/check-in domain**
  - For seat and check-in flows, enforce state machines, optimistic locking, and DB patterns as defined in `PRD.md` and `database-patterns.mdc`.
  - Reject suggestions that bypass versioning, state validation, or transactional boundaries.
- **Testing**
  - New features or bug fixes should include/adjust tests:
    - `*Test.java` for unit tests.
    - `*IntegrationTest.java` for integration tests.
  - Aim to maintain or improve **80%+ coverage**.

---

## Frontend (React / TypeScript) Guidelines

- **Structure and naming**
  - Respect existing `frontend/src` structure (`components`, `pages`, `services`, `hooks`, `contexts`, `types`, `utils`).
  - Use clear component and hook names that match existing patterns.
- **API interactions**
  - Centralize HTTP calls in `services` (or existing API client files).
  - Ensure frontend contracts stay consistent with backend DTOs and API documentation.
- **UX & accessibility**
  - Prefer accessible patterns (labels, aria attributes, keyboard navigation).
  - Avoid bloated, overly complex components; factor shared logic into hooks/utilities.

---

## Documentation & Diagrams

- **Follow required docs structure from `documentation-standards.mdc`.**
  - Keep `PRD.md`, `README.md`, `PROJECT_STRUCTURE.md`, `WORKFLOW_DESIGN.md`, `ARCHITECTURE.md`, and API specs consistent with the actual implementation.
  - When Copilot helps write docs, ensure they:
    - Accurately reflect current behavior.
    - Use concise, clear language.
    - Reference existing standards instead of redefining them.
- **Diagrams / pseudo-code**
  - Copilot-generated diagrams (e.g., Mermaid) must be checked for correctness and kept high-level.

---

## External Integrations & Mocking

- **Follow existing patterns for external APIs (e.g., weather, aviation).**
  - Reuse existing clients, DTOs, and error-handling patterns instead of creating new, parallel integrations.
  - When adding new external calls, ensure timeouts, retries, and failure handling match current design.
- **Use mocks for tests and local development.**
  - Prefer MockServer / recorded expectations (see `mockserverSetup.md`, `*-recorded-expectations.json`) when testing integrations.
  - Do not let Copilot introduce real network calls into unit tests.

---

## Performance, Reliability, and Observability

- **Performance**
  - Avoid unnecessary N+1 queries; use repository/query patterns from `database-patterns.mdc`.
  - Use pagination for list endpoints and queries that can grow large.
- **Reliability**
  - For critical workflows (seat reservation, check-in, waitlist), ensure:
    - Idempotent operations where appropriate.
    - Proper transaction boundaries at the service layer.
- **Logging & metrics**
  - Logging must be meaningful and follow SLF4J patterns.
  - Avoid “log spam” (too many DEBUG logs or logging full payloads).

---

## When to Reject Copilot Suggestions

Reject or heavily edit suggestions that:

- Conflict with **PRD.md** or any `.cursor/rules/*.mdc` file.
- Introduce new frameworks, libraries, or architectural patterns without clear need.
- Break existing public APIs or contracts unless you are explicitly doing a versioned change.
- Remove or weaken validation, security checks, or state-machine enforcement.
- Generate large blocks of boilerplate that you don’t understand or cannot maintain.

---

## Section 14 — Guardrails: Two-Stage CI/CD Pipeline

The Sky-High repo enforces a two-stage automated guardrails pipeline on every PR that touches `backend/src/**/*.java`.

### Stage 1 — Fast Gate (`guardrails-fast-gate.yml`)

**Goal:** block a PR within ~2 minutes on critical (ERROR-level) issues.
**Runs on:** every `pull_request` targeting `main` or `develop`.
**Tools:**

| Tool | Scope | Config |
|---|---|---|
| Checkstyle | diff-aware (changed files only) | `backend/checkstyle.xml` |
| Semgrep | diff-aware (`--diff-aware` flag) | `.semgrep/skyhigh.yml` |

**Behaviour:**
- Only **ERROR-severity** findings fail the gate; warnings are passed through to Stage 2.
- On failure, an automated PR comment explains the violations and provides a Copilot Chat prompt template.
- Results are uploaded as SARIF to GitHub Code Scanning and as artefacts for Stage 2.

**Checkstyle ERRORs include:** tab characters, wildcard imports, missing braces, empty catch blocks, `System.out` usage, `printStackTrace()`, naming violations, method length > 100 lines, cyclomatic complexity > 15.

**Semgrep ERRORs include:** hardcoded credentials, SQL injection via string concat, unsafe deserialization, weak crypto (MD5/SHA-1), unsigned JWTs, field injection (`@Autowired` on private fields), repository calls inside `@RestController`, `@Transactional` on interfaces.

### Stage 2 — Issue Creation Bot (`guardrails-issue-bot.yml`)

**Goal:** convert non-blocking WARNING findings into GitHub Issues that a developer can resolve in Copilot Chat in under a minute.
**Runs on:** `workflow_run` — triggered automatically after Stage 1 completes, so it adds zero latency to the PR feedback loop.

**Behaviour:**
- Downloads the Checkstyle XML and Semgrep SARIF artefacts from the Stage 1 run.
- For each WARNING-level finding, creates a GitHub Issue with:
  - Label: `copilot-fixable`
  - Pre-filled Copilot Chat prompt, e.g.:
    > `@workspace Fix the Checkstyle violation in PaymentService line 42: Magic number '3600000' — extract to a named constant.`
- Deduplicates: if an identical open issue already exists, it is skipped.

**Checkstyle WARNINGs include:** trailing whitespace, line length > 120, magic numbers, TODO/FIXME comments, missing Javadoc on public methods, cyclomatic complexity > 10.

**Semgrep WARNINGs include:** TODO/FIXME comments, `System.out` usage, empty catch blocks, `Thread.sleep()` magic values, unannotated nullable returns.

### How to Fix a Violation in Copilot Chat

1. Open the Issue (or the failed PR step) to see the exact violation.
2. Open the relevant file in VS Code.
3. Paste the pre-filled prompt from the Issue body into Copilot Chat (`Ctrl+I` / `Cmd+I`).
4. Accept the suggestion, push — the gate re-runs automatically.

### Adding / Adjusting Rules

- **Checkstyle:** edit `backend/checkstyle.xml`. Set `severity="error"` to block PRs, `severity="warning"` for Issue-bot-only.
- **Semgrep:** edit `.semgrep/skyhigh.yml`. Set `severity: ERROR` to block; `severity: WARNING` for Issue-bot. Add a `copilot_prompt` key under `metadata` to customise the Copilot Chat prompt in the filed Issue.
- Both configs are diff-aware — rules apply only to touched lines in a PR, keeping gate runtime under 2 minutes.

---

## Developer Responsibilities

- **You own the code, not Copilot.**
  - Review every suggestion as if it were a human PR.
  - Ensure code is readable, tested, and aligned with project standards before committing.
- **Keep rules up to date.**
  - When major conventions change (e.g., new auth flow, new deployment approach), update:
    - Relevant `.cursor/rules/*.mdc` files.
    - This `.github/copilot-instructions.md`.

