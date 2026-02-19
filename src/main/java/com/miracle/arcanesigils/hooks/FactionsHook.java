package com.miracle.arcanesigils.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Reflection-based Factions hook that works with any fork
 * (VulcanFactions, SaberFactions, FactionsUUID, etc.)
 * Uses the Factions plugin's own classloader and lazily discovers methods.
 */
public class FactionsHook {

    private static boolean available;
    private static Method getInstanceMethod;
    private static Method getByPlayerMethod;
    private static volatile Method getRelationToMethod;

    public static void init() {
        try {
            // Find VulcanFactions plugin registered with Bukkit
            Plugin factionsPlugin = Bukkit.getPluginManager().getPlugin("VulcanFactions");
            if (factionsPlugin == null) {
                available = false;
                return;
            }

            // Use the Factions plugin's classloader to load classes
            // This ensures we get VulcanFactions' classes, not any stale jars
            ClassLoader cl = factionsPlugin.getClass().getClassLoader();
            Class<?> fPlayersClass = cl.loadClass("com.massivecraft.factions.FPlayers");

            getInstanceMethod = fPlayersClass.getMethod("getInstance");
            Object instance = getInstanceMethod.invoke(null);
            getByPlayerMethod = instance.getClass().getMethod("getByPlayer", Player.class);

            // Don't try to load FPlayer interface here - it may trigger NoClassDefFoundError
            // getRelationTo will be discovered lazily on first actual use
            available = true;
        } catch (Exception | NoClassDefFoundError e) {
            available = false;
        }
        if (available) {
            Bukkit.getLogger().info("[ArcaneSigils] Factions detected (reflection) - faction targeting enabled.");
        } else {
            Bukkit.getLogger().info("[ArcaneSigils] Factions not detected - faction targeting disabled.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isAlly(Player player, Player target) {
        if (!available) return false;
        try {
            String relation = getRelationName(player, target);
            return "MEMBER".equals(relation) || "ALLY".equals(relation);
        } catch (Exception | NoClassDefFoundError e) {
            available = false;
            return false;
        }
    }

    public static boolean isEnemy(Player player, Player target) {
        if (!available) return false;
        try {
            String relation = getRelationName(player, target);
            return !"MEMBER".equals(relation) && !"ALLY".equals(relation);
        } catch (Exception | NoClassDefFoundError e) {
            available = false;
            return false;
        }
    }

    private static String getRelationName(Player player, Player target) throws Exception {
        Object instance = getInstanceMethod.invoke(null);
        Object fp = getByPlayerMethod.invoke(instance, player);
        Object ft = getByPlayerMethod.invoke(instance, target);

        // Lazily discover getRelationTo from the actual FPlayer implementation class
        if (getRelationToMethod == null) {
            synchronized (FactionsHook.class) {
                if (getRelationToMethod == null) {
                    for (Method m : fp.getClass().getMethods()) {
                        if (m.getName().equals("getRelationTo") && m.getParameterCount() == 1) {
                            getRelationToMethod = m;
                            break;
                        }
                    }
                    if (getRelationToMethod == null) {
                        throw new NoSuchMethodException("getRelationTo not found on " + fp.getClass().getName());
                    }
                }
            }
        }

        Object relation = getRelationToMethod.invoke(fp, ft);
        // All forks use an enum with name() - MEMBER, ALLY, TRUCE, NEUTRAL, ENEMY
        return ((Enum<?>) relation).name();
    }
}
