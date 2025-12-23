# Textures Needed for Arcane Sigils Resource Pack

## Location: `assets/arcanesigils/textures/font/`

### Notification Background
- **notification_bg.png** - Dark semi-transparent panel background
  - Size: 200x16 pixels
  - Style: Dark gray (#1a1a1a) with ~80% opacity, subtle border

### Progress Bar Segments (0-100%)
Each bar shows fill progress. Size: 60x4 pixels each.

| File | Fill % | Description |
|------|--------|-------------|
| bar_0.png | 0% | Empty bar (dark gray) |
| bar_10.png | 10% | 10% filled |
| bar_20.png | 20% | 20% filled |
| bar_30.png | 30% | 30% filled |
| bar_40.png | 40% | 40% filled |
| bar_50.png | 50% | 50% filled |
| bar_60.png | 60% | 60% filled |
| bar_70.png | 70% | 70% filled |
| bar_80.png | 80% | 80% filled |
| bar_90.png | 90% | 90% filled |
| bar_100.png | 100% | Full bar |

**Bar Colors:**
- Background: Dark gray (#2a2a2a)
- Fill: Gradient from red (#ff4444) at low to green (#44ff44) at full
- Or solid color based on type (cooldown=red, buff=blue)

---

## Location: `assets/minecraft/textures/gui/`

### Boss Bar Retexture
- **bars.png** - Minimalist boss bar texture
  - Size: 256x256 pixels (vanilla format)
  - Contains 6 color variants (PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE)
  - Each bar has 2 rows: background (notched) and progress (filled)

  **Recommended Changes for Clean Faction Look:**
  - Remove the notched/segmented dividers (make solid)
  - Thinner bar height (original is 5px, try 3-4px)
  - Darker, more transparent background
  - Smoother color fills without gradients
  - Add subtle glow/outline for visibility

  **Layout (vanilla bars.png reference):**
  ```
  Row 0-4:   PINK background
  Row 5-9:   PINK progress
  Row 10-14: BLUE background
  Row 15-19: BLUE progress
  ... (continues for each color)
  ```

  **Color Hex Recommendations:**
  - Background: #1a1a1a with 70% opacity
  - PINK: #ff66aa
  - BLUE: #3366ff (used for binds)
  - RED: #ff4444 (cooldowns)
  - GREEN: #44ff44 (ready)
  - YELLOW: #ffff44 (low time warning)
  - PURPLE: #aa44ff
  - WHITE: #ffffff

---

## Style Guide (Clean Faction Look)

- **Colors**: Dark backgrounds, bright accent colors
- **Opacity**: Semi-transparent panels (70-85%)
- **Borders**: 1px subtle borders or none
- **Font**: Use default Minecraft font, keep it readable
- **Spacing**: Generous padding, not cramped

## Testing

Place resource pack in `.minecraft/resourcepacks/` or have server host it.
Test command: `/tellraw @s {"text":"\uE000"}` should show notification background.
