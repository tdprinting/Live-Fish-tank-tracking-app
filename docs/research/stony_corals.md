# Stony corals (LPS packs 1-2): hammer, torch, frogspawn, duncan, acan, candy cane, open brain, bubble

The first scleractinian (stony) corals in the sim: they build an aragonite skeleton, so they take real alkalinity and
calcium, mind swings more than soft corals, and leave white skeleton where tissue dies. Large-polyp stony corals also
put out a second set of tentacles after dark. Code: `core/sim/StonyCorals.kt` (night tentacles, sweepers, stings),
`CoralKind.EUPHYLLIA` / `CoralKind.LPS` (`SimSpecies.kt`), `CoralSim.params` / `CA_STONY` / `MG_STONY`,
`CoralHealth.profileOf`, `CoralGrowth.growth`; models `tools/blender/stony_corals.py`; shaders `polyp.vert`
(`uPolypMode` 1 night tentacles, 2 day strands), `static.vert` / `softcoral.frag` (COLOR_0.a skeleton).

Sources: the full source notes with every URL are in `stony_corals_sources.md` (compiled 2026-10-20). Most husbandry
numbers in the hobby are retail care sheets (Bulk Reef Supply / ORA, Top Shelf, Gensou, Reef Chasers, Fragbox, Queen
City Corals); bleaching thresholds follow NOAA Coral Reef Watch (1 °C over the monthly maximum, DHW 4 / 8). There are
no measured aquarium growth rates or sweeper lengths for these species: those are labelled design estimates.

Taxonomy: hammer and frogspawn are now *Fimbriaphyllia ancora* and *F. divisa* (split from *Euphyllia* in 2017); the
torch stays *Euphyllia glabrescens*. Species ids use the current names.

## §1 Needs

| Coral | Temp °C | PAR | Flow | Alk dKH | Ca / Mg | Feeding | Difficulty |
|---|---|---|---|---|---|---|---|
| Hammer | 24-26.7 | 75-200 | low-moderate, indirect | 8-9.5 | 400-450 / 1250-1350 | photosynthetic; mysis 1-2×/wk helps | moderate |
| Torch | 24.4-26.7 | 100-200 | moderate, indirect | 8-10 | 400-450 / 1250-1350 (est.) | photosynthetic; small meaty foods | moderate |
| Frogspawn | 23.9-26.7 | 75-150 | gentle, indirect | 8-10 | 400-450 / 1250-1400 | photosynthetic; meaty 1-2×/wk | moderate |
| Duncan | 24.4-26.7 | 75-150 | moderate | 8-10 (est.) | 400-450 (est.) | grows best fed 1-3×/wk | easy |

In the sim (`CoralSim.params`): optimum 24-27 °C, tolerated 22.5-29 °C; Euphyllia light band 0.2-0.8, LPS 0.15-0.75
(the sim's 0..1 light scale; Kenya is 0.15-0.85). Both are `flowSensitive`: a hard jet "tears the tissue off the
skeleton" ([retail], torch guide). Stony corals use the stony Ca / Mg bands (Ca from 400, stressed under 350; Mg from
1250) at the organ-pipe weight (0.8 vs 0.3 for soft corals), and an alkalinity jump counts twice as much as for a soft
coral: "swings above 1.0 dKH in 24 h commonly trigger recession in Euphyllia" ([retail], weak source).
Growth vigour uses the calcification factor (Ca and alkalinity) like the organ pipe.

Calcification: `demandDkhPerDay` 0.03 per Euphyllia colony and 0.02 per LPS colony (× size and tissue) of this 60 gal,
several times a soft coral (est.: a small LPS-heavy mixed reef of this size uses a few tenths of a dKH a day).

Heat: the acute pathway already treats these genera as LPS (`HeatModel.CORAL_LPS`, necrosis from 34.5 °C).

## §2 Night tentacles, sweepers and stings

- Euphyllia keep their day tentacles about half out at night (`nightExtension` 0.5) and send out long sweeper
  tentacles; duncans and other meaty LPS shrink their mantle (0.6) and open feeding tentacles ([retail] bubble coral,
  duncan, candy cane guides: "feeding tentacles extended at night").
- `Coral.tentacles` (0..1) follows darkness with a ~30 min time constant, held in by stress and a touch (snaps in
  within a minute). The renderer grows the night-tentacle node out with it.
- Sweepers are grown against competitors: with another species inside sweeper reach a Euphyllia develops a full set
  over ~6 days and reabsorbs them over ~20 days once nothing is in reach (design estimate after Hidaka & Yamazato 1984
  on *Galaxea* sweeper development; hobby: "strong current and close neighbours produce more sweepers"). Without a
  rival it still puts out 35 % of a set at night.
- Reach past the colony edge (`StonyCorals.sting`; retail spacing advice 6-10 in):

| Coral | Day tentacles | Sweepers at night | Sting strength | Source |
|---|---|---|---|---|
| Torch | 5 cm | 20 cm | 0.45 | retail: sweepers 1-4 in, "up to about 25 cm"; spacing 6-10 in |
| Frogspawn | 4 cm | 15 cm | 0.35 | retail: sweepers "about 6 in (15 cm) beyond the coral" |
| Hammer | 3.5 cm | 12 cm | 0.35 | retail spacing 6-8 in; sweeper length not sourced (est.) |
| Duncan | 1.5 cm | 5 cm | 0.12 | retail: "short nighttime sweepers", give 3-4 in |

