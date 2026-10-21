# Local fish stores: how they look and feel

Background for the 3D fish stores (docs/ECONOMY.md §5, core `StoreLife.kt`, `tools/blender/store_rooms.py`,
`store_fronts.py`, `store_people.py`). Gathered 2026-10-21 from store tours, hobby forums and reef club write-ups; it
is a design brief, not a citation list.

## Layouts people recognise

* **The pet shop with a fish corner** (Shelly's): a strip-mall unit with an awning and window decals, a bell on a
  glass door, dog and cat food near the front, the fish in a back corner on a rack of matching tanks. Drop ceiling,
  vinyl floor, a hand-written "fish of the week" board. Often a shop cat. Staff know regulars and their kids by name.
* **The coral garage** (Frag Shack): a converted garage or warehouse bay, the roll-up door open in business hours,
  concrete floor, open rafters, long shallow frag tanks under blue (actinic) light, a whiteboard of today's frags, RO/DI
  filters and a barrel of mixed salt water by the back. Relaxed, hobbyist-run; trading frags for store credit is normal.
  A shop dog is common.
* **The big aquarium store** (Big Blue): a big box with automatic sliding doors, carts, wide aisles, gondolas of dry
  goods, walls of identical display tanks by section, a fish room / quarantine rack behind a window, a free water
  testing station, a club bulletin board with meet-ups and frag swaps.
* **The high-end gallery** (Deep Reef): few tanks, each a showpiece, dark walls and track or spot lighting so the tanks
  glow, stone and brass, a seating area and coffee for customers, appointment-style service, "please don't tap the
  glass" signage.

## What makes a store feel lived in

* Staff doing the daily work in view: feeding, netting a fish into a bag, testing water, scraping glass, carrying
  buckets, ringing up the till, chatting with a regular at the counter.
* Regulars: the old-timer who has kept fish for decades, the coral collector photographing polyps, the parent and kid
  setting up a first tank. People trade frags and spare snails with each other.
* Kids pressing their hands and faces on the glass, pointing, asking for "that one", and being asked gently not to tap.
* Sound: the hum of many pumps and air stones, quiet voices, the door bell.

## How the game uses it

* Each store's size and finish rise with its unlock level: bay width (165, 175, 190, 240 cm), ceiling, floor (vinyl,
  concrete, large tile, marble) and lighting (troffer, shop light and string lights, high-bays, coffered spots).
* Each store has its own cast and routines (`StoreLife.CASTS`), its own lines for tapped glass and trades, and one
  regular who may offer the keeper a swap (`StoreTrades`).
