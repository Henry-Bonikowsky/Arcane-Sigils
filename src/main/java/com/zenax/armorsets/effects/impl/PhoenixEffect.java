package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.*;

public class PhoenixEffect extends AbstractEffect {
    public PhoenixEffect() { super("PHOENIX", "Totem"); }
    @Override public boolean execute(EffectContext c) { Player p = c.getPlayer(); c.cancelEvent(); double h = c.getParams() != null ? c.getParams().getValue() : 4; p.setHealth(Math.min(h, p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue())); p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1)); p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1)); p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0)); p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation(), 100); p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f); return true; }
}
