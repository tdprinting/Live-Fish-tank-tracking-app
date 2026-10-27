# ReefLive work list (open owner-facing items, 2026-10-27)

Everything from the earlier list (heat spasm, fish snap, cleaner shrimp, invisible fish, tank size, sandbox redesign, PAR/turnover sliders) is fixed on main; see `HANDOFF.md` Status.

## Bugs / checks
- Fish-health visuals (gasping, lethargy, loss of equilibrium, spasms, dead sink-then-float, velvet, fin rot, HLLE, Brooklynella) are unverified in motion once merged.
- Graphics performance thresholds (#206) are untested on a device: need `?stats` numbers from the owner's phone.
- NO3 79 / PO4 0.22 at sandbox start once appeared; defaults are 5.0 / 0.04. Check logged rows on device (possible NO3 vs NO3-N unit mix).
- High-speed time-lapse may look coarse (behaviour steps capped at 0.5 s).

## Features / tuning
- PAR bleaching is faster than research (1-2 days at 800 PAR vs 3-7).
- Fish behaviours: feeding displacement by rank, tang blueDeepen, coral-vs-coral warfare.
- Tracker: cycle tracker, acclimation timer, pest/algae/disease log.
