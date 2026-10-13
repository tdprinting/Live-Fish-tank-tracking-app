# Cleaner wrasse (Labroides dimidiatus)

Sim hook: `BrainKind.WRASSE` · Adult size: ~10-12 cm [L] · Tank count: 1

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| S1 | Wikipedia Bluestreak cleaner wrasse | https://en.wikipedia.org/wiki/Bluestreak_cleaner_wrasse | greeting dance; station occupancy |
| S2 | FishBase | https://fishbase.se/summary/SpeciesSummary.php?id=5459 | unfamiliar visitor greeted with tail up-down dance |
| S3 | Australian Museum | https://australianmuseum.net.au/Common-Cleanerfish-Labroides-dimidiatus | enters mouth/gill chamber of large fish |
| S9 | Wainwright et al. 2002 / Fulton et al. 2005 (proxy, labrids) | https://fishlab.ucdavis.edu/wp-content/uploads/sites/397/2020/06/Wainwright-et-al-2002.pdf | labrid routine speeds ~1-3 BL/s (low fin aspect ratio) to 4-7 BL/s (high); Ucrit ~3->5 BL/s [M, proxy not species-specific] |

NOTE: No footage was watched. Wikimedia Commons video downloads (candidates found: clownfish CAS-Aquarium-Clownfish&Coral.ogv, Aquarium of Cattolica - Clownfishes.webm; Paracanthurus - August 14 2021.webm; Labroides dimidiatus cleaning Acanthurus mata - Gijon Aquarium) were blocked by HTTP 429 rate limiting from this server, and no usable Commons clip was found for the other species. YouTube rows V1-V3 remain queued for hand review. No rows upgraded to [H]; numbers stay [L] unless a source is cited.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion | labriform pectoral swimming, undulating bobbing | | [L] |
| Cruise | constant undulating travel along stripe line | 1-1.5 BL/s | [L] |
| Burst | quick | 6 BL/s | [L] |
| Fin beat | pecs ~5 Hz; rear body wiggles | | [L] |
| Posture | horizontal; dance = rear raised/lowered | rear bob +-20 deg, ~2 Hz, 2-4 s | [S1][S2] [M] |
| Idle | hovers at station | | [L] |
| Turning | tight | | [L] |
| Colour | black stripe over blue | | |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone | fixed cleaning station on rock | leash 3-5 BL | [S1] [M] |
| Day/night | diurnal; sleeps in crevice (mucous cocoon in some wrasses) | | [L] |
| Feeding | picks skin parasites/mucus off clients incl. mouth/gills | pecks at 2-3 Hz per client | [S3] [M] |
| Social | clients pause and hold still | client enters pose: stall, fins spread | [S3] [M] |
| V1 | Coral reef cleaning station - Parrotfish get a bath from a wrasse (YouTube) | https://www.youtube.com/watch?v=wQJ_q_P7XsI | wild station, parrotfish client [M] |
| V2 | Cleaner Wrasse Cleaning Barracuda Teeth at Kapalai Pier (YouTube) | https://www.youtube.com/watch?v=TOC2Qc2Qedw | wild, barracuda client [M] |
| V3 | RD's Fish tank (Cleaner Wrasse cleaning inside Mappa Puffer's gill) (YouTube) | https://www.youtube.com/watch?v=oDK4wOB7uaI | aquarium, gill cleaning [M] |
| Tells | rear-up dance, picking at others, hovering at station | | |

## Priority fixes for Opus
1. Greeting dance (rear up-down) when a fish approaches station.
2. Client "freeze" pose while being cleaned (mouth/gill inspection).
3. Fixed station with return behaviour.
