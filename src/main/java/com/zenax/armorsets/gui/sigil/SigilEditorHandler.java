package com.zenax.armorsets.gui.sigil;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.core.SigilManager;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for the SIGIL_EDITOR GUI.
 * This is the main editing screen for a sigil.
 *
 * Layout (3 rows = 27 slots):
 * Row 0: [_][_][_][D][P][R][_][_][_]
 * Row 1: [_][_][A][_][_][_][B][_][_]
 * Row 2: [X][S][L][_][_][C][LP][ET][E]
 *
 * Where:
 * _ = Gray glass pane filler
 * X = Back button (slot 18)
 * D = Description (slot 3)
 * P = Preview (slot 4)
 * R = Rename (slot 5)
 * A = Sigil Config (slot 11)
 * B = Signals/Abilities (slot 15)
 * S = Save (slot 19)
 * L = Change filename (slot 20)
 * C = Crate name (slot 23) - only if exclusive
 * LP = Lore Prefix (slot 24) - only if exclusive
 * ET = Switch Type (slot 25) - only if exclusive
 * E = Toggle Exclusivity (slot 26)
 */
public class SigilEditorHandler extends AbstractHandler {

    // Slot positions
    private static final int SLOT_DESCRIPTION = 3;
    private static final int SLOT_PREVIEW = 4;
    private static final int SLOT_RENAME = 5;
    private static final int SLOT_CONFIG = 11;
    private static final int SLOT_SIGNALS = 15;
    private static final int SLOT_BACK = 18;
    private static final int SLOT_SAVE = 19;
    private static final int SLOT_FILENAME = 20;
    private static final int SLOT_CRATE = 23;
    private static final int SLOT_LORE_PREFIX = 24;
    private static final int SLOT_SWITCH_TYPE = 25;
    private static final int SLOT_EXCLUSIVITY = 26;

