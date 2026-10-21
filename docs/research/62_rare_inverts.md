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

## 2. Peacock mantis shrimp (*Odontodactylus scyllarus*, smasher): `odontodactylus_scyllarus`

Its own brain, `MantisShrimpBrain`, on the shrimp limb rig (`ShrimpLimbs`, mantis tables) with a pair of raptorial
clubs (limb class CLAW, offsets in `uLimb[13]`/`[14]`). Model: `tools/blender/mantis_shrimp.py` (12 cm, 5.2k tris).

| Fact | Source | In the sim |
|------|--------|------------|
| 3-18 cm | Wikipedia | 12 cm adult (`LifeCycle` min tank 150 L) |
| Digs U-shaped burrows in loose sediment near reefs | Wikipedia | `site()`: two openings 1.5 body lengths apart on open sand 4-14 cm from the rock, with room for the body all round; it digs pits and carries sand out a load at a time (`Dig`), dumping it past the opening |
| Spends much of its time in the burrow, ambushing from the mouth [I] | care guides | day: spells of 4-12 min watching at the mouth, then 1.5-6 min rests down the hole; night: in the burrow; a startle or a threat sends it backwards down the hole (`Duck`) |
| Smasher: hits snails, crabs and clams with its club, strike out in ~3 ms at up to 23 m/s | Patek et al. 2004 (Nature), UC Berkeley | `Hunt` stalks cleanup crew on the sand near home (`Predation.preyWeight`: snails, hermits, crabs, shrimp, clams, urchins), `Smash` hits it every 1-2.5 s (snail 3-10 hits, hermit 2-6) until it cracks; the clubs go out in 2.7 ms (`SMASH_OUT_S`), hold, fold back over 0.25 s; each hit is a Snap sound and a sand puff |
| Eats fish too | Wikipedia | small fish (< 0.45 body lengths) that come right over the mouth may be struck (15%) |
| Carries food home | care guides [I] | `Carry`: the cracked meal rides under its head back to the burrow and is eaten at the mouth over 5-15 min |
| Can crack aquarium glass; not reef safe; one per tank | Wikipedia | store warnings: one per tank, eats the cleanup crew, needs 5 cm of sand, can crack glass |
| Green body, orange legs, leopard spots on the carapace, red clubs, blue-edged tail, eyes on stalks | Wikipedia | model colours and parts; eyes swivel while it watches |
| $90-250 | Reef Exclusive, That Fish Place | `l("odontodactylus_scyllarus", 7, 180)` |

No clipping: it walks only on open sand with its whole body clear of the rock and glass (it turns on the spot and
goes round), and the stiff body lies along the bed from head to tail fan (`postSteer`).

Tests: `RareInvertsTest` (burrow, digging and rests, a hermit hunted and smashed, the club strike).
