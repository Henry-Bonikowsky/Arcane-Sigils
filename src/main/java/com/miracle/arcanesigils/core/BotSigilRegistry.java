package com.miracle.arcanesigils.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores virtual sigil registrations for bot players.
 *
 * Bots don't have real socketed items, so this registry tells the
 * SignalHandler which sigils to fire for them. Cooldowns, chance rolls,
 * and flow execution all go through the normal pipeline.
 */
public class BotSigilRegistry {

    private final Map<UUID, List<String>> registrations = new ConcurrentHashMap<>();

    public void register(UUID playerId, List<String> sigilIds) {
        registrations.put(playerId, List.copyOf(sigilIds));
    }

    public void unregister(UUID playerId) {
        registrations.remove(playerId);
    }

    public List<String> getRegisteredSigils(UUID playerId) {
        return registrations.getOrDefault(playerId, Collections.emptyList());
    }

    public boolean hasBotSigils(UUID playerId) {
        return registrations.containsKey(playerId);
    }

    public void clear() {
        registrations.clear();
    }
}
