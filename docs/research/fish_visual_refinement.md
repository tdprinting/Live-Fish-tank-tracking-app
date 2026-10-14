# Fish model and motion refinement

## Photo references

These photographs were used for visual comparison, not copied into the textures.
The asset textures remain procedurally generated. Proportions and animation
constants below are artistic estimates, not biological measurements.

- [Pajama cardinal, In-Depth Images / Underwater Kwaj](https://www.underwaterkwaj.com/lembeh/fish/apogon/Sphaeramia-nematoptera.htm): pointed head, raised first dorsal, narrow second-dorsal extension, large dark-edged scales in the mid-body band.
- [Falco hawkfish, Guam Reef Life](https://www.guamreeflife.com/species-id-v2/reef-fishes/heavy-bodied-large-lips/hawkfishes/): splayed pectorals supporting a perch; fine membranes between the lower rays.
- [Tailspot blenny, Reef Life Survey](https://reeflifesurvey.com/species/ecsenius-stigmatura/): muted brown-orange posterior, smooth scaleless skin, broad supporting pectorals, eye ring and dark tail-base spot.
- [NFRDI marine ornamental species chart](https://nfrdi.da.gov.ph/2025/11/24/feature-safeguarding-philippine-marine-ornamental-fishes-through-science-based-conservation/): comparative clownfish, blue tang, cleaner wrasse and engineer-goby silhouettes. The blue tang tail is shallowly notched rather than deeply crescent-shaped.

## Asset changes

All twelve fish and both detail levels are rebuilt in Blender. Median fins now
sample their silhouette landmarks exactly, preserving narrow filaments instead
of missing them between uniform samples. Slight camber adds surface relief.
Eyes have smoother geometry and higher-resolution iris textures. A painted
catchlight was removed so the highlight can follow actual scene illumination.
Fine scale contrast is reduced. The cardinal has a more pointed head and a
coarser dark-edged scale band. The tang has an emarginate tail. The hawkfish and
blennies have broader, more splayed pectorals; the tailspot loses its artificial
scale grid and yellow tail-body tint.

## Runtime movement

The Android renderer uses `fish.vert` and simulation-driven vertex masks, not
glTF animation channels. The neutral GLBs therefore retain that contract:
unit length, +X forward, part names/extras, eye pivots and COLOR_0 masks.

- The body wave starts behind the head. Higher wave-count fish bend farther
  forward; the original invertebrate envelope is retained.
- Pectorals open on the power stroke and feather during recovery. Their tips
  lag their roots, and zero stroke amplitude no longer triggers a fallback flap.
- Membrane movement follows the fish's swim phase and activity instead of an
  always-running global-time oscillation. The amplitude is smaller and weighted
  toward fin tips.
- The normal correction uses the correct sign for a spine coordinate that
  increases along negative model X.

The existing species-specific simulation still controls speed, gait changes,
perching, sleep, banking, escape and beat-and-glide behavior.

## Editable Blender study

Run from the repository root with Blender 5.1:

```
blender -b --python tools/blender/refine_fish.py -- --out build/fish-study
```

Use `--skip-build` after exports are current. `Fish-motion-study.blend` contains
all twelve fish with packed textures and editable cyclic shape keys. Play frames
1–64 at 32 fps. `Fish-models.png` is the contact sheet. The study demonstrates
neutral swimming/perching with the same wave and fin formulas; it does not
replay simulation paths, feeding, gill breathing or escapes. The GLBs remain
neutral meshes because the application supplies those animations dynamically.

Validation: `python tools/validate_glb.py` checks geometry, masks, textures and
asset budgets. `node tools/graphics/shader_preview/render.cjs --check` compiles
the real app shaders at quality 0, 1 and 2. Device-level appearance and frame
rate should still be checked in the Android app.
