# Message identity: the upstream doctrine

Everything in this file is measured or quoted from upstream sources. Where a statement is inference it is
marked **inference**. Do not act on an inference as if it were a ruling.

## The doctrine (in force)

From [NousResearch/hermes-agent#110316](https://github.com/NousResearch/hermes-agent/issues/110316), comment
by `@snaffuo` (2026-09-14):

> **Disposition confirmed (Agent 0, per standing ruling): DO NOT FIX.** … The doctrine response —
> **never cite a `messages_read` id as a stable cross-read identifier; cite content hash or scope instead**
> (rules from 09-13, live case rows 297874/298338/298830, sha 113588ea) — was adopted and is in force. No code
> change is proposed or planned on this issue. Any future fix proposal should start from a fresh issue, not
> this one.

Three consequences, all binding on us:

1. **Never** treat a `messages_read` id (the SQLite rowid) as identity across two reads. It changes when
   compaction moves a message to a new row.
2. **Content hash or scope** is the sanctioned substitute. "Scope" means the session the row belongs to.
3. **#110316 is closed to fixes.** Any upstream proposal must open a fresh issue and argue against the
   doctrine, not just the code.

We do not file against #110316. The project ruled; we comply on our side.

## Why upstream refused stable ids

**Inference, not stated anywhere:** the schema change is expensive (`id INTEGER PRIMARY KEY AUTOINCREMENT` is
the rowid, and `messages` is ~97% of `state.db`), while a content hash gives the same guarantee with no
migration. The maintainers wrote the rule but not the reasoning. Treat the "why" as unknown.

## Two different key shapes

These are not interchangeable, and conflating them is the mistake this file exists to prevent.

### 1. Identity — proposed `message_uid`

`@kvnloo` (CONTRIBUTOR), same issue thread:

> Mint a stable id at insert. New column, e.g. `message_uid`, deterministic:
> **`sha256(session_id + role + content + timestamp)`**, truncated to 16 hex chars. **Not a security
> boundary** — 16 hex chars against ~70k duplicates is fine — just an identity.

Four parts: `session_id | role | content | timestamp`. Note `timestamp` **is** in it.

Shipped in upstream as a real column (`messages.message_uid`), minted by trigger
`messages_message_uid_insert` (`lower(hex(randomblob(16)))`) — so in practice it is random, not the sha256
above. The formula is the *proposal*; the implementation mints an opaque uid.

### 2. Row matching — the store's dedupe key

`persistence/sqlite/conversation/_store/messages.py:571` and
`tests/contracts/_reference/conversation/lineage.py:92`, identical:

```python
donors.setdefault((row["role"], row["content"], row["tool_call_id"], row["tool_calls"], row["tool_name"]), [])
```

Five parts: `role | content | tool_call_id | tool_calls | tool_name`. No `session_id`, no `timestamp`. This is
how the store finds a matching parent row when carrying timestamps across a handoff
(`_carry_parent_timestamps`) — it is **row matching**, not identity.

A third shape exists: `_stable_tool_key` (`identity.py:96`) returns
`("assistant", None, timestamp, tool_call_id, call_ids, tool_name)` for tool-calling assistant rows only, so a
pruned carried copy collapses with its durable original (#117750).

## What compaction does to identity

Measured in `tests/persistence_sqlite/conversation/test_compaction_carried_tail.py:71-73`:

```python
assert len(copies) == 2, "original + carried copy, no clone"
assert sorted(r["active"] for r in copies) == [0, 1]
assert len({r["message_uid"] for r in copies}) == 1
```

**One `message_uid` across the original and its carried copy.** So `message_uid` survives compaction and is
safe as logical identity. The rowid does not survive.

`display_identity` / `display_order` are indexed and trigger-filled, but appear **nowhere** in
`persistence/conversation/messages.py` wire keys — not usable client-side.

## Where our code stands

| Concept | In our code | Shape |
|---|---|---|
| Identity | `ChatMessage.id` | `message_uid`, else the content fingerprint |
| Position | `ChatMessage.origin` | `Local \| Rest(serverIndex) \| Resume(rowId)` |
| Duplicate detection | `collapseDuplicateRows` | `session_id \| role \| content \| timestamp` |

`MessageOrigin.kt` holds the reasoning for splitting identity from position. `MessageRowKey.kt` holds the
fingerprint and the dedup.

The fingerprint uses FNV-1a 64-bit, not sha256: identity, not a security boundary — the same justification
`@kvnloo` gives for truncating their hash to 16 hex.

## Open questions (unverified)

- Does `message_uid` ever arrive null on a real payload? If not, our fingerprint fallback is dead code. One
  read of a long session settles it.
- Can two genuinely different messages share `session_id | role | content | timestamp` in real traffic? If
  so, `collapseDuplicateRows` over-collapses.
- Why the maintainers chose the doctrine over stable ids is not written down anywhere.
