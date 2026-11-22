# Pulse engineering workflow

## Roles
- **ChatGPT:** teach concepts, help define requirements/acceptance criteria, discuss architecture, review diffs/results shared by the owner, explain code and prepare interview scenarios.
- **Codex:** inspect actual repository, propose plans, implement approved scoped tasks, write/update tests/docs, run commands and report actual output.
- **Project owner:** make product/engineering decisions, approve architectural/security/schema changes, verify behavior and understand important design decisions.

## Feature lifecycle
1. Learn the problem and concepts.
2. Define requirements and acceptance criteria.
3. Ask Codex to inspect relevant code and propose a plan.
4. Review and approve the plan.
5. Codex implements within scope.
6. Inspect diff and run relevant tests.
7. Review security, concurrency, data integrity and failure behavior.
8. Walk through important code together.
9. Update docs and roadmap.
10. Owner commits through normal Git workflow.

## Autonomy
Read-only inspection is generally allowed. Scoped implementation/tests are allowed after task approval. Architecture, dependencies, schema, API contracts and security changes require approval. Destructive data operations, deployments, pushes and secret changes require explicit approval.

## Git discipline
Start clean where practical; use a feature branch for substantial work; inspect `git status` and `git diff`; do not commit/push without approval; keep secrets and local environment files out of Git.

## Review checklist
Acceptance criteria? Existing architecture? Edge/failure cases? Concurrency? Authorization? Safe migrations? Tests actually run? Safe logs/errors? Docs updated? Can owner explain design?

## Handoff format
Task, goal, acceptance criteria, relevant files/constraints, approved plan, files changed, commands/tests and actual results, unresolved risks, learning points.

## Definition of done
Code existing is not enough. Behavior must be verified, relevant tests pass, risks reviewed, docs updated, and owner understands major decisions.
