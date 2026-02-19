package com.miracle.arcanesigils.effects;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages temporary skin changes for players using ProtocolLib.
 * Handles fetching skins from Mojang API, applying changes via packets,
 * and restoring original skins after duration expires.
 */
public class SkinChangeManager implements Listener {

    private final ArmorSetsPlugin plugin;
    private final ProtocolManager protocolManager;

    // Track original skins for restoration
    private final Map<UUID, SkinData> originalSkins = new ConcurrentHashMap<>();

    // Track active restore tasks
    private final Map<UUID, BukkitTask> restoreTasks = new ConcurrentHashMap<>();

    // Cache fetched skins to avoid repeated API calls
    private final Map<String, SkinData> skinCache = new ConcurrentHashMap<>();

    // Track when skin was last changed (for debug timing)
    private final Map<UUID, Long> skinChangeTimestamps = new ConcurrentHashMap<>();

    // Track USE_ENTITY packets for debug
    private final Map<UUID, Long> lastUseEntityPacket = new ConcurrentHashMap<>();
    
    // Preserve noDamageTicks and maxNoDamageTicks during skin change
    private final Map<UUID, Integer> preservedNoDamageTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> preservedMaxNoDamageTicks = new ConcurrentHashMap<>();

    public SkinChangeManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerPacketDebugListener();
    }

    /**
     * Register a packet listener to track USE_ENTITY (attack) packets.
     * This helps debug when client sends attack packets but server doesn't register damage.
     */
    private void registerPacketDebugListener() {
        ArmorSetsPlugin pluginRef = this.plugin; // Capture for inner class
        SkinChangeManager self = this;

        protocolManager.addPacketListener(
            new com.comphenix.protocol.events.PacketAdapter(plugin,
                    com.comphenix.protocol.events.ListenerPriority.MONITOR,
                    PacketType.Play.Client.USE_ENTITY) {
                @Override
                public void onPacketReceiving(com.comphenix.protocol.events.PacketEvent event) {
                    Player attacker = event.getPlayer();
                    PacketContainer packet = event.getPacket();

                    try {
                        // Get the target entity ID from the packet
                        int targetEntityId = packet.getIntegers().read(0);

                        // Check if the attacker or any nearby player has skin change active
                        boolean attackerHasSkinChange = self.hasSkinChange(attacker);
                        boolean attackerIsStunned = pluginRef.getStunManager() != null &&
                                pluginRef.getStunManager().isStunned(attacker);

                        // Find the target entity by ID - use Bukkit.getEntity on main thread
                        // For now, just try to get nearby players by entity ID
                        org.bukkit.entity.Entity targetEntity = null;
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getEntityId() == targetEntityId) {
                                targetEntity = p;
                                break;
                            }
                        }

                        boolean targetHasSkinChange = false;
                        boolean targetIsStunned = false;
                        if (targetEntity instanceof Player targetPlayer) {
                            targetHasSkinChange = self.hasSkinChange(targetPlayer);
                            targetIsStunned = pluginRef.getStunManager() != null &&
                                    pluginRef.getStunManager().isStunned(targetPlayer);
                        }

                        // Only log if either party has skin change or stun
                        if (!attackerHasSkinChange && !attackerIsStunned &&
                                !targetHasSkinChange && !targetIsStunned) {
                            return;
                        }

                        // Get the action type (ATTACK, INTERACT, etc.)
                        Object actionWrapper = packet.getEnumEntityUseActions().read(0);
                        String actionType = "UNKNOWN";
                        boolean isAttack = false;
                        if (actionWrapper != null) {
                            try {
                                // WrappedEnumEntityUseAction has getAction() method
                                java.lang.reflect.Method getAction = actionWrapper.getClass().getMethod("getAction");
                                Object action = getAction.invoke(actionWrapper);
                                actionType = action != null ? action.toString() : "NULL";
                                isAttack = actionType.contains("ATTACK");
                            } catch (Exception ex) {
                                actionType = actionWrapper.getClass().getSimpleName();
                            }
                        }

                        lastUseEntityPacket.put(attacker.getUniqueId(), System.currentTimeMillis());

                        StringBuilder sb = new StringBuilder();
                        sb.append("\n---------- USE_ENTITY PACKET ----------\n");
                        sb.append("FROM: ").append(attacker.getName())
                                .append(" (entityId=").append(attacker.getEntityId()).append(")\n");
                        sb.append("  hasSkinChange=").append(attackerHasSkinChange)
                                .append(", isStunned=").append(attackerIsStunned).append("\n");

                        if (targetEntity != null) {
                            sb.append("TARGET: ").append(targetEntity.getName())
                                    .append(" (entityId=").append(targetEntityId).append(")\n");
                            if (targetEntity instanceof Player tp) {
                                sb.append("  hasSkinChange=").append(targetHasSkinChange)
                                        .append(", isStunned=").append(targetIsStunned).append("\n");
                                sb.append("  skinDebug: ").append(self.getDebugInfo(tp)).append("\n");
                            }
                        } else {
                            sb.append("TARGET: Entity ID ").append(targetEntityId)
                                    .append(" NOT FOUND in online players!\n");
                            sb.append("  (Note: only checking online players, not all entities)\n");
                        }

                        sb.append("ACTION: ").append(actionType);
                        if (isAttack) {
                            sb.append(" [IS ATTACK - should fire EntityDamageByEntityEvent]");
                        }
                        sb.append("\n");

                        // Check timing
                        long attackerMs = self.getTimeSinceSkinChange(attacker);
                        if (attackerMs >= 0) {
                            sb.append("  Attacker skin change ").append(attackerMs).append("ms ago\n");
                        }
                        if (targetEntity instanceof Player tp) {
                            long targetMs = self.getTimeSinceSkinChange(tp);
                            if (targetMs >= 0) {
                                sb.append("  Target skin change ").append(targetMs).append("ms ago\n");
                            }
                        }

                        sb.append("----------------------------------------");
                        pluginRef.getLogger().info(sb.toString());

                        // Send clearer message to attacker
                        String targetName = targetEntity != null ? targetEntity.getName() : "ID:" + targetEntityId;
                        attacker.sendMessage("§e[Debug] " + actionType + " -> " + targetName +
                                (isAttack ? " §c(ATTACK - watch for damage event)" : ""));

                    } catch (Exception e) {
                        pluginRef.getLogger().warning("[SkinDebug] Error in USE_ENTITY handler: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        );
    }

    /**
     * Change a player's skin temporarily.
     *
     * @param target          The player whose skin to change
     * @param skinSource      Username or UUID to copy skin from
     * @param durationSeconds How long the skin change lasts
     */
    public void changeSkin(Player target, String skinSource, int durationSeconds) {
        UUID uuid = target.getUniqueId();

        // Cancel existing restore task if any
        BukkitTask existingTask = restoreTasks.remove(uuid);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Store original skin if not already stored
        if (!originalSkins.containsKey(uuid)) {
            SkinData original = getSkinFromPlayer(target);
            if (original != null) {
                originalSkins.put(uuid, original);
            }
        }

        // Fetch new skin async to avoid blocking
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SkinData newSkin = fetchSkin(skinSource);
            if (newSkin == null || !newSkin.isValid()) {
                plugin.getLogger().warning("Failed to fetch skin for: " + skinSource);
                return;
            }

            // Apply skin on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!target.isOnline()) return;

                applySkinChange(target, newSkin);

                // Schedule restoration
                BukkitTask restoreTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    restoreSkin(target);
                }, durationSeconds * 20L);

                restoreTasks.put(uuid, restoreTask);
            });
        });
    }

    /**
     * Restore a player's original skin.
     */
    public void restoreSkin(Player target) {
        UUID uuid = target.getUniqueId();

        // Cancel restore task if scheduled
        BukkitTask task = restoreTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        // Get original skin
        SkinData original = originalSkins.remove(uuid);
        if (original == null || !target.isOnline()) {
            skinChangeTimestamps.remove(uuid); // Clean up timestamp
            return;
        }

        plugin.getLogger().info("[SkinDebug] Restoring original skin for " + target.getName());
        applySkinChange(target, original);
    }

    /**
     * Apply a skin change to a player using packets.
     * This makes the change visible to all players including the target themselves.
     */
    private void applySkinChange(Player target, SkinData skin) {
        try {
            // Track timing for debug
            skinChangeTimestamps.put(target.getUniqueId(), System.currentTimeMillis());

            // Store current immunity state for restoration (preserve existing immunity, don't force reset)
            int currentMaxNoDamageTicks = target.getMaximumNoDamageTicks();
            int currentNoDamageTicks = target.getNoDamageTicks();
            preservedMaxNoDamageTicks.put(target.getUniqueId(), currentMaxNoDamageTicks);
            preservedNoDamageTicks.put(target.getUniqueId(), currentNoDamageTicks);

            plugin.getLogger().info(String.format(
                "[SkinChange] Preserving immunity for %s: noDamageTicks=%d, max=%d",
                target.getName(), currentNoDamageTicks, currentMaxNoDamageTicks
            ));

            // Get all online players who can see this player
            Collection<? extends Player> viewers = Bukkit.getOnlinePlayers();

            // Create modified game profile with new skin
            WrappedGameProfile newProfile = createProfileWithSkin(target, skin);

            // Step 1: Remove player from tab list for all viewers
            sendPlayerInfoRemove(target, viewers);

            // Step 2: Add player back with new skin
            sendPlayerInfoAdd(target, newProfile, viewers);

            // Step 3: Respawn entity for all viewers (so they see new skin)
            respawnPlayerEntity(target, viewers);
            
            // FORCE standard immunity (10 ticks) after respawn - try multiple times for reliability
            for (long delay : new long[]{1L, 2L, 3L}) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!target.isOnline()) return;

                    // Restore preserved immunity values (don't force reset to 10)
                    Integer preservedMax = preservedMaxNoDamageTicks.get(target.getUniqueId());
                    Integer preservedCurrent = preservedNoDamageTicks.get(target.getUniqueId());

                    int maxImmunity = preservedMax != null ? preservedMax : 20;
                    int currentImmunity = preservedCurrent != null ? preservedCurrent : 10;

                    target.setMaximumNoDamageTicks(maxImmunity);
                    target.setNoDamageTicks(currentImmunity); // Restore original, don't force

                    plugin.getLogger().info(String.format(
                        "[SkinChange] ✓ Restored immunity for %s: noDamageTicks=%d, max=%d",
                        target.getName(), currentImmunity, maxImmunity
                    ));
                }, delay);
            }
            
            // Cleanup after final attempt
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                preservedMaxNoDamageTicks.remove(target.getUniqueId());
                preservedNoDamageTicks.remove(target.getUniqueId());
            }, 5L);

            plugin.getLogger().info("[SkinDebug] Applied skin change to " + target.getName() +
                    " (entityId=" + target.getEntityId() + ", viewers=" + (viewers.size() - 1) + ")");

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply skin change: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send ENTITY_DESTROY packet to all viewers.
     */
    private void sendEntityDestroy(Player target, Collection<? extends Player> viewers) {
        try {
            PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroyPacket.getIntLists().write(0, List.of(target.getEntityId()));

            for (Player viewer : viewers) {
                if (!viewer.equals(target) && viewer.canSee(target)) {
                    protocolManager.sendServerPacket(viewer, destroyPacket);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send ENTITY_DESTROY: " + e.getMessage());
        }
    }

    /**
     * Send entity spawn packets to all viewers with full entity data.
     */
    private void sendEntitySpawn(Player target, Collection<? extends Player> viewers) {
        try {
            int entityId = target.getEntityId();
            Location loc = target.getLocation();

            // Spawn entity packet
            PacketContainer spawnPacket = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            spawnPacket.getIntegers().write(0, entityId);
            spawnPacket.getUUIDs().write(0, target.getUniqueId());
            spawnPacket.getEntityTypeModifier().write(0, org.bukkit.entity.EntityType.PLAYER);
            spawnPacket.getDoubles().write(0, loc.getX());
            spawnPacket.getDoubles().write(1, loc.getY());
            spawnPacket.getDoubles().write(2, loc.getZ());
            spawnPacket.getBytes().write(0, (byte) (loc.getPitch() * 256.0F / 360.0F));
            spawnPacket.getBytes().write(1, (byte) (loc.getYaw() * 256.0F / 360.0F));
            spawnPacket.getBytes().write(2, (byte) (loc.getYaw() * 256.0F / 360.0F));

            for (Player viewer : viewers) {
                if (!viewer.equals(target) && viewer.canSee(target)) {
                    protocolManager.sendServerPacket(viewer, spawnPacket);
                    sendEquipmentPackets(target, viewer);
                }
            }

            // For self-view
            target.updateInventory();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send entity spawn: " + e.getMessage());
        }
    }

    /**
     * Create a WrappedGameProfile with the given skin data.
     */
    private WrappedGameProfile createProfileWithSkin(Player player, SkinData skin) {
        WrappedGameProfile profile = WrappedGameProfile.fromPlayer(player);

        // Create new profile with same UUID and name but new skin
        WrappedGameProfile newProfile = new WrappedGameProfile(player.getUniqueId(), player.getName());

        // Add skin texture property
        WrappedSignedProperty textureProperty = WrappedSignedProperty.fromValues(
            "textures",
            skin.getTextureValue(),
            skin.getTextureSignature()
        );

        newProfile.getProperties().put("textures", textureProperty);

        return newProfile;
    }

    /**
     * Send PLAYER_INFO_REMOVE packet to remove player from tab list.
     */
    private void sendPlayerInfoRemove(Player target, Collection<? extends Player> viewers) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            packet.getUUIDLists().write(0, List.of(target.getUniqueId()));

            for (Player viewer : viewers) {
                protocolManager.sendServerPacket(viewer, packet);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send PLAYER_INFO_REMOVE: " + e.getMessage());
        }
    }

    /**
     * Send PLAYER_INFO packet to add player with new profile.
     */
    private void sendPlayerInfoAdd(Player target, WrappedGameProfile profile, Collection<? extends Player> viewers) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);

            // Set actions
            EnumSet<EnumWrappers.PlayerInfoAction> actions = EnumSet.of(
                EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
                EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
                EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE
            );
            packet.getPlayerInfoActions().write(0, actions);

            // Create PlayerInfoData
            PlayerInfoData playerInfoData = new PlayerInfoData(
                target.getUniqueId(),
                target.getPing(),
                true, // listed
                EnumWrappers.NativeGameMode.fromBukkit(target.getGameMode()),
                profile,
                null // display name (use default)
            );

            // Write to index 1 (1.20.6+ quirk)
            packet.getPlayerInfoDataLists().write(1, List.of(playerInfoData));

            for (Player viewer : viewers) {
                protocolManager.sendServerPacket(viewer, packet);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send PLAYER_INFO: " + e.getMessage());
        }
    }

    /**
     * Respawn the player entity so viewers see the new skin.
     * For the player themselves, we need special handling.
     */
    private void respawnPlayerEntity(Player target, Collection<? extends Player> viewers) {
        try {
            int entityId = target.getEntityId();
            Location loc = target.getLocation();

            // Destroy entity packet
            PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroyPacket.getIntLists().write(0, List.of(entityId));

            // In 1.20.2+, NAMED_ENTITY_SPAWN was replaced with SPAWN_ENTITY for all entities
            PacketContainer spawnPacket = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            spawnPacket.getIntegers().write(0, entityId);
            spawnPacket.getUUIDs().write(0, target.getUniqueId());
            // Entity type for player - get from EntityType enum
            spawnPacket.getEntityTypeModifier().write(0, org.bukkit.entity.EntityType.PLAYER);
            spawnPacket.getDoubles().write(0, loc.getX());
            spawnPacket.getDoubles().write(1, loc.getY());
            spawnPacket.getDoubles().write(2, loc.getZ());
            // Pitch and yaw as angles (0-255 mapped to 0-360)
            spawnPacket.getBytes().write(0, (byte) (loc.getPitch() * 256.0F / 360.0F));
            spawnPacket.getBytes().write(1, (byte) (loc.getYaw() * 256.0F / 360.0F));
            spawnPacket.getBytes().write(2, (byte) (loc.getYaw() * 256.0F / 360.0F)); // Head yaw

            for (Player viewer : viewers) {
                if (viewer.equals(target)) {
                    // For self-view, use RESPAWN packet instead
                    respawnSelf(target);
                } else if (viewer.canSee(target)) {
                    protocolManager.sendServerPacket(viewer, destroyPacket);
                    protocolManager.sendServerPacket(viewer, spawnPacket);

                    // Send equipment packets so armor is visible
                    sendEquipmentPackets(target, viewer);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to respawn player entity: " + e.getMessage());
        }
    }

    /**
     * Respawn the player to themselves so they see their own skin change.
     * Note: Self-view skin changes are complex due to first-person perspective.
     * Currently, the player won't see their own skin change, but others will see it.
     * A proper implementation would require dimension switching or respawn packets.
     */
    private void respawnSelf(Player target) {
        // For now, just update the player's inventory to trigger a partial refresh
        // The player won't see their own skin change in first person, but:
        // - They'll see it if they open inventory (partial view)
        // - Other players will see the change
        // - Full self-view would require dimension switching which is disruptive
        target.updateInventory();

        // Note: To see your own skin change, the client would need to be sent
        // a dimension change or respawn packet, which causes screen flickering.
        // This is a known limitation of Minecraft's protocol.
    }

    /**
     * Send equipment packets so the player's armor is visible after respawn.
     */
    private void sendEquipmentPackets(Player target, Player viewer) {
        try {
            PacketContainer equipPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            equipPacket.getIntegers().write(0, target.getEntityId());

            List<Pair<EnumWrappers.ItemSlot, org.bukkit.inventory.ItemStack>> equipment = new ArrayList<>();
            equipment.add(new Pair<>(EnumWrappers.ItemSlot.MAINHAND, target.getInventory().getItemInMainHand()));
            equipment.add(new Pair<>(EnumWrappers.ItemSlot.OFFHAND, target.getInventory().getItemInOffHand()));
            equipment.add(new Pair<>(EnumWrappers.ItemSlot.HEAD, target.getInventory().getHelmet()));
            equipment.add(new Pair<>(EnumWrappers.ItemSlot.CHEST, target.getInventory().getChestplate()));
            equipment.add(new Pair<>(EnumWrappers.ItemSlot.LEGS, target.getInventory().getLeggings()));
            equipment.add(new Pair<>(EnumWrappers.ItemSlot.FEET, target.getInventory().getBoots()));

            equipPacket.getSlotStackPairLists().write(0, equipment);

            protocolManager.sendServerPacket(viewer, equipPacket);
        } catch (Exception e) {
            // Equipment packets are optional, don't warn
        }
    }

    /**
     * Get the current skin data from a player.
     */
    private SkinData getSkinFromPlayer(Player player) {
        try {
            WrappedGameProfile profile = WrappedGameProfile.fromPlayer(player);
            Collection<WrappedSignedProperty> textures = profile.getProperties().get("textures");

            if (textures != null && !textures.isEmpty()) {
                WrappedSignedProperty texture = textures.iterator().next();
                return new SkinData(texture.getValue(), texture.getSignature());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get skin from player: " + e.getMessage());
        }
        return null;
    }

    /**
     * Fetch skin data from Mojang API by username or UUID.
     */
    private SkinData fetchSkin(String source) {
        // Check cache first
        String cacheKey = source.toLowerCase();
        if (skinCache.containsKey(cacheKey)) {
            return skinCache.get(cacheKey);
        }

        try {
            String uuid;

            // If source looks like a UUID, use it directly
            if (source.length() == 32 || source.length() == 36) {
                uuid = source.replace("-", "");
            } else {
                // Fetch UUID from username
                uuid = fetchUuidFromUsername(source);
                if (uuid == null) {
                    return null;
                }
            }

            // Fetch profile with skin
            SkinData skin = fetchSkinFromUuid(uuid);
            if (skin != null) {
                skinCache.put(cacheKey, skin);
            }
            return skin;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fetch skin for " + source + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Fetch UUID from Mojang API by username.
     */
    private String fetchUuidFromUsername(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() != 200) {
                return null;
            }

            JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
            return json.get("id").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetch skin data from Mojang API by UUID.
     */
    private SkinData fetchSkinFromUuid(String uuid) {
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() != 200) {
                return null;
            }

            JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
            var properties = json.getAsJsonArray("properties");

            for (var prop : properties) {
                JsonObject propObj = prop.getAsJsonObject();
                if ("textures".equals(propObj.get("name").getAsString())) {
                    String value = propObj.get("value").getAsString();
                    String signature = propObj.has("signature") ? propObj.get("signature").getAsString() : "";
                    return new SkinData(value, signature);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fetch skin from UUID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Check if a player currently has a changed skin.
     */
    public boolean hasSkinChange(Player player) {
        return originalSkins.containsKey(player.getUniqueId());
    }

    /**
     * Get time in milliseconds since skin was last changed.
     * Returns -1 if no skin change is active.
     */
    public long getTimeSinceSkinChange(Player player) {
        Long timestamp = skinChangeTimestamps.get(player.getUniqueId());
        if (timestamp == null) return -1;
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Get debug information about a player's skin change state.
     */
    public String getDebugInfo(Player player) {
        UUID uuid = player.getUniqueId();
        StringBuilder sb = new StringBuilder();
        sb.append("hasSkinChange=").append(originalSkins.containsKey(uuid));
        Long timestamp = skinChangeTimestamps.get(uuid);
        if (timestamp != null) {
            long elapsed = System.currentTimeMillis() - timestamp;
            sb.append(", msSinceSkinChange=").append(elapsed);
            sb.append(", ticksSinceSkinChange=").append(elapsed / 50);
        }
        return sb.toString();
    }

    /**
     * Clean up when player quits.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // Cancel restore task
        BukkitTask task = restoreTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        // Remove from tracking (no need to restore, they're disconnecting)
        originalSkins.remove(uuid);
        skinChangeTimestamps.remove(uuid);
        lastUseEntityPacket.remove(uuid);
    }

    /**
     * Clean up skin changes when player dies.
     * Restores original skin immediately to prevent it persisting on respawn.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        restoreSkin(player);  // Restore original skin immediately on death
    }

    /**
     * FIX: Override AdvancedChat's InvincibleEffect which wrongly cancels damage
     * when the attacker has a skin change (interprets respawn packet as actual respawn).
     *
     * Runs at HIGH priority to uncancell after AdvancedChat's NORMAL priority handler.
     */
    /**
     * Fix: Override damage cancellation when ANY involved party has recent skin change.
     * This addresses issues where external plugins (AdvancedChat) or Minecraft itself
     * incorrectly cancels damage events due to entity tracking desync from respawn packets.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageFixSkinChangeDesync(EntityDamageByEntityEvent event) {
        if (!event.isCancelled()) return;

        // Get involved parties
        Player attacker = null;
        Player victim = null;

        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj) {
            if (proj.getShooter() instanceof Player shooter) {
                attacker = shooter;
            }
        }

        if (event.getEntity() instanceof Player p) {
            victim = p;
        }

        if (attacker == null) return;

        // Respect Minecraft's built-in immunity frames (noDamageTicks)
        // This prevents uncancelling legitimate immunity-based cancellations
        // which was causing the "multiple hits" bug on skin restore
        if (event.getEntity() instanceof org.bukkit.entity.LivingEntity le && le.getNoDamageTicks() > 0) {
            return;
        }


        // Check if attacker has recent skin change
        long attackerMs = getTimeSinceSkinChange(attacker);
        if (attackerMs >= 0 && attackerMs < 5000) {
            event.setCancelled(false);
            plugin.getLogger().info("[SkinFix] Uncancelled: attacker " + attacker.getName() +
                " had skin change " + attackerMs + "ms ago");
            return;
        }

        // Check if victim has recent skin change
        if (victim != null) {
            long victimMs = getTimeSinceSkinChange(victim);
            if (victimMs >= 0 && victimMs < 5000) {
                event.setCancelled(false);
                plugin.getLogger().info("[SkinFix] Uncancelled: victim " + victim.getName() +
                    " had skin change " + victimMs + "ms ago");
                return;
            }
        }

        // Check if any player NEAR the victim had recent skin change
        if (victim != null) {
            for (Player nearby : victim.getWorld().getPlayers()) {
                if (nearby == attacker || nearby == victim) continue;
                if (nearby.getLocation().distanceSquared(victim.getLocation()) > 2500) continue;

                long nearbyMs = getTimeSinceSkinChange(nearby);
                if (nearbyMs >= 0 && nearbyMs < 3000) {
                    event.setCancelled(false);
                    plugin.getLogger().info("[SkinFix] Uncancelled: nearby player " + nearby.getName() +
                        " had skin change " + nearbyMs + "ms ago (victim=" + victim.getName() + ")");
                    return;
                }
            }
        }
    }


    /**
     * Clean up all active skin changes on shutdown.
     */
    public void shutdown() {
        // Cancel all restore tasks
        for (BukkitTask task : restoreTasks.values()) {
            task.cancel();
        }
        restoreTasks.clear();

        // Restore all active skin changes
        for (UUID uuid : originalSkins.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                restoreSkin(player);
            }
        }
        originalSkins.clear();
        skinCache.clear();
    }
}
