# Agent Instructions

## Localization

Any text added or changed that can be shown to players, admins, or server operators must go through the UAS multilingual translation system. Do not hard-code visible English strings in GUI screens, commands, chat messages, alerts, validation errors, tooltips, or admin panels.

Use `Component.translatable(...)` or `UasTranslations` for visible text, and add matching keys to every supported language file:

- `en_us.json`
- `nl_nl.json`
- `de_de.json`
- `fr_fr.json`

Keep runtime values as placeholders or literal data instead of translating them. Examples: player names, item names, UUIDs, auction IDs, mod IDs, money values, counts, and dates.

When adding new translation keys, keep all supported language files in key parity and update localization tests if the new text is part of a critical user-facing path.
