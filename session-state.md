# 2026-02-04 | Arcane Sigils

DONE: Deployed v1.1.55 with death cleanup fixes using ACE-FCA workflow. Fixed two issues: (1) Dying while stunned now resets skin via PlayerDeathEvent handlers in SkinChangeManager + StunManager, (2) Sandstorm speed debuff now cleaned up on death/disconnect via AttributeModifierManager event handlers (now implements Listener and registered in plugin).

FILES: SkinChangeManager.java (lines 17, 702-709), StunManager.java (lines 13, 144-150), AttributeModifierManager.java (lines 8-15, 45, 383-409), ArmorSetsPlugin.java (line 358), pom.xml

NEXT: Server restart required. Test: (1) Get stunned and die - verify skin resets on respawn, (2) Get Sandstorm debuff and die/disconnect - verify speed normal on respawn/reconnect, (3) Ancient Set flow logs now visible, check what's blocking activation.

CONTEXT: Used ACE-FCA workflow (research → plan → implement). Research documents in scratch/ for both issues. Both bugs caused by missing PlayerDeathEvent handlers. AttributeModifierManager now properly registered as listener. All existing quit handlers preserved, death handlers added alongside them.
