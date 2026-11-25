# House-components overhaul — implementation plan

**Goal:** every screen composes from our own components only; Material widgets appear in `SharedComponents.kt` and nowhere else.

**Architecture:** `SharedComponents.kt` becomes the single place that touches Material 3 widgets (`ListItem`, `Button`, `Switch`, `Card`, `IconButton`). Screens call `Hermes*` wrappers with tokens baked in. A CI guard fails the build when a screen imports a Material widget directly, so the rule cannot rot.

**Tech stack:** Kotlin 2.4.21, Compose Multiplatform 1.12.2 (M3 from `androidx.compose.material3`), Roborazzi goldens at `w460dp-h920dp-420dpi`, jj workspaces, `pylon-build.sh`.

---

## Current context

- **30 screens** under `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/*/*Screen.kt`.
- **10 files** already use `HermesListItem` (Settings, Bots, Channels + detail, Keys, Providers, Webhooks, Cron, Skills, MCP — the last four are open PRs).
- Existing house layer: `HermesListItem`, `HermesIconTile`, `HermesIconButton`, `HermesButton`, `HermesListDefaults`, plus older helpers (`InfoRow`, `StatCard`, `ToggleRow`, `SectionHeader`, `StatusBadge`, `SearchBar`, `FilterChipRow`).
- Material widgets are still used directly in: `Card(` 152 hits / 38 files; `TextButton(` 90 / 31; `OutlinedButton(` 41 / 21; `Surface(` 50 / 29; `IconButton(` 98 / 42. (`Button(` shows 328 / 59 because the grep matches `IconButton(` too — treat it as "many", not a precise count.)
- Open PRs at plan time: #157 Plugins, #158 Skills, #159 MCP, #160 components, plus the Files transport branch.

**Assumptions:** the spire palette and spacing tokens (`Spacing.sm = 8`, `md = 16`, `lg = 24`) stay as they are; goldens stay the review mechanism; nothing in this plan changes behaviour, only how a screen is written.

---

## Proposed approach

Four rules, applied by components rather than by reviewers:

1. **Tile** — `HermesIconTile`: 40 dp, 10 dp radius, 22 dp glyph.
2. **Row control** — `HermesIconButton`: 40 dp target, 20 dp glyph.
3. **Buttons** — `HermesButton(PRIMARY | SECONDARY | DESTRUCTIVE)`; no `labelSmall` overrides.
4. **Rows** — `HermesListItem` for anything list-shaped; `HermesCard` (new) for grouped panels.

Missing primitives to add before mass migration: `HermesCard`, `HermesSectionHeader`, `HermesChip`, `HermesSwitchRow`, `HermesFormField`, `HermesEmptyState`, `HermesErrorState`, `HermesTopBar`, `HermesDialog`. Each one absorbs a pattern that is currently copied 3–20 times.

---

## Step-by-step plan

### Phase 0 — clear the decks (no new code)

1. Merge #157, #158, #159, #160 and the Files transport.
2. Move Skills, MCP, Plugins tiles onto `HermesIconTile` (one commit, three files).
3. Verify: `pylon-build.sh :ui:jvmTest`, `ktlintCheck`, `recordRoborazziDebug` all 0.

### Phase 1 — inventory and gaps (read-only, produces a list)

4. For each of the 30 screens, list every Material widget call and its house equivalent.
   Command: `grep -rnE "\b(Card|Surface|Button|OutlinedButton|TextButton|IconButton|Switch|ListItem|FilterChip|SuggestionChip)\(" ui/src/commonMain/kotlin/com/m57/hermescontrol/ui --include=*.kt`
   Output: a table in `.hermes/plans/inventory.md`, one row per screen, columns: widget → house component → "exists / missing".
5. Freeze the component list: any pattern used by **3 or more** screens gets a component; anything used by one screen stays local.

### Phase 2 — build the missing primitives (one commit each, TDD)

