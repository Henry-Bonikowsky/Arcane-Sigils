package com.zenax.armorsets.sets;

import com.zenax.armorsets.events.TriggerType;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Represents a set synergy (full set bonus).
 */
public class SetSynergy {

    private final String id;
    private TriggerType trigger;
    private TriggerConfig triggerConfig;

    public SetSynergy(String id) {
        this.id = id;
        this.trigger = TriggerType.ATTACK;
        this.triggerConfig = new TriggerConfig();
    }

    public String getId() {
        return id;
    }

    public TriggerType getTrigger() {
        return trigger;
    }

    public void setTrigger(TriggerType trigger) {
        this.trigger = trigger;
    }

    public TriggerConfig getTriggerConfig() {
        return triggerConfig;
    }

    public void setTriggerConfig(TriggerConfig triggerConfig) {
        this.triggerConfig = triggerConfig;
    }

    /**
     * Load SetSynergy from configuration.
     */
    public static SetSynergy fromConfig(String id, ConfigurationSection section) {
        if (section == null) return null;

        SetSynergy synergy = new SetSynergy(id);

        String triggerStr = section.getString("trigger", "ATTACK");
        TriggerType trigger = TriggerType.fromConfigKey(triggerStr);
        synergy.setTrigger(trigger != null ? trigger : TriggerType.ATTACK);

        TriggerConfig config = new TriggerConfig();
        config.setChance(section.getDouble("chance", 100));
        config.setEffects(section.getStringList("effects"));
        config.setCooldown(section.getDouble("cooldown", 0));
        synergy.setTriggerConfig(config);

        return synergy;
    }
}
