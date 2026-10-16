package com.m57.hermescontrol.ui.common

import org.jetbrains.compose.resources.StringResource

/**
 * In-memory [StringResolver] for unit tests.
 *
 * Each test sets up the exact messages it expects the ViewModel to
 * pull out of the resource pipeline. The keys are matched by structural
 * equality on the wrapped `id` (the [StringResource] value class).
 *
 * `get` returns the registered message **verbatim** — no `String.format`
 * substitution. The original `R.string.x` format strings the tests
 * stubbed (`"Server returned HTTP %1\$d"`, `"Connection failed: %1\$s"`,
 * …) translated to hardcoded `String` values before the ViewModel saw
 * them, so this stub does the same: tests assert on the final
 * `errorMessage` after the resolver has done its job.
 */
class FakeStringResolver : StringResolver {
    private val messages = mutableMapOf<StringResource, String>()
    val invocations: MutableList<Pair<StringResource, List<Any>>> = mutableListOf()

    fun stub(
        key: StringResource,
        value: String,
    ) {
        messages[key] = value
    }

    override suspend fun get(
        key: StringResource,
        vararg args: Any,
    ): String {
        invocations += key to args.toList()
        // Unstubbed keys return "" rather than throwing: the previous
        // `mockk(relaxed = true)` Application returned "" for any getString it was
        // not explicitly stubbed for, so tests that never asserted that particular
        // message still passed. Throwing here would turn every such path into a
        // failure that has nothing to do with the migration.
        val template = messages[key] ?: return ""
        // Mirror Context.getString(id, vararg): substitute positional args when
        // present. Stubs without placeholders pass through unchanged, so tests that
        // pre-format their expected text keep working.
        return if (args.isEmpty()) template else String.format(template, *args)
    }
}
