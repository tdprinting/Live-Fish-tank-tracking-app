# Video behaviour research

Per-species notes for refining animation and AI behaviour, one file per organism in the current sim
(fish and inverts by `BrainKind`, corals by `CoralKind`, pods by `MicroGroup`, algae by `AlgaeGroup`).
Each file follows [_TEMPLATE.md](_TEMPLATE.md): an **Animation spec** (renderer), a **Behaviour spec** (AI),
and **Priority fixes for Opus**.

## How to read the evidence tags
- **[H]** seen in footage. So far only Tee's own night clips ([tee_tank_night.md](tee_tank_night.md)): YouTube
  refuses video playback to the research server and Wikimedia Commons rate-limited it, so no third-party footage was watched.
- **[M]** stated by a source (care guide, paper, video title/description from search results).
- **[L]** reasoned estimate. Most numbers (speeds, Hz, timings) are [L] and are starting values to tune.

YouTube links (`V#` rows) came from search listings and are queued for hand review. **Start with [SIM_CHANGES.md](SIM_CHANGES.md)**:
the reviewed, code-mapped change list from the 2026-10-13 pass.

## Files
| Group | Files |
|---|---|
| Fish | [ocellaris](fish_ocellaris_clownfish.md) · [blue tang](fish_blue_tang.md) · [pajama cardinal](fish_pajama_cardinalfish.md) · [cleaner wrasse](fish_cleaner_wrasse.md) · [engineer goby](fish_engineer_goby.md) · [falco hawkfish](fish_falco_hawkfish.md) · [lawnmower blenny](fish_lawnmower_blenny.md) · [tailspot blenny](fish_tailspot_blenny.md) · [indigo dottyback](fish_indigo_dottyback.md) |
| Inverts | [coral banded shrimp](invert_banded_shrimp.md) · [skunk cleaner shrimp](invert_cleaner_shrimp.md) · [hermits](invert_hermit.md) · [sally lightfoot](invert_sally_crab.md) · [brittle star](invert_brittle_star.md) · [tube worm](invert_feather_duster.md) · [nassarius](invert_nassarius.md) · [astrea](invert_astrea.md) · [asterina](invert_asterina.md) |
| Corals & macro | [Kenya tree](coral_kenya.md) · [GSP](coral_gsp.md) · [zoas/palys](coral_zoa.md) · [clove](coral_clove.md) · [mushroom](coral_mushroom.md) · [organ pipe](coral_organ_pipe.md) · [red macro](coral_macro.md) · [aiptasia](coral_aiptasia.md) |
| Microfauna | [all 16 MicroGroups](micro_fauna.md) |
| Algae | [all 7 AlgaeGroups](algae_visual.md) · [growth drivers and kinds](algae_growth.md) |
| Microfauna (more kinds) | [catalog of extra cryptic fauna](microfauna_catalog.md) |
| Host coral | [green hairy mushroom, the clowns' host](coral_hairy_mushroom.md) |
| Sand | [how animals move and stir sand](sand_bioturbation.md) |
| Tee's tank | [night clips, lights off](tee_tank_night.md) · [daylight photos: long-polyp GSP, leathers, elephant ear](tee_tank_photos.md) |

## Headline findings for Opus
- Juvenile blue tang "plays dead" on its side when startled and rests that way at night; add a roll-to-side pose.
- Falco hawkfish has no swim bladder: perch-hop and ambush from a perch, never cruise.
- Pajama cardinal is nocturnal: hides by day in a loose shoal, feeds after lights-off.
- Cleaner wrasse and skunk shrimp work fixed cleaning stations with a greeting dance; clients freeze.
- Engineer goby digs by carrying sand in its mouth and spitting it out, then peeks from the burrow.
- Brittle star arms bend sideways and row; nassarius stay buried with siphon up, then the sand "boils" at feeding.
- `MicroGroup.CALANOID` is labelled "Tigger pod", but Tigger pods are *Tigriopus californicus*, a harpacticoid
  (~1 mm); the calanoid sold for reefs is *Parvocalanus*. Label and size need a fix.
- Dinoflagellates are glossy brown strings that retreat at night; cyano mats lift with O₂ bubbles only in light.
