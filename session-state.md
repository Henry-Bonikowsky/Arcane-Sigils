# 2026-02-19 | Arcane Sigils

DONE: Extended bug-fixing session with Sins via Discord DM. ALL CRITICAL BUGS RESOLVED. Deployed v1.2.22 through v1.2.25.

**v1.2.22-v1.2.23**: Iterative stun fixes + diagnostic logging
- Attempted setCancelled(true) only → regression (players could move)
- Attempted 4-tick teleport → insufficient
- Added info-level logging to FlowContext.resolveValue, VariableNode, ApplyUniversalMarkEffect

**v1.2.24**: Two major fixes
1. **StunManager complete rewrite**: 3-layer freeze approach (setTo(frozenLocation) + walkSpeed=0/flySpeed=0 + one-time initial teleport). No periodic teleport (avoids hitbox desync).
2. **King's Brace ROOT CAUSE**: `seasonal-pass.yml` on server had OLD duplicate copies of ALL 9 pharaoh set sigils. Java's `File.listFiles()` loaded it AFTER `pharaoh-set.yml` alphabetically, overwriting correct flows with broken old versions. Deleted seasonal-pass.yml, mummy-kit.yml, test-dummy.yml from server.

**v1.2.25**: King's Brace charge cap fix
- Added `check_at_max` CONDITION node before `add_charge` in DEFENSE flow
- If `{sigil.charge} >= 100`, skips to END (DR already applied with infinite duration)

CONFIRMED WORKING (Sins 5:09 AM):
- All damage scaling, stacking, amplification, reduction working perfectly
- King's Brace charges cap at 100 correctly
- Royal Bolster shows correct values
- Everything properly stacking

NEXT (for Peach):
1. **Replace CHANGE_SKIN with sand particles during stun** - skin model change causes hitbox/hit registration bugs. Use sand particle burst instead for the aesthetic.
2. **Per-player damage log toggle** - add a command or chat option to turn damage stats on/off per player. Keep logs available for testing but off by default for regular players.
3. **ancient_break duplicate** - exists in both anubis-set.yml and pharaoh-set.yml (pre-existing, not yet reported).

CONTEXT: v1.2.25 is current deployed version. Sins confirmed all working at 5:09 AM. Session complete.
