package com.miracle.arcanesigils.gui.sigil;

import com.miracle.arcanesigils.core.Sigil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Encapsulates filter state for the sigil browser.
 * Handles AND logic for active filters and sorting.
 */
public class FilterState {

    private Set<String> activeRarities = new HashSet<>();
    private Set<String> activeSlotTypes = new HashSet<>();
    private Integer minTier = null;
    private Integer maxTier = null;
    private Set<String> activeCrates = new HashSet<>();
    private String sortMode = "NONE"; // NONE, ALPHABETICAL, RARITY, TIER
    private boolean reverseSortOrder = false; // false = ascending, true = descending

    /**
     * Apply all active filters to a list of sigils.
     * Returns filtered list (AND logic).
     */
    public List<Sigil> applyFilters(List<Sigil> sigils) {
        return sigils.stream()
            .filter(this::passesRarityFilter)
            .filter(this::passesSlotTypeFilter)
            .filter(this::passesTierFilter)
            .filter(this::passesCrateFilter)
            .collect(Collectors.toList());
    }

    /**
     * Apply sorting to a list of sigils.
     */
    public List<Sigil> applySorting(List<Sigil> sigils) {
        List<Sigil> sorted = new ArrayList<>(sigils);
        
        switch (sortMode) {
            case "ALPHABETICAL" -> {
                Comparator<Sigil> comp = Comparator.comparing(Sigil::getId);
                sorted.sort(reverseSortOrder ? comp.reversed() : comp);
            }
            case "RARITY" -> {
                Comparator<Sigil> comp = Comparator.comparingInt(this::getRarityPriority)
                    .thenComparing(Sigil::getId);
                sorted.sort(reverseSortOrder ? comp.reversed() : comp);
            }
            case "TIER" -> {
                Comparator<Sigil> comp = Comparator.comparingInt(Sigil::getMaxTier)
                    .thenComparing(Sigil::getId);
                // Note: TIER default is high-to-low, so reverse logic is inverted
                sorted.sort(reverseSortOrder ? comp : comp.reversed());
            }
            default -> {
                // NONE - keep insertion order
            }
        }
        
        return sorted;
    }

    /**
     * Apply filters and sorting in one pass.
     */
    public List<Sigil> applyFiltersAndSorting(List<Sigil> sigils) {
        List<Sigil> filtered = applyFilters(sigils);
        return applySorting(filtered);
    }

    /**
     * Reset all filters to default state.
     */
    public void reset() {
        activeRarities.clear();
        activeSlotTypes.clear();
        minTier = null;
        maxTier = null;
        activeCrates.clear();
        sortMode = "NONE";
        reverseSortOrder = false;
    }

    /**
     * Check if any filters are active.
     */
    public boolean isEmpty() {
        return activeRarities.isEmpty() 
            && activeSlotTypes.isEmpty() 
            && minTier == null 
            && maxTier == null 
            && activeCrates.isEmpty();
    }

    /**
     * Get all unique crate values from a list of sigils.
     * Returns stripped crate names (no MiniMessage formatting).
     */
    public static Set<String> getUniqueCrates(List<Sigil> sigils) {
        Set<String> crates = new HashSet<>();
        for (Sigil sigil : sigils) {
            String crate = sigil.getCrate();
            if (crate != null && !crate.isEmpty()) {
                crates.add(stripMiniMessage(crate));
            } else {
                crates.add("Standard/Uncrated");
            }
        }
        return crates;
    }

    /**
     * Strip MiniMessage formatting from a string.
     * Removes <gradient:...>text</gradient>, <color:...>text</color>, etc.
     */
    public static String stripMiniMessage(String text) {
        if (text == null) return null;
        // Remove all MiniMessage tags: <tag:...>content</tag> or <tag>content</tag>
        return text.replaceAll("<[^>]+>", "").trim();
    }

    // ============ FILTER CHECKS ============

    private boolean passesRarityFilter(Sigil sigil) {
        if (activeRarities.isEmpty()) return true;
        return activeRarities.contains(sigil.getRarity().toUpperCase());
    }

    private boolean passesSlotTypeFilter(Sigil sigil) {
        if (activeSlotTypes.isEmpty()) return true;
        
        // Check if sigil can socket into any of the active slot types
        for (String slotType : activeSlotTypes) {
            if (sigil.getSocketables().contains(slotType.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean passesTierFilter(Sigil sigil) {
        int tier = sigil.getMaxTier();
        if (minTier != null && tier < minTier) return false;
        if (maxTier != null && tier > maxTier) return false;
        return true;
    }

    private boolean passesCrateFilter(Sigil sigil) {
        if (activeCrates.isEmpty()) return true;
        
        String sigilCrate = sigil.getCrate();
        if (sigilCrate == null || sigilCrate.isEmpty()) {
            return activeCrates.contains("Standard/Uncrated");
        }
        
        String strippedCrate = stripMiniMessage(sigilCrate);
        return activeCrates.contains(strippedCrate);
    }

    // ============ HELPER METHODS ============

    private int getRarityPriority(Sigil sigil) {
        return switch (sigil.getRarity().toUpperCase()) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }

    // ============ GETTERS/SETTERS ============

    public Set<String> getActiveRarities() {
        return activeRarities;
    }

    public void setActiveRarities(Set<String> activeRarities) {
        this.activeRarities = activeRarities != null ? activeRarities : new HashSet<>();
    }

    public void toggleRarity(String rarity) {
        if (activeRarities.contains(rarity)) {
            activeRarities.remove(rarity);
        } else {
            activeRarities.add(rarity);
        }
    }

    public Set<String> getActiveSlotTypes() {
        return activeSlotTypes;
    }

    public void setActiveSlotTypes(Set<String> activeSlotTypes) {
        this.activeSlotTypes = activeSlotTypes != null ? activeSlotTypes : new HashSet<>();
    }

    public void toggleSlotType(String slotType) {
        if (activeSlotTypes.contains(slotType)) {
            activeSlotTypes.remove(slotType);
        } else {
            activeSlotTypes.add(slotType);
        }
    }

    public Integer getMinTier() {
        return minTier;
    }

    public void setMinTier(Integer minTier) {
        this.minTier = minTier;
    }

    public Integer getMaxTier() {
        return maxTier;
    }

    public void setMaxTier(Integer maxTier) {
        this.maxTier = maxTier;
    }

    public Set<String> getActiveCrates() {
        return activeCrates;
    }

    public void setActiveCrates(Set<String> activeCrates) {
        this.activeCrates = activeCrates != null ? activeCrates : new HashSet<>();
    }

    public void toggleCrate(String crate) {
        if (activeCrates.contains(crate)) {
            activeCrates.remove(crate);
        } else {
            activeCrates.add(crate);
        }
    }

    public String getSortMode() {
        return sortMode;
    }

    public void setSortMode(String sortMode) {
        this.sortMode = sortMode != null ? sortMode : "NONE";
    }

    public void cycleSortMode() {
        sortMode = switch (sortMode) {
            case "NONE" -> "ALPHABETICAL";
            case "ALPHABETICAL" -> "RARITY";
            case "RARITY" -> "TIER";
            case "TIER" -> "NONE";
            default -> "NONE";
        };
    }

    public boolean isReverseSortOrder() {
        return reverseSortOrder;
    }

    public void setReverseSortOrder(boolean reverseSortOrder) {
        this.reverseSortOrder = reverseSortOrder;
    }

    public void toggleReverseSortOrder() {
        this.reverseSortOrder = !this.reverseSortOrder;
    }
}
