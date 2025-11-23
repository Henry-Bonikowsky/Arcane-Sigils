package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class ImplantsEffect extends AbstractEffect {
    public ImplantsEffect() { super("IMPLANTS", "Cybernetic buffs"); }
    @Override public boolean execute(EffectContext c) { c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0)); c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, 0)); return true; }
}
