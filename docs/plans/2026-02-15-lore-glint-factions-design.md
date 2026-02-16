# Lore Perfection, Glint Removal, Factions Integration — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Unify exclusive sigil lore styling under "Ancient Crate Exclusive", remove enchant glint from player items, add custom enchant ordering, and add faction-based target filtering (@NearbyAllies/@NearbyEnemies).

**Architecture:** Three independent changes. (1) YAML config updates + SocketManager lore rebuild logic for crate unification and enchant ordering. (2) Paper 1.21 `setEnchantmentGlintOverride(false)` API for glint removal. (3) New `FactionsHook` utility + target specifier extensions in AbstractEffect for faction filtering.

**Tech Stack:** Paper 1.21 API, FactionsUUID API (soft dep via JitPack), Adventure Components (MiniMessage)

---

## Task 1: Enchant Glint Removal

**Files:**
- Modify: `src/main/java/com/miracle/arcanesigils/core/SigilManager.java:306-310`
- Modify: `src/main/java/com/miracle/arcanesigils/core/SocketManager.java:194-196`

**Step 1: Remove dummy enchant from sigil items, add glint override**

In `SigilManager.createSigilItem()` (lines 306-310), replace the dummy enchant block:

```java
// OLD (lines 306-310):
if (itemForm.isGlow()) {
    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
}

// NEW:
meta.setEnchantmentGlintOverride(false);
```

This removes the glow from ALL sigil items and prevents glint. The `glow` YAML field is now ignored for sigil items.

**Step 2: Add glint override on socketed items**

In `SocketManager.socketSigil()`, after line 195 (`meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)`), add:

```java
meta.setEnchantmentGlintOverride(false);
```

Also add the same line in `unsocketSigil()` after line 236, and `unsocketSpecificSigil()` after line 275. This ensures armor/weapons never show glint even with real enchantments.

**Step 3: Build and verify**

```bash
mvn clean package -DskipTests -q
```

Expected: BUILD SUCCESS, no compilation errors.

**Step 4: Commit**

```bash
git add src/main/java/com/miracle/arcanesigils/core/SigilManager.java src/main/java/com/miracle/arcanesigils/core/SocketManager.java
git commit -m "fix: remove enchant glint from sigil items and socketed gear"
```

---

## Task 2: Enchant Ordering in Lore

**Files:**
- Modify: `src/main/java/com/miracle/arcanesigils/core/SocketManager.java:320,417-428`

**Step 1: Add enchant order constants**

At the top of `SocketManager` class (near other constants), add:

```java
// Enchantment display order per item type
private static final Map<Enchantment, Integer> SWORD_ENCHANT_ORDER = Map.of(
    Enchantment.SHARPNESS, 0,
    Enchantment.FIRE_ASPECT, 1,
    Enchantment.LOOTING, 2,
    Enchantment.UNBREAKING, 3
);

private static final Map<Enchantment, Integer> ARMOR_ENCHANT_ORDER = Map.of(
    Enchantment.PROTECTION, 0,
    Enchantment.FIRE_PROTECTION, 1,
    Enchantment.PROJECTILE_PROTECTION, 2,
    Enchantment.UNBREAKING, 3
);
```

**Step 2: Change `updateItemLore` signature to accept Material**

Change signature from:
```java
private void updateItemLore(ItemMeta meta, List<String> sigilIds)
```
to:
```java
private void updateItemLore(ItemMeta meta, List<String> sigilIds, Material material)
```

Update ALL call sites (lines 194, 235, 274, 680, 725, 805) to pass the item's Material. For the `updateItemLorePublic` wrapper (line 679), add a Material parameter too:
```java
public void updateItemLorePublic(ItemMeta meta, List<String> sigilIds, Material material) {
    updateItemLore(meta, sigilIds, material);
}
```

Check each call site: `socketSigil` has `item.getType()`, `unsocketSigil` has `armor.getType()`, etc. Grep all callers of `updateItemLorePublic` to update them as well.

**Step 3: Sort enchantments by custom order**

Replace the enchant lore block (lines 417-428) with:

