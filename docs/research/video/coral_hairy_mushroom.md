# Green hairy mushroom (Rhodactis sp.), the clown pair's host

Sim hook: `CoralKind.MUSHROOM` (already matches "rhodactis") · Disc: 3-8 cm in Tee's tank [H], genus up to 20+ cm [M] · Tank count: a cluster of 5+ discs on the lower-left rock [H]

**Tee confirmed 2026-10-13: the two ocellaris host in these hairy green mushrooms.** The sim currently anchors the pair to `HOST_KENYA`; that is wrong for this tank.

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| T84 | Tee's IMG_0084.mov (night, torch) | owner upload 2026-10-13 | Clown pair hovering 1-3 cm above a cluster of fuzzy green discs on purple coralline rock |
| W1 | Animal-World, green hairy mushroom | https://animal-world.com/green-hairy-mushroom | Short split-ended tentacles in radial rows; larger than the purple form |
| W2 | Animal-World, purple hairy mushroom | https://animal-world.com/purple-hairy-mushroom | Purple form often hosts clownfish; branched marginal tentacles |
| W3 | Quality Marine, Hairy Mushroom | https://www.qualitymarine.com/quality-marine/coral/polyps-mushroom/rhodactis/mushroom-hairy-55517/ | Rhodactis as low-maintenance clown host |
| W4 | LiveAquaria, hairy mushroom coral | https://liveaquaria.com/products/hairy-mushroom-coral | Sold sizes 1-5 in |
| W5 | Reef Central thread "hairy mushrooms" | https://forums.reefcentral.com/threads/hairy-mushrooms.320480 | Anecdote: very large Rhodactis can eat small fish (likely elephant ear); not seen here |
| W6 | Seascape Studio coral guide | https://seascapestudio.net/reference/cnidarian.php?id=61 | Genus can exceed 10 in across |

## Appearance (for model and texture)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Shape | Flat-to-domed round discs, edges touching, packed in a cluster across the rock face | Disc radius 2-4 cm, 5-8 discs, 10-20% overlap | [T84 0:00] [H] |
| Surface | Whole oral disc covered in short fuzzy "hairs" (tentacles), giving a velvet/moss look; no visible central mouth at this resolution | Normal-map of dense short bumps, 1-2 mm tentacles, radial rows | [T84 0:00] [H], [W1] [M] |
| Colour (white torch) | Dull olive-to-grey green, paler at the rim, sitting on bright pink/purple coralline | Base #6E7F5A-ish olive green, rim lighter | [T84 0:00] [H] |
| Colour (blue actinics) | Green hairy Rhodactis typically fluoresce green | Emissive green under blue channel | [L] |
| Night state | Discs stay spread and fuzzy at night (not closed) | No night closure; slight 10-15% deflation | [T84 0:00-0:03] [H] |

## Behaviour (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Host role | Both clowns sleep and hover over the cluster, 1-3 cm above, body-length apart, female (orange) slightly higher | Add rock anchor `HOST_SHROOM` on the lower-left rock at the mushroom cluster; set clown `hostAnchor` to it; host radius ~ cluster radius (~8 cm) | [T84 0:00-0:03] [H], Tee |
| Contact | Clowns nuzzle/rub disc surfaces (typical of hosted clowns) | Rub animation every 5-20 s while near host by day | [W2] [M] / [L] |
| Sting | Rhodactis has no meaningful sting for clowns | No acclimation sting penalty | [W3] [M] |
| Host stress | Constant contact can make discs partly close | When clowns rub, disc deflation 20% for 30-60 s | [L] |
| Predation | Large Rhodactis rarely engulf small fish | Do not model for discs under 10 cm | [W5] [M] |

## Priority fixes for Opus
1. Move the clown pair's host from `HOST_KENYA` to a new `HOST_SHROOM` anchor on the lower-left purple rock where the green hairy mushroom cluster sits.
2. Model the cluster as 5-8 fuzzy olive-green discs (short-tentacle normal map) on coralline rock, staying open at night.
3. Clown host loop: hover 1-3 cm above discs, rub/nuzzle periodically, sleep there at night, female higher.
