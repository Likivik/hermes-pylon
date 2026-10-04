# Tool progress identity provenance

Adapted the `tool.progress` / `tool.generating` ID propagation and matching from upstream `1ab30f1e90a72774756c5bb7f3fdb1def7c34503` onto downstream `cfbb58bf5750ee5f696adf16731600d8006f740d`.

Unlike the upstream helper, an explicit unknown or conflicting tool ID never falls back to the tool name. Legacy events without an ID match by name only when exactly one running tool qualifies. Existing session fences and pending-process handling remain unchanged; this is not a wholesale cherry-pick.
