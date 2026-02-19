package com.miracle.arcanesigils.effects.impl;
import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

public class BleedingEffect extends AbstractEffect {
    public BleedingEffect() { super("BLEEDING", "DOT"); }
    @Override public boolean execute(EffectContext c) {
        double dmg = c.getParams() != null ? c.getParams().getValue() : 1;
        int dur = c.getParams() != null ? c.getParams().getDuration() : 5;
        LivingEntity t = getTarget(c);
        if (t == null) return false;
        new BukkitRunnable() { int r = dur; public void run() { if (r-- <= 0 || t.isDead()) { cancel(); return; } t.damage(dmg); }}.runTaskTimer(ArmorSetsPlugin.getInstance(), 0L, 20L);
        return true;
    }
}
