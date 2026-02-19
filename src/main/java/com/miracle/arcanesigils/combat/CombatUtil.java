package com.miracle.arcanesigils.combat;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plugin damage modifier layer on top of vanilla damage.
 *
 * Vanilla handles: weapon base damage, sharpness, crits, armor, toughness, enchants.
 * We layer: amp/reduction from ModifierRegistry, soft cap, max damage per hit,
 * blocking DR, crit multiplier, and enchant scaling overrides.
 */
public final class CombatUtil {

    private final ModifierRegistry registry;

    // Config values
    private boolean fallDamageDisabled;
    private boolean softCapEnabled;
    private double softCapThreshold;
    private double softCapFalloff;
    private double maxDamagePerHit;
    private double blockingDamageReduction;
    private double critMultiplier;

    // Enchant scaling
    private boolean protectionEnabled;
    private boolean sharpnessEnabled;
    private boolean unbreakingEnabled;
    private final double[] protectionDR = new double[10];
    private final double[] sharpnessBonus = new double[10];
    private final double[] unbreakingChance = new double[10];

    // Durability (legacy flat values — kept for migration)
    private boolean durabilityEnabled;
    private final Map<String, Integer> durabilityValues = new HashMap<>();

    public CombatUtil(ArmorSetsPlugin plugin, ModifierRegistry registry) {
        this.registry = registry;
        loadConfig(plugin.getConfig());
    }

    public void loadConfig(FileConfiguration config) {
        fallDamageDisabled = config.getBoolean("combat.fall-damage-disabled", false);
        softCapEnabled = config.getBoolean("combat.soft-cap.enabled", true);
        softCapThreshold = config.getDouble("combat.soft-cap.threshold", 20.0);
        softCapFalloff = config.getDouble("combat.soft-cap.falloff", 0.5);
        maxDamagePerHit = config.getDouble("combat.max-damage-per-hit", 20.0);
        blockingDamageReduction = config.getDouble("combat.blocking-damage-reduction", 1.0);
        critMultiplier = config.getDouble("combat.crit-multiplier", 1.5);

        // Protection scaling
        protectionEnabled = config.getBoolean("combat.protection-scaling.enabled", false);
        for (int i = 0; i < 10; i++) {
            protectionDR[i] = config.getDouble("combat.protection-scaling." + (i + 1), (i + 1) * 4.0);
        }

        // Sharpness scaling
        sharpnessEnabled = config.getBoolean("combat.sharpness-scaling.enabled", false);
        for (int i = 0; i < 10; i++) {
            sharpnessBonus[i] = config.getDouble("combat.sharpness-scaling." + (i + 1), 0.5 * (i + 1) + 0.5);
        }

        // Unbreaking scaling — chance that a durability hit is ignored
        // Formula: 60% + 40% / (level + 1) chance to consume durability
        // So chance to IGNORE = 1 - (60% + 40% / (level + 1))
        unbreakingEnabled = config.getBoolean("combat.unbreaking-scaling.enabled", false);
        for (int i = 0; i < 10; i++) {
            double defaultChance = 1.0 - (0.6 + 0.4 / (i + 2));
            unbreakingChance[i] = config.getDouble("combat.unbreaking-scaling." + (i + 1),
                    Math.round(defaultChance * 10000.0) / 10000.0);
        }

        // Durability (legacy flat values)
        durabilityEnabled = config.getBoolean("combat.durability.enabled", false);
        durabilityValues.clear();
        durabilityValues.put("LEATHER", config.getInt("combat.durability.leather", 80));
        durabilityValues.put("CHAINMAIL", config.getInt("combat.durability.chainmail", 240));
        durabilityValues.put("IRON", config.getInt("combat.durability.iron", 240));
        durabilityValues.put("GOLD", config.getInt("combat.durability.gold", 112));
        durabilityValues.put("DIAMOND", config.getInt("combat.durability.diamond", 528));
        durabilityValues.put("NETHERITE", config.getInt("combat.durability.netherite", 592));
    }

    /**
     * Apply plugin damage modifiers on top of vanilla damage.
     */
    public double applyPluginModifiers(Player victim, double vanillaDamage) {
        return applyPluginModifiers(victim.getUniqueId(), vanillaDamage, victim);
    }

    /**
     * Apply plugin damage modifiers for any entity (no debug chat).
     */
    public double applyPluginModifiers(UUID victimId, double vanillaDamage) {
        return applyPluginModifiers(victimId, vanillaDamage, null);
    }

