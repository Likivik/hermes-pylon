package com.m57.hermescontrol

/**
 * No screens are assembled on this target yet.
 *
 * The web and desktop shells still render their own root, so an empty graph is
 * the honest answer: `NavigationController.isPrimary` returns false for every
 * key and the drawer renders nothing. Filling this in is the per-platform side
 * of the migration, not a shared-code concern.
 */
actual fun screenGraph(): ScreenGraph = ScreenGraph(screens = emptyList(), primary = emptySet())
