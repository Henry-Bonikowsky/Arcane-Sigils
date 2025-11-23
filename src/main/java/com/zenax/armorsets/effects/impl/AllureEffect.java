package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class AllureEffect extends AbstractEffect {
    public AllureEffect() { super("ALLURE", "Charm nearby"); }
    @Override public boolean execute(EffectContext c) { double r = c.getParams() != null ? c.getParams().getValue() : 5; getNearbyEntities(c, r).forEach(e -> { e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1)); e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0)); }); return true; }
}
