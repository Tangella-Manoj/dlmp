# DLMP Frontend

React + TypeScript + Vite + Tailwind CSS v4 SPA for the Distributed Loan Management Platform.
Talks to the live [API gateway](https://dlmp-gateway.onrender.com) by default — no local backend
required to develop against it.

## Stack

- **React 19 + TypeScript** — strict, `erasableSyntaxOnly` config (no enums/legacy TS-only syntax)
- **Vite + Tailwind CSS v4** — CSS-first theme (`src/index.css`), no `tailwind.config.js`
- **TanStack Query** — server-state caching, refetching, mutations
- **React Hook Form + Zod** — forms validated against the exact same rules as the backend DTOs
- **React Router v7** — role-gated routes (`ProtectedRoute`, `RoleRoute`)
- **Recharts** — portfolio dashboard charts (lazy-loaded — see below)
- **Axios** — single client with automatic silent access-token refresh on 401

## Getting started

```bash
npm install
npm run dev          # http://localhost:5173, talks to the live Render gateway
```

To point at a local backend instead, create `.env.local`:
```
VITE_API_BASE_URL=http://localhost:8090
```
(and make sure the gateway's `CORS_ALLOWED_ORIGINS` includes `http://localhost:5173`.)

## Structure

```
src/
  api/          typed API modules, one per backend service (auth, loans, payments, ...)
  components/
    ui/         design-system primitives (Button, Card, Input, Dialog, Badge, ...)
    layout/      Navbar, AppLayout, AuthLayout, NotificationBell
    loans/       loan-domain components (LoanCard, EmiScheduleTable, PayEmiDialog, ...)
    charts/      Recharts wrappers for the reports dashboard
  context/       AuthContext (session state, login/register/logout)
  routes/        ProtectedRoute / RoleRoute guards
  pages/         one file per route
  lib/           token storage, formatting, zod schemas, EMI calculator, loan status metadata
  types/         TypeScript types mirroring every backend DTO exactly
```

## Auth model

The access token is stored client-side (`localStorage`, via `lib/tokenStore.ts`) and attached to
every request. On a 401, the client automatically calls `/api/v1/auth/refresh` once (de-duplicated
across concurrent requests) and retries the original call; if refresh also fails, the session is
cleared and the user is redirected to `/login`.

## Roles

- **ROLE_CUSTOMER** — dashboard, apply for a loan, pay EMIs, notifications, profile
- **ROLE_LOAN_OFFICER` / `ROLE_ADMIN** — all-loans console (approve/reject/disburse), portfolio
  reports dashboard

Route access is enforced client-side for UX (instant redirect, no flash of forbidden content) —
the backend enforces the real authorization on every request regardless.

## Build & deploy

```bash
npm run build   # tsc -b && vite build → dist/
```

Deployed to Vercel. `vercel.json` rewrites all paths to `index.html` (required for client-side
routing — without it, refreshing `/loans/abc123` 404s) and long-caches `/assets/*`.