6. `HermesCard` — flat container, optional 1 dp outline, no fill; replaces 152 `Card(` uses in waves. Test: golden with three cards, assert no surface fill.
7. `HermesSectionHeader` — title, optional trailing action, 8 dp bottom gap; replaces the two existing `SectionHeader` variants.
8. `HermesChip` — selected/unselected/disabled, 32 dp height; replaces `FilterChip`/`SuggestionChip`.
9. `HermesSwitchRow` — a `HermesListItem` whose only control is the switch (Apple semantics); replaces hand-rolled switch rows.
10. `HermesFormField` — outlined text field with label, error text, password toggle; replaces the ~20 inline `OutlinedTextField` blocks.
11. `HermesEmptyState` / `HermesErrorState` — unify the existing `EmptyState`/`ErrorState` call sites (both already exist; this merges the variants).
12. `HermesTopBar` — title, back/menu icon, actions row; wraps `HermesScaffold`.
13. `HermesDialog` — title/body/confirm-dismiss with variant matching `HermesButton`.

Each of 6–13: golden in `app/src/test/assets/roborazzi/hermes-<name>.png`, `record 0`, `ktlint 0`.

### Phase 3 — the guard, before the migration

14. Add `app/src/test/java/com/m57/hermescontrol/ComponentDisciplineTest.kt`: reads every `.kt` under `ui/src` and fails if a file outside `ui/common/SharedComponents.kt` references `androidx.compose.material3.{Card,Button,OutlinedButton,TextButton,IconButton,Switch,FilterChip}`.
15. Run it: expect a red test listing every offending file — that list becomes the migration checklist.
16. Add a temporary allow-list of the 30 screen paths so CI stays green while the migration runs; each migrated screen is deleted from the list.

### Phase 4 — migrate in waves (one PR per wave)

17. **Wave A — list screens:** Files, Logs, Sessions, Processes, Profiles, Toolsets.
18. **Wave B — form screens:** Config, Gateway, System, Model, Diagnostics.
19. **Wave C — dashboards:** Achievements, Analytics, Billing, Kanban.
20. **Wave D — chat:** `chat/` components (they compose their own cards and bubbles).
21. Per wave: migrate, `record` goldens, `ktlintCheck`, `:ui:jvmTest`, then delete those screens from the allow-list and re-run the guard test.

### Phase 5 — close out

22. Delete the allow-list; the guard now enforces the rule everywhere.
23. Delete dead helpers (`InfoRow`, `ToggleRow`, `StatCard`) if the inventory shows zero call sites.
24. Update `docs/design/components.md` with the final component list and the four rules.

---

## Files likely to change

- `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/common/SharedComponents.kt` — all new primitives.
- `ui/src/commonMain/kotlin/com/m57/hermescontrol/ScreenGraphEntries.kt` — only if a Route name changes.
- Every `ui/.../ui/*/*Screen.kt` — in waves, one PR each.
- `app/src/test/java/com/m57/hermescontrol/ComponentDisciplineTest.kt` — new guard.
- `app/src/test/assets/roborazzi/*.png` — goldens per screen and per component.

## Tests and validation

- Per commit: `pylon-build.sh ktlintCheck`, then `:ui:compileKotlinWasmJs :ui:compileKotlinJvm :app:compileDebugKotlin`, then `:ui:jvmTest`, then `:app:recordRoborazziDebug --tests "*<Screen>RoboTest*"`. All four must be 0. The build lock serialises these — never two at once.
- The guard test is the only one that can fail for a *new* reason after a wave; treat its list as the source of truth for what is left.
- Browser check per wave: build dist, restart the preview, walk the screen on the tailnet URL.

## Risks and tradeoffs

- **Over-abstraction.** A component per screen is worse than no component. The 3-use rule in step 5 is the brake.
- **Token drift.** `HermesCard` must not take a `containerColor` parameter or screens will reintroduce the grey we just removed.
- **Golden churn.** Every wave re-records goldens; review them as diffs, not as a rubber stamp.
- **Chat is different.** `chat/` composes streaming content with its own measurement; expect it to be the last and the least mechanical.
- **The guard can be dodged** by an alias import. Accept that; it is a ratchet, not a wall.

## Open questions

1. Does `HermesListItem` stay the only row, or do form screens get `HermesFormRow` (label left, control right)?
2. Should `HermesButton` take a leading icon slot, or is `IconButton` + `TextButton` enough?
3. Chat bubbles: a component, or leave them alone and exempt `chat/` from the guard?
4. Is a `material3` import ban too strict for `ModalBottomSheet`-style surfaces that have no equivalent yet?