    public SigilEditorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil == null) {
            player.closeInventory();
            return;
        }
        // Refresh sigil from manager in case it was reloaded
        Sigil freshSigil = plugin.getSigilManager().getSigil(sigil.getId());
        if (freshSigil != null) {
            sigil = freshSigil;
            session.put("sigil", sigil);
        }
        int parentPage = session.getInt("parentPage", 1);
        String parentFilter = session.get("parentFilter", String.class);
        openGUI(guiManager, player, sigil, parentPage, parentFilter != null ? parentFilter : "NONE");
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        var v = session.validator(player);
        Sigil sigil = v.require("sigil", Sigil.class);
        if (v.handleInvalid()) return;

        switch (slot) {
            case SLOT_BACK -> handleBack(player, session);
            case SLOT_DESCRIPTION -> handleDescription(player, session, sigil);
            case SLOT_PREVIEW -> handlePreview(player, session, sigil);
            case SLOT_RENAME -> handleRename(player, session, sigil, event);
            case SLOT_CONFIG -> handleConfig(player, session, sigil);
            case SLOT_SIGNALS -> handleSignals(player, session, sigil, event);
            case SLOT_SAVE -> handleSave(player, session, sigil);
            case SLOT_FILENAME -> handleFilename(player, session, sigil);
            case SLOT_CRATE -> handleCrate(player, session, sigil);
            case SLOT_LORE_PREFIX -> handleLorePrefix(player, session, sigil);
            case SLOT_SWITCH_TYPE -> handleSwitchType(player, session, sigil);
            case SLOT_EXCLUSIVITY -> handleExclusivity(player, session, sigil);
            default -> playSound(player, "click");
        }
    }

    /**
     * Handle back button - return to Sigils Menu (with unsaved changes check).
     */
    private void handleBack(Player player, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);

        // Check for unsaved changes
        if (sigil != null && hasUnsavedChanges(session, sigil)) {
            // Check if already confirmed
            Boolean confirmed = session.get("backConfirmed", Boolean.class);
            if (confirmed == null || !confirmed) {
                // First click - warn about unsaved changes
                session.put("backConfirmed", true);
                player.sendMessage(TextUtil.colorize("§eYou have unsaved changes!"));
                player.sendMessage(TextUtil.colorize("§7Click back again to discard changes, or save first."));
                playSound(player, "error");
                return;
            }
        }

        // Confirmed or no changes - go back
        playSound(player, "click");
        int page = session.getInt("parentPage", 1);
        String filter = session.get("parentFilter", String.class);
        if (filter == null) filter = "NONE";
        SigilsMenuHandler.openGUI(guiManager, player, page, filter);
    }

    /**
     * Check if the sigil has unsaved changes by comparing current state with saved state.
     */
    private boolean hasUnsavedChanges(GUISession session, Sigil sigil) {
        String savedHash = session.get("savedStateHash", String.class);
        if (savedHash == null) return false; // No baseline to compare
        String currentHash = computeSigilHash(sigil);
        return !savedHash.equals(currentHash);
    }

    /**
     * Compute a hash of the sigil's important fields for change detection.
     */
    private static String computeSigilHash(Sigil sigil) {
        StringBuilder sb = new StringBuilder();
        sb.append(sigil.getName()).append("|");
        sb.append(String.join(",", sigil.getDescription())).append("|");
        sb.append(sigil.getSourceFile()).append("|");
        sb.append(sigil.isExclusive()).append("|");
        sb.append(sigil.getExclusiveType()).append("|");
        sb.append(sigil.getRarity()).append("|");
        sb.append(sigil.getMaxTier()).append("|");
        sb.append(sigil.getCrate()).append("|");
        sb.append(sigil.getLorePrefix()).append("|");
        // Include flows count for basic change detection
        sb.append(sigil.hasFlow() ? sigil.getFlows().size() : 0).append("|");
        if (sigil.hasFlow()) {
            for (var flow : sigil.getFlows()) {
                sb.append(flow.getTrigger()).append(":");
                sb.append(flow.hasNodes() ? flow.getGraph().getNodeCount() : 0).append(",");
            }
        }
        return sb.toString();
    }

    /**
     * Refresh the editor GUI while preserving all session data (including saved state hash).
     */
    private void refreshGUI(Player player, GUISession session, Sigil sigil) {
        int parentPage = session.getInt("parentPage", 1);
        String parentFilter = session.get("parentFilter", String.class);
        if (parentFilter == null) parentFilter = "NONE";
        String originalFile = session.get("originalFile", String.class);
        String savedStateHash = session.get("savedStateHash", String.class);
        openGUIWithOriginalFile(guiManager, player, sigil, parentPage, parentFilter, originalFile, savedStateHash);
    }

    /**
     * Create a Runnable that refreshes the GUI (for use in lambda callbacks).
     */
    private Runnable createRefresher(Player player, GUISession session, Sigil sigil) {
        // Capture values for use in lambda
        int parentPage = session.getInt("parentPage", 1);
        String parentFilter = session.get("parentFilter", String.class);
        if (parentFilter == null) parentFilter = "NONE";
        String originalFile = session.get("originalFile", String.class);
        String savedStateHash = session.get("savedStateHash", String.class);
        final String finalFilter = parentFilter;
        return () -> openGUIWithOriginalFile(guiManager, player, sigil, parentPage, finalFilter, originalFile, savedStateHash);
    }

    /**
     * Handle description edit - open book input for unlimited multiline text.
     */
    private void handleDescription(Player player, GUISession session, Sigil sigil) {
        playSound(player, "click");
        List<String> currentDesc = sigil.getDescription();
        Runnable refresh = createRefresher(player, session, sigil);

        guiManager.getBookInputHelper().requestBookInput(player, "Description", currentDesc,
            newDescLines -> {
                sigil.setDescription(newDescLines);
                player.sendMessage(TextUtil.colorize("§aDescription updated! (" + newDescLines.size() + " lines)"));
                refresh.run();
            },
            refresh
        );
    }

    /**
     * Handle preview - show effects summary.
     */
    private void handlePreview(Player player, GUISession session, Sigil sigil) {
        playSound(player, "click");
        // For now, just show a message. Later this could open a preview GUI
        player.sendMessage(TextUtil.colorize("§7=== " + sigil.getName() + " Preview ==="));
        if (!sigil.getDescription().isEmpty()) {
            for (String line : sigil.getDescription()) {
                player.sendMessage(TextUtil.colorize("§f" + line));
            }
        }
        if (sigil.hasFlow()) {
            player.sendMessage(TextUtil.colorize("§7Flows: §f" + sigil.getFlows().size()));
        }
    }

    /**
     * Handle rename - left for name, right for ID.
     */
    private void handleRename(Player player, GUISession session, Sigil sigil, InventoryClickEvent event) {
        playSound(player, "click");

        if (event.isLeftClick()) {
            Runnable refresh = createRefresher(player, session, sigil);
            guiManager.getInputHelper().requestText(player, "Display Name", sigil.getName(),
                newName -> {
                    sigil.setName(newName);
                    player.sendMessage(TextUtil.colorize("§aDisplay name updated to: §f" + newName));
                    refresh.run();
                },
                refresh
            );
        } else if (event.isRightClick()) {
            player.sendMessage(TextUtil.colorize("§cWarning: Changing sigil ID is not recommended!"));
            player.sendMessage(TextUtil.colorize("§7ID changes require config file modification."));
            playSound(player, "error");
        }
    }

    /**
     * Handle sigil config button - open sigil config GUI.
     */
    private void handleConfig(Player player, GUISession session, Sigil sigil) {
        playSound(player, "click");
        SigilConfigHandler.openGUI(plugin, guiManager, player, sigil);
    }

    /**
     * Handle flow button - open the visual Flow Builder.
     * All settings (signal type, cooldown, chance) are configured in the START node.
     *
     * For BEHAVIOR type sigils, opens FlowListHandler to manage multiple flows.
     * For regular sigils, opens FlowBuilder directly for single flow editing.
     */
    private void handleSignals(Player player, GUISession session, Sigil sigil, InventoryClickEvent event) {
        playSound(player, "click");

        // Behaviors can have multiple flows - open FlowList to manage them
        if (sigil.isBehavior()) {
            com.zenax.armorsets.gui.flow.FlowListHandler.openGUI(guiManager, player, sigil);
            return;
        }

        // Regular sigils - open FlowBuilder directly (single flow)
        if (sigil.hasFlow()) {
            // Sigil has a flow configured - open FlowBuilder with existing flow
            com.zenax.armorsets.flow.FlowConfig flow = sigil.getFlow();
            com.zenax.armorsets.gui.flow.FlowBuilderHandler.openGUI(
                guiManager, player, sigil,
                flow.getTrigger() != null ? flow.getTrigger() : "flow",
                flow.getGraph(), flow  // Pass FlowConfig to preserve metadata
            );
        } else {
            // No flow yet - open FlowBuilder with new flow (START node will configure signal type)
            com.zenax.armorsets.gui.flow.FlowBuilderHandler.openGUI(
                guiManager, player, sigil, "flow"
            );
        }
    }

    /**
     * Handle save button - save sigil to YAML.
     */
    private void handleSave(Player player, GUISession session, Sigil sigil) {
        SigilManager sigilManager = plugin.getSigilManager();
        if (sigilManager != null) {
            String originalFile = session.get("originalFile", String.class);
            String currentFile = sigil.getSourceFile();

            if (originalFile != null && !originalFile.equals(currentFile)) {
                sigilManager.saveSigil(sigil, originalFile);
                session.put("originalFile", currentFile);
            } else {
                sigilManager.saveSigil(sigil);
            }

            // Update the saved state hash since we just saved
            String newHash = computeSigilHash(sigil);
            session.put("savedStateHash", newHash);

            player.sendMessage(TextUtil.colorize("§aSigil saved to file!"));
            player.sendMessage(TextUtil.colorize("§7File: §f" + sigil.getSourceFile()));
            playSound(player, "success");
        } else {
            player.sendMessage(TextUtil.colorize("§cError: Could not save sigil - SigilManager not available"));
            playSound(player, "error");
        }

        refreshGUI(player, session, sigil);
    }

    /**
     * Handle filename change - open sign input.
     */
    private void handleFilename(Player player, GUISession session, Sigil sigil) {
        playSound(player, "click");
        String currentFile = sigil.getSourceFile() != null ? sigil.getSourceFile() : "sigils.yml";
        String currentName = currentFile.replace(".yml", "");
        Runnable refresh = createRefresher(player, session, sigil);

        guiManager.getInputHelper().requestText(player, "Filename", currentName,
            newFilename -> {
                String finalFilename = newFilename;
                if (!finalFilename.endsWith(".yml")) {
                    finalFilename += ".yml";
                }
                sigil.setSourceFile(finalFilename);
                player.sendMessage(TextUtil.colorize("§aFilename updated to: §f" + finalFilename));
                refresh.run();
            },
            refresh
        );
    }

    /**
     * Handle crate name button - set the crate this exclusive sigil belongs to.
     */
    private void handleCrate(Player player, GUISession session, Sigil sigil) {
        if (!sigil.isExclusive()) {
            player.sendMessage(TextUtil.colorize("§cSigil must be exclusive to set crate!"));
            playSound(player, "error");
            return;
        }

        playSound(player, "click");
        String currentCrate = sigil.getCrate() != null ? sigil.getCrate() : "";
        Runnable refresh = createRefresher(player, session, sigil);

        guiManager.getInputHelper().requestText(player, "Crate Name", currentCrate,
            newCrate -> {
                if (newCrate.isEmpty()) {
                    sigil.setCrate(null);
                    player.sendMessage(TextUtil.colorize("§7Crate name cleared"));
                } else {
                    sigil.setCrate(newCrate);
                    player.sendMessage(TextUtil.colorize("§aCrate set to: §f" + newCrate));
                }
                refresh.run();
            },
            refresh
        );
    }

    /**
     * Handle lore prefix button - set the symbol prefix for exclusive sigils in lore.
     */
    private void handleLorePrefix(Player player, GUISession session, Sigil sigil) {
        if (!sigil.isExclusive()) {
            player.sendMessage(TextUtil.colorize("§cSigil must be exclusive to set lore prefix!"));
            playSound(player, "error");
            return;
        }

        playSound(player, "click");
        String currentPrefix = sigil.getLorePrefix() != null ? sigil.getLorePrefix() : "⚖";
        Runnable refresh = createRefresher(player, session, sigil);

        guiManager.getInputHelper().requestText(player, "Lore Symbol", currentPrefix,
            newPrefix -> {
                if (newPrefix.isEmpty()) {
                    sigil.setLorePrefix(null);
                    player.sendMessage(TextUtil.colorize("§7Lore prefix reset to default (⚖)"));
                } else {
                    sigil.setLorePrefix(newPrefix);
                    player.sendMessage(TextUtil.colorize("§aLore prefix set to: §f" + newPrefix));
                }
                refresh.run();
            },
            refresh
        );
    }

    /**
     * Handle switch type button - toggle between EFFECT and ACTION exclusive types.
     */
    private void handleSwitchType(Player player, GUISession session, Sigil sigil) {
        if (!sigil.isExclusive()) {
            player.sendMessage(TextUtil.colorize("§cSigil must be exclusive to switch type!"));
            playSound(player, "error");
            return;
        }

        Sigil.ExclusiveType currentType = sigil.getExclusiveType();
        Sigil.ExclusiveType newType = (currentType == Sigil.ExclusiveType.EFFECT)
            ? Sigil.ExclusiveType.ACTION
            : Sigil.ExclusiveType.EFFECT;

        sigil.setExclusiveType(newType);

        String typeName = (newType == Sigil.ExclusiveType.ACTION) ? "Ability" : "Signals";
        player.sendMessage(TextUtil.colorize("§7Exclusive type: §a" + typeName));
        playSound(player, "click");
        refreshGUI(player, session, sigil);
    }

    /**
     * Handle exclusivity toggle - toggle exclusive flag.
     */
    private void handleExclusivity(Player player, GUISession session, Sigil sigil) {
        sigil.setExclusive(!sigil.isExclusive());
        String status = sigil.isExclusive() ? "§aExclusive" : "§7Regular";
        player.sendMessage(TextUtil.colorize("§7Status: " + status));
        playSound(player, "click");
        refreshGUI(player, session, sigil);
    }

    /**
     * Open the Sigil Editor GUI for a player.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil) {
        openGUI(guiManager, player, sigil, 1, "NONE");
    }

    /**
     * Open the Sigil Editor GUI for a player with parent menu info for back navigation.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, int parentPage, String parentFilter) {
        // Store original filename for tracking changes
        String originalFile = sigil.getSourceFile() != null ? sigil.getSourceFile() : "sigils.yml";
        // Compute initial state hash for unsaved changes detection
        String savedStateHash = computeSigilHash(sigil);
        openGUIWithOriginalFile(guiManager, player, sigil, parentPage, parentFilter, originalFile, savedStateHash);
    }

    /**
     * Open the Sigil Editor GUI with explicit original filename tracking (without hash - for internal refreshes).
     */
    public static void openGUIWithOriginalFile(GUIManager guiManager, Player player, Sigil sigil,
                                                int parentPage, String parentFilter, String originalFile) {
        // Preserve existing hash (refresh scenario) - will be null on first open
        openGUIWithOriginalFile(guiManager, player, sigil, parentPage, parentFilter, originalFile, null);
    }

    /**
     * Open the Sigil Editor GUI with explicit original filename and state hash tracking.
     */
    public static void openGUIWithOriginalFile(GUIManager guiManager, Player player, Sigil sigil,
                                                int parentPage, String parentFilter, String originalFile,
                                                String savedStateHash) {
        if (sigil == null) {
            player.sendMessage(TextUtil.colorize("§cError: Sigil not found!"));
            return;
        }

        // Breadcrumb title: "Sigils > Fire Burst"
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("§7Sigils > §f" + sigil.getName()));

        // Fill background with gray glass panes
        ItemBuilder.fillBackground(inv);

        // Row 0: Description, Preview, Rename
        inv.setItem(SLOT_DESCRIPTION, buildDescriptionItem(sigil));
        inv.setItem(SLOT_PREVIEW, buildPreviewItem(sigil));
        inv.setItem(SLOT_RENAME, buildRenameItem(sigil));

        // Row 1: Sigil Config, Signals/Abilities
        inv.setItem(SLOT_CONFIG, buildConfigItem(sigil));
        inv.setItem(SLOT_SIGNALS, buildSignalsItem(sigil));

        // Row 2: Back, Save, Filename, Switch Type (if exclusive), Exclusivity
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Sigils Menu"));
        inv.setItem(SLOT_SAVE, buildSaveItem(sigil));
        inv.setItem(SLOT_FILENAME, buildFilenameItem(sigil));

        // Only show exclusive-related options if exclusive
        if (sigil.isExclusive()) {
            inv.setItem(SLOT_CRATE, buildCrateItem(sigil));
            inv.setItem(SLOT_LORE_PREFIX, buildLorePrefixItem(sigil));
            inv.setItem(SLOT_SWITCH_TYPE, buildSwitchTypeItem(sigil));
        }

        inv.setItem(SLOT_EXCLUSIVITY, buildExclusivityItem(sigil));

        // Create session with parent info for back navigation
        GUISession session = new GUISession(GUIType.SIGIL_EDITOR);
        session.put("sigil", sigil);
        session.put("parentPage", parentPage);
        session.put("parentFilter", parentFilter);
        // Track original filename for detecting changes
        session.put("originalFile", originalFile);
        // Track saved state hash for unsaved changes detection
        if (savedStateHash != null) {
            session.put("savedStateHash", savedStateHash);
        }

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Build description item.
     */
    private static ItemStack buildDescriptionItem(Sigil sigil) {
        List<String> lore = new ArrayList<>();

        if (!sigil.getDescription().isEmpty()) {
            lore.addAll(sigil.getDescription());
        } else {
            lore.add("§7No description set");
        }

        lore.add("");
        lore.add("§7Click to edit with book");
        lore.add("§8(No line limit)");

        return ItemBuilder.createItem(Material.BOOK, "§eDescription", lore);
    }

    /**
     * Build preview item.
     */
    private static ItemStack buildPreviewItem(Sigil sigil) {
        List<String> lore = new ArrayList<>();

        // Get material from item form
        Material material = Material.ECHO_SHARD;
        if (sigil.getItemForm() != null) {
            material = sigil.getItemForm().getMaterial();
        }

        // Build lore with flows summary
        int flowCount = sigil.hasFlow() ? sigil.getFlows().size() : 0;
        int totalNodes = 0;
        if (sigil.hasFlow()) {
            for (var flow : sigil.getFlows()) {
                if (flow.hasNodes()) {
                    totalNodes += flow.getGraph().getNodeCount();
                }
            }
        }

        lore.add("§7Flows: §f" + flowCount);
        lore.add("§7Nodes: §f" + totalNodes);
        lore.add("§7Tier: §f" + sigil.getTier() + "§7/&f" + sigil.getMaxTier());
        lore.add("");

        if (!sigil.getDescription().isEmpty()) {
            lore.add("§7Description:");
            for (String line : sigil.getDescription()) {
                lore.add("§8 " + line);
            }
        }

        return ItemBuilder.createItem(material, "§e" + sigil.getName(), lore);
    }

    /**
     * Build rename item.
     */
    private static ItemStack buildRenameItem(Sigil sigil) {
        return ItemBuilder.createItem(
            Material.NAME_TAG,
            "§eRename Sigil",
            "§7Current: §f" + sigil.getName(),
            "§7ID: §f" + sigil.getId(),
            "",
            "§7Left-click: Rename display name",
            "§7Right-click: Rename ID §c(not recommended)"
        );
    }

    /**
     * Build config item.
     */
    private static ItemStack buildConfigItem(Sigil sigil) {
        return ItemBuilder.createItem(
            Material.EMERALD,
            "§aSigil Config",
            "§7Configure item display,",
            "§7socketable items, and tiers",
            "",
            "§7Click to open"
        );
    }

    /**
     * Build flow editor item.
     * Shows flow info (type, trigger, nodes) or "Not configured" if no flow.
     */
    private static ItemStack buildSignalsItem(Sigil sigil) {
        List<String> lore = new ArrayList<>();

        // Check if sigil has a unified flow configured
        if (sigil.hasFlow()) {
            com.zenax.armorsets.flow.FlowConfig flow = sigil.getFlow();
            boolean isAbility = flow.isAbility();

            Material material = isAbility ? Material.WIND_CHARGE : Material.FIRE_CHARGE;
            String typeLabel = isAbility ? "§dAbility" : "§6Signal";

            lore.add("§7Type: " + typeLabel);

            if (flow.getTrigger() != null && !flow.getTrigger().isEmpty()) {
                lore.add("§7Trigger: §f" + flow.getTrigger());
            }

            if (flow.getCooldown() > 0) {
                lore.add("§7Cooldown: §f" + flow.getCooldown() + "s");
            }

            if (flow.getChance() < 100) {
                lore.add("§7Chance: §f" + (int) flow.getChance() + "%");
            }

            // Show node count
            int nodeCount = flow.getGraph() != null ? flow.getGraph().getNodeCount() : 0;
            lore.add("§7Nodes: §f" + nodeCount);

            lore.add("");
            lore.add("§aFlow configured");
            lore.add("");
            lore.add("§eClick to edit");

            return ItemBuilder.createItem(material, "§bEdit Flow", lore);
        } else {
            // No flow - check for legacy activation config
            boolean hasLegacy = (sigil.getActivation() != null && !sigil.getActivation().getEffects().isEmpty());

            if (hasLegacy) {
                lore.add("§7Legacy configuration detected");
                lore.add("");
                lore.add("§7Click to create new flow");
                lore.add("§8(Legacy will be preserved)");
            } else {
                lore.add("§7No flow configured");
                lore.add("");
                lore.add("§7Click to configure");
            }

            return ItemBuilder.createItem(Material.CARTOGRAPHY_TABLE, "§bEdit Flow", lore);
        }
    }

    /**
     * Format an effect string for display in lore (truncate and simplify).
     */
    private static String formatEffectName(String effect) {
        if (effect == null || effect.isEmpty()) return "Unknown";

        // Remove target suffix if present (e.g., "@Victim")
        String display = effect.split("\\s+@")[0];

        // Truncate if too long
        if (display.length() > 25) {
            display = display.substring(0, 22) + "...";
        }

        return display;
    }

    /**
     * Build save item.
     */
    private static ItemStack buildSaveItem(Sigil sigil) {
        String filename = sigil.getSourceFile() != null ? sigil.getSourceFile() : "sigils.yml";

        return ItemBuilder.createItem(
            Material.PAPER,
            "§aSave",
            "§7Save sigil to YAML",
            "§7File: §f" + filename
        );
    }

    /**
     * Build filename item.
     */
    private static ItemStack buildFilenameItem(Sigil sigil) {
        String filename = sigil.getSourceFile() != null ? sigil.getSourceFile() : "sigils.yml";

        return ItemBuilder.createItem(
            Material.WRITABLE_BOOK,
            "§eChange Filename",
            "§7Current: §f" + filename,
            "",
            "§7Click to change"
        );
    }

    /**
     * Build crate item (only visible if exclusive).
     */
    private static ItemStack buildCrateItem(Sigil sigil) {
        String crateName = sigil.getCrate() != null ? sigil.getCrate() : "§7None";

        return ItemBuilder.createItem(
            Material.CHEST,
            "§6Crate Exclusive",
            "§7Current: §f" + crateName,
            "",
            "§7Click to set crate name",
            "§8(Leave empty to clear)"
        );
    }

    /**
     * Build lore prefix item (only visible if exclusive).
     */
    private static ItemStack buildLorePrefixItem(Sigil sigil) {
        String prefix = sigil.getLorePrefix() != null ? sigil.getLorePrefix() : "⚖";

        return ItemBuilder.createItem(
            Material.OAK_SIGN,
            "§eLore Symbol",
            "§7Current: §f" + prefix,
            "",
            "§7Click to set symbol",
            "§8(Used in socketed item lore)"
        );
    }

    /**
     * Build switch type item (only visible if exclusive).
     */
    private static ItemStack buildSwitchTypeItem(Sigil sigil) {
        boolean isAction = sigil.getExclusiveType() == Sigil.ExclusiveType.ACTION;
        Material material = isAction ? Material.ENDER_EYE : Material.ENDER_PEARL;
        String typeName = isAction ? "ACTION" : "EFFECT";

        return ItemBuilder.createItem(
            material,
            "§bSwitch Type",
            "§7Current: §f" + typeName,
            "",
            "§7Click to switch"
        );
    }

    /**
     * Build exclusivity item.
     */
    private static ItemStack buildExclusivityItem(Sigil sigil) {
        boolean exclusive = sigil.isExclusive();
        Material material = exclusive ? Material.DIAMOND : Material.COAL;
        String status = exclusive ? "§aExclusive" : "§7Regular";

        return ItemBuilder.createItem(
            material,
            "§dExclusivity",
            "§7Status: " + status,
            "",
            "§7Click to toggle"
        );
    }
}
