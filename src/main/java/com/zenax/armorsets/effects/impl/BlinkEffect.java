package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class BlinkEffect extends AbstractEffect {
    public BlinkEffect() { super("BLINK", "Short teleport"); }
    @Override public boolean execute(EffectContext c) {
        double d = Math.min(c.getParams() != null ? c.getParams().getValue() : 5, 50);
        Player p = c.getPlayer(); Location s = p.getLocation();
        Location t = s.clone().add(s.getDirection().normalize().multiply(d));
        Location safe = findSafe(t); if (safe == null) safe = findSafe(s.clone().add(s.getDirection().normalize().multiply(d/2)));
        if (safe != null) { safe.setYaw(s.getYaw()); safe.setPitch(s.getPitch()); s.getWorld().spawnParticle(Particle.PORTAL, s, 30); p.teleport(safe); safe.getWorld().spawnParticle(Particle.PORTAL, safe, 30); return true; }
        return false;
    }
    private Location findSafe(Location l) { for (int y = 0; y <= 5; y++) { Location c = l.clone().add(0,y,0); if (isSafe(c)) return c; if (y > 0) { c = l.clone().add(0,-y,0); if (isSafe(c) && c.getY() > c.getWorld().getMinHeight()) return c; } } return null; }
    private boolean isSafe(Location l) { Block f = l.getBlock(); return f.isPassable() && f.getRelative(0,1,0).isPassable() && f.getRelative(0,-1,0).getType().isSolid(); }
}
