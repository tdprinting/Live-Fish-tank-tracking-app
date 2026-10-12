# ReefLive work list (open items)

## Sim
- Heat-death spasm animation missing (research 08; SIMULATION §10)
- Extreme sandbox ranges for every parameter (research 09 part B)
- Check NO3 79 / PO4 0.22 red at sandbox start: are those defaults or logged readings?
- Clock said "Day" at 18:00 with light at 50%; check against the photoperiod schedule

## Behaviour / camera
- Fish snap to fixed spots and don't react to time-speed changes (AI not ticking at higher speeds / during time-lapse?)

## Models / rendering
- Cleaner shrimp animation broken
- Fish nearly invisible: contrast/rim light, closer default camera

## UI
- Sandbox redesign: controls inline on the tank screen (no pop-up sheet). The tank stays visible above a docked, scrollable control strip grouped by category (Water / Nutrients / Light & Flow / Events). Each slider shows a value + unit and a safe-range band (green/amber/red zones), with tap-to-type exact values, step/ramp/schedule as a simple mode chip, one-tap presets (heater stuck, power outage, overfeed, ...), and reset per parameter and all. Must stay usable one-handed at phone width. Covers the realism sandbox controls + extreme ranges from research 09.
- Tank uses about 1/3 of the screen; big empty gap. Fill it; pinch-zoom / landscape
- Sandbox button redundant when the Sandbox toggle is active; crowds the nav bar

## Leftovers
- app/schemas/1.json: user to download from run 38224331252 artifact
- 15 harmless .NNN mesh names in 8 GLBs
