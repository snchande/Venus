# Arima Notebooks — Maintainer Guide

This document is for current and prospective maintainers of Arima Notebooks. It covers the maintainer hierarchy, responsibilities, day-to-day operations, how to review and merge contributions, how to manage the community, and how new partners can join as co-maintainers.

---

## Maintainer Hierarchy

Arima Notebooks has a two-tier maintainer structure:

### Founding Maintainer

The **Founding Maintainer** created the project and holds final authority over:

- What gets merged into `main`
- The overall project direction and roadmap
- Acceptance or rejection of co-maintainer nominations
- Release versioning and timing
- Breaking changes to the notebook format or API

The Founding Maintainer's decision is final. Co-maintainers may advocate for a position, but should defer gracefully once a decision is made.

### Co-Maintainers

**Co-Maintainers** are trusted contributors who have been invited to share the maintenance workload. They can:

- Review and approve pull requests
- Merge PRs that the Founding Maintainer has pre-approved or that are clearly non-controversial (docs, bug fixes, minor improvements)
- Triage issues and close duplicates/stale issues
- Manage Discussion threads
- Help welcome and onboard new contributors

Co-maintainers **cannot** merge into `main` unilaterally on:
- Breaking changes
- New feature PRs that haven't been discussed and approved by the Founding Maintainer
- Changes to project governance or this document
- Changes to the notebook file format (`.vnb`)
- Changes to core execution engines (JShell, Java, Node.js paths)

When in doubt, tag `@founding-maintainer` for a decision rather than merge.

---

## Becoming a Co-Maintainer

Co-maintainership is **by invitation only** from the Founding Maintainer. It is not earned automatically by contribution volume.

### What the Founding Maintainer looks for

- A track record of quality PRs that are focused, well-tested, and don't need heavy back-and-forth to fix
- Good judgment in discussions — constructive, respectful, technically sound
- Understanding of the project's philosophy (local-first, no build step for frontend, JVM-native)
- Reliability — someone who will be around to help, not disappear after one sprint

### If you're interested

Do not ask to be made a maintainer. Instead:

1. Contribute consistently over time
2. Help triage issues — reproduce bugs, ask clarifying questions, label issues
3. Review open PRs and leave thoughtful comments (even without merge rights)
4. Participate in Discussions constructively

The Founding Maintainer will reach out when the time is right.

### When a co-maintainer is invited

