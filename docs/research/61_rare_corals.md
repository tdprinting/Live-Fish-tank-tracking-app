# 61. Rare and expensive corals: gold torch, bounce mushroom, scolymia

Design note for the rare corals at Frag Shack and Deep Reef Imports. Sourced facts and URLs are in
[60_rare_species_sources.md](60_rare_species_sources.md) (§6-8 there). **[I]** marks an inference or design estimate.

All three are built as loose frags at `StonyCorals.STUDIO` (`tools/blender/stony_corals.py`) and placed on a free
spot when added (`coral.frag` in the sheet, `SimSpecies.fragColony`), the way the stony corals are.

## 1. Gold torch (*Euphyllia glabrescens*, gold morph): `euphyllia_glabrescens_gold`

| Fact | Source | In the sim |
|------|--------|------------|
| Rare because of its colour; the species is common, exported under quota | Wikipedia | same EUPHYLLIA kind, sheet and sting table as the torch (`StonyCorals.sting` matches the id prefix) |
| Gold heads US$125-350 (Reef Chasers $125, Aquarium Depot CA$250-350, UK ~£250) | listings, Reef Builders 2019 | `l("euphyllia_glabrescens_gold", 6, 250)` |
| Golden-yellow tentacles (~#D9A81E) with blue/green tips, brown-gold base; tubular tentacles with knob tips | Wikipedia, listing names | `EUPHYLLIA["gold_torch"]`: torch tentacles, gold body, blue-green tips; two heads |
| Long sweepers with potent nematocysts, ~10-15 cm [I] | Wikipedia | the torch's sweepers (20 cm grown), tolerates other Euphyllia |
| Moderate light (PAR 100-150) [I], low-moderate flow | care guides | lightHi 0.7 (torch 0.8), hardiness 0.5 |

## 2. Bounce mushroom (*Rhodactis inchoata* 'bounce'): `rhodactis_inchoata`

| Fact | Source | In the sim |
|------|--------|------------|
| Vesicles "comically enlarged", neon orange with green highlights, "a ring of neon marbles"; brown-olive disc | Reef Builders 2015 | `bounce_mushroom()`: ~75 round orange vesicles (ordinary polyps: they deflate as the disc closes), biggest in a ring part-way out |
| Common bounce $100-300 a polyp; record $6,000 | ReefCentral, Reef Builders 2015 | `l("rhodactis_inchoata", 8, 250)` |
| Slow growth; nobody can make a Rhodactis bounce | Reef Builders | `CoralHealth` fission every ~180 days (hairy: 60) |
| Low-moderate light, low flow [I]; one strain only bounces under blue-rich LED | Reef Builders 2023 | lightLo 0.15 / lightHi 0.45, lightBleach 1.4 |
| Corallimorph, can sting neighbours slightly [I] | Wikipedia (Rhodactis) | the MUSHROOM kind's soft-coral pressure |

## 3. Scolymia (*Homophyllia australis*): `homophyllia_australis`

| Fact | Source | In the sim |
|------|--------|------------|
| One large fleshy cup-shaped polyp, raised radial septa ridges; contrasting centre and rim | Reef Builders 2024 | `scolymia()`: an 8 x 7 cm cup, 36 septa ridges, red centre and green rim, a short night feeding tentacle ring |
| PAR 50-150 (up to ~200); too much light more harmful than too little | Top Shelf, Reef Chasers | lightLo 0.1 / lightHi 0.5; `CoralHealth` lightBleach 1.5 |
| Low on the sandbed with space round it; sting reach ~5-8 cm [I] | Reef Builders | ground SAND; `Sting(1.0, 6.0, 0.25)` |
| Spot-feed meaty foods 2-3 times a week | Reef Builders 2024 | LPS kind: catches mysis and brine (`Simulation.coralCatch`); feedNeed 0.6 (duncan 0.5) |
| 24-26 °C, dKH 8-12, Ca 400-500, Mg 1250-1350 | Reef Builders 2024 | temp opt 24-26; stony Ca/Mg ranges |
| US$150-450 (LiveAquaria $150-349, Top Shelf UFO $450) | listings | `l("homophyllia_australis", 7, 300)` |

## Tests

`RareCoralsTest`: kinds, sheets, shop rows, sting reach, Euphyllia peace, scolymia night tentacles and light needs,
the bounce's slow split, and placement on a free spot.
