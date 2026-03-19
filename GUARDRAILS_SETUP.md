# Guardrails Pipeline — Setup Guide

This guide explains how to replicate the two-stage static analysis guardrails pipeline
(Checkstyle + Semgrep → GitHub Issues) into any Spring Boot / Maven repository.

---

## Tools Overview

### Checkstyle

Checkstyle is a static analysis tool that enforces **Java coding style and formatting rules**.
It parses source files and flags violations of conventions you define — things like naming,
indentation, import ordering, method length, magic numbers, and missing Javadoc.

It does **not** understand program logic or security — it only looks at the surface structure
of the code. Rules are defined in an XML config file (`checkstyle.xml`) and run via a Maven
plugin during the build. It is fast (milliseconds per file) and has zero false positives for
purely structural rules.

**Examples of what it catches:**
- Wildcard imports (`import java.util.*`)
- Lines longer than 120 characters
- Missing braces on `if` statements
- `System.out.println` usage
- TODO/FIXME comments left in code

---

### Semgrep

Semgrep is a static analysis tool that finds **bugs, security vulnerabilities, and
architectural pattern violations** by matching code against rules written in a
pattern language that mirrors the code itself.

Unlike Checkstyle, Semgrep understands code structure — it can match across multiple
lines, track variable names, and express rules like "find any JWT builder that calls
`compact()` without first calling `signWith()`". Rules are written in YAML and can
target Java, Python, JavaScript, Go, and many other languages.

It is used here to enforce security standards (no SQL injection, no hardcoded
credentials) and Spring Boot architecture rules (no field injection, no business
logic in controllers) that Checkstyle cannot express.

**Examples of what it catches:**
- Hardcoded passwords or tokens in variables
- SQL built by string concatenation (injection risk)
- `@Autowired` on private fields (field injection)
- Weak hashing algorithms (MD5, SHA-1)
- Unsigned JWT tokens

---

### How they complement each other

| | Checkstyle | Semgrep |
|---|---|---|
| **Checks** | Style & formatting | Security & architecture |
| **Config format** | XML | YAML |
| **Rule expressiveness** | Structural patterns only | Full AST pattern matching |
| **Speed** | Very fast | Fast |
| **False positives** | Very low | Low–medium |
| **In this pipeline** | Warns on style; blocks on explicit `severity="error"` rules | Blocks PR on any `severity: ERROR` rule match |

---

## What the Pipeline Does

| Stage | Workflow | When | What |
|-------|----------|------|------|
| 1 | `guardrails-fast-gate.yml` | Every PR to `main` / `develop` | Runs Checkstyle + Semgrep. Blocks merge on ERROR findings. Posts PR comment. Creates `copilot-fixable` Issues. |
| 2 | `guardrails-issue-bot.yml` | After fast-gate completes (once on `main`) | Supplementary issue bot triggered via `workflow_run`. Active once the workflow is merged to the default branch. |

---

## Files to Copy

Copy these files from this repository into your target repository, preserving the directory structure:

```
.github/
  workflows/
    guardrails-fast-gate.yml       ← Stage 1: blocks PR, creates issues
    guardrails-issue-bot.yml       ← Stage 2: supplementary (needs main branch)
.semgrep/
  skyhigh.yml                      ← Semgrep rules (customise for your project)
backend/
  checkstyle.xml                   ← Checkstyle rules (customise for your project)
```

> **Note:** `guardrails-issue-bot.yml` only activates after it is merged to the default
> branch (`main`). Until then, the fast-gate handles issue creation directly.

---

## Step-by-Step Setup

### Step 1 — Copy the workflow files

```
your-repo/
  .github/workflows/guardrails-fast-gate.yml
  .github/workflows/guardrails-issue-bot.yml
  .semgrep/<your-project>.yml
  backend/checkstyle.xml            ← or wherever your Maven module lives
```

---

### Step 2 — Update `guardrails-fast-gate.yml`

Open `.github/workflows/guardrails-fast-gate.yml` and change these values:

| Line | What to change | Example |
|------|----------------|---------|
| `branches:` | Change `main` / `develop` to your protected branch names | `main`, `release` |
| `--config .semgrep/skyhigh.yml` | Rename to match your Semgrep rules file | `--config .semgrep/myproject.yml` |
| `backend/src/main/java` (×2) | Change to your Java source root | `src/main/java` or `service/src/main/java` |
| `working-directory: ./backend` | Change to your Maven module directory | `./` or `./api` |
| `backend/target/checkstyle-result.xml` (×2) | Update the path to match your module | `target/checkstyle-result.xml` |

**If your Java source is not under a `backend/` subdirectory** (i.e. the pom.xml is at the repo root):
- Remove `working-directory: ./backend` from the Checkstyle step
- Change all `backend/target/...` paths to `target/...`
- Change `backend/src/main/java` to `src/main/java`

---

### Step 3 — Update `guardrails-issue-bot.yml`

No path changes needed — it reads artefacts uploaded by the fast-gate.

Only change needed: if you renamed the workflow in `guardrails-fast-gate.yml` (the `name:` field at the top), update the matching reference here:

```yaml
# guardrails-issue-bot.yml line 21
workflows: ["Guardrails – Fast Gate"]   # must match name: in fast-gate file exactly
```

