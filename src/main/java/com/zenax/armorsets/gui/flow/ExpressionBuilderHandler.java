package com.zenax.armorsets.gui.flow;

import com.zenax.armorsets.ArmorSetsPlugin;
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
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI handler for building expressions visually.
 *
 * Supports two modes:
 * - Visual Mode: Dropdown-based expression building (no typing)
 * - Text Mode: Direct text input
 *
 * Layout (27 slots - 3 rows):
 * Row 0: [Back][Insert][Clear][---][---][---][---][---][Mode]
 * Row 1: [---][Value1 ▼][Operator ▼][Value2 ▼][---][---][---][---][---]
 * Row 2: [Preview:                                                    ]
 */
public class ExpressionBuilderHandler extends AbstractHandler {

    private static final int INVENTORY_SIZE = 27;

    // Top row
    private static final int SLOT_BACK = 0;
    private static final int SLOT_INSERT = 1;
    private static final int SLOT_CLEAR = 2;
    private static final int SLOT_MODE_TOGGLE = 8;

    // Middle row - expression building
    private static final int SLOT_VALUE1 = 10;
    private static final int SLOT_OPERATOR = 12;
    private static final int SLOT_VALUE2 = 14;

    // Bottom row
    private static final int SLOT_PREVIEW = 22;

    // Operators
    private static final List<String> OPERATORS = Arrays.asList(
        "<", ">", "<=", ">=", "==", "!=",
        "+", "-", "*", "/", "%",
        "AND", "OR"
    );

    // Common values
    private static final List<String> PLAYER_STATS = Arrays.asList(
        "{player.health}", "{player.health_percent}", "{player.food}",
        "{player.armor}", "{player.xp}", "{player.effects}"
    );

    private static final List<String> VICTIM_STATS = Arrays.asList(
        "{victim.health}", "{victim.health_percent}", "{victim.armor}",
        "{victim.effects}", "{victim.distance}"
    );

    private static final List<String> EVENT_DATA = Arrays.asList(
        "{damage}", "{distance}", "{cooldown}", "{tier}"
    );

    private static final List<String> FUNCTIONS = Arrays.asList(
        "min()", "max()", "random()", "abs()", "round()",
        "hasMark()", "hasPotion()"
    );

