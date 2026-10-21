# 60. Rare and expensive fish, batch 1: gem tang and wrought iron butterflyfish

Design note for the first two late-game Deep Reef Imports fish. Every sourced fact below comes from
[60_rare_species_sources.md](60_rare_species_sources.md), which lists the URLs. **[I]** marks an inference or a design
estimate rather than a sourced fact. Code constants that rest on a fact carry `@Ev(..., "60 §n fact m")`.

## 1. Gem tang (*Zebrasoma gemmatum*)

| # | Fact | Source | In the sim |
|---|------|--------|------------|
| 1 | Max 22 cm TL; deep Zebrasoma body | Wikipedia | `zebrasoma_gemmatum.json` 22 cm max, 18 cm adult; `gem_tang.glb` from the yellow tang profile |
| 2 | Dark brown with many small white spots on head, body, dorsal and anal fins; yellow caudal fin | Wikipedia | `paint_gem_tang` / `fins_gem_tang` in `tools/blender/fish_species.py` |
| 3 | 22-26 °C, SG 1.020-1.025, pH 8.1-8.4; a deeper (25-60 m) fish | snippet, Wikipedia | temp opt 23-25.5, tol 22-27; StressModel thermal ceiling as the other Zebrasoma |
| 4 | Herbivore: nori, spirulina, herbivore pellets, occasional mysis | snippet | the Zebrasoma TangBrain: grazes rock all day, NibbleNori, TangFeed |
| 5 | Territorial, poor with other Zebrasoma and similar tangs; add last | Wikipedia, snippet | uses the zebra_* pair rows; `gem_tang_care` warning |
| 6 | 125-240 gal recommended | snippet | `minTankGallons` 150 [I] |
| 7 | US$750-1,500 retail (LiveAquaria $747, Top Shelf $1,000) | listings | PriceBook `l("zebrasoma_gemmatum", 8, 1000)` |
| 8 | Wedges into rock to sleep [I, genus norm] | genus | TangSleep crevice bed, same as yellow and purple tang |

Body room (§1, [I]): at 18 cm it is the longest fish in the tank, so it opts into the body glass guard
(`BodySpline.glassGuard`) and every place it is sent to keeps its whole body clear: grazing spots need room for
its tail (`RoomyGraze`), patrol turns back 1.3 body lengths from the end glass and keeps 0.8 L off the front and
back panes, its fallback rest spots are moved or turned so the tail fits, it routes over the rock to its rest,
it steers its tail point off the glass, and when it keeps clear of a fish it moves into open water rather than
into a pocket by the glass.

## 2. Wrought iron butterflyfish (*Chaetodon daedalma*)

| # | Fact | Source | In the sim |
|---|------|--------|------------|
| 9 | Grazes fanworms, hydroids, tunicates, barnacles and gastropods | Wikipedia | RockPeck (pecks the rock film, `PECK_FOOD`), DusterPeck (pecks the feather duster, which pulls its crown in; `CROWN_BITE_P` it loses it) |
| 10 | Reef safety unknown; likely to pick at soft corals and zoas [I] | inferred | CoralNip only when hungry (`NIP_HUNGER`), zoas most, Kenya tree least; `butterfly_corals` warning |
| 11 | Algae plus small benthic invertebrates | Wikipedia | diet MYSIS/BRINE/MEATY/PODS/NORI; NibbleNori at low weight |
| 12 | "Vigorous appetite" in a tank; inquisitive | Reef Builders 2009 | ButterflyFeed comes boldly for meaty food (mysis bonus); GlassCurious |
| 13 | Nomadic, ranges widely over clear outer reefs below 10 m | Wikipedia | Rove: along the rock faces in mid-water, a body length or two off the rock |
| 14 | Sociable; schools, and pairs breed ("beautiful pair caught") | Wikipedia, Reef Builders 2010 | two butterflies pair up (`mateId`) and the follower swims beside its mate (`PAIR_REACH_CM`) |
| 15 | Rests near rock at night [I] | inferred | ButterflySleep: flank-on under an overhang via the tangs' Dock glide; open rest by the rock as a fallback |
| 16 | Japan endemic, warm-temperate: 20-25 °C [I] | Wikipedia + [I] | temp opt 21-25, tol 19-27; the DEEP_REEF heat profile; thermal ceiling 27.5; `butterfly_cool` warning |
| 17 | ~15 cm TL; black body, pale-yellow scale centres in a black lattice; yellow fin edges | Wikipedia | 15 cm max, 13 cm adult; `paint_wrought_iron` hex-scale lattice |
| 18 | US$1,800-4,500 (LiveAquaria $1,800, Reef Exclusive $4,440) | Reef Builders, listing | PriceBook `l("chaetodon_daedalma", 9, 2500)` |

The fanworm warning (`butterfly_fanworms`) is a WARN when the tank has a feather duster, since the butterfly
will work it over every hour or two.

## Tests

`RareFishBatch1Test` (sheets, shop rows, warnings, rock pecking, duster pecking, pairing, sleeping against the
rock, gem tang grazing) and `FishGlitchTest.rareFishDoNotGlitchOrClip` (no rock, sand, glass or surface
penetration and no body pops over a day and a night with the default stock).
