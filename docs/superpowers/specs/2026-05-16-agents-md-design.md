# AGENTS.md Design

## Goal

Create a repository-level `AGENTS.md` that gives AI coding agents enough context to work safely in `solen-aio` without needing to rediscover the project structure on every task.

## Audience

The file is for AI agents first, not for human onboarding. It should optimize for reducing incorrect edits, unsafe commands, and cross-module confusion.

## Repository Facts To Capture

### Overall shape

- The repository is split into `backend/` and `frontend/`.
- There is no current top-level project runner.
- The current default branch state is aligned to `origin/master`.

### Backend

- `backend/` is a Maven multi-module project.
- Root module file: `backend/pom.xml`.
- Modules currently declared:
  - `backend/solen-server`
  - `backend/solen-conch-server`
  - `backend/solen-server-demo`
  - `backend/solen-app`
- `solen-app` appears to be the business/API application layer.
- `solen-server` appears to contain the device protocol, Netty server, packet encoding/decoding, and event abstractions.
- Configuration currently lives at `backend/solen-app/src/main/resources/application.yml`.
- The config file contains example tenant credentials and a JWT secret, so the final `AGENTS.md` must treat config editing as sensitive.

### Frontend

- `frontend/` is an older React application scaffolded from ICE Design Pro.
- Main package file: `frontend/package.json`.
- Key stack signals from the existing package file:
  - React 16
  - Redux and redux-thunk
  - react-router-dom v4
  - ice-scripts build tooling
- The final `AGENTS.md` should explicitly warn agents not to assume modern React conventions or introduce unnecessary framework migrations.

## Required Behavior In AGENTS.md

The final document should be a strong-constraint execution guide with these sections.

### 1. Project Overview

- One short description of what the repository is.
- One short description of how the backend and frontend relate.

### 2. Repository Map

- Describe the purpose of `backend/`.
- Describe the purpose of `frontend/`.
- Describe what each major backend module is for, at least at a practical level.

### 3. Working Rules By Area

#### Backend rules

- Prefer small, localized edits.
- Put API/business changes in `solen-app` unless protocol or transport behavior actually needs to move into `solen-server`.
- Treat `application.yml` and related credentials as sensitive.
- Avoid broad refactors across Maven modules unless the task requires it.

#### Frontend rules

- Follow existing React 16, Redux, and ICE Design patterns.
- Avoid introducing modern React-only APIs, TypeScript migrations, or state-management rewrites unless explicitly requested.
- Preserve existing route, locale, and store conventions.

### 4. Verification Commands

Include practical commands agents can run before claiming completion:

- Backend:
  - `./mvnw test` from `backend/`
  - optionally narrower module-scoped Maven commands when touching one module
- Frontend:
  - `npm install` only if dependencies are needed and lockfile changes are intentional
  - `npm run build`
  - `npm run lint`

The final wording should say to choose the narrowest verification that proves the change, then escalate to broader validation if needed.

### 5. Sensitive Areas And Guardrails

- Do not overwrite secrets or example credentials without explicit instruction.
- Do not rewrite the whole frontend stack.
- Do not mix protocol-layer changes with unrelated UI cleanup.
- Do not update generated or lock files unless required by the task.

### 6. Git And Change Scope

- Never discard user changes.
- Keep commits scoped to the requested task.
- Do not commit unless explicitly asked.

## Tone And Style

- Direct and operational.
- Repository-specific, not generic AI boilerplate.
- Use concise bullets.
- Avoid speculative claims about runtime behavior that have not been verified from the repository contents.

## Out Of Scope

- Do not attempt to document every controller, page, or domain object.
- Do not invent missing architecture details.
- Do not define team process that is not inferable from the repository or user instructions.

## Self-Review

- The scope is focused on a single repository-level guidance file, so it is appropriate for one implementation step.
- No placeholders remain.
- The design stays within repository-observable facts plus explicit user preferences.
