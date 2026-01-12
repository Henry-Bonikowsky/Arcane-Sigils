package com.miracle.arcanesigils.combat;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Configuration for the 1.8 Legacy Combat system.
 * All values are live-reloadable and can be modified in-game.
 */
public class LegacyCombatConfig {

    private final ArmorSetsPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    // Master toggle
    private boolean enabled = true;

    // Attack Cooldown settings
    private boolean attackCooldownEnabled = true;
    private double attackSpeed = 1024.0;

    // Sword Blocking settings
    private boolean swordBlockingEnabled = true;
    private double blockDamageReduction = 50.0;
    private int consumeDuration = 72000;
    private double slowdownMultiplier = 0.2;

    // Knockback settings (Kohi defaults)
    private boolean knockbackEnabled = true;
    private double kbHorizontalBase = 0.35;
    private double kbVerticalBase = 0.35;
    private double kbVerticalCap = 0.4;
    private double kbExtraHorizontal = 0.425;
    private double kbExtraVertical = 0.077;
    private int damageImmunityTicks = 10;


    // Regeneration settings
    private boolean regenerationEnabled = true;
    private double regenAmount = 1.0;
    private int regenIntervalTicks = 80;
    private int regenMinHunger = 18;
    private boolean disableSaturationBoost = true;

    // Hitbox settings
    private boolean hitboxEnabled = true;
    private double reachExtension = 0.1;

    // Sweep Attack settings
    private boolean sweepAttackEnabled = true;

    // Critical Hit settings
    private boolean criticalHitsEnabled = true;
    private double critDamageMultiplier = 1.5;
    private double critFallingThreshold = -0.0784;

    // Fishing Rod settings
    private boolean fishingRodEnabled = true;
    private double rodHitDamage = 1.0;
    private double rodKnockbackForce = 0.4;
    private double rodKnockbackVertical = 0.2;
    private boolean rodTrickingEnabled = true;
    private int rodTrickWindowMs = 100;
    private double rodTrickMultiplier = 2.0;

    // Tool Damage settings
    private boolean toolDamageEnabled = true;
    private double axeDamageReduction = 3.0;

    // Golden Apple settings
    private boolean goldenAppleEnabled = true;
    private double eatTimeSeconds = 1.0;

    // Attack Indicator settings
    private boolean attackIndicatorEnabled = true;

    // Potions settings
    private boolean potionsEnabled = true;
    private double potionThrowVelocity = 0.5;  // Throw velocity multiplier (1.0 = vanilla)
    private double splashRadius = 4.0;         // Splash potion radius in blocks

    // Projectile KB settings
    private boolean projectileKbEnabled = true;
    private double snowballKb = 0.3;
    private double snowballKbVertical = 0.15;
    private double eggKb = 0.25;
    private double eggKbVertical = 0.1;

    // CPS Limit settings
    private boolean cpsLimitEnabled = true;
    private int maxCps = 12;

