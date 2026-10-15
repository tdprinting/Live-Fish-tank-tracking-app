# Tee's tank at night, feeding under the blue moonlights (owner video, 2026-10-15)

Top-tier evidence: Tee's own 60 gal tank, one 2 min 33 s phone video (IMG_0106, 900 x 1600, 10 fps, mono AAC
audio) taken at night with only the blue moonlights on, right after Tee threw food in. The camera pans the front glass
left to right, then goes close on the corals and the sand. Cite as `[N 1:24]` (time in the clip). Everything seen is
**[H]**; an identification carries its own confidence; reasoned values are **[L]**.
Companion to [tee_tank_night.md](tee_tank_night.md) (torch clips, lights off) and [tee_tank_photos.md](tee_tank_photos.md).

## Fish and inverts during a night feeding
| Animal | What it did | Sim before | Sim change | Cite |
|---|---|---|---|---|
| Blue tang (Dori) | Up and swimming through mid-water, darting at food over the rock | Slept through it | **Wakes for food** | [N 0:08, 0:20, 0:28-0:34, 2:28-2:32] |
| Ocellaris pair (Nemo, Marlin) | Both out of the host, high in the tank near the top rock, together, snapping at falling food | Female only, male stayed nestled | **Both wake for food** | [N 0:04, 0:20-0:22, 0:30-0:36, 1:16-1:20] |
| Pajama cardinal | Hovering mid-water at the rock front, feeding | Already fed at night | none | [N 0:24, 0:28, 1:28] |
| Cleaner wrasse (Shakira) | Swimming actively low and mid-water, along the rock face and over the sand | Stayed in its cocoon | **Wakes for food** | [N 0:02, 0:36-0:38, 1:04-1:10, 2:08-2:14] |
| Diamond goby (Sandy) | Out on open sand at the front, between the zoa frags, lying in a shallow scoop, head raised, then sifting | Stayed in its burrow | **Wakes for food that reaches the sand** | [N 0:52-1:00, 1:24-1:26, 2:12-2:16] |
| Engineer goby (Ricky Bobby Sanchez) | Out on open sand beside the red macro at the front, cream saddles on black (juvenile pattern) | Stayed in its burrow | **Wakes for food near its burrow** | [N 1:12, 2:20-2:26] |
| Falco hawkfish (Hawk tuah) | Stayed perched upright in the cave under the flat table rock, head out, watching | Rested on the sand | none (stays put either way) | [N 1:24-1:28] |
| Lawnmower blenny (Dallas) | Lying still on top of the big green hairy-mushroom mound, not feeding | Rested in its hole | none (noted: a mushroom-top rest spot is a possible extra) | [N 0:12-0:16] |
| Coral banded shrimp (Spike) | Out on the open rock face and under an overhang, walking upside down, long white antennae sweeping, picking with its claws | Out at night already | none | [N 0:04, 0:40-0:50, 1:24, 2:02-2:06] |
| Leopard wrasse (Marina) | Not seen: buried in the sand | Buried | none | whole clip |
| Dottyback, tailspot blenny | Not clearly seen | | none | |

Rule implemented (`Feed` in `ai/CommonBehaviors.kt`): for `NIGHT_WAKE_S` = 150 s after a feeding at night, a hungry
tang, clown, cleaner wrasse, diamond goby or engineer goby scores feeding at 0.86-0.93, and its night rest (sleep,
cocoon, nestle, burrow) is damped to 60 % so it steps aside. A roused fish sees sinking food as silhouettes against the
moonlit surface (`Perceive.ROUSED_LIGHT` = 0.15 effective light [L]) and follows the scent out of its cave or burrow
(no line of sight needed). Once it is fed (hunger <= 0.2) or the food is gone, the rest wins again. The hawkfish,
blennies and leopard wrasse are left out because they did not move in the clip. Test: `NightFeedingTest`.

## New or different things in the tank
| Item | Observation | Action | Cite |
|---|---|---|---|
| Lower-right lime mound | Close up it is dozens of hairy discs, each with a **purple-pink oral centre**: a big **green hairy mushroom (Rhodactis) colony** (~20 cm across, on a rock on the sand near the front-right), not long-polyp GSP. The upper-left lime dome is long-polyp GSP (thin curling tentacles, no discs) | Sent to the coral thread (owns coral models) | [N 0:10-0:16, 1:38-1:42] vs [N 1:32-1:36] |
| Feather duster | Purple-red one-sided fan just behind and left of that mushroom mound, near the right end | Placement note for the coral/invert owner | [N 1:44-1:46] |
| Red macro | Bushy red clumps at the foot of the centre-front rock and on the sand by the engineer goby; a red feathery clump on the sand at the front | Placement note | [N 1:12-1:20, 1:50, 2:20-2:26] |
| Sand frags | Two frag rocks on the sand front-centre: a yellow-lime coral on a pink-rimmed base (left) and an orange zoa colony with yellow rings (right, ~10 cm apart) | Matches `coral_shroom_front_left` / `coral_zoa_front_center` area | [N 0:04, 0:52-1:00, 2:12] |
| Table rock | A flat plate rock ~5 cm thick roofs a dark cave left of the Kenya tree, mid-height; the hawkfish and the banded shrimp use the cave | The current central arch / `CAVE_SHELF` | [N 0:04, 1:24-1:26] |
| Equipment | Black powerhead on the back glass mid-right, Flipper cleaner low on the front glass, clear return tube across the upper left | Already modelled | [N 0:08, 0:30, 1:32] |

Nothing in the clip is outside Tee's livestock list.

## Sound
Both of Tee's clips with audio (this video and `reference/kenya_clip/kenya_tree_tee.mov`) agree. Median spectrum over the
clip (robust to splashes and handling), octave bands relative to the total:

| | 63 Hz | 125 | 250 | 500 | 1 k | 2 k | 4 k | 8 k | centroid | 50 ms level p10-p90 | drips/s | tonal lines |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Tee, IMG_0106 | -9 | -5 | -6 | -7 | -14 | -20 | -27 | -28 | 287 Hz | 2.8 dB | 0.12 | 59, 102, 121, 238 Hz (+10 to +17 dB) |
| Tee, Kenya clip | -8 | -6 | -5 | -8 | -14 | -18 | -22 | -24 | 340 Hz | 2.2 dB | 0.16 | 59, 90, 121, 148, 238, 301 Hz |
| Sim before | -5 | -5 | -7 | -9 | -16 | -28 | -38 | -59 | 195 Hz | 4.3 dB | 0.67 | none |
| Sim after | -5 | -5 | -7 | -10 | -14 | -18 | -30 | -47 | 239 Hz | 3.1 dB | ~0 | 60, 120 Hz (+8, +9) |

What a real tank sounds like here: a steady bed (no surges), most energy in the low water rumble, a smooth HOB-waterfall
"shhh" that peaks at 1-2.5 kHz (the old mix had almost nothing above 1 kHz), soft 60 Hz-mains pump hum at 60, 120 and
240 Hz, and only an occasional drip. Changes (`tools/audio/generate_sounds.py`, `TankSoundMix`): HOB loop is now that
smooth waterfall hump with one soft drip per loop; hum loop has 60/120/180/240/300 Hz lines (kept ~6 dB gentler than the
recording so it does not read as a buzz on phone speakers); flow surges cut from 30 % to 10 %; bubble pops about a
third as often and half as loud; the night mix eases off to 85 % instead of 50 % (the pumps run all night).
The 8 kHz band stays below the recording on purpose: above 4 kHz the phone mic mostly hears room noise, and that hiss was
what made the earlier mix "scratchy".
