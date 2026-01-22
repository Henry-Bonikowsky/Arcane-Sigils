package com.miracle.arcanesigils.notifications;

public enum NotificationType {
    ABILITY_PROC("show_attacker"),
    COOLDOWN("show_cooldowns"),
    TIER_UP("show_tier_ups"),
    SET_BONUS("show_set_bonuses");

    private final String configKey;

    NotificationType(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }
}
