# Security Policy

## Supported versions

This is a single-branch portfolio project (no maintained release lines) - security fixes land on
`master` only, then roll out via the existing CI/CD pipeline to the live deployment. There is no
LTS/backport policy.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for a suspected vulnerability. Instead, use
[GitHub's private vulnerability reporting](../../security/advisories/new) for this repository
(Security tab → "Report a vulnerability"), or contact the maintainer directly via the email on
the [GitHub profile](https://github.com/ronybrand).

Include, where applicable:

- A description of the vulnerability and its potential impact.
- Steps to reproduce (a minimal request/payload is ideal).
- The affected endpoint(s) or component(s).

This project is maintained on a best-effort basis (no SLA), but reports are taken seriously and
triaged as soon as possible. Since this API is live and publicly reachable (see README), reports
affecting the running deployment get priority over reports affecting only local/dev usage.

## Scope

In scope: the application code in this repository (`src/main`), the Terraform-managed
infrastructure under [`terraform/`](terraform/), the deployment scripts under
[`deploy/`](deploy/), and the CI/CD workflows that build and ship it.

Out of scope: the separate [`angular_estado`](https://github.com/ronybrand/angular_estado),
[`react_state`](https://github.com/ronybrand/react_state) and
[`estado-ai-agent`](https://github.com/ronybrand/estado-ai-agent) repositories, which have their
own security policies. Dependency vulnerabilities are tracked automatically via Dependabot and
CodeQL (see badges in [README.md](README.md)) rather than manual reports.

## What this project already does

- Automated dependency updates via Dependabot, auto-merged after CI passes.
- Static analysis on every push/PR via [CodeQL](.github/workflows/codeql.yml).
- No production secrets committed to the repository - configuration is via environment variables,
  with real secrets populated through the deployment pipeline, not hardcoded.
- JWT-based authentication (see `docs/adr/0017-*.md`), with request-id correlation and CORS
  restricted to `/auth/**`.
- Infrastructure drift is checked automatically (`.github/workflows/terraform-drift-check.yml`)
  so the live account can't silently diverge from what's reviewed in `terraform/`.