    private double applyPluginModifiers(UUID victimId, double vanillaDamage, Player debugTarget) {
        double damage = vanillaDamage;

        // 1. Damage amplification (increases damage taken)
        double ampMultiplier = registry.getMultiplier(victimId, ModifierType.DAMAGE_AMPLIFICATION);
        damage *= ampMultiplier;

        // 2. Damage reduction + Charge DR (combined, no cap — Max DR removed)
        double drMultiplier = registry.getMultiplier(victimId, ModifierType.DAMAGE_REDUCTION);
        double chargeDrMultiplier = registry.getMultiplier(victimId, ModifierType.CHARGE_DR);
        double combinedDR = drMultiplier * chargeDrMultiplier;
        damage *= combinedDR;

        // 3. Soft damage cap (diminishing returns above threshold)
        boolean softCapped = false;
        if (softCapEnabled && damage > softCapThreshold) {
            double excess = damage - softCapThreshold;
            damage = softCapThreshold + (excess * softCapFalloff);
            softCapped = true;
        }

        // 4. Hard cap: max damage per hit
        boolean hardCapped = damage > maxDamagePerHit;
        damage = Math.min(damage, maxDamagePerHit);

        // 5. Floor (never negative)
        damage = Math.max(0, damage);

        // 6. Always log modifier state
        boolean hasModifiers = ampMultiplier != 1.0 || drMultiplier != 1.0 || chargeDrMultiplier != 1.0;
        if (hasModifiers) {
            LogHelper.debug("[CombatUtil] MODIFIERS ACTIVE for %s: Amp=%.3f DR=%.3f ChgDR=%.3f -> %.2f => %.2f%s%s",
                    victimId, ampMultiplier, drMultiplier, chargeDrMultiplier,
                    vanillaDamage, damage,
                    softCapped ? " [SoftCap]" : "",
                    hardCapped ? " [HardCap]" : "");
        }

        // 7. Debug chat output
        if (LogHelper.isDebugEnabled() && debugTarget != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("§8[§cDMG§8] ");
            sb.append(String.format("§7Base: §f%.1f", vanillaDamage));
            if (ampMultiplier != 1.0) {
                sb.append(String.format(" §8-> §cAmp: §f%.2fx", ampMultiplier));
            }
            if (drMultiplier != 1.0 || chargeDrMultiplier != 1.0) {
                sb.append(String.format(" §8-> §9DR: §f%.4fx", combinedDR));
            }
            if (softCapped) sb.append(" §8-> §eSoftCap");
            if (hardCapped) sb.append(" §8-> §4HardCap");
            sb.append(String.format(" §8-> §fFinal: §e%.1f", damage));
            debugTarget.sendMessage(sb.toString());

            debugTarget.sendMessage(String.format(
                    "§8  Amp=§f%.3f §8DR=§f%.3f §8ChgDR=§f%.3f §8CombDR=§f%.3f",
                    ampMultiplier, drMultiplier, chargeDrMultiplier, combinedDR));
        }

        return damage;
    }

    // === Getters for GUI / other systems ===

    public boolean isFallDamageDisabled() { return fallDamageDisabled; }
    public boolean isSoftCapEnabled() { return softCapEnabled; }
    public double getSoftCapThreshold() { return softCapThreshold; }
    public double getSoftCapFalloff() { return softCapFalloff; }
    public double getMaxDamagePerHit() { return maxDamagePerHit; }
    public double getBlockingDamageReduction() { return blockingDamageReduction; }
    public double getCritMultiplier() { return critMultiplier; }

    // Enchant scaling getters
    public boolean isProtectionEnabled() { return protectionEnabled; }
    public boolean isSharpnessEnabled() { return sharpnessEnabled; }
    public boolean isUnbreakingEnabled() { return unbreakingEnabled; }

    /**
     * Get configured Protection DR% for a given level (1-10).
     */
    public double getProtectionDR(int level) {
        if (level < 1 || level > 10) return 0;
        return protectionDR[level - 1];
    }

    /**
     * Get configured Sharpness bonus damage for a given level (1-10).
     */
    public double getSharpnessBonus(int level) {
        if (level < 1 || level > 10) return 0;
        return sharpnessBonus[level - 1];
    }

    /**
     * Get configured Unbreaking ignore chance for a given level (1-10).
     * Returns the probability that a durability hit is ignored.
     */
    public double getUnbreakingIgnoreChance(int level) {
        if (level < 1 || level > 10) return 0;
        return unbreakingChance[level - 1];
    }

    // Durability getters (legacy)
    public boolean isDurabilityEnabled() { return durabilityEnabled; }

    public int getDurability(String material) {
        return durabilityValues.getOrDefault(material, -1);
    }

    public Map<String, Integer> getDurabilityValues() { return durabilityValues; }
}
