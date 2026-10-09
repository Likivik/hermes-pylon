package com.m57.hermescontrol.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts a loosely-typed request map into the floor's wire type.
 *
 * The gateway takes [JsonObject] rather than `Map<String, Any>`: kotlinx
 * serialization cannot encode `Any` at all, so a raw map could only ever throw
 * at runtime. Converting at the boundary makes that impossible by construction
 * while keeping dynamic-key payloads ergonomic at the call site.
 */
fun Map<String, Any?>.toJsonObject(): JsonObject = JsonObject(mapValues { (_, value) -> value.toJsonElement() })

/** Recursively converts a value into a serializable [JsonElement]. */
fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        else -> JsonPrimitive(this.toString())
    }