```java
if (meta.hasEnchants()) {
    // Sort enchantments by custom order based on item type
    List<Map.Entry<Enchantment, Integer>> sortedEnchants = new ArrayList<>(meta.getEnchants().entrySet());
    Map<Enchantment, Integer> orderMap = isSword(material) ? SWORD_ENCHANT_ORDER : ARMOR_ENCHANT_ORDER;

    sortedEnchants.sort((a, b) -> {
        int orderA = orderMap.getOrDefault(a.getKey(), 100);
        int orderB = orderMap.getOrDefault(b.getKey(), 100);
        if (orderA != orderB) return Integer.compare(orderA, orderB);
        // Fallback: alphabetical by enchant name
        return a.getKey().getKey().getKey().compareTo(b.getKey().getKey().getKey());
    });

    for (var entry : sortedEnchants) {
        Enchantment enchant = entry.getKey();
        int level = entry.getValue();
        String enchantName = formatEnchantmentName(enchant);
        String roman = toRomanNumeral(level);
        enchantLore.add(TextUtil.parseComponent("§8➤ §7" + enchantName + " §b" + roman));
    }
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
}
```

**Step 4: Build and verify**

```bash
mvn clean package -DskipTests -q
```

**Step 5: Commit**

```bash
git add src/main/java/com/miracle/arcanesigils/core/SocketManager.java
git commit -m "feat: custom enchant ordering in item lore (sword and armor)"
```

---

## Task 3: Crate Style Unification (YAML)

**Files:**
- Modify: `src/main/resources/sigils/pharaoh-set.yml` (lines 20-21, 146-147, 322-323, 456-457, 684-685)
- Modify: `src/main/resources/sigils/seasonal-pass.yml` (lines 28-29, 96-97, 383-384, 487-488, 634-635, 780-781, 856-857, 963-964, 1043-1044)
- Modify: `src/main/resources/sets/ancient_set.yml` (lines 6-8)

**IMPORTANT**: Pull these files from server first per CLAUDE.md workflow!

**Step 1: Pull YAML files from server**

```bash
python host/deploy.py pull plugins/ArcaneSigils/sigils/pharaoh-set.yml scratch/server-pharaoh-set.yml
python host/deploy.py pull plugins/ArcaneSigils/sigils/seasonal-pass.yml scratch/server-seasonal-pass.yml
python host/deploy.py pull plugins/ArcaneSigils/sets/ancient_set.yml scratch/server-ancient_set.yml
```

Compare server versions with local. Use server version as base if different.

**Step 2: Update pharaoh-set.yml**

For ALL sigils (pharaoh_curse, sandstorm, royal_bolster, quick_sand, rulers_hand, royal_guard), change:
```yaml
# OLD:
crate: <gradient:#FFD700:#CD853F>Pharaoh Crate Exclusive</gradient>
lore_prefix: <gradient:#FFD700:#CD853F>⚖</gradient>

# NEW:
crate: <gradient:#FFD700:#CD853F>Ancient Crate Exclusive</gradient>
lore_prefix: <gradient:#FFD700:#CD853F>☽</gradient>
```

**Step 3: Update seasonal-pass.yml**

For ALL sigils (ancient_crown, kings_brace, cleopatra, divine_intervention, niles_grace, ancient_break, horus_arrow_stun, maelstrom, sandwalker), change:
```yaml
# OLD:
crate: "<gradient:#9400D3:#4B0082>Seasonal Pass Exclusive</gradient>"
lore_prefix: "<gradient:#9400D3:#4B0082>♔</gradient>"

# NEW:
crate: "<gradient:#FFD700:#CD853F>Ancient Crate Exclusive</gradient>"
lore_prefix: "<gradient:#FFD700:#CD853F>☽</gradient>"
```

**Step 4: Update ancient_set.yml**

```yaml
# OLD:
crates:
- Pharaoh Crate Exclusive
- Seasonal Pass Exclusive

# NEW:
crates:
- Ancient Crate Exclusive
```

**Step 5: Commit**

```bash
git add src/main/resources/sigils/pharaoh-set.yml src/main/resources/sigils/seasonal-pass.yml src/main/resources/sets/ancient_set.yml
git commit -m "feat: unify pharaoh and seasonal sets under Ancient Crate Exclusive style"
```

