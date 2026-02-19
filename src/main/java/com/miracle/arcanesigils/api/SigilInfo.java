package com.miracle.arcanesigils.api;

/**
 * Immutable snapshot of an equipped sigil's state.
 */
public record SigilInfo(
    String id,
    String name,
    int tier,
    String slot,
    String activationType
) {}
