# Company Margin Rules

## Status

Implemented.

## Purpose

Define our margin on every dispatch, so the transfer price to a branch is landed cost plus our margin plus the distribution expenses.

## Actors

- Owner
- Accountant

## Rules

- A rule sets a markup percentage for a chicken type, optionally scoped to a branch.
- Rules are effective dated. The rule that applies is the newest one effective on or before the business date, preferring a branch-specific rule over a global one.
- Our margin per kg = `landed cost x markup percent`.
- If no rule is configured, the margin is zero.
- Transfer price per kg = `landed cost + our margin per kg + allocated distribution expense per kg`.
- Distribution expenses marked `MANUAL` are not passed through to the branch and stay on our side.

## Data

- `company_margin_rules`

## API

Implemented:

- `POST /api/v1/company-margin-rules`
- `GET /api/v1/company-margin-rules`
- `GET /api/v1/company-margin-rules/{id}`

See [Company Margin Rules API](../api/margin-rules.md).

## Acceptance Criteria

- The rule effective on the distribution date is the one applied.
- A branch-specific rule wins over a global rule for the same chicken type.
