# Role-Adaptive Account Navigation Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make Personal Center the sole left-side account entry and remove the duplicate top-right button, while keeping the dashboard available only to administrators.

**Architecture:** `AdminLayout` renders direct navigation entries according to the authenticated role: administrators retain Dashboard and all users receive Personal Center. The menu store and router use the same access rule so ordinary users cannot open the administrator dashboard directly.

**Tech Stack:** Vue 3, TypeScript, Pinia, Vue Router, Element Plus, Vite.

---

### Task 1: Align role-aware access and route fallback

**Files:**
- Modify: `frontend/src/stores/menu.ts`
- Modify: `frontend/src/router/index.ts`

**Step 1: Define the permitted paths**

Keep `/profile` universally available. Permit `/dashboard` through the existing administrator override only.

**Step 2: Make the route fallback role-aware**

Return `authStore.defaultEntryPath` when a user opens a route outside their permitted paths, so a normal user returns to `/profile` rather than an inaccessible dashboard.

**Step 3: Verify TypeScript**

Run: `npm run check` from `frontend`.

Expected: exit code 0.

### Task 2: Make the left navigation the single Personal Center entry

**Files:**
- Modify: `frontend/src/layout/AdminLayout.vue`

**Step 1: Render direct entries by role**

Show Dashboard only to administrators. Show Personal Center to every signed-in user and make its active state resolve correctly.

**Step 2: Remove the duplicate header control**

Remove the top-right Personal Center button; retain the displayed user name and the existing logout action in their current area.

**Step 3: Verify production build**

Run: `npm run build` from `frontend`.

Expected: type checking and Vite build complete successfully.