---

## Task 4: Lore Badge Symbol Fix + Filtering

**Files:**
- Modify: `src/main/java/com/miracle/arcanesigils/core/SocketManager.java:334,478`

**Step 1: Update lore stripping to catch ☽ symbol**

In `updateItemLore()` line 334, add `☽` to the strip check:

```java
// OLD:
if (plain.contains("➤") || plain.startsWith("▶") || plain.contains("☆") || plain.contains("⚖") ||

// NEW:
if (plain.contains("➤") || plain.startsWith("▶") || plain.contains("☆") || plain.contains("⚖") || plain.contains("☽") ||
```

**Step 2: Fix badge trailing symbol**

Line 478 currently hardcodes `⚖` as trailing symbol. Extract the actual symbol from the lore_prefix:

```java
// OLD (line 478):
finalLore.add(TextUtil.parseComponent(prefix + " " + crateName + " " + trailingColor + "⚖"));

// NEW — extract symbol character from prefix:
String symbol = "☽"; // default
if (prefix != null) {
    // Strip MiniMessage tags to get raw symbol
    String stripped = prefix.replaceAll("<[^>]+>", "").trim();
    if (!stripped.isEmpty()) {
        symbol = stripped;
    }
}
finalLore.add(TextUtil.parseComponent(prefix + " " + crateName + " " + trailingColor + symbol));
```

**Step 3: Build and verify**

```bash
mvn clean package -DskipTests -q
```

**Step 4: Commit**

```bash
git add src/main/java/com/miracle/arcanesigils/core/SocketManager.java
git commit -m "fix: derive badge symbol from lore_prefix, add ☽ to lore stripping"
```

---

## Task 5: Factions Integration — Dependencies

**Files:**
- Modify: `pom.xml` (add FactionsUUID dependency)
- Modify: `src/main/resources/plugin.yml` (add softdepend)

**Step 1: Add FactionsUUID Maven dependency**

In `pom.xml`, after the WorldGuard dependency block (line 93), add:

```xml
<!-- FactionsUUID - Optional soft dependency for faction-based targeting -->
<dependency>
    <groupId>com.github.drtshock</groupId>
    <artifactId>Factions</artifactId>
    <version>1.6.9.5-U0.6.21</version>
    <scope>provided</scope>
</dependency>
```

JitPack repository is already configured in pom.xml (line 39).

**Step 2: Add Factions to plugin.yml softdepend**

```yaml
# OLD:
softdepend:
  - ItemsAdder
  - PlaceholderAPI
  - WorldGuard

# NEW:
softdepend:
  - ItemsAdder
  - PlaceholderAPI
  - WorldGuard
  - Factions
```

**Step 3: Build to verify dependency resolves**

```bash
mvn clean package -DskipTests -q
```

If dependency fails to resolve, try alternative version `1.6.9.5-U0.6.31` or check JitPack availability.

**Step 4: Commit**

```bash
git add pom.xml src/main/resources/plugin.yml
git commit -m "chore: add FactionsUUID soft dependency for faction targeting"
```

---

## Task 6: Factions Integration — FactionsHook

**Files:**
- Create: `src/main/java/com/miracle/arcanesigils/hooks/FactionsHook.java`

**Step 1: Create FactionsHook utility class**

```java
package com.miracle.arcanesigils.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class FactionsHook {

    private static boolean available;

    public static void init() {
        available = Bukkit.getPluginManager().getPlugin("Factions") != null;
        if (available) {
            Bukkit.getLogger().info("[ArcaneSigils] Factions detected — faction targeting enabled.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isAlly(Player player, Player target) {
        if (!available) return false;
        try {
            com.massivecraft.factions.FPlayer fp = com.massivecraft.factions.FPlayers.getInstance().getByPlayer(player);
            com.massivecraft.factions.FPlayer ft = com.massivecraft.factions.FPlayers.getInstance().getByPlayer(target);
            com.massivecraft.factions.perms.Relation rel = fp.getRelationTo(ft);
            return rel == com.massivecraft.factions.perms.Relation.MEMBER
                || rel == com.massivecraft.factions.perms.Relation.ALLY;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isEnemy(Player player, Player target) {
        if (!available) return false;
        try {
            com.massivecraft.factions.FPlayer fp = com.massivecraft.factions.FPlayers.getInstance().getByPlayer(player);
            com.massivecraft.factions.FPlayer ft = com.massivecraft.factions.FPlayers.getInstance().getByPlayer(target);
            com.massivecraft.factions.perms.Relation rel = fp.getRelationTo(ft);
            return rel == com.massivecraft.factions.perms.Relation.ENEMY;
        } catch (Exception e) {
            return false;
        }
    }
}
```

