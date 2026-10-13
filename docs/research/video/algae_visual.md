# Reef nuisance algae, visual behaviour (AlgaeGroup)

Sim source: `core/src/main/kotlin/com/reeflive/core/sim/AlgaeModel.kt` (`AlgaeGroup`: DIATOMS, FILM, HAIR, CYANO, DINOS, BUBBLE, CORALLINE). Surfaces used in tests: GLASS, SAND, ROCK_TOP, ROCK_SHADE.

**Scope note:** No video footage was watched. YouTube results are titles and blurbs only. Several web pages were search snippets, not opened. Every claim is tagged **[M]** (stated by a source) or **[L]** (inferred). Nothing is **[H]**. FILM and CORALLINE have no source found; all their values are **[L]**. Numbers are not given by any source for these algae, so speed and growth figures are **[L]**.

## Sources
| # | Page | URL | What it supports |
|---|---|---|---|
| A1 | Gensou, dinoflagellate guide (commercial) | https://gensou.sg/dinoflagellates-reef-tank-complete-guide/ | Slippery brown strings, bubbles, retreat at night, Amphidinium on sand (overstated) |
| A2 | UltimateReef, "Is this Dinoflagellates?" | https://ultimatereef.net/threads/is-this-dinoflagellates.899585/ | Shake test: dinos pass paper towel and reform in light (forum snippet, not opened) |
| A3 | YouTube, "Dinoflagellates Under Microscope" | https://www.youtube.com/watch?v=jtpuvLwl6Q8 | Reef-tank drop under microscope, Ostreopsis or Amphidinium question (blurb only) |
| A4 | YouTube, "Reef Tank Microscopy - Coraline Algae" | https://www.youtube.com/watch?v=lsUaIYrPTiI | Coralline under microscope (title only, not watched) |
| A5 | Manta Systems, diatoms | https://mantasystems.net/a/blog/post/diatoms | Matte brown film, wipes off, reforms (snippet) |
| A6 | GNA, diatom causes | https://gna.it.com/diatoms-reef-tank-causes | Silicate source, 30–60 day new-tank bloom (snippet) |
| A7 | YouTube, "Diatoms Under The Microscope" | https://www.youtube.com/watch?v=FFiGo3Ew66c | Diatom microscope clip (title only) |
| A8 | YouTube, "How to tackle Diatoms in Your Reef Tank" | https://www.youtube.com/watch?v=-p7D3O-vbSM | Diatom control (title only) |
| A9 | Gensou, cyanobacteria guide (commercial) | https://gensou.sg/?p=41043 | Sheet mats, gas bubbles, earthy smell when disturbed (snippet) |
| A10 | arXiv 1511.04339, cyanobacteria migration | https://arxiv.org/pdf/1511.04339 | Oxygen bubbles form only in light, not dark (freshwater *Microcystis*) |
| A11 | YouTube, "Cyanobacteria - A Reeftank Microscopy Story" | https://www.youtube.com/watch?v=QIw7wt7UgyI | Cyano under microscope (title only) |
| A12 | Reef Builders, Valonia | https://reefbuilders.com/2023/05/08/how-to-deal-with-valonia-bubble-algae | Bubble algae control (snippet) |
| A13 | YouTube, "Help! I Wrecked My Tank Ep. 3 - Managing Bubble Algae" | https://www.youtube.com/watch?v=47OFwE6BWLs | "Grows like wildfire", chokes corals (blurb) |
| A14 | YouTube, "Bubble Algae Control... Emerald Crabs" | https://www.youtube.com/watch?v=WPgtzXaoE2Y | Emerald crab control clip (title only) |
| A15 | Gensou, Chaetomorpha guide | https://gensou.sg/?p=23789 | Chaeto not palatable to fish or inverts; flow range (snippet) |
| A16 | YouTube, "Reef Tank Microscopy Ep.3 - Chaeto and Hair Algae" | https://www.youtube.com/watch?v=_V3PLCSPGs8 | Hair algae under microscope (title only) |
| A17 | ReefCentral, hair algae thread | https://forums.reefcentral.com/threads/hair-alge.954605 | Hair algae control anecdotes (snippet) |

---

## DIATOMS (sim: GLASS, SAND)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Texture / colour | Fine brown or golden-brown dust; matte; no strands or gel | Matte dust layer, tan to brown, low alpha | A5 [M] |
| Flow behaviour | Film blows off with a turkey baster and quickly returns | Wipe away in flow, re-film over time | A5 [M] |
| Growth / day-night | Common in new tanks; fades as tank matures (30–60 days) | Fast growth in first 60 days, then slow [M]; day/night: none [L] | A6 [M] |
| Grazed patches | Snails and gobies clean glass; leaves clean smear | Clean smear tracks on glass [L] | [L] |
| Tell | Silica cell walls, dusty look; matte not slimy | Matte, dusty, no sheen | A5 [M] |

## GREEN FILM (sim: GLASS, ROCK_TOP)
No source found. All values are [L].

| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Texture / colour | Thin green coat on glass and rock, slimy | Thin green alpha layer, soft edge | [L] |
| Flow behaviour | Not stated | Little motion, slight smear in flow | [L] |
| Growth / day-night | Grows in light; no source | Grow faster under light, slight retreat at night [L] | [L] |
| Grazed patches | Snails and blennies clean it | Clean patch on glass where snails pass [L] | [L] |
| Tell | Green, slimy, wipes off | Green sheen | [L] |