---

### Step 4 — Customise the Semgrep rules

Copy `.semgrep/skyhigh.yml` to `.semgrep/<your-project>.yml` and edit:

- **Keep** the rules that apply to your stack (all rules target Java / Spring Boot)
- **Remove** rules that don't apply (e.g. `jwt-none-algorithm` if you don't use JJWT)
- **Add** new rules following the same structure:

```yaml
- id: your-rule-id
  pattern: |
    <semgrep pattern>
  message: >
    Description of the problem and how to fix it.
  languages: [java]
  severity: ERROR        # ERROR = blocks PR | WARNING = creates issue only
  metadata:
    category: security   # or best-practice / architecture / maintainability
    copilot_prompt: "@workspace Fix the <issue> in $CLASS"
```

**Severity guide:**
- `ERROR` → fast-gate fails the PR and creates a GitHub Issue
- `WARNING` → PR is not blocked; Issue is still created

---

### Step 5 — Customise `checkstyle.xml`

Copy `backend/checkstyle.xml` to your Maven module directory and edit to taste.

Key property to note — all rules are `severity="warning"` globally:

```xml
<module name="Checker">
    <property name="severity" value="warning"/>
```

This means Checkstyle findings **never block the build on their own**. The fast-gate
workflow passes `-Dcheckstyle.violationSeverity=error` at runtime, so only rules you
explicitly override to `severity="error"` inside the XML will block PRs.

To make a specific Checkstyle rule **block PRs**, add `severity="error"` to that module:

```xml
<module name="AvoidStarImport">
    <property name="severity" value="error"/>
</module>
```

---

### Step 6 — Add the Checkstyle plugin to `pom.xml`

Add this plugin inside the `<build><plugins>` section of your `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.3.1</version>
    <configuration>
        <configLocation>checkstyle.xml</configLocation>
        <consoleOutput>false</consoleOutput>
        <failOnViolation>false</failOnViolation>
        <failsOnError>false</failsOnError>
        <logViolationsToConsole>false</logViolationsToConsole>
        <includeTestSourceDirectory>false</includeTestSourceDirectory>
        <outputFile>${project.build.directory}/checkstyle-result.xml</outputFile>
    </configuration>
    <executions>
        <execution>
            <id>checkstyle-check</id>
            <phase>validate</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

> `failOnViolation` and `failsOnError` are both `false` — the workflow controls
> whether violations block the PR, not Maven directly.

---

### Step 7 — Configure GitHub repository settings

#### 7a. Enable GitHub Code Scanning (for SARIF upload)
Go to **Settings → Code security → Code scanning** and ensure it is enabled.
This is required for the `github/codeql-action/upload-sarif` step to work.

#### 7b. Make the fast-gate a required status check
Go to **Settings → Branches → Branch protection rules** for `main` (and `develop`):
1. Enable **"Require status checks to pass before merging"**
2. Search for and add: `Fast Gate (Checkstyle + Semgrep)`

Without this, GitHub will show the gate result but still allow merging.

#### 7c. Optional — add `SEMGREP_APP_TOKEN` secret
If you have a Semgrep Cloud account, add your token at
**Settings → Secrets and variables → Actions → New repository secret**:
- Name: `SEMGREP_APP_TOKEN`
- Value: your Semgrep Cloud token

This is optional. Without it the free OSS scanner runs fine — findings just won't
appear in the Semgrep Cloud dashboard.

---

## Verification

Once set up, open a pull request with a deliberate violation to confirm the pipeline works:

```java
// Test field injection rule (should block PR)
@Autowired
private SomeService someService;
```

Expected behaviour:
1. Fast-gate check appears within ~2 minutes
2. Check fails with "Fast Gate FAILED"
3. A PR comment appears listing the violations
4. A GitHub Issue labelled `copilot-fixable` is created with a Copilot Chat prompt

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Gate never runs | Branch name not in `branches:` list | Add your branch name to the workflow trigger |
| Gate passes despite violations | Branch protection rule not set | Add the status check as required (Step 7b) |
| Issues not created | Workflows not on `main` yet | Merge the PR; issue-bot only runs from default branch. Fast-gate creates issues directly so this should work regardless. |
| SARIF upload fails | Code scanning not enabled | Enable it in repository security settings (Step 7a) |
| Checkstyle step fails with "file not found" | Wrong `working-directory` or `outputFile` path | Adjust paths in the workflow to match your module layout (Step 2) |
| `semgrep: command not found` | Python setup step missing | Ensure the `Set up Python` and `Install Semgrep` steps are present before the Semgrep scan step |

---

## File Reference Summary

| File | Copy as-is? | Must customise |
|------|-------------|----------------|
| `.github/workflows/guardrails-fast-gate.yml` | Yes | Branch names, source paths, Semgrep config path |
| `.github/workflows/guardrails-issue-bot.yml` | Yes | Only if you rename the fast-gate workflow |
| `.semgrep/skyhigh.yml` | Rename it | Remove/add rules to fit your project |
| `backend/checkstyle.xml` | Yes | Move to your Maven module root; tune rules |
| `backend/pom.xml` (plugin section only) | Add plugin | Update `configLocation` if checkstyle.xml moves |