    public LegacyCombatConfig(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        configFile = new File(plugin.getDataFolder(), "combat.yml");
        if (!configFile.exists()) {
            plugin.saveResource("combat.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        loadValues();
    }

    private void loadValues() {
        ConfigurationSection root = config.getConfigurationSection("legacy-combat");
        if (root == null) {
            plugin.getLogger().warning("combat.yml missing 'legacy-combat' section, using defaults");
            return;
        }

        enabled = root.getBoolean("enabled", true);

        // Attack Cooldown
        ConfigurationSection ac = root.getConfigurationSection("attack-cooldown");
        if (ac != null) {
            attackCooldownEnabled = ac.getBoolean("enabled", true);
            attackSpeed = ac.getDouble("attack-speed", 1024.0);
        }

        // Sword Blocking
        ConfigurationSection sb = root.getConfigurationSection("sword-blocking");
        if (sb != null) {
            swordBlockingEnabled = sb.getBoolean("enabled", true);
            blockDamageReduction = sb.getDouble("damage-reduction", 50.0);
            consumeDuration = sb.getInt("consume-duration", 72000);
            slowdownMultiplier = sb.getDouble("slowdown-multiplier", 0.2);
        }

        // Knockback
        ConfigurationSection kb = root.getConfigurationSection("knockback");
        if (kb != null) {
            knockbackEnabled = kb.getBoolean("enabled", true);
            kbHorizontalBase = kb.getDouble("horizontal-base", 0.35);
            kbVerticalBase = kb.getDouble("vertical-base", 0.35);
            kbVerticalCap = kb.getDouble("vertical-cap", 0.4);
            kbExtraHorizontal = kb.getDouble("extra-horizontal", 0.425);
            kbExtraVertical = kb.getDouble("extra-vertical", 0.077);
        }

        // Regeneration
        ConfigurationSection regen = root.getConfigurationSection("regeneration");
        if (regen != null) {
            regenerationEnabled = regen.getBoolean("enabled", true);
            regenAmount = regen.getDouble("regen-amount", 1.0);
            regenIntervalTicks = regen.getInt("regen-interval-ticks", 80);
            regenMinHunger = regen.getInt("min-hunger", 18);
            disableSaturationBoost = regen.getBoolean("disable-saturation-boost", true);
        }

        // Hitbox
        ConfigurationSection hb = root.getConfigurationSection("hitbox");
        if (hb != null) {
            hitboxEnabled = hb.getBoolean("enabled", true);
            reachExtension = hb.getDouble("reach-extension", 0.1);
        }

        // Sweep Attack
        ConfigurationSection sweep = root.getConfigurationSection("sweep-attack");
        if (sweep != null) {
            sweepAttackEnabled = sweep.getBoolean("enabled", true);
        }

        // Critical Hits
        ConfigurationSection crit = root.getConfigurationSection("critical-hits");
        if (crit != null) {
            criticalHitsEnabled = crit.getBoolean("enabled", true);
            critDamageMultiplier = crit.getDouble("damage-multiplier", 1.5);
            critFallingThreshold = crit.getDouble("falling-threshold", -0.0784);
        }

        // Fishing Rod
        ConfigurationSection rod = root.getConfigurationSection("fishing-rod");
        if (rod != null) {
            fishingRodEnabled = rod.getBoolean("enabled", true);
            rodHitDamage = rod.getDouble("hit-damage", 1.0);
            rodKnockbackForce = rod.getDouble("knockback-force", 0.4);
            rodKnockbackVertical = rod.getDouble("knockback-vertical", 0.2);
            rodTrickingEnabled = rod.getBoolean("rod-tricking", true);
            rodTrickWindowMs = rod.getInt("rod-trick-window-ms", 100);
            rodTrickMultiplier = rod.getDouble("rod-trick-multiplier", 2.0);
        }

        // Tool Damage
        ConfigurationSection tool = root.getConfigurationSection("tool-damage");
        if (tool != null) {
            toolDamageEnabled = tool.getBoolean("enabled", true);
            axeDamageReduction = tool.getDouble("axe-damage-reduction", 3.0);
        }

        // Golden Apple
        ConfigurationSection gapple = root.getConfigurationSection("golden-apple");
        if (gapple != null) {
            goldenAppleEnabled = gapple.getBoolean("enabled", true);
            eatTimeSeconds = gapple.getDouble("eat-time-seconds", 1.0);
        }

        // Attack Indicator
        ConfigurationSection indicator = root.getConfigurationSection("attack-indicator");
        if (indicator != null) {
            attackIndicatorEnabled = indicator.getBoolean("enabled", true);
        }

        // Potions
        ConfigurationSection pots = root.getConfigurationSection("potions");
        if (pots != null) {
            potionsEnabled = pots.getBoolean("enabled", true);
            potionThrowVelocity = pots.getDouble("throw-velocity", 0.5);
            splashRadius = pots.getDouble("splash-radius", 4.0);
        }

        // Projectile KB
        ConfigurationSection projKb = root.getConfigurationSection("projectile-kb");
        if (projKb != null) {
            projectileKbEnabled = projKb.getBoolean("enabled", true);
            snowballKb = projKb.getDouble("snowball-knockback", 0.3);
            snowballKbVertical = projKb.getDouble("snowball-vertical", 0.15);
            eggKb = projKb.getDouble("egg-knockback", 0.25);
            eggKbVertical = projKb.getDouble("egg-vertical", 0.1);
        }

        // CPS Limit
        ConfigurationSection cps = root.getConfigurationSection("cps-limit");
        if (cps != null) {
            cpsLimitEnabled = cps.getBoolean("enabled", true);
            maxCps = cps.getInt("max-cps", 12);
        }
    }

    public void save() {
        ConfigurationSection root = config.createSection("legacy-combat");
        root.set("enabled", enabled);

        // Attack Cooldown
        ConfigurationSection ac = root.createSection("attack-cooldown");
        ac.set("enabled", attackCooldownEnabled);
        ac.set("attack-speed", attackSpeed);

        // Sword Blocking
        ConfigurationSection sb = root.createSection("sword-blocking");
        sb.set("enabled", swordBlockingEnabled);
        sb.set("damage-reduction", blockDamageReduction);
        sb.set("consume-duration", consumeDuration);
        sb.set("slowdown-multiplier", slowdownMultiplier);

        // Knockback
        ConfigurationSection kb = root.createSection("knockback");
        kb.set("enabled", knockbackEnabled);
        kb.set("horizontal-base", kbHorizontalBase);
        kb.set("vertical-base", kbVerticalBase);
        kb.set("vertical-cap", kbVerticalCap);
        kb.set("extra-horizontal", kbExtraHorizontal);
        kb.set("extra-vertical", kbExtraVertical);

        // Regeneration
        ConfigurationSection regen = root.createSection("regeneration");
        regen.set("enabled", regenerationEnabled);
        regen.set("regen-amount", regenAmount);
        regen.set("regen-interval-ticks", regenIntervalTicks);
        regen.set("min-hunger", regenMinHunger);
        regen.set("disable-saturation-boost", disableSaturationBoost);

        // Hitbox
        ConfigurationSection hb = root.createSection("hitbox");
        hb.set("enabled", hitboxEnabled);
        hb.set("reach-extension", reachExtension);

        // Sweep Attack
        ConfigurationSection sweep = root.createSection("sweep-attack");
        sweep.set("enabled", sweepAttackEnabled);

        // Critical Hits
        ConfigurationSection crit = root.createSection("critical-hits");
        crit.set("enabled", criticalHitsEnabled);
        crit.set("damage-multiplier", critDamageMultiplier);
        crit.set("falling-threshold", critFallingThreshold);

        // Fishing Rod
        ConfigurationSection rod = root.createSection("fishing-rod");
        rod.set("enabled", fishingRodEnabled);
        rod.set("knockback-force", rodKnockbackForce);
        rod.set("knockback-vertical", rodKnockbackVertical);
        rod.set("rod-tricking", rodTrickingEnabled);
        rod.set("rod-trick-window-ms", rodTrickWindowMs);
        rod.set("rod-trick-multiplier", rodTrickMultiplier);

        // Tool Damage
        ConfigurationSection tool = root.createSection("tool-damage");
        tool.set("enabled", toolDamageEnabled);
        tool.set("axe-damage-reduction", axeDamageReduction);

        // Golden Apple
        ConfigurationSection gapple = root.createSection("golden-apple");
        gapple.set("enabled", goldenAppleEnabled);
        gapple.set("eat-time-seconds", eatTimeSeconds);

        // Attack Indicator
        ConfigurationSection indicator = root.createSection("attack-indicator");
        indicator.set("enabled", attackIndicatorEnabled);

        // Potions
        ConfigurationSection pots = root.createSection("potions");
        pots.set("enabled", potionsEnabled);
        pots.set("throw-velocity", potionThrowVelocity);
        pots.set("splash-radius", splashRadius);

        // Projectile KB
        ConfigurationSection projKb = root.createSection("projectile-kb");
        projKb.set("enabled", projectileKbEnabled);
        projKb.set("snowball-knockback", snowballKb);
        projKb.set("snowball-vertical", snowballKbVertical);
        projKb.set("egg-knockback", eggKb);
        projKb.set("egg-vertical", eggKbVertical);

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save combat.yml", e);
        }
    }

    // Helper for module enabled checks
    public boolean isModuleEnabled(String moduleId) {
        if (!enabled) return false;
        return switch (moduleId) {
            case "attack-cooldown" -> attackCooldownEnabled;
            case "sword-blocking" -> swordBlockingEnabled;
            case "knockback" -> knockbackEnabled;
            case "regeneration" -> regenerationEnabled;
            case "hitbox" -> hitboxEnabled;
            case "sweep-attack" -> sweepAttackEnabled;
            case "critical-hits" -> criticalHitsEnabled;
            case "fishing-rod" -> fishingRodEnabled;
            case "tool-damage" -> toolDamageEnabled;
            case "golden-apple" -> goldenAppleEnabled;
            case "attack-indicator" -> attackIndicatorEnabled;
            case "potions" -> potionsEnabled;
            case "projectile-kb" -> projectileKbEnabled;
            case "cps-limit" -> cpsLimitEnabled;
            default -> false;
        };
    }

    public void setModuleEnabled(String moduleId, boolean value) {
        switch (moduleId) {
            case "attack-cooldown" -> attackCooldownEnabled = value;
            case "sword-blocking" -> swordBlockingEnabled = value;
            case "knockback" -> knockbackEnabled = value;
            case "regeneration" -> regenerationEnabled = value;
            case "hitbox" -> hitboxEnabled = value;
            case "sweep-attack" -> sweepAttackEnabled = value;
            case "critical-hits" -> criticalHitsEnabled = value;
            case "fishing-rod" -> fishingRodEnabled = value;
            case "tool-damage" -> toolDamageEnabled = value;
            case "golden-apple" -> goldenAppleEnabled = value;
            case "attack-indicator" -> attackIndicatorEnabled = value;
            case "potions" -> potionsEnabled = value;
            case "projectile-kb" -> projectileKbEnabled = value;
            case "cps-limit" -> cpsLimitEnabled = value;
        }
    }

    // Getters and setters for all values
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    // Attack Cooldown
    public boolean isAttackCooldownEnabled() { return attackCooldownEnabled; }
    public void setAttackCooldownEnabled(boolean v) { this.attackCooldownEnabled = v; }
    public double getAttackSpeed() { return attackSpeed; }
    public void setAttackSpeed(double v) { this.attackSpeed = v; }

    // Sword Blocking
    public boolean isSwordBlockingEnabled() { return swordBlockingEnabled; }
    public void setSwordBlockingEnabled(boolean v) { this.swordBlockingEnabled = v; }
    public double getBlockDamageReduction() { return blockDamageReduction; }
    public void setBlockDamageReduction(double v) { this.blockDamageReduction = v; }
    public int getConsumeDuration() { return consumeDuration; }
    public void setConsumeDuration(int v) { this.consumeDuration = v; }
    public double getSlowdownMultiplier() { return slowdownMultiplier; }
    public void setSlowdownMultiplier(double v) { this.slowdownMultiplier = v; }

    // Knockback
    public boolean isKnockbackEnabled() { return knockbackEnabled; }
    public void setKnockbackEnabled(boolean v) { this.knockbackEnabled = v; }
    public double getKbHorizontalBase() { return kbHorizontalBase; }
    public void setKbHorizontalBase(double v) { this.kbHorizontalBase = v; }
    public double getKbVerticalBase() { return kbVerticalBase; }
    public void setKbVerticalBase(double v) { this.kbVerticalBase = v; }
    public double getKbVerticalCap() { return kbVerticalCap; }
    public void setKbVerticalCap(double v) { this.kbVerticalCap = v; }

    // Regeneration
    public boolean isRegenerationEnabled() { return regenerationEnabled; }
    public void setRegenerationEnabled(boolean v) { this.regenerationEnabled = v; }
    public double getRegenAmount() { return regenAmount; }
    public void setRegenAmount(double v) { this.regenAmount = v; }
    public int getRegenIntervalTicks() { return regenIntervalTicks; }
    public void setRegenIntervalTicks(int v) { this.regenIntervalTicks = v; }
    public int getRegenMinHunger() { return regenMinHunger; }
    public void setRegenMinHunger(int v) { this.regenMinHunger = v; }
    public boolean isDisableSaturationBoost() { return disableSaturationBoost; }
    public void setDisableSaturationBoost(boolean v) { this.disableSaturationBoost = v; }

    // Hitbox
    public boolean isHitboxEnabled() { return hitboxEnabled; }
    public void setHitboxEnabled(boolean v) { this.hitboxEnabled = v; }
    public double getReachExtension() { return reachExtension; }
    public void setReachExtension(double v) { this.reachExtension = v; }

    // Sweep Attack
    public boolean isSweepAttackEnabled() { return sweepAttackEnabled; }
    public void setSweepAttackEnabled(boolean v) { this.sweepAttackEnabled = v; }

    // Critical Hits
    public boolean isCriticalHitsEnabled() { return criticalHitsEnabled; }
    public void setCriticalHitsEnabled(boolean v) { this.criticalHitsEnabled = v; }
    public double getCritDamageMultiplier() { return critDamageMultiplier; }
    public void setCritDamageMultiplier(double v) { this.critDamageMultiplier = v; }
    public double getCritFallingThreshold() { return critFallingThreshold; }
    public void setCritFallingThreshold(double v) { this.critFallingThreshold = v; }

    // Fishing Rod
    public boolean isFishingRodEnabled() { return fishingRodEnabled; }
    public void setFishingRodEnabled(boolean v) { this.fishingRodEnabled = v; }
    public double getRodHitDamage() { return rodHitDamage; }
    public void setRodHitDamage(double v) { this.rodHitDamage = v; }
    public double getRodKnockbackForce() { return rodKnockbackForce; }
    public void setRodKnockbackForce(double v) { this.rodKnockbackForce = v; }
    public double getRodKnockbackVertical() { return rodKnockbackVertical; }
    public void setRodKnockbackVertical(double v) { this.rodKnockbackVertical = v; }
    public boolean isRodTrickingEnabled() { return rodTrickingEnabled; }
    public void setRodTrickingEnabled(boolean v) { this.rodTrickingEnabled = v; }
    public int getRodTrickWindowMs() { return rodTrickWindowMs; }
    public void setRodTrickWindowMs(int v) { this.rodTrickWindowMs = v; }
    public double getRodTrickMultiplier() { return rodTrickMultiplier; }
    public void setRodTrickMultiplier(double v) { this.rodTrickMultiplier = v; }

    // Tool Damage
    public boolean isToolDamageEnabled() { return toolDamageEnabled; }
    public void setToolDamageEnabled(boolean v) { this.toolDamageEnabled = v; }
    public double getAxeDamageReduction() { return axeDamageReduction; }
    public void setAxeDamageReduction(double v) { this.axeDamageReduction = v; }

    // Golden Apple
    public boolean isGoldenAppleEnabled() { return goldenAppleEnabled; }
    public void setGoldenAppleEnabled(boolean v) { this.goldenAppleEnabled = v; }
    public double getEatTimeSeconds() { return eatTimeSeconds; }
    public void setEatTimeSeconds(double v) { this.eatTimeSeconds = v; }

    // Attack Indicator
    public boolean isAttackIndicatorEnabled() { return attackIndicatorEnabled; }
    public void setAttackIndicatorEnabled(boolean v) { this.attackIndicatorEnabled = v; }

    // Potions
    public boolean isPotionsEnabled() { return potionsEnabled; }
    public void setPotionsEnabled(boolean v) { this.potionsEnabled = v; }
    public double getPotionThrowVelocity() { return potionThrowVelocity; }
    public void setPotionThrowVelocity(double v) { this.potionThrowVelocity = v; }
    public double getSplashRadius() { return splashRadius; }
    public void setSplashRadius(double v) { this.splashRadius = v; }

    // Projectile KB
    public boolean isProjectileKbEnabled() { return projectileKbEnabled; }
    public void setProjectileKbEnabled(boolean v) { this.projectileKbEnabled = v; }
    public double getSnowballKb() { return snowballKb; }
    public void setSnowballKb(double v) { this.snowballKb = v; }
    public double getSnowballKbVertical() { return snowballKbVertical; }
    public void setSnowballKbVertical(double v) { this.snowballKbVertical = v; }
    public double getEggKb() { return eggKb; }
    public void setEggKb(double v) { this.eggKb = v; }
    public double getEggKbVertical() { return eggKbVertical; }
    public void setEggKbVertical(double v) { this.eggKbVertical = v; }

    // CPS Limit
    public boolean isCpsLimitEnabled() { return cpsLimitEnabled; }
    public void setCpsLimitEnabled(boolean v) { this.cpsLimitEnabled = v; }
    public int getMaxCps() { return maxCps; }
    public void setMaxCps(int v) { this.maxCps = v; }

    // Additional KB getters/setters
    public double getKbExtraHorizontal() { return kbExtraHorizontal; }
    public void setKbExtraHorizontal(double v) { this.kbExtraHorizontal = v; }
    public double getKbExtraVertical() { return kbExtraVertical; }
    public void setKbExtraVertical(double v) { this.kbExtraVertical = v; }


    // Aliases for compatibility
    public double getKbHorizontal() { return getKbHorizontalBase(); }
    public void setKbHorizontal(double v) { setKbHorizontalBase(v); }
    public double getKbVertical() { return getKbVerticalBase(); }
    public void setKbVertical(double v) { setKbVerticalBase(v); }
    public double getKbVerticalLimit() { return getKbVerticalCap(); }
    public void setKbVerticalLimit(double v) { setKbVerticalCap(v); }


    // Knockback getters/setters
    public int getDamageImmunityTicks() { return damageImmunityTicks; }
    public void setDamageImmunityTicks(int v) { this.damageImmunityTicks = v; }
}