## HAIR (sim: ROCK_TOP, flow-exposed)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Texture / colour | Green filamentous strands; spreads fast (green hair); chaeto is a green macro, not a grazing target | Long green strands, branched; chaeto as a dense mat | A16 [M], A15 [M] |
| Flow behaviour | Sources conflict on flow needs; no source on Ceramium flow | Strands sway and stream with flow [L] | A15 [M] (conflict), [L] |
| Growth / day-night | Spreads quickly; no day/night source | Steady growth in light [L] | [M] (spread), [L] (day/night) |
| Grazed patches | Not palatable to fish or inverts [M]; one blenny reported to clear it (anecdote) | Short stubble on grazed rock; strands stay on ungrazed rock [L] | A15 [M], A17 [M] |
| Tell | Long, thin, tangled strands | Tangled thread geometry | [M] |

## CYANOBACTERIA (sim: ROCK_TOP, SAND)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Texture / colour | Sheet-like mat; red, brown or blue-green; gas bubbles; earthy smell when disturbed | Sheet mat with O2 bubble pockets; red-brown tint | A9 [M] |
| Flow behaviour | Sheets peel off rock when siphoned; bubbles lift parts of mat by day | Sheets peel and roll in flow; bubble pockets lift edges | A9 [M], A10 [M] (freshwater) |
| Growth / day-night | Oxygen bubbles form only in light (freshwater study); O2 drops at night from respiration; no source says mats lift at night | Bubbles and lifting in lights-on; mats stay flat at night | A10 [M], A9 [M]; lift at night [L] |
| Grazed patches | Siphon or scrape removes sheets; no grazers named | Bare patch where sheet peeled, pale rock beneath [L] | A9 [M] |
| Tell | Smooth sheet with bubbles, not strings | Continuous sheet, not filaments | A9 [M] |

## DINOFLAGELLATES (sim: SAND, ROCK_SHADE)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Texture / colour | Brown, slippery, stringy strands; gas bubbles trapped; not matte | Brown strands, glossy highlight | A1 [M] |
| Flow behaviour | Strings reform after shaking, pass through paper towel; sway in flow | Strands sway and stretch; re-form after disturbance | A2 [M] |
| Growth / day-night | Retreat into sand or rock at night; look clean in morning, worse by afternoon | Strands retract at lights-off; re-emerge at lights-on [M] | A1 [M] (commercial, overstated) |
| Grazed patches | Amphidinium on sand is hard to clear; no grazing source | Sand patch with brown stain persists [L] | A1 [M] |
| Tell | Bubbles do not confirm dinos (cyano and hair algae also trap bubbles) | Give bubbles to cyano and hair, not dinos only | A2 [M], A3 [M] (ID only) |

## BUBBLE (sim: ROCK_SHADE)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Texture / colour | Green, spherical, shiny cell wall; few mm to a couple of inches | Glossy green sphere, size 2–50 mm [M] | A12 [M] |
| Flow behaviour | Anchored by holdfast; sways gently; popping releases spores (disputed) | Anchored sphere, slight sway; no streaming | A12 [M] |
| Growth / day-night | Spreads fast ("like wildfire" per one blog) | Steady growth, no day/night change [L] | A13 [M] |
| Grazed patches | Emerald crabs tear them; crab can ignore if well-fed; fish largely ineffective | Torn sphere; crab-picked stalk [M] | A14 [M] |
| Tell | Single large cell, spherical | Round balloon shape | A12 [M] |

## CORALLINE (sim: ROCK_SHADE)
No source found for flow or day-night. Values are [L]. Coralline is a desired crust, not a nuisance.

| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Texture / colour | Pink or purple hard crust | Flat pink or purple crust, matte | A4 [M] (title only) |
| Flow behaviour | No motion (calcareous) | Static | [L] |
| Growth / day-night | Slow growth; no day/night source | Slow growth; no day/night change [L] | [L] |
| Grazed patches | Grazers can reduce it (one commenter in A17 thread) | Scrape marks: pale patches [L] | A17 [M] |
| Tell | Hard, flat, ridged edges | Hard crust, ridge detail | [L] |

---

## Priority fixes for Opus
1. **Dinoflagellates should be glossy brown strings that stretch with flow and retract into sand or rock at night** (A1 [M]). Bubbles alone must not mark them as dinos (A2 [M]).
2. **Cyanobacteria are sheets with O2 bubble pockets that lift only in light** (A10 [M] freshwater); at night mats stay flat (A9 [M]). Sheets peel as sheets, not strands.
3. **Diatoms are matte dust that wipes off and re-films, not strands** (A5 [M]). Keep them off the strand/gel path.
4. **Hair algae flow and grazing are unsourced for Ceramium** (A15 [M] conflicts). Keep flow behaviour [L] until checked. Grazed patches show stubble.
5. **FILM and CORALLINE have no source; all values are [L].** Bubble algae should be a static anchored sphere (A12 [M]). Verify the FILM and CORALLINE visuals against footage before tuning, and watch A3, A7 and A11 when available.
</content>
