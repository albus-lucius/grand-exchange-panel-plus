# Development Notes

## UI Widget Debug Overlay

**Toggle**: Enable "Show Debug" in the plugin config panel.

### What it does

Draws a green text overlay on the left side of the game screen listing **every visible widget that has text**, across ALL loaded interfaces. This is the primary tool for identifying unknown dialogs, popups, or UI elements.

### How it works

1. Calls `client.getWidgetRoots()` to get all root widgets currently loaded
2. Recursively walks each root's children:
   - `getStaticChildren()` — fixed child widgets defined in the interface
   - `getDynamicChildren()` — children created at runtime (e.g., list items, slot contents)
   - `getNestedChildren()` — widgets nested from other interface groups
3. Skips any widget where `isHidden()` returns true
4. For each visible widget with non-empty `getText()`, draws a line:
   ```
   group:child idx=index [widget text]
   ```
5. Top line (yellow) shows detection state variables:
   ```
   varbit4439=0 pleaseWait=false inSubView=false
   ```

### How to use for identifying unknown widgets

1. Turn on "Show Debug"
2. Get the game into the state where the unknown dialog/widget is visible
3. Screenshot — the green text list shows every visible text widget with its ID
4. Compare with a screenshot of the normal state to find what's new
5. The `group:child` values map to `client.getWidget(group, child)`

### Key widget IDs discovered

| Widget | Description |
|--------|-------------|
| `465:7-14` | GE offer slots 0-7 |
| `465:4` | Back button |
| `465:24` | Collect area (detail view), children 2-3 are item slots |
| `465:6` | Top bar / collect-all area |
| `916:7` | "Please wait..." dialog (separate interface group!) |

### File-based debug logging (alternative)

The debug overlay can also be changed to write widget snapshots to a file instead of drawing on screen. Write to `System.getProperty("user.home") + "/.runelite/ge-debug.log"` using `java.io.FileWriter` with append mode. Throttle to every ~200ms. This captures state transitions that are hard to screenshot.

### Notes

- The "Please wait..." spinner when clicking Modify is on interface **916**, not 465
- `client.getWidget(group, child)` only gets top-level children — use `getChild(n)`, `getStaticChildren()`, `getDynamicChildren()` for nested ones
- Previous text scans of `client.getWidget(0..600, 0..50)` missed the "Please wait..." widget because they only checked direct children, not the recursive tree via `getWidgetRoots()`
- Varbit 4439 (`GE_SELECTEDSLOT`) is 0 during the "Please wait..." transition — it only becomes non-zero after the detail view fully loads

## Logging

- Project has `src/main/resources/logback.xml` that overrides RuneLite's logback config
- **Logs go to `./log.log` in the project root** (NOT to `~/.runelite/logs/client.log`)
- Also writes to CONSOLE (gradle run stdout)
- Use `@Slf4j` on class + `log.info()`, `log.debug()`, etc.
- `<logger name="com.grandexchangepanelplus" level="DEBUG"/>` enables DEBUG for our package

### How to read logs
```bash
# From the project root:
cat log.log
tail -f log.log
grep "MySearchTerm" log.log
```

### Hot-swap (DCEVM-11 + HotswapAgent)
- Method body changes are hot-swapped on `gradle build`
- Adding new fields, methods, or annotations (like `@Slf4j` to a class that didn't have it) requires a full restart of `gradle run`