- Sting pressure falls linearly from contact to the current reach (day tentacles plus the sweepers that are out), so
  a neighbour 8 cm from a torch is safe by day and stung at night. Euphyllia of different species tolerate each other
  (hammers, frogspawn and torches are commonly kept touching); other LPS sting anything not their own species.
- Buds of any coral will not settle inside a stony coral's sweeper reach (`CoralHealth.place` clearance).
- Large polyps catch meaty food: target-fed mysis, brine shrimp and krill bits count as caught food for stony corals,
  and their reach grows 3 cm with the night tentacles out. They also eat pods (microfauna small-polyp predation).

## §3 Bleaching, recession and death

| Coral | Heat | Light | Dark fade | Food need | Recedes | Recovers |
|---|---|---|---|---|---|---|
| Euphyllia | 1.2 | 1.1 | 0.02/day | 0.3 | 1.4 | 0.7 |
| Duncan | 0.9 | 1.0 | 0.02/day | 0.5 | 0.9 | 1.0 |
| Other LPS | 1.0 | 1.1 | 0.02/day | 0.5 | 1.1 | 0.9 |

Euphyllia recede fast once failing: brown jelly (ciliate infection on stressed tissue) and recession are the usual
killers in every retail guide, and a receded head is slow to recover. Duncans are the forgiving stony coral. Dead
tissue leaves the skeleton: the renderer shows bare white limestone where the tissue has melted (COLOR_0.a < 0.5), and
the colony is cleared two days after death like the others.

## §4 Growth

No measured aquarium rates. Both kinds grow as `Form.TREE` (new heads = colony size) at 0.2 %/day of size at full
vigour (a 3-head frag about doubles in a year; retail "moderate"), up to 2.2× the model. They do not drop new
colonies on their own: new heads stay on the colony (fragging is a keeper's job).

## §5 Look

- Hammer: thick teal tentacles with fluorescent lime crossbar (hammer / anchor) tips on 3 branching heads.
- Torch: long, slim, flowing olive tentacles with round glowing yellow-green tips.
- Frogspawn: green tentacles that split at the end into pale pink knobby beads.
- Duncan: tall ribbed tubes, each with a flat green disc (fluorescent centre ring) ringed by short tentacles, a small
  daughter head budding off the tallest.
- Sweepers and night tentacles: thin pale strands with white nematocyst-laden tips.
- Fluorescence: tentacle materials carry an emissive colour, so they glow under actinic / Blue light (PR #146).

## §6 Prices (My tank stores, sand dollars ≈ US$)

Retail 2025-26 (BRS / ORA listings; estimates where marked): duncan 1 in, 3-5 heads $80-95 (BRS); hammer frag
$40-120 (est.); torch frag $60-200 (est.); frogspawn frag $50-200 (est.). Game prices: duncan 55, hammer 60,
frogspawn 60, torch 75; Frag Shack from level 2-3.

## §7 Pack 2: acan, candy cane, open brain, bubble

Sources are in stony_corals_sources.md §5-8 (Reef Builders, TFH/Goemans, ORA via Bulk Reef Supply, Fragbox). All four
are `CoralKind.LPS`: the meaty-LPS chemistry bands, night feeding tentacles and meaty-food catching from pack 1.

| | Acan (*Micromussa lordhowensis*) | Candy cane (*Caulastrea furcata*) | Open brain (*Trachyphyllia geoffroyi*) | Bubble (*Plerogyra sinuosa*) |
|---|---|---|---|---|
| Light (PAR) | 50-125 | 100-150 | 50-150, red morphs shaded | 50-150, bubbles shrink in strong light |
| Flow | low-moderate | low, indirect | gentle | very gentle |
| Sting day / sweepers (cm) | 1 / 5 (est.) | 1 / 6 (est.) | 1.5 / 5 (est., short) | 2 / 12 ("aggressive", long sweepers) |
| Health profile | forgiving like duncan | light bleach ×1.2 (colour washes out) | light ×1.3, recede ×1.2, slow recovery | heat and light ×1.1/1.3 (vesicles deflate over 28 °C) |
| Placement | rock | rock | sand only (`StonyCorals.onSand`: swollen tissue tears on rock) | rock |
| Price (level) | 50 (3) | 35 (2; ORA frag $29-34) | 90 (4) | 70 (4) |

Prices other than the candy cane are estimates from the retail ranges in the sources (not verified listings).

Look: acan, an encrusting carpet of 11 plump polyps (red mantle, green ring, orange eye); candy cane, 10 branching
tubes with green and cream striped heads; open brain, one swollen figure-8 cushion with radial ridges and a glowing
green rim on a cone skeleton; bubble, 40 grape-like vesicles over thin septal walls. The open brain mantle and the
bubbles use polyp mode 3 (polyp.vert): they swell with the colony's extension and deflate as the night tentacles come
out, which is when the bubble's long sweepers show.
