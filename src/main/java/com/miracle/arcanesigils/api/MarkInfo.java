package com.miracle.arcanesigils.api;

import java.util.UUID;

/**
 * Immutable snapshot of an active mark on an entity.
 */
public record MarkInfo(
    String name,
    double multiplier,
    long expiryTimeMs,
    UUID ownerUUID
) {}
