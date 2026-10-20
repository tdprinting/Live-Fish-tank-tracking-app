# 41 Green star polyps: mat, growth and retraction

Scope: the GSP colonies in Tee's tank (*Briareum* sp., also sold as *Pachyclavularia violacea*; family Briareidae),
which are the long-tentacle form (Tee's photos [P p12/p13] in `video/tee_tank_photos.md`). This note backs
`core/.../sim/GspPolyps.kt`, `GspMatBuilder.kt`, `CoralGrowth.kt` (GSP row) and the renderer's `GspMatDraw`.

Evidence levels follow `evidence_ledger.md`: [S] peer-reviewed study, [C] care guide or zoo sheet, [K] widely
reported hobby knowledge, [E] design estimate.

## 1. Body plan

| Fact | Level | Source |
|---|---|---|
| The colony is a thin, rubbery mat of stolons, connected into a mesh, purple to maroon (green or brown in some morphs). Each polyp sits in its own tubular calyx in the mat. | C | Cleveland Metroparks Zoo care sheet (Animals: Green Star Polyps) |
| Polyps are octocoral polyps with 8 feathery tentacles, fluorescent green; they can pull back fully into the calyx, leaving the bare mat with small pits. | C / K | compendium S2 (gensou.sg care guide), LiveAquaria |
| Long-tentacle morphs have tentacles 1.5-2 cm long on short brown stalks; a mature colony reads as a dense carpet or dome of tentacles. | Owner photo | `video/tee_tank_photos.md` [P p12-p14] |
| *Briareum* colonies tend to build up into thick lumps, so they spread a little more slowly than thin sheets of related genera. | C | hobby article quoted in the LiveAquaria / Quality Marine notes |

In the sim: the mat is a skin a millimetre or so thick on the rock (drawn as a copy of the rock's own triangles,
lifted 0.7 mm), purple with dark calyx pits that have a green rim (closed polyps), and long-tentacle polyps planted on
it at about 0.8 cm, so the crowns overlap into a carpet.

## 2. Growth rate and growth pattern

| Fact | Level | Source |
|---|---|---|
| About 2.5 cm (1 in) of spread per month under good conditions. | C | Cleveland Metroparks Zoo care sheet |
| Several cm per month; covers a 30 cm rock face in 3-4 months; rarely crosses a sand gap wider than about 5 cm. | C | compendium 03 C2 (S2) |
| Caribbean *B. asbestinum* (wild, tagged colonies, 2 years): net colony growth 16.6 cm/yr, potential 71 cm/yr, the gap made up by fragmentation and predation. Polyps near a growing tip are younger (fewer gametes than those 5 cm or more behind it). | S | Brazeau & Lasker 1992, *Biol. Bull.* 183:269 |
| Spreads over rock, glass, pumps and other corals by overgrowth, not by stinging. | C / K | compendium 03 D, retailer notes |
| Growth is light-driven (photosynthetic); it prefers moderate light and moderate-strong flow and grows faster with some nitrate and phosphate. | C | compendium 03 C1 |

Model:

- `CoralGrowth` keeps the mat edge speed (0.1 cm/day at full vigour, from the 30 cm in 3-4 months row) and the
  sand-gap rule for coverage and overgrowth.
- `GspMatBuilder` lays the mat out along the drawn rock: distance is measured over the rock surface (it never jumps
  through rock or open water to another rock) and costs up to 2.5x more through shaded rock (the mesh's open-sky term),
  so the mat runs out over lit faces first and creeps into caves and under ledges [E, from the light dependence].
- The margin is lobed, with thin stolon runners reaching up to ~2.4 cm ahead (the classic purple "fingers" on glass
  and rock [K]); the newest stolon is paler. Polyps sprout about 0.5 cm behind the margin and lengthen over the next
  2 cm; the oldest polyps in the middle stand a little taller (Brazeau & Lasker: tip polyps are the young ones).

## 3. Polyp retraction

| Fact | Level | Source |
|---|---|---|
| Polyps retract into the mat when disturbed (touch, a fish nipping) and reopen once left alone. | C | LiveAquaria product notes |
| In gorgonian octocorals, a touch withdraws the polyps in the immediate vicinity; the colonial conduction system's signal weakens with distance, so the response is local. In other octocorals retraction spreads little between polyps. | S | *J. Exp. Biol.* 79:191 (1979); Horridge 1957 (*Nature* 180:421) |
| Polyps close at night (most GSP; a few stay partly open). | K | compendium S2 row, "widely reported" |
| Low flow: polyps shrink to knobs; moderate-strong flow keeps them out. | C | compendium S2 |
| Stress (parameter swings, high or low light, poor water) keeps them closed. | C / K | compendium 03 C1 stressor table |
| A newly added or moved colony may stay closed for a day or two. | K | common hobby report |

Model:

- `CoralSim` (unchanged) sets the colony extension from the light schedule (closed at night), stress, flow and
  colony-wide disturbance.
- `GspPolyps.poke`: a tap, a puff, a tang's nip, or a snail, hermit, crab or resting fish on the mat (checked once a
  second) closes the polyps under it at once and a ring spreading 3 cm/s to ~3.5 cm around it [E]; they stay in for 4 s
  after the last touch and reopen over 40 s [E]. A walker keeps refreshing its spot, so a closed trail follows it.
- Only the share of the mat the ring covers counts toward the colony-wide extension: a small frag shuts whole, a big
  mat barely changes.
- `GspPolyps.settleFactor`: a newly placed colony opens from 30 % to full over its first 18 h [E]; colonies loaded
  from a save are settled.

## Sources

- Brazeau, D. A. and Lasker, H. R. (1992). Growth rates and growth strategy in a clonal marine invertebrate, the
  Caribbean octocoral *Briareum asbestinum*. Biological Bulletin 183: 269-277.
  https://www.journals.uchicago.edu/doi/10.2307/1542214
- J. Exp. Biol. 79: 191 (1979): colonial conduction and polyp withdrawal near the stimulus in the gorgonians
  *Muricea californica* and *Lophogorgia chilensis*. https://cob.silverchair.com/jeb/article-pdf/79/1/191/3362189/jexbio_79_1_191.pdf
- Horridge, G. A. (1957). The co-ordination of the protective retraction of coral polyps. Nature 180: 421.
  https://www.nature.com/articles/180421a0.pdf
- Cleveland Metroparks Zoo, Green Star Polyps care sheet. https://resourcelibrary.clevelandmetroparks.com/animals/280
- LiveAquaria, Starburst polyp. https://www.liveaquaria.com/products/starburst-polyp
- Quality Marine, Green Starburst Polyp (*Briareum*). https://qualitymarine.com/quality-marine/coral/polyps/briareum-pachyclavularia/starburst-green-55533
- ReefLive research compendium 03 C1-C2, D (S2: gensou.sg green star polyp care guide).