**Step 2: Initialize hook in ArmorSetsPlugin.onEnable()**

Find `onEnable()` in `ArmorSetsPlugin.java` and add after other plugin init:

```java
FactionsHook.init();
```

Add import: `import com.miracle.arcanesigils.hooks.FactionsHook;`

**Step 3: Build and verify**

```bash
mvn clean package -DskipTests -q
```

**Step 4: Commit**

```bash
git add src/main/java/com/miracle/arcanesigils/hooks/FactionsHook.java src/main/java/com/miracle/arcanesigils/ArmorSetsPlugin.java
git commit -m "feat: add FactionsHook for faction relationship checks"
```

---

## Task 7: Factions Integration — Target Specifiers

**Files:**
- Modify: `src/main/java/com/miracle/arcanesigils/effects/impl/AbstractEffect.java:267-307`
- Modify: `src/main/java/com/miracle/arcanesigils/effects/EffectContext.java:248`

**Step 1: Add getNearbyAllies and getNearbyEnemies to AbstractEffect**

After `getNearbyEntities()` (line 280), add:

```java
/**
 * Get nearby allies (faction MEMBER or ALLY) within a radius.
 * Falls back to all nearby entities if Factions is not installed.
 */
protected List<LivingEntity> getNearbyAllies(EffectContext context, double radius) {
    List<LivingEntity> nearby = getNearbyEntities(context, radius);
    if (!com.miracle.arcanesigils.hooks.FactionsHook.isAvailable()) {
        return nearby; // Fallback: all entities
    }
    Player player = context.getPlayer();
    return nearby.stream()
        .filter(entity -> {
            if (entity instanceof Player target) {
                return com.miracle.arcanesigils.hooks.FactionsHook.isAlly(player, target);
            }
            return false; // Non-players are not allies
        })
        .collect(java.util.stream.Collectors.toList());
}

/**
 * Get nearby enemies (faction ENEMY) within a radius.
 * Falls back to all nearby entities if Factions is not installed.
 */
protected List<LivingEntity> getNearbyEnemies(EffectContext context, double radius) {
    List<LivingEntity> nearby = getNearbyEntities(context, radius);
    if (!com.miracle.arcanesigils.hooks.FactionsHook.isAvailable()) {
        return nearby; // Fallback: all entities
    }
    Player player = context.getPlayer();
    return nearby.stream()
        .filter(entity -> {
            if (entity instanceof Player target) {
                return com.miracle.arcanesigils.hooks.FactionsHook.isEnemy(player, target);
            }
            return true; // Non-players (mobs) count as enemies
        })
        .collect(java.util.stream.Collectors.toList());
}
```

**Step 2: Update parseNearbyRadius to handle new prefixes**

After the `@Nearby:` block (line 304), add:

```java
// Handle @NearbyAllies:X format
if (target.startsWith("@NearbyAllies:")) {
    try {
        return Double.parseDouble(target.substring(14));
    } catch (NumberFormatException e) {
        return defaultRadius;
    }
}

// Handle @NearbyEnemies:X format
if (target.startsWith("@NearbyEnemies:")) {
    try {
        return Double.parseDouble(target.substring(15));
    } catch (NumberFormatException e) {
        return defaultRadius;
    }
}
```

**Step 3: Update EffectContext.getTargetLocation**

Line 248 already handles `startsWith("@Nearby")` which will match `@NearbyAllies` and `@NearbyEnemies` — no change needed.

