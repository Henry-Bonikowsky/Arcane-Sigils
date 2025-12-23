package com.zenax.dungeons.party;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all dungeon parties.
 * Note: With the new portal-based party system, parties are now formed dynamically
 * when players enter a portal within the 30-second window. This manager can still
 * be used for pre-formed parties if needed.
 */
public class PartyManager {
    private final Plugin plugin;
    private final Map<UUID, Party> parties;
    private final Map<UUID, UUID> playerToParty;
    private final int defaultMaxSize;
    private final long inviteTimeoutMillis;

    private static final int DEFAULT_MAX_SIZE = 4;
    private static final int DEFAULT_INVITE_TIMEOUT = 60; // seconds

    /**
     * Creates a new party manager with default settings.
     */
    public PartyManager() {
        this.plugin = com.zenax.dungeons.DungeonsAddon.getInstance().getPlugin();
        this.parties = new ConcurrentHashMap<>();
        this.playerToParty = new ConcurrentHashMap<>();
        this.defaultMaxSize = DEFAULT_MAX_SIZE;
        this.inviteTimeoutMillis = DEFAULT_INVITE_TIMEOUT * 1000L;
    }

    /**
     * Creates a new party manager.
     *
     * @param plugin The plugin instance
     * @param maxSize Default maximum party size
     * @param inviteTimeoutSeconds Invitation timeout in seconds
     */
    public PartyManager(Plugin plugin, int maxSize, int inviteTimeoutSeconds) {
        this.plugin = plugin;
        this.parties = new ConcurrentHashMap<>();
        this.playerToParty = new ConcurrentHashMap<>();
        this.defaultMaxSize = maxSize;
        this.inviteTimeoutMillis = inviteTimeoutSeconds * 1000L;
    }

    /**
     * Creates a new party with the given leader.
     *
     * @param leader The party leader
     * @return The created Party, or null if the player is already in a party
     */
    public Party createParty(Player leader) {
        if (isInParty(leader)) {
            return null;
        }

        Party party = new Party(leader, defaultMaxSize);
        parties.put(party.getPartyId(), party);
        playerToParty.put(leader.getUniqueId(), party.getPartyId());

        plugin.getLogger().info("Player " + leader.getName() + " created party " + party.getPartyId());
        return party;
    }