The Founding Maintainer will:
1. Open a private conversation to discuss expectations
2. Add the person to the GitHub repository with **Write** access (not Admin)
3. Announce the addition in a GitHub Discussion
4. Update the [Maintainers section](#current-maintainers) of this document

---

## Current Maintainers

| Role | GitHub | Responsibilities |
|------|--------|-----------------|
| **Founding Maintainer** | @snchande | Final merge authority · Roadmap · Releases |

*Co-maintainers will be listed here as they are added.*

---

## Day-to-Day Operations

### Issue Triage (do this within 48 hours of a new issue)

1. Read the issue carefully
2. Apply a label (see label guide below)
3. If it's a duplicate: comment with a link to the original, close it
4. If it's unclear: ask for clarification (OS, Java version, steps to reproduce)
5. If it's clearly a bug you can reproduce: label `bug`, acknowledge it
6. If it's a feature request: label `enhancement`, add to the appropriate milestone or backlog
7. If it's a question: redirect to Discussions, close the issue

**Label guide:**

| Label | When to use |
|-------|-------------|
| `bug` | Confirmed defect — something doesn't work as documented |
| `enhancement` | New feature or improvement request |
| `documentation` | Docs-only change needed |
| `good first issue` | Simple, well-defined, low-risk — good for new contributors |
| `help wanted` | We want this done but don't have bandwidth ourselves |
| `question` | Should be a Discussion, not an issue |
| `duplicate` | Same as another open issue |
| `wontfix` | Won't be addressed — explain why in a comment |
| `needs-info` | Waiting on more information from the reporter |
| `breaking-change` | Would require a major version bump |

### Reviewing Pull Requests

Do this within a week of a PR being opened. Longer than two weeks without a response discourages contributors.

**What to check:**

1. **Scope** — does the PR do one focused thing, or is it mixing unrelated changes?
2. **Correctness** — does it actually solve the stated problem?
3. **Tests** — has the contributor tested it? (check their PR description)
4. **Code quality** — follows project conventions? No Lombok, no frontend build tools, constructor injection?
5. **Docs** — if behavior changed, are docs updated? Is `CHANGELOG.md` updated?
6. **Merge safety** — could this break existing notebooks or the API contract?

**How to respond:**

- If it needs changes: be specific. "This doesn't follow the existing pattern" is less helpful than "Please follow the Builder pattern used in `Cell.java` — see lines 45–80."
- If it's good: approve it. Leave a comment if you're approving with suggestions ("LGTM — small nit: could rename this variable, but not a blocker").
- If it's a feature the Founding Maintainer should weigh in on: tag them before merging.

**Merge strategy:** Use **squash merge** for all PRs. This keeps `main` history clean. The squash commit message should follow Conventional Commits format.

### Handling Stale Issues and PRs

A GitHub Action can automate this, but the manual rule is:

- Issues with `needs-info` label: close after **30 days** of no response, with a polite comment that they can reopen with the requested information
- PRs with no activity after **60 days**: comment asking if the author plans to continue, close after **14 more days** of no response

---

## Release Process

Arima Notebooks uses [Semantic Versioning](https://semver.org/):

- **PATCH** (1.2.x) — bug fixes only, no new features, no API changes
- **MINOR** (1.x.0) — new features, new endpoints, new cell types, backwards-compatible
- **MAJOR** (x.0.0) — breaking changes to notebook format, API, or major architecture changes

### Cutting a Release (Founding Maintainer only)

1. **Update `CHANGELOG.md`** — move everything from `[Unreleased]` to a new versioned section with today's date
2. **Bump the version** in `pom.xml`: `<version>1.x.0</version>` (remove `-SNAPSHOT` for the release)
3. **Commit**: `chore(release): bump version to 1.x.0`
4. **Tag**: `git tag -a v1.x.0 -m "Release v1.x.0"`
5. **Push**: `git push origin main --tags`
6. **Create a GitHub Release** — copy the CHANGELOG section as the release notes, attach the JAR from `target/`
7. **Re-add `-SNAPSHOT`** to `pom.xml` for the next development cycle: `<version>1.x+1.0-SNAPSHOT</version>`
8. **Commit**: `chore: begin development of 1.x+1.0`

---

## Community Management

### Tone and Culture

The tone you set as a maintainer is the tone the community will adopt. A few principles:

- **Respond to every first-time contributor** — even if it's just "thanks for the PR, reviewing now." Silence discourages people from contributing again.
- **Say no kindly** — "That's not a direction we want to take, but here's why" is a complete answer. You don't owe a long explanation, but you do owe a reason.
- **Celebrate contributions publicly** — mention contributors by name in release notes and in Discussions when something ships.
- **Don't let frustration show in public threads** — if a contributor is being difficult, take it to DMs or close the issue/PR with a clear reason.

### When a Contribution Doesn't Fit

Use this pattern:

> "Thanks for the PR! After reviewing it, this isn't something we're going to merge — [brief reason, e.g., it moves away from the no-build-step frontend philosophy / we'd prefer to solve this differently / it's out of scope for now]. We appreciate the effort and hope you'll continue contributing."

Then close the PR. You don't need to leave it open indefinitely waiting for a rewrite unless you genuinely want them to rewrite it.

### Handling Difficult Interactions

If someone is rude, demanding, or violating the Code of Conduct:

1. **First offense** — respond calmly and point to the Code of Conduct
2. **Second offense** — issue a formal warning in the thread, explaining consequences
3. **Third offense or serious first offense** — block the user from the repository (GitHub → Settings → Moderation)

Document these actions. If you're a co-maintainer, loop in the Founding Maintainer before blocking anyone.

### Growing the Contributor Base

- **Label `good first issue`** generously on small, well-defined tasks — this is the #1 driver of first-time contributors
- **Write good issue descriptions** — a vague issue title gets no takers; a clear one with "here's what needs to change and why" gets PRs
- **Respond quickly to first PRs** — a contributor's second PR is far more likely if their first one was reviewed within a few days
- **Post in Java/JVM communities** when notable releases ship — Reddit (`r/java`), Dev.to, Hacker News Show HN

---

## Protecting `main`

Set up these branch protection rules on GitHub (Settings → Branches → Add Rule for `main`):

- [x] Require a pull request before merging
- [x] Require at least 1 approval (Founding Maintainer should be the required reviewer for major changes)
- [x] Dismiss stale reviews when new commits are pushed
- [x] Do not allow bypassing the above settings (even for admins — enforces discipline)

The Founding Maintainer holds **Admin** access. Co-maintainers hold **Write** access only.

---

## Maintainer Offboarding

If a co-maintainer needs to step down (voluntarily or otherwise):

1. Have a private conversation first
2. Remove their Write access in GitHub repository settings
3. Update the [Current Maintainers](#current-maintainers) table in this document
4. Post a thank-you in Discussions acknowledging their contributions
5. If it's an involuntary removal for Code of Conduct reasons, document the decision privately but do not publicize the details

---

*This document is owned by the Founding Maintainer and may be updated at any time. Co-maintainers may propose changes via PR, but changes require Founding Maintainer approval.*