**Step 4: Update effects that use @Nearby targets**

Each effect that calls `getNearbyEntities` needs to check for the new specifiers. In these files, update the nearby-entity handling blocks:

- `MarkEffect.java:94` — add `@NearbyAllies` / `@NearbyEnemies` check
- `ModifyAttributeEffect.java:89` — same
- `PotionEffectEffect.java:91` — same
- `DealDamageEffect.java:21,71` — same

Pattern for each effect (example from MarkEffect):
```java
// OLD:
if (targetStr != null && (targetStr.startsWith("@Nearby") || targetStr.startsWith("@NearbyEntities"))) {
    double radius = parseNearbyRadius(targetStr, 5);
    List<LivingEntity> nearbyEntities = getNearbyEntities(context, radius);

// NEW:
if (targetStr != null && targetStr.startsWith("@Nearby")) {
    double radius = parseNearbyRadius(targetStr, 5);
    List<LivingEntity> nearbyEntities;
    if (targetStr.startsWith("@NearbyAllies")) {
        nearbyEntities = getNearbyAllies(context, radius);
    } else if (targetStr.startsWith("@NearbyEnemies")) {
        nearbyEntities = getNearbyEnemies(context, radius);
    } else {
        nearbyEntities = getNearbyEntities(context, radius);
    }
```

**Step 5: Build and verify**

```bash
mvn clean package -DskipTests -q
```

**Step 6: Commit**

```bash
git add src/main/java/com/miracle/arcanesigils/effects/impl/AbstractEffect.java src/main/java/com/miracle/arcanesigils/effects/impl/MarkEffect.java src/main/java/com/miracle/arcanesigils/effects/impl/ModifyAttributeEffect.java src/main/java/com/miracle/arcanesigils/effects/impl/PotionEffectEffect.java src/main/java/com/miracle/arcanesigils/effects/impl/DealDamageEffect.java
git commit -m "feat: add @NearbyAllies and @NearbyEnemies target specifiers with Factions integration"
```

---

## Task 8: Update Sigil YAML to Use Faction Targets

**Files:**
- Modify: `src/main/resources/sigils/seasonal-pass.yml` (Nile's Grace flows)
- Modify: `src/main/resources/sigils/pharaoh-set.yml` (Sandstorm flows)

**Step 1: Update Nile's Grace — allies only**

In `seasonal-pass.yml`, Nile's Grace `regen_nearby` and `resist_nearby` nodes, change:
```yaml
# OLD:
target: "@NearbyEntities:10"

# NEW:
target: "@NearbyAllies:10"
```

**Step 2: Update Sandstorm — enemies only**

In `pharaoh-set.yml`, Sandstorm `mark`, `slowness`, and `glowing` nodes, change:
```yaml
# OLD:
target: '@NearbyEntities:{radius}'

# NEW:
target: '@NearbyEnemies:{radius}'
```

**Step 3: Commit**

```bash
git add src/main/resources/sigils/seasonal-pass.yml src/main/resources/sigils/pharaoh-set.yml
git commit -m "feat: update Nile's Grace to @NearbyAllies, Sandstorm to @NearbyEnemies"
```

---

## Task 9: Final Build, Version Bump, Deploy

**Step 1: Bump version in pom.xml**

Change version from `1.1.57` to `1.1.58`.

**Step 2: Full build**

```bash
mvn clean package -DskipTests -q
```

**Step 3: Deploy to server**

```bash
python host/deploy.py deploy
```

**Step 4: Commit version bump**

```bash
git add pom.xml
git commit -m "chore: bump version to 1.1.58"
```

**Step 5: Manual server testing**

After server restart, verify:
1. Socket a sigil → armor lore shows enchants in correct order (Prot, Fire Prot, Proj Prot, Unbreaking)
2. Armor with enchants has NO glint shimmer
3. Sigil items have NO glint
4. Exclusive sigils show ☽ prefix with gold gradient
5. Badge shows "☽ Ancient Crate Exclusive ☽"
6. GUI buttons still glow (active filters, etc.)
7. (If Factions installed) Nile's Grace only heals allies, Sandstorm only debuffs enemies
