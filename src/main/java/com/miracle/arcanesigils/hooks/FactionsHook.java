package com.miracle.arcanesigils.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class FactionsHook {

    private static boolean available;

    public static void init() {
        available = Bukkit.getPluginManager().getPlugin("Factions") != null;
        if (available) {
            Bukkit.getLogger().info("[ArcaneSigils] Factions detected - faction targeting enabled.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isAlly(Player player, Player target) {
        if (!available) return false;
        try {
            com.massivecraft.factions.FPlayer fp = com.massivecraft.factions.FPlayers.getInstance().getByPlayer(player);
            com.massivecraft.factions.FPlayer ft = com.massivecraft.factions.FPlayers.getInstance().getByPlayer(target);
            com.massivecraft.factions.perms.Relation rel = fp.getRelationTo(ft);
            return rel == com.massivecraft.factions.perms.Relation.MEMBER
                || rel == com.massivecraft.factions.perms.Relation.ALLY;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isEnemy(Player player, Player target) {
        if (!available) return false;
        try {
            com.massivecraft.factions.FPlayer fp = com.massivecraft.factions.FPlayers.getInstance().getByPlayer(player);
            com.massivecraft.factions.FPlayer ft = com.massivecraft.factions.FPlayers.getInstance().getByPlayer(target);
            com.massivecraft.factions.perms.Relation rel = fp.getRelationTo(ft);
            return rel == com.massivecraft.factions.perms.Relation.ENEMY;
        } catch (Exception e) {
            return false;
        }
    }
}