    public ExpressionBuilderHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        switch (slot) {
            case SLOT_BACK -> handleBack(player, session);
            case SLOT_INSERT -> handleInsert(player, session);
            case SLOT_CLEAR -> handleClear(player, session);
            case SLOT_MODE_TOGGLE -> handleModeToggle(player, session);
            case SLOT_VALUE1 -> handleValue1(player, session);
            case SLOT_OPERATOR -> handleOperator(player, session);
            case SLOT_VALUE2 -> handleValue2(player, session);
            default -> playSound(player, "click");
        }
    }

    private void handleBack(Player player, GUISession session) {
        playSound(player, "click");
        Consumer<String> callback = session.get("expressionCallback", null);
        if (callback != null) {
            callback.accept(null); // Cancelled
        }
    }

    private void handleInsert(Player player, GUISession session) {
        String expression = buildExpression(session);
        if (expression == null || expression.isEmpty()) {
            player.sendMessage(TextUtil.colorize("§cNo expression built!"));
            playSound(player, "error");
            return;
        }

        playSound(player, "success");
        Consumer<String> callback = session.get("expressionCallback", null);
        if (callback != null) {
            callback.accept(expression);
        }
    }

    private void handleClear(Player player, GUISession session) {
        session.remove("exprValue1");
        session.remove("exprOperator");
        session.remove("exprValue2");
        playSound(player, "click");
        refreshGUI(player, session);
    }

    private void handleModeToggle(Player player, GUISession session) {
        boolean textMode = session.getBooleanOpt("textMode");
        session.put("textMode", !textMode);
        playSound(player, "click");

        if (!textMode) {
            // Switching to text mode - open text input
            String currentExpression = buildExpression(session);
            guiManager.getInputHelper().requestText(player, "Expression",
                currentExpression != null ? currentExpression : "",
                input -> {
                    Consumer<String> callback = session.get("expressionCallback", null);
                    if (callback != null) {
                        callback.accept(input);
                    }
                },
                () -> refreshGUI(player, session)
            );
        } else {
            refreshGUI(player, session);
        }
    }

    private void handleValue1(Player player, GUISession session) {
        playSound(player, "click");
        openValueSelector(player, session, "exprValue1", "Left Value");
    }

    private void handleOperator(Player player, GUISession session) {
        playSound(player, "click");
        openOperatorSelector(player, session);
    }

    private void handleValue2(Player player, GUISession session) {
        playSound(player, "click");
        openValueSelector(player, session, "exprValue2", "Right Value");
    }

    private void openValueSelector(Player player, GUISession session, String valueKey, String title) {
        // Create value selector GUI
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parseComponent("§8" + title));

        int slot = 0;

        // Numbers (row 0)
        for (String num : Arrays.asList("0", "1", "5", "10", "20", "50", "100")) {
            inv.setItem(slot++, ItemBuilder.createItem(Material.PAPER, "§f" + num, "§7Number: " + num));
        }
        inv.setItem(slot++, ItemBuilder.createItem(Material.NAME_TAG, "§eCustom Number...", "§7Enter a custom number"));
        slot = 9;

        // Event data (row 1)
        for (String eventVar : EVENT_DATA) {
            inv.setItem(slot++, ItemBuilder.createItem(Material.BOOK, "§6" + eventVar, "§7Event data variable"));
        }
        slot = 18;

        // Player stats (row 2)
        for (String playerVar : PLAYER_STATS) {
            inv.setItem(slot++, ItemBuilder.createItem(Material.PLAYER_HEAD, "§a" + playerVar, "§7Player statistic"));
        }
        slot = 27;

        // Victim stats (row 3)
        for (String victimVar : VICTIM_STATS) {
            inv.setItem(slot++, ItemBuilder.createItem(Material.SKELETON_SKULL, "§c" + victimVar, "§7Victim statistic"));
        }
        slot = 36;

        // Functions (row 4)
        for (String func : FUNCTIONS) {
            inv.setItem(slot++, ItemBuilder.createItem(Material.COMMAND_BLOCK, "§d" + func, "§7Function"));
        }

        // Back button
        inv.setItem(45, ItemBuilder.createBackButton("Expression Builder"));

        // Create session for value selector
        GUISession selectorSession = new GUISession(GUIType.EXPRESSION_VALUE_SELECTOR);
        selectorSession.put("expressionSession", session);
        selectorSession.put("valueKey", valueKey);
        selectorSession.put("expressionCallback", session.get("expressionCallback"));

        guiManager.openGUI(player, inv, selectorSession);
    }

    private void openOperatorSelector(Player player, GUISession session) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("§8Select Operator"));

        int slot = 0;
        for (String op : OPERATORS) {
            Material material = getOperatorMaterial(op);
            String color = getOperatorColor(op);
            inv.setItem(slot++, ItemBuilder.createItem(material, color + op, "§7Operator: " + op));
        }

        // Back button
        inv.setItem(18, ItemBuilder.createBackButton("Expression Builder"));

        GUISession selectorSession = new GUISession(GUIType.EXPRESSION_OPERATOR_SELECTOR);
        selectorSession.put("expressionSession", session);
        selectorSession.put("expressionCallback", session.get("expressionCallback"));

        guiManager.openGUI(player, inv, selectorSession);
    }

    private Material getOperatorMaterial(String op) {
        return switch (op) {
            case "<", ">", "<=", ">=" -> Material.COMPARATOR;
            case "==", "!=" -> Material.OBSERVER;
            case "+", "-", "*", "/", "%" -> Material.ANVIL;
            case "AND", "OR" -> Material.REDSTONE;
            default -> Material.PAPER;
        };
    }

    private String getOperatorColor(String op) {
        return switch (op) {
            case "<", ">", "<=", ">=" -> "§e";
            case "==", "!=" -> "§b";
            case "+", "-", "*", "/", "%" -> "§a";
            case "AND", "OR" -> "§c";
            default -> "§f";
        };
    }

    private String buildExpression(GUISession session) {
        String value1 = session.get("exprValue1", String.class);
        String operator = session.get("exprOperator", String.class);
        String value2 = session.get("exprValue2", String.class);

        if (value1 == null || value1.isEmpty()) {
            return null;
        }

        StringBuilder expr = new StringBuilder(value1);

        if (operator != null && !operator.isEmpty()) {
            expr.append(" ").append(operator);

            if (value2 != null && !value2.isEmpty()) {
                expr.append(" ").append(value2);
            }
        }

        return expr.toString();
    }

    private void refreshGUI(Player player, GUISession session) {
        Consumer<String> callback = session.get("expressionCallback", null);
        openGUI(guiManager, player, session.get("exprValue1", String.class),
                session.get("exprOperator", String.class),
                session.get("exprValue2", String.class),
                callback);
    }

    /**
     * Open the Expression Builder GUI.
     *
     * @param guiManager The GUI manager
     * @param player The player
     * @param initialValue Initial expression value (can be null)
     * @param callback Callback with the built expression (or null if cancelled)
     */
    public static void openGUI(GUIManager guiManager, Player player, String initialValue,
                               Consumer<String> callback) {
        openGUI(guiManager, player, initialValue, null, null, callback);
    }

    public static void openGUI(GUIManager guiManager, Player player, String value1, String operator,
                               String value2, Consumer<String> callback) {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE,
                TextUtil.parseComponent("§8Expression Builder"));

        // Top row
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Cancel"));
        inv.setItem(SLOT_INSERT, ItemBuilder.createItem(Material.LIME_DYE, "§aInsert Expression",
                "§7Insert the built expression", "", "§7Click to insert"));
        inv.setItem(SLOT_CLEAR, ItemBuilder.createItem(Material.BUCKET, "§cClear",
                "§7Clear all values", "", "§7Click to clear"));

        // Fill decoration
        for (int i = 3; i <= 7; i++) {
            inv.setItem(i, ItemBuilder.createBackground());
        }

        inv.setItem(SLOT_MODE_TOGGLE, ItemBuilder.createItem(Material.COMMAND_BLOCK,
                "§eSwitch to Text Mode",
                "§7Current: §fVisual Mode",
                "",
                "§7Click to type expression directly"));

        // Middle row - expression components
        inv.setItem(9, ItemBuilder.createBackground());
        inv.setItem(11, ItemBuilder.createBackground());
        inv.setItem(13, ItemBuilder.createBackground());
        inv.setItem(15, ItemBuilder.createBackground());
        inv.setItem(16, ItemBuilder.createBackground());
        inv.setItem(17, ItemBuilder.createBackground());

        // Value 1
        String val1Display = value1 != null ? value1 : "§8(click)";
        inv.setItem(SLOT_VALUE1, ItemBuilder.createItem(Material.BOOK,
                "§e" + val1Display,
                "§7Left value",
                "",
                "§7Click to select"));

        // Operator
        String opDisplay = operator != null ? operator : "§8(click)";
        inv.setItem(SLOT_OPERATOR, ItemBuilder.createItem(Material.COMPARATOR,
                "§b" + opDisplay,
                "§7Operator",
                "",
                "§7Click to select"));

        // Value 2
        String val2Display = value2 != null ? value2 : "§8(click)";
        inv.setItem(SLOT_VALUE2, ItemBuilder.createItem(Material.BOOK,
                "§e" + val2Display,
                "§7Right value",
                "",
                "§7Click to select"));

        // Bottom row - preview
        for (int i = 18; i < 27; i++) {
            if (i != SLOT_PREVIEW) {
                inv.setItem(i, ItemBuilder.createBackground());
            }
        }

        // Build preview
        StringBuilder preview = new StringBuilder();
        if (value1 != null) preview.append(value1);
        if (operator != null) preview.append(" ").append(operator);
        if (value2 != null) preview.append(" ").append(value2);

        String previewText = preview.length() > 0 ? preview.toString() : "§8(empty)";
        inv.setItem(SLOT_PREVIEW, ItemBuilder.createItem(Material.MAP,
                "§fPreview: §e" + previewText,
                "§7This is your expression"));

        // Create session
        GUISession session = new GUISession(GUIType.EXPRESSION_BUILDER);
        session.put("exprValue1", value1);
        session.put("exprOperator", operator);
        session.put("exprValue2", value2);
        session.put("expressionCallback", callback);

        guiManager.openGUI(player, inv, session);
    }
}
