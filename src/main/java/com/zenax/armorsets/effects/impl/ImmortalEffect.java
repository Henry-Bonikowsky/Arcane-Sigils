package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.entity.Player;

public class ImmortalEffect extends AbstractEffect {
    public ImmortalEffect() { super("IMMORTAL", "Prevent death"); }
    @Override public boolean execute(EffectContext c) { Player p = c.getPlayer(); c.cancelEvent(); p.setHealth(2.0); p.setNoDamageTicks(40); return true; }
}