    /**
     * Creates a party from a set of players who entered through a portal.
     * The first player in the set becomes the leader.
     * This bypasses the normal invitation system since players implicitly joined by entering the portal.
     *
     * @param playerUuids Set of player UUIDs who entered the portal
     * @param maxSize Maximum party size (usually from dungeon settings)
     * @return The created Party, or null if creation failed
     */
    public Party createPartyFromPortal(Set<UUID> playerUuids, int maxSize) {
        if (playerUuids == null || playerUuids.isEmpty()) {
            return null;
        }

        // Find the first online player to be the leader
        Player leader = null;
        for (UUID uuid : playerUuids) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // Make sure they're not already in a party
                if (!isInParty(player)) {
                    leader = player;
                    break;
                }
            }
        }

        if (leader == null) {
            plugin.getLogger().warning("Cannot create portal party: no eligible leader found");
            return null;
        }

        // Create the party with the leader
        Party party = new Party(leader, maxSize);
        parties.put(party.getPartyId(), party);
        playerToParty.put(leader.getUniqueId(), party.getPartyId());

        // Add remaining players
        for (UUID uuid : playerUuids) {
            if (uuid.equals(leader.getUniqueId())) {
                continue; // Leader already added
            }

            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline() && !isInParty(player)) {
                party.addMember(player);
                playerToParty.put(uuid, party.getPartyId());
            }
        }

        plugin.getLogger().info("Created portal party " + party.getPartyId() +
                               " with " + party.getSize() + " members");
        return party;
    }

    /**
     * Disbands a party.
     *
     * @param partyId The party UUID
     * @return true if the party was disbanded successfully
     */
    public boolean disbandParty(UUID partyId) {
        Party party = parties.remove(partyId);
        if (party == null) {
            return false;
        }

        // Remove all player mappings
        for (UUID memberUuid : party.getMembers()) {
            playerToParty.remove(memberUuid);
        }

        plugin.getLogger().info("Party " + partyId + " has been disbanded");
        return true;
    }

    /**
     * Disbands a party.
     *
     * @param party The party to disband
     * @return true if the party was disbanded successfully
     */
    public boolean disbandParty(Party party) {
        return disbandParty(party.getPartyId());
    }

    /**
     * Gets a party by its UUID.
     *
     * @param partyId The party UUID
     * @return The Party, or null if not found
     */
    public Party getParty(UUID partyId) {
        return parties.get(partyId);
    }

    /**
     * Gets the party a player is in.
     *
     * @param player The player
     * @return The Party, or null if the player is not in a party
     */
    public Party getPlayerParty(Player player) {
        UUID partyId = playerToParty.get(player.getUniqueId());
        return partyId != null ? parties.get(partyId) : null;
    }

    /**
     * Gets the party a player UUID is in.
     *
     * @param playerUuid The player UUID
     * @return The Party, or null if the player is not in a party
     */
    public Party getPlayerParty(UUID playerUuid) {
        UUID partyId = playerToParty.get(playerUuid);
        return partyId != null ? parties.get(partyId) : null;
    }

    /**
     * Checks if a player is in a party.
     *
     * @param player The player
     * @return true if the player is in a party
     */
    public boolean isInParty(Player player) {
        return playerToParty.containsKey(player.getUniqueId());
    }

    /**
     * Checks if a player UUID is in a party.
     *
     * @param playerUuid The player UUID
     * @return true if the player is in a party
     */
    public boolean isInParty(UUID playerUuid) {
        return playerToParty.containsKey(playerUuid);
    }

    /**
     * Invites a player to a party.
     *
     * @param party The party
     * @param player The player to invite
     * @return true if the invitation was sent successfully
     */
    public boolean invitePlayer(Party party, Player player) {
        if (isInParty(player)) {
            return false;
        }

        if (party.invitePlayer(player)) {
            plugin.getLogger().info("Player " + player.getName() + " was invited to party " + party.getPartyId());
            return true;
        }

        return false;
    }

    /**
     * Accepts a party invitation.
     *
     * @param player The player accepting the invitation
     * @param party The party to join
     * @return true if the player joined successfully
     */
    public boolean acceptInvitation(Player player, Party party) {
        if (isInParty(player)) {
            return false;
        }

        if (!party.hasInvitation(player)) {
            return false;
        }

        if (party.addMember(player)) {
            playerToParty.put(player.getUniqueId(), party.getPartyId());
            plugin.getLogger().info("Player " + player.getName() + " joined party " + party.getPartyId());
            return true;
        }

        return false;
    }

    /**
     * Declines a party invitation.
     *
     * @param player The player declining the invitation
     * @param party The party invitation to decline
     * @return true if the invitation was declined successfully
     */
    public boolean declineInvitation(Player player, Party party) {
        return party.removeInvitation(player.getUniqueId());
    }

    /**
     * Removes a player from their party.
     *
     * @param player The player to remove
     * @return true if the player was removed successfully
     */
    public boolean leaveParty(Player player) {
        Party party = getPlayerParty(player);
        if (party == null) {
            return false;
        }

        playerToParty.remove(player.getUniqueId());

        // If player is the leader, disband the party
        if (party.isLeader(player)) {
            plugin.getLogger().info("Leader " + player.getName() + " left party " + party.getPartyId() + ", disbanding");
            disbandParty(party);
            return true;
        }

        party.removeMember(player);
        plugin.getLogger().info("Player " + player.getName() + " left party " + party.getPartyId());

        // Disband if empty
        if (party.isEmpty()) {
            disbandParty(party);
        }

        return true;
    }

    /**
     * Kicks a player from a party.
     *
     * @param party The party
     * @param playerUuid The UUID of the player to kick
     * @return true if the player was kicked successfully
     */
    public boolean kickPlayer(Party party, UUID playerUuid) {
        if (!party.isMember(playerUuid)) {
            return false;
        }

        if (party.removeMember(playerUuid)) {
            playerToParty.remove(playerUuid);
            plugin.getLogger().info("Player " + playerUuid + " was kicked from party " + party.getPartyId());

            // Disband if empty
            if (party.isEmpty()) {
                disbandParty(party);
            }

            return true;
        }

        return false;
    }

    /**
     * Finds a party that invited a specific player.
     *
     * @param player The player to check
     * @return The Party that invited the player, or null if none found
     */
    public Party findInvitingParty(Player player) {
        for (Party party : parties.values()) {
            if (party.hasInvitation(player)) {
                return party;
            }
        }
        return null;
    }

    /**
     * Gets all parties that invited a specific player.
     *
     * @param player The player to check
     * @return List of parties with pending invitations
     */
    public List<Party> findAllInvitingParties(Player player) {
        List<Party> invitingParties = new ArrayList<>();
        for (Party party : parties.values()) {
            if (party.hasInvitation(player)) {
                invitingParties.add(party);
            }
        }
        return invitingParties;
    }

    /**
     * Gets all active parties.
     *
     * @return Map of party UUIDs to Parties
     */
    public Map<UUID, Party> getAllParties() {
        return new HashMap<>(parties);
    }

    /**
     * Gets the number of active parties.
     *
     * @return The party count
     */
    public int getPartyCount() {
        return parties.size();
    }

    /**
     * Cleans up expired invitations for all parties.
     *
     * @return The total number of expired invitations removed
     */
    public int cleanupExpiredInvitations() {
        int totalRemoved = 0;
        for (Party party : parties.values()) {
            totalRemoved += party.removeExpiredInvitations(inviteTimeoutMillis);
        }

        if (totalRemoved > 0) {
            plugin.getLogger().info("Cleaned up " + totalRemoved + " expired party invitation(s)");
        }

        return totalRemoved;
    }

    /**
     * Cleans up empty parties.
     *
     * @return The number of parties disbanded
     */
    public int cleanupEmptyParties() {
        int cleaned = 0;
        Iterator<Map.Entry<UUID, Party>> iterator = parties.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Party> entry = iterator.next();
            Party party = entry.getValue();

            if (party.isEmpty()) {
                iterator.remove();
                cleaned++;
                plugin.getLogger().info("Cleaned up empty party: " + entry.getKey());
            }
        }

        return cleaned;
    }

    /**
     * Shuts down the party manager, disbanding all parties.
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down PartyManager...");

        for (UUID partyId : new HashSet<>(parties.keySet())) {
            disbandParty(partyId);
        }

        playerToParty.clear();

        plugin.getLogger().info("PartyManager shutdown complete");
    }
}
