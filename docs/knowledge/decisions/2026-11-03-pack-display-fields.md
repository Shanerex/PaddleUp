tags: [packs, rooms, web, design]
Packs may carry an optional `display` section {name, summary, icon}: name <= 40 chars, summary <= 280, icon a Material Symbols name ([a-z0-9_]{1,40}); bad values are violations, a missing section reads PackDisplayDto.EMPTY.
Why: the wizard and join mocks show a pack name, summary and icon; humanising the id ("Cricket t20") read badly and naming the sport in core UI breaks NFR-12.
The room snapshot keeps packName (PackLabelsDto), so later pack edits never rename an existing room's pack. The UI falls back to the pack id when name is null.
