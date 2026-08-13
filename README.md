# BuildTools

BuildTools is a NeoForge 1.21.1 survival building mod. It gives players craftable tools for fast server-side building while charging exact block costs from inventory.

## VS Code Setup

1. Open this folder in VS Code.
2. Install the Extension Pack for Java if VS Code asks.
3. Let the Java extension import the Gradle project.
4. Run the `NeoForge: Build` task once.
5. Use `NeoForge: Run Client` to start the development client.

The Gradle project targets Java 21 through toolchains. The wrapper itself can run on the installed JDK configured in `.vscode/settings.json`.

## Tools

- Selection Staff: left-click a block for point one, right-click a block for point two, sneak right-click to open the mode menu.
- Advanced Selection Staff: right-click adds a point, left-click removes the aimed point, and the advanced menu provides smart shapes, measurements, transforms, sharing, and presets.
- Builder Wand: middle-click a block or press the configured Build Materials key to choose material, then right-click once to preview and again to build. Left-click a block opens the mode menu.
- Advanced Builder Wand: builds with weighted palettes, gradients, random patterns, block rotation, advanced shapes, and saved ghost plans.
- Builder Brush: right-click previews/applies terrain edits, sneak right-click picks the replace target, and left-click opens brush mode/radius/depth/density controls.
- Area Breaker: right-click to break blocks in the selected area and drop them normally.
- Blueprint Trowel: sneak right-click to copy the current selection, then right-click a block to preview a paste and right-click the same spot again to confirm.
- Undo Token: right-click to undo the most recent BuildTools operation.
- Redo Token: right-click to redo the most recently undone BuildTools operation.
- Magnet: keep it enabled in your inventory to collect nearby dropped items directly into your inventory; right-click toggles it.
- Storage Link: use it on a container to add or remove that container as a BuildTools material source. The link is reusable; use it in the air to open the linked-storage manager.

## Controls and workflow

Most advanced shortcuts are intentionally configurable. Open **Options > Controls > Key Binds > BuildTools** to bind any actions you want. The in-game status overlay shows the bindings that apply to the currently held tool and calls out unbound actions the first time they are relevant.

The normal build workflow is: make a selection, choose a shape and material, right-click once to inspect the preview and material requirements, then repeat the action or use the Confirm Preview binding. Large previews use representative samples while keeping an exact full-area outline. Queued operations report progress; use Cancel Preview to stop one and roll back blocks already changed.

Saved blueprint, preset, and palette libraries support explicit names, renaming, reordering, safe two-click deletion, paging, and search. Material checklists are paged and can show only missing items. Creative material browsing includes the full block catalogue; survival browsing combines inventory, linked storage, and the active palette.
