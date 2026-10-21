# 62. Rare and expensive invertebrates

Design note for the rare inverts. Sourced facts and URLs are in [60_rare_species_sources.md](60_rare_species_sources.md)
(§11-14 there). **[I]** marks an inference or design estimate.

## 1. Ultra crocea clam (*Tridacna crocea*, boring clam): `tridacna_crocea`

It runs on the maxima clam's `GiantClamBrain` (research 45 §1): photosynthesis, a little filtering, a shadow snap and
bleaching. The shell and mantle come from `tools/blender/maxima_clam.py` (`use("crocea_clam")`) on the same `ClamRig`.

| Fact | Source | In the sim |
|------|--------|------------|
| Up to 15 cm; farmed clams sold at 1-2 in | Wikipedia, ORA listings | 15 cm max, 8 cm adult; model 8 cm |
| Bores into rock (it grinds a pit with its foot and byssus) [I: glue on rock, not sand] | Wikipedia (common name), care guides | `GiantClamBrain.findHome`: also takes sloping rock (normal y ≥ 0.6), searches 120 cm for rock before it would ever use the sand |
| Zooxanthellae host; PAR 250-400 [I]; the shallowest, most light-hungry Tridacna | care guides | `PAR_LOW_CROCEA` 250 (maxima 200): it pales at a light a maxima is fine in |
| Mantle blue, green, purple, gold, orange or brown with spots, stripes or squiggles; "ultra" gold ~#E3B21E with iridescent blue-green lines [I] | Wikipedia, listings | `LOOKS["crocea_clam"]`: gold mantle, electric blue-green streaks and edge eyes, 6 low folds, worn-flat scutes |
| CITES II; ORA ultra grade $117-167 for 1-1.5 in; Reef Exclusive $166 | listings | `l("tridacna_crocea", 6, 150)` (maxima 5/$110); store warning about light and rock |

Tests: `RareInvertsTest`.
