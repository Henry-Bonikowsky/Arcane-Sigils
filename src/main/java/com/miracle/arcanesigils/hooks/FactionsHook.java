package com.miracle.arcanesigils.hooks;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.iface.RelationParticipator;
import com.massivecraft.factions.perms.Relation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Direct Vulcan Factions API hook.
 * All methods are static and return safe defaults when Factions is unavailable.
 */
public class FactionsHook {

    private static boolean available;

    public static void init() {
        try {
            if (Bukkit.getPluginManager().getPlugin("VulcanFactions") == null) {
                available = false;
            } else {
                // Verify core API class is accessible
                FPlayers.getInstance();
                available = true;
            }
        } catch (Exception | NoClassDefFoundError e) {
            available = false;
        }
        if (available) {
            Bukkit.getLogger().info("[ArcaneSigils] Factions detected - faction targeting enabled.");
        } else {
            Bukkit.getLogger().info("[ArcaneSigils] Factions not detected - faction targeting disabled.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    // -------------------------------------------------------------------------
    // Relation queries
    // -------------------------------------------------------------------------

    private static Relation getRelationEnum(Player player, Player target) {
        FPlayer fp = FPlayers.getInstance().getByPlayer(player);
        FPlayer ft = FPlayers.getInstance().getByPlayer(target);
        if (fp == null || ft == null) return null;
        return fp.getRelationTo(ft);
    }

    /** Returns true when the two players are in the same faction or allied factions. */
    public static boolean isAlly(Player player, Player target) {
        if (!available) return false;
        try {
            Relation rel = getRelationEnum(player, target);
            if (rel == null) return false;
            return rel.isMember() || rel.isAlly();
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /** Returns true when the two players are enemies. */
    public static boolean isEnemy(Player player, Player target) {
        if (!available) return false;
        try {
            Relation rel = getRelationEnum(player, target);
            if (rel == null) return false;
            return rel.isEnemy();
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /** Returns true when the two players are in a truce. */
    public static boolean isTruce(Player player, Player target) {
        if (!available) return false;
        try {
            Relation rel = getRelationEnum(player, target);
            if (rel == null) return false;
            return rel.isTruce();
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /** Returns true when the two players are neutral (no relation). */
    public static boolean isNeutral(Player player, Player target) {
        if (!available) return false;
        try {
            Relation rel = getRelationEnum(player, target);
            if (rel == null) return false;
            return rel.isNeutral();
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Returns the raw enum name of the relation (MEMBER, ALLY, TRUCE, NEUTRAL, ENEMY),
     * or null if Factions is unavailable or either player has no FPlayer record.
     */
    public static String getRelation(Player player, Player target) {
        if (!available) return null;
        try {
            Relation rel = getRelationEnum(player, target);
            return rel == null ? null : rel.name();
        } catch (Exception | NoClassDefFoundError e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Territory queries
    // -------------------------------------------------------------------------

    /** Returns true when the player is standing in their own faction's territory. */
    public static boolean isInOwnTerritory(Player player) {
        if (!available) return false;
        try {
            FPlayer fp = FPlayers.getInstance().getByPlayer(player);
            return fp != null && fp.isInOwnTerritory();
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /** Returns true when the player is standing in enemy territory. */
    public static boolean isInEnemyTerritory(Player player) {
        if (!available) return false;
        try {
            FPlayer fp = FPlayers.getInstance().getByPlayer(player);
            return fp != null && fp.isInEnemyTerritory();
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /** Returns true when the player is standing in allied territory. */
    public static boolean isInAllyTerritory(Player player) {
        if (!available) return false;
        try {
            FPlayer fp = FPlayers.getInstance().getByPlayer(player);
            return fp != null && fp.isInAllyTerritory();
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /** Returns true when the player is standing in a warzone. */
    public static boolean isInWarzone(Player player) {
        if (!available) return false;
        try {
            Faction faction = Board.getInstance().getFactionAt(new FLocation(player));
            return faction != null && faction.isWarZone();
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /** Returns true when the player is standing in a safezone. */
    public static boolean isInSafezone(Player player) {
        if (!available) return false;
        try {
            Faction faction = Board.getInstance().getFactionAt(new FLocation(player));
            return faction != null && faction.isSafeZone();
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Faction info
    // -------------------------------------------------------------------------

    /** Returns true when the player belongs to any faction. */
    public static boolean hasFaction(Player player) {
        if (!available) return false;
        try {
            FPlayer fp = FPlayers.getInstance().getByPlayer(player);
            return fp != null && fp.hasFaction();
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Returns the player's faction tag (display name), or null if they have none
     * or Factions is unavailable.
     */
    public static String getFactionName(Player player) {
        if (!available) return null;
        try {
            FPlayer fp = FPlayers.getInstance().getByPlayer(player);
            if (fp == null || !fp.hasFaction()) return null;
            return fp.getFaction().getTag();
        } catch (Exception | NoClassDefFoundError e) {
            return null;
        }
    }

    /**
     * Returns the player's faction role name (RECRUIT, NORMAL, MODERATOR, COLEADER, LEADER, etc.),
     * or null if they have none or Factions is unavailable.
     */
    public static String getFactionRole(Player player) {
        if (!available) return null;
        try {
            FPlayer fp = FPlayers.getInstance().getByPlayer(player);
            if (fp == null || !fp.hasFaction()) return null;
            return fp.getRole().name();
        } catch (Exception | NoClassDefFoundError e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Targeting helpers
    // -------------------------------------------------------------------------

    /**
     * Returns nearby players (within {@code radius} blocks) who are allies
     * (MEMBER or ALLY relation) of {@code player}. The player themselves is excluded.
     */
    public static List<Player> getNearbyAllyPlayers(Player player, double radius) {
        List<Player> result = new ArrayList<>();
        if (!available) return result;
        try {
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (!(entity instanceof Player nearby)) continue;
                if (nearby.equals(player)) continue;
                if (nearby.getLocation().distance(player.getLocation()) > radius) continue;
                try {
                    Relation rel = getRelationEnum(player, nearby);
                    if (rel != null && (rel.isMember() || rel.isAlly())) {
                        result.add(nearby);
                    }
                } catch (Exception | NoClassDefFoundError ignored) {
                }
            }
        } catch (Exception | NoClassDefFoundError e) {
            // return whatever we gathered
        }
        return result;
    }

    /**
     * Returns nearby players (within {@code radius} blocks) who are NOT allies
     * (i.e. enemy, truce, neutral, or factionless) of {@code player}.
     * The player themselves is excluded.
     */
    public static List<Player> getNearbyEnemyPlayers(Player player, double radius) {
        List<Player> result = new ArrayList<>();
        if (!available) return result;
        try {
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (!(entity instanceof Player nearby)) continue;
                if (nearby.equals(player)) continue;
                if (nearby.getLocation().distance(player.getLocation()) > radius) continue;
                try {
                    Relation rel = getRelationEnum(player, nearby);
                    // null relation means no FPlayer record (factionless) → treat as non-ally
                    if (rel == null || (!rel.isMember() && !rel.isAlly())) {
                        result.add(nearby);
                    }
                } catch (Exception | NoClassDefFoundError ignored) {
                    result.add(nearby); // safe default: treat unknown as non-ally
                }
            }
        } catch (Exception | NoClassDefFoundError e) {
            // return whatever we gathered
        }
        return result;
    }

    /**
     * Returns nearby players (within {@code radius} blocks) who are members of
     * the same faction as {@code player} (MEMBER relation only, not allies).
     * The player themselves is excluded.
     */
    public static List<Player> getNearbyFactionMembers(Player player, double radius) {
        List<Player> result = new ArrayList<>();
        if (!available) return result;
        try {
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (!(entity instanceof Player nearby)) continue;
                if (nearby.equals(player)) continue;
                if (nearby.getLocation().distance(player.getLocation()) > radius) continue;
                try {
                    Relation rel = getRelationEnum(player, nearby);
                    if (rel != null && rel.isMember()) {
                        result.add(nearby);
                    }
                } catch (Exception | NoClassDefFoundError ignored) {
                }
            }
        } catch (Exception | NoClassDefFoundError e) {
            // return whatever we gathered
        }
        return result;
    }
}
