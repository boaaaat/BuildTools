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

- Selection Staff: right-click a block for point one, sneak right-click for point two, right-click air to cycle shape.
- Builder Wand: put the block to place in the offhand, then right-click to build. Sneak or right-click air cycles fill/replace mode.
- Blueprint Trowel: right-click to copy the current selection, sneak right-click to paste.
- Undo Token: right-click to undo the most recent BuildTools operation.
