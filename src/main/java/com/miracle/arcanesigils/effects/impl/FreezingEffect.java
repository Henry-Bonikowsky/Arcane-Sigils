package com.miracle.arcanesigils.effects.impl;
import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.*;

public class FreezingEffect extends AbstractEffect {
    public FreezingEffect() { super("FREEZING", "Freeze target"); }
    @Override public boolean execute(EffectContext c) {
        int d = (int)(c.getParams() != null ? c.getParams().getValue() : 5);
        LivingEntity t = getTarget(c); if (t == null) return false;
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, d*20, 2));
        t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, d*20, 1));
        t.setFreezeTicks(d*20);
        return true;
    }
}
