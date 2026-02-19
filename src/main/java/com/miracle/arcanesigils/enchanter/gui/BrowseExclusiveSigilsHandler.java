package com.miracle.arcanesigils.enchanter.gui;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.RomanNumerals;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Browse Exclusive Sigils - paginated list of exclusive sigils.
 * Layout: 3 rows (27 slots)
 */
public class BrowseExclusiveSigilsHandler extends AbstractHandler {

    private static final int ITEMS_PER_PAGE = 18;
    private static final int BACK_BUTTON_SLOT = 18;
    private static final int PREV_PAGE_SLOT = 19;
    private static final int PAGE_INDICATOR_SLOT = 22;
    private static final int NEXT_PAGE_SLOT = 26;

    public BrowseExclusiveSigilsHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        // Back button
        if (slot == BACK_BUTTON_SLOT) {
            navigateBack(player, session);
            return;
        }

        // Previous page
        if (slot == PREV_PAGE_SLOT) {
            int page = session.getInt("page", 1);
            if (page > 1) {
                session.put("page", page - 1);
                reopen(player, session);
                playSound(player, "page");
            }
            return;
        }

        // Next page
        if (slot == NEXT_PAGE_SLOT) {
            int page = session.getInt("page", 1);
            List<Sigil> sigils = getExclusiveSigils();
            int maxPage = (int) Math.ceil((double) sigils.size() / ITEMS_PER_PAGE);
            if (page < maxPage) {
                session.put("page", page + 1);
                reopen(player, session);
                playSound(player, "page");
            }
            return;
        }

        // Clicked on a sigil
        if (slot < 18) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() != Material.GRAY_STAINED_GLASS_PANE) {
                String sigilId = getSigilIdFromDisplayName(clickedItem);
                if (sigilId != null) {
                    playSound(player, "click");
                    GUISession tierSession = new GUISession(GUIType.ENCHANTER_TIER_COMPARISON);
                    tierSession.put("parentType", GUIType.ENCHANTER_BROWSE_EXCLUSIVE);
                    tierSession.put("sigilId", sigilId);
                    tierSession.put("page", session.getInt("page", 1)); // Preserve page
                    guiManager.reopenGUI(player, tierSession);
                }
            }
        }
    }

    @Override
    public void reopen(Player player, GUISession session) {
        int page = session.getInt("page", 1);
        List<Sigil> sigils = getExclusiveSigils();
        int maxPage = Math.max(1, (int) Math.ceil((double) sigils.size() / ITEMS_PER_PAGE));

        // Clamp page
        page = Math.max(1, Math.min(page, maxPage));
        session.put("page", page);

        Inventory inv = Bukkit.createInventory(null, 27,
            TextUtil.colorize("§7Enchanter > §fExclusive Sigils §7(Page " + page + "/" + maxPage + ")"));

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Calculate pagination
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, sigils.size());

        // Place sigils
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Sigil sigil = sigils.get(i);
            inv.setItem(slot, createSigilItem(sigil));
            slot++;
        }

        // Navigation
        inv.setItem(BACK_BUTTON_SLOT, ItemBuilder.createBackButton("Main Menu"));
        inv.setItem(PREV_PAGE_SLOT, ItemBuilder.createPageArrow(false, page, maxPage));
        inv.setItem(PAGE_INDICATOR_SLOT, ItemBuilder.createPageIndicator(page, maxPage, sigils.size()));
        inv.setItem(NEXT_PAGE_SLOT, ItemBuilder.createPageArrow(true, page, maxPage));

        guiManager.openGUI(player, inv, session);
    }

    private List<Sigil> getExclusiveSigils() {
        return plugin.getSigilManager().getAllSigils().stream()
            .filter(Sigil::isExclusive)
            .sorted((a, b) -> a.getName().compareTo(b.getName()))
            .collect(Collectors.toList());
    }

    private ItemStack createSigilItem(Sigil sigil) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Max Tier: §e" + RomanNumerals.toRoman(sigil.getMaxTier()));

        // Show tier-scaled parameter ranges if they exist
        if (sigil.getTierScalingConfig() != null && sigil.getTierScalingConfig().hasParams()) {
            lore.add("");
            lore.add("§6Scaling Parameters:");

            java.util.Set<String> paramNames = sigil.getTierScalingConfig().getParams().getParameterNames();
            for (String paramName : paramNames) {
                String minValue = sigil.getTierScalingConfig().getParamValueAsString(paramName, 1);
                String maxValue = sigil.getTierScalingConfig().getParamValueAsString(paramName, sigil.getMaxTier());
                String range = formatParameterRange(paramName, minValue, maxValue);
                lore.add("§7  " + range);
            }
        }

        lore.add("");
        lore.addAll(sigil.getDescription());
        lore.add("");
        lore.add("§d§lEXCLUSIVE");
        lore.add("§7Click to view all tiers");

        Material material = sigil.getItemForm() != null ?
            sigil.getItemForm().getMaterial() : Material.NETHER_STAR;

        return ItemBuilder.createItem(material, sigil.getName(), lore);
    }

    /**
     * Format a parameter range for display (e.g., "Damage: 5 → 20")
     */
    private String formatParameterRange(String paramName, String minValue, String maxValue) {
        String name = capitalizeFirst(paramName);
        String lower = paramName.toLowerCase();

        // Add units based on common parameter names
        String unit = "";
        if (lower.contains("chance") || lower.contains("percent")) {
            unit = "%";
        } else if (lower.contains("duration") || lower.contains("cooldown")) {
            unit = "s";
        } else if (lower.contains("damage") || lower.contains("heal") || lower.contains("health")) {
            unit = " ❤";
        } else if (lower.contains("range") || lower.contains("radius")) {
            unit = " blocks";
        } else if (lower.contains("speed") || lower.contains("velocity")) {
            unit = "x";
        }

        return name + ": §e" + minValue + unit + " §7→ §e" + maxValue + unit;
    }

    /**
     * Capitalize and format parameter name (e.g., "mark_chance" -> "Mark Chance").
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;

        // Replace underscores with spaces and capitalize each word
        String[] words = str.split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }

    private String getSigilIdFromDisplayName(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        String plainName = TextUtil.stripColors(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(item.getItemMeta().displayName()));
        for (Sigil sigil : plugin.getSigilManager().getAllSigils()) {
            if (TextUtil.stripColors(sigil.getName()).equals(plainName)) {
                return sigil.getId();
            }
        }
        return null;
    }

}
