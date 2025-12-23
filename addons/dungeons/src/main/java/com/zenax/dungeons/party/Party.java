package com.zenax.dungeons.party;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a dungeon party.
 * A party is a group of players who can enter dungeons together.
 */
public class Party {
    private final UUID partyId;
    private final UUID leaderId;
    private final Set<UUID> members;
    private final Map<UUID, Long> invitations;
    private final long creationTime;
    private final int maxSize;

    /**
     * Creates a new party with the given leader.
     *
     * @param leader The party leader
     * @param maxSize Maximum number of party members
     */
    public Party(Player leader, int maxSize) {
        this.partyId = UUID.randomUUID();
        this.leaderId = leader.getUniqueId();
        this.members = ConcurrentHashMap.newKeySet();
        this.invitations = new ConcurrentHashMap<>();
        this.creationTime = System.currentTimeMillis();
        this.maxSize = maxSize;

        // Leader is automatically a member
        this.members.add(leader.getUniqueId());
    }

    /**
     * Gets the party's unique ID.
     *
     * @return The party ID
     */
    public UUID getPartyId() {
        return partyId;
    }

    /**
     * Gets the leader's UUID.
     *
     * @return The leader's UUID
     */
    public UUID getLeaderId() {
        return leaderId;
    }

    /**
     * Checks if a player is the party leader.
     *
     * @param playerUuid The player's UUID
     * @return true if the player is the leader
     */
    public boolean isLeader(UUID playerUuid) {
        return leaderId.equals(playerUuid);
    }

    /**
     * Checks if a player is the party leader.
     *
     * @param player The player
     * @return true if the player is the leader
     */
    public boolean isLeader(Player player) {
        return isLeader(player.getUniqueId());
    }

    /**
     * Gets all party member UUIDs.
     *
     * @return Set of member UUIDs
     */
    public Set<UUID> getMembers() {
        return new HashSet<>(members);
    }

    /**
     * Gets the number of members in the party.
     *
     * @return The member count
     */
    public int getSize() {
        return members.size();
    }

    /**
     * Gets the maximum party size.
     *
     * @return The maximum size
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Checks if the party is full.
     *
     * @return true if the party has reached max size
     */
    public boolean isFull() {
        return members.size() >= maxSize;
    }

    /**
     * Checks if a player is a member of the party.
     *
     * @param playerUuid The player's UUID
     * @return true if the player is a member
     */
    public boolean isMember(UUID playerUuid) {
        return members.contains(playerUuid);
    }

    /**
     * Checks if a player is a member of the party.
     *
     * @param player The player
     * @return true if the player is a member
     */
    public boolean isMember(Player player) {
        return isMember(player.getUniqueId());
    }

    /**
     * Adds a member to the party.
     *
     * @param playerUuid The player's UUID
     * @return true if the player was added successfully
     */
    public boolean addMember(UUID playerUuid) {
        if (isFull()) {
            return false;
        }

        if (members.contains(playerUuid)) {
            return false;
        }

        members.add(playerUuid);
        invitations.remove(playerUuid);
        return true;
    }

    /**
     * Adds a member to the party.
     *
     * @param player The player
     * @return true if the player was added successfully
     */
    public boolean addMember(Player player) {
        return addMember(player.getUniqueId());
    }

    /**
     * Removes a member from the party.
     *
     * @param playerUuid The player's UUID
     * @return true if the player was removed successfully
     */
    public boolean removeMember(UUID playerUuid) {
        // Cannot remove the leader this way
        if (isLeader(playerUuid)) {
            return false;
        }

        return members.remove(playerUuid);
    }

    /**
     * Removes a member from the party.
     *
     * @param player The player
     * @return true if the player was removed successfully
     */
    public boolean removeMember(Player player) {
        return removeMember(player.getUniqueId());
    }

    /**
     * Invites a player to the party.
     *
     * @param playerUuid The player's UUID
     * @return true if the invitation was sent successfully
     */
    public boolean invitePlayer(UUID playerUuid) {
        if (isFull()) {
            return false;
        }

        if (members.contains(playerUuid)) {
            return false;
        }

        invitations.put(playerUuid, System.currentTimeMillis());
        return true;
    }

    /**
     * Invites a player to the party.
     *
     * @param player The player
     * @return true if the invitation was sent successfully
     */
    public boolean invitePlayer(Player player) {
        return invitePlayer(player.getUniqueId());
    }

    /**
     * Checks if a player has a pending invitation.
     *
     * @param playerUuid The player's UUID
     * @return true if the player has a pending invitation
     */
    public boolean hasInvitation(UUID playerUuid) {
        return invitations.containsKey(playerUuid);
    }

    /**
     * Checks if a player has a pending invitation.
     *
     * @param player The player
     * @return true if the player has a pending invitation
     */
    public boolean hasInvitation(Player player) {
        return hasInvitation(player.getUniqueId());
    }

    /**
     * Removes an invitation.
     *
     * @param playerUuid The player's UUID
     * @return true if the invitation was removed
     */
    public boolean removeInvitation(UUID playerUuid) {
        return invitations.remove(playerUuid) != null;
    }

    /**
     * Gets all pending invitation UUIDs.
     *
     * @return Set of invited player UUIDs
     */
    public Set<UUID> getInvitations() {
        return new HashSet<>(invitations.keySet());
    }

    /**
     * Removes expired invitations.
     *
     * @param timeoutMillis The timeout in milliseconds
     * @return The number of expired invitations removed
     */
    public int removeExpiredInvitations(long timeoutMillis) {
        long currentTime = System.currentTimeMillis();
        int removed = 0;

        Iterator<Map.Entry<UUID, Long>> iterator = invitations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > timeoutMillis) {
                iterator.remove();
                removed++;
            }
        }

        return removed;
    }

    /**
     * Gets the party creation time.
     *
     * @return The creation timestamp in milliseconds
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Checks if the party is empty (no members).
     *
     * @return true if the party has no members
     */
    public boolean isEmpty() {
        return members.isEmpty();
    }

    /**
     * Checks if the party has only the leader.
     *
     * @return true if only the leader remains
     */
    public boolean isSolo() {
        return members.size() == 1 && members.contains(leaderId);
    }
}
