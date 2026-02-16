package com.miracle.arcanesigils.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Disables player collision globally using a scoreboard Team with COLLISION_RULE set to NEVER.
 * All players are added to the same team on the main scoreboard.
 * Collision is evaluated server-side using the main scoreboard regardless of player display scoreboards.
 */
public class CollisionDisabler implements Listener {

    private static final String TEAM_NAME = "as_nocollide";
    private Team noCollisionTeam;

    public CollisionDisabler() {
        setupTeam();
    }

    private void setupTeam() {
        Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        noCollisionTeam = mainScoreboard.getTeam(TEAM_NAME);

        if (noCollisionTeam == null) {
            noCollisionTeam = mainScoreboard.registerNewTeam(TEAM_NAME);
        }
        noCollisionTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        addToTeam(event.getPlayer());
    }

    private void addToTeam(Player player) {
        if (noCollisionTeam != null && !noCollisionTeam.hasEntry(player.getName())) {
            noCollisionTeam.addEntry(player.getName());
        }
    }

    /**
     * Add all currently online players to the no-collision team
     */
    public void disableForAll() {
        if (noCollisionTeam == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!noCollisionTeam.hasEntry(player.getName())) {
                noCollisionTeam.addEntry(player.getName());
            }
        }
    }

    /**
     * Clean up on shutdown
     */
    public void shutdown() {
        // Team persists in scoreboard, no cleanup needed
    }
}
