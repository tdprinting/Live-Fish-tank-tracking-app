"""The outside of the fish stores: each store's front wall with its door and windows, the street in front and the sky
(Tee 2026-10-21: "enter in the front door of the fish shop"). The camera starts on the sidewalk and walks in through
the door (render/StoreRoom.kt, Camera.storeEntry).

  FrontBay   one bay of the store's front wall (windows, the facade's own look), every bay but the door's
  FrontDoor  the door bay: the opening, its frame, the store's sign above it
  DoorL/R    door leaves (pivot at the hinge, or the panel's centre for sliding doors); DoorUp a roll-up door
  Street     sidewalk, curb, road or parking lot and street furniture, drawn once in front of the door
  Neighbor   a neighbouring building's front, drawn past each end of the store
  Sky        a big backdrop behind the roofs (store.frag paints the gradient)

The front wall's outer face is at z = FRONT + WALL. Materials named out_* are lit by the sun and sky (store.frag kind
10), sky* is the backdrop (kind 13).
"""
import math

import numpy as np

from store_geo import Group, cylinder, ellipsoid, mat, text
from store_people import loft

WALL = 14.0


class Front:
    """The geometry every front piece shares: bay span, floor, ceiling, where the front wall stands."""

    def __init__(self, x0, x1, cx, floor, ceil, front):
        self.x0, self.x1, self.cx = x0, x1, cx
        self.floor, self.ceil, self.front = floor, ceil, front
        self.top = ceil + 70           # parapet
        self.out = front + WALL        # outer face


def wall_with_hole(g, f, hx0, hx1, hy0, hy1, m, inner):
    """The front wall over the bay with a rectangular hole (window or door), inner face painted [inner]."""
    z0, z1 = f.front, f.out
    for lo, hi in (((f.x0, f.floor, z0), (hx0, f.top, z1)), ((hx1, f.floor, z0), (f.x1, f.top, z1)),
                   ((hx0, f.floor, z0), (hx1, hy0, z1)), ((hx0, hy1, z0), (hx1, f.top, z1))):
        if hi[0] - lo[0] > 0.01 and hi[1] - lo[1] > 0.01:
            g.box((lo[0], lo[1], z0 + 1.0), hi, m)
            g.box(lo, (hi[0], hi[1], z0 + 1.0), inner)


def frame(g, x0, x1, y0, y1, z, w, m):
    """A window or door frame of bars [w] thick on the plane z."""
    for lo, hi in (((x0 - w, y0 - w, z), (x1 + w, y0, z + w)), ((x0 - w, y1, z), (x1 + w, y1 + w, z + w)),
                   ((x0 - w, y0, z), (x0, y1, z + w)), ((x1, y0, z), (x1 + w, y1, z + w))):
        g.box(lo, hi, m)


# ============================================================== each store's front
STYLES = {
    # a strip-mall pet shop: beige stucco, a striped awning, vinyl letters, a glass door with a bell
    "shellys": dict(wall="#E8D6B4", base="#B98F6A", frame="#C9CCD1", awning=("#E23B3B", "#FFFFFF"), door="swing1",
                    door_w=96, door_h=212, win_y=(42, 210), sign="#E23B3B", sign_board="#FFF6E2", neighbor="#D8C8A8"),
    # a beach garage: teal corrugated siding, a roll-up door, a painted board sign
    "frag_shack": dict(wall="#3E8C8A", base="#2B4A4A", frame="#1C1E23", awning=None, door="rollup",
                       door_w=150, door_h=222, win_y=(70, 190), sign="#FFFFFF", sign_board="#7A5236", neighbor="#C9B89A"),
    # a big box: blue metal panels over a glass curtain wall, automatic sliding doors
    "big_blue": dict(wall="#1F4F9A", base="#C7CDD4", frame="#8C939C", awning=None, door="slide",
                     door_w=150, door_h=232, win_y=(10, 250), sign="#5FD7FF", sign_board="#0F2A55", neighbor="#B9C0C8"),
    # a gallery: charcoal stone, bronze-framed tall windows and double doors, brass lanterns
    "deep_reef": dict(wall="#24222A", base="#141218", frame="#8A6A3A", awning=None, door="swing2",
                      door_w=130, door_h=250, win_y=(30, 270), sign="#E2C46A", sign_board="#141218", neighbor="#3A3640"),
}


def front_bay(f, sid, name):
    """A bay of the front wall with a window (or the door bay with the door opening) and the store's dressing."""
    st = STYLES[sid]
    g = Group(name)
    door = name == "FrontDoor"
    wallm = mat("out_wall", st["wall"], 0.85)
    inner = mat("wall_front", "#E8E4DC" if sid != "deep_reef" else "#2A2420", 0.9)
    framem = mat("out_frame", st["frame"], 0.35, metal=0.0)
    glass = mat("glass_shop", "#CFE6EE", 0.04, alpha=0.18)
    cx, fl = f.cx, f.floor
    if door:
        dw, dh = st["door_w"], st["door_h"]
        # windows either side of the door on the big-box and gallery fronts
        side = sid in ("big_blue", "deep_reef")
        hx0, hx1 = (f.x0 + 14, f.x1 - 14) if side else (cx - dw / 2, cx + dw / 2)
        hy1 = fl + (st["win_y"][1] if side else dh)
        wall_with_hole(g, f, hx0, hx1, fl, hy1, wallm, inner)
        frame(g, cx - dw / 2, cx + dw / 2, fl, fl + dh, f.out - 1, 4, framem)
        if side:
            for x0, x1 in ((hx0, cx - dw / 2 - 4), (cx + dw / 2 + 4, hx1)):
                g.box((x0, fl, f.front + 6), (x1, hy1, f.front + 6.6), glass)
                frame(g, x0, x1, fl, hy1, f.front + 5, 3, framem)
            # a transom over the door
            g.box((cx - dw / 2, fl + dh + 4, f.front + 6), (cx + dw / 2, hy1, f.front + 6.6), glass)
        g.box((cx - dw / 2 - 6, fl - 0.4, f.front), (cx + dw / 2 + 6, fl + 0.3, f.out + 10), mat("out_threshold", "#8C939C", 0.4))
        if st["door"] == "rollup":
            # the roll-up door's housing over the opening (inside) and its side tracks
            g.box((cx - dw / 2 - 6, fl + dh, f.front - 26), (cx + dw / 2 + 6, fl + dh + 30, f.front), mat("rollup_housing", "#3A3F48", 0.5))
            for s in (-1, 1):
                g.box((cx + s * (dw / 2 + 1) - 3, fl, f.front - 6), (cx + s * (dw / 2 + 1) + 3, fl + dh, f.front), mat("rollup_track", "#5A6068", 0.4))
    else:
        y0, y1 = st["win_y"]
        hx0, hx1 = f.x0 + 18, f.x1 - 18
        wall_with_hole(g, f, hx0, hx1, fl + y0, fl + y1, wallm, inner)
        g.box((hx0, fl + y0, f.front + 6), (hx1, fl + y1, f.front + 6.6), glass)
        frame(g, hx0, hx1, fl + y0, fl + y1, f.out - 1, 3.5, framem)
        if sid == "big_blue":
            for x in np.linspace(hx0, hx1, 4)[1:-1]:
                g.box((x - 2, fl + y0, f.out - 1), (x + 2, fl + y1, f.out + 2), framem)
        if sid == "deep_reef":
            g.box(((hx0 + hx1) / 2 - 2, fl + y0, f.out - 1), ((hx0 + hx1) / 2 + 2, fl + y1, f.out + 2), framem)
    _dress(g, f, sid, st, door)
    return g


def _dress(g, f, sid, st, door):
    """Each front's look: base course, awning, siding ribs, panels, lanterns."""
    cx, fl, out = f.cx, f.floor, f.out
    base = mat("out_base", st["base"], 0.8)
    if sid == "shellys":
        g.box((f.x0, fl, out), (f.x1, fl + 40 if not door else fl + 0.1, out + 2), base) if not door else None
        # a striped awning over the window or door, and the parapet cap
        a0, a1 = st["awning"]
        n = 8
        y = fl + (st["win_y"][1] if not door else st["door_h"]) + 18
        for k in range(n):
            x0 = f.x0 + 10 + (f.x1 - f.x0 - 20) * k / n
            x1 = f.x0 + 10 + (f.x1 - f.x0 - 20) * (k + 1) / n
            m = mat("out_awning_a" if k % 2 == 0 else "out_awning_b", a0 if k % 2 == 0 else a1, 0.8)
            g.add(_slope((x0, y, out), (x1, y, out + 70), drop=34), m)
        g.box((f.x0, f.top - 8, out), (f.x1, f.top, out + 6), mat("out_cap", "#C9B48E", 0.7))
        if not door:
            # pet food bags stacked in the window and a paw-print decal
            for k in range(4):
                col = ("#2F8FE0", "#E8463C", "#45B36B", "#F3C33B")[k]
                x = f.cx - 45 + k * 30
                g.box((x - 11, fl + 42, f.front - 8), (x + 11, fl + 82, f.front - 0.5), mat(f"food_bag_{k}", col, 0.7))
            for k in range(3):
                x, y = f.cx + 30 + k * 14, fl + 150 + (k % 2) * 10
                for (dx, dy, r) in ((0, 0, 4), (-4, 5, 1.6), (0, 6.5, 1.6), (4, 5, 1.6)):
                    g.add(ellipsoid((x + dx, y + dy, f.front + 7), (r, r, 0.3), 8, 3), mat("decal_paw", "#FFFFFF", 0.6))
    elif sid == "frag_shack":
        # corrugated siding ribs and wooden posts
        rib = mat("out_rib", "#357A78", 0.6)
        x = f.x0 + 4
        while x < f.x1 - 2:
            if not (door and abs(x - cx) < st["door_w"] / 2 + 6) and not (not door and f.x0 + 14 < x < f.x1 - 14 and fl + st["win_y"][0] - 4 < fl + st["win_y"][1]):
                g.box((x - 1.2, fl, out), (x + 1.2, f.top, out + 2.2), rib)
            elif not door:
                for lo, hi in (((x - 1.2, fl, out), (x + 1.2, fl + st["win_y"][0] - 4, out + 2.2)), ((x - 1.2, fl + st["win_y"][1] + 4, out), (x + 1.2, f.top, out + 2.2))):
                    g.box(lo, hi, rib)
            else:
                g.box((x - 1.2, fl + st["door_h"] + 4, out), (x + 1.2, f.top, out + 2.2), rib)
            x += 9
        post = mat("out_wood", "#7A5236", 0.85)
        for xx in (f.x0 + 4, f.x1 - 4):
            g.box((xx - 5, fl, out), (xx + 5, f.top, out + 8), post)
        if not door:
            # a coral poster in the window and a beach cruiser bike
            g.box((cx - 26, fl + 100, f.front - 1), (cx + 26, fl + 170, f.front - 0.4), mat("poster_coral", "#3C2E78", 0.7))
            for k, col in enumerate(("#FF6FA8", "#7DFF6A", "#5BE0FF")):
                g.add(ellipsoid((cx - 12 + k * 12, fl + 130 + (k % 2) * 12, f.front - 0.3), (7, 9, 0.4), 10, 4), mat(f"glow_poster{k}", col, 0.4, emit=col, strength=0.8))
    elif sid == "big_blue":
        # flat metal panels with seams, a white base, a fascia band over the windows
        seam = mat("out_seam", "#163C78", 0.6)
        for x in np.linspace(f.x0, f.x1, 4):
            g.box((x - 0.6, fl + st["win_y"][1], out), (x + 0.6, f.top, out + 0.8), seam)
        g.box((f.x0, fl + st["win_y"][1] + 8, out), (f.x1, fl + st["win_y"][1] + 12, out + 3), mat("out_band", "#5FD7FF", 0.4))
        if not door:
            g.box((f.x0, fl, out), (f.x1, fl + st["win_y"][0], out + 1), base)
    else:
        # stone slabs, a bronze band and a brass lantern each side of the door / window
        seam = mat("out_seam", "#141218", 0.8)
        for y in np.arange(fl + 60, f.top, 60):
            g.box((f.x0, y - 0.5, out), (f.x1, y + 0.5, out + 0.5), seam)
        g.box((f.x0, f.ceil - 6, out), (f.x1, f.ceil, out + 3), mat("out_bronze", "#8A6A3A", 0.3))
        for s in (-1, 1):
            x = cx + s * ((st["door_w"] / 2 + 26) if door else (f.x1 - f.x0) / 2 - 10)
            g.box((x - 5, fl + 190, out), (x + 5, fl + 196, out + 8), mat("out_bronze", "#8A6A3A", 0.3))
            g.add(cylinder((x, fl + 176, out + 12), 5, 22, 12), mat("lamp_lantern", "#FFE2A8", 0.3, emit="#FFC870", strength=3.0))
            g.add(cylinder((x, fl + 198, out + 12), 6.5, 4, 12), mat("out_bronze", "#8A6A3A", 0.3))
            # a clipped topiary in a black planter
            if door:
                px = cx + s * (st["door_w"] / 2 + 40)
                g.add(cylinder((px, fl, out + 40), 18, 52, 16), mat("out_planter", "#141218", 0.4))
                g.add(ellipsoid((px, fl + 88, out + 40), (22, 26, 22), 14, 10), mat("out_leaf", "#2E6B3A", 0.8))
                g.add(cylinder((px, fl + 52, out + 40), 2, 14, 8), mat("out_wood", "#5A3A22", 0.8))


def _slope(lo, hi, drop):
    """A sloping awning panel from the wall at [lo] (x0, y, z) out to [hi] (x1, y - drop, z1), with a front valance."""
    x0, y, z0 = lo
    x1, _, z1 = hi
    V = [(x0, y, z0), (x1, y, z0), (x1, y - drop, z1), (x0, y - drop, z1), (x0, y - drop - 12, z1), (x1, y - drop - 12, z1)]
    F = [(0, 3, 2, 1), (0, 1, 2, 3), (3, 4, 5, 2), (3, 2, 5, 4)]
    return np.array(V, float), F


def front_sign(f, sid, display_name):
    """The store's name above the door, outside: lit letters on the facade (a board behind them on some)."""
    st = STYLES[sid]
    g = Group("FrontSign")
    y = f.floor + (st["win_y"][1] + 46 if sid in ("big_blue", "deep_reef") else st["door_h"] + 76)
    y = min(y, f.top - 34)
    width = {"shellys": 300, "frag_shack": 320, "big_blue": 420, "deep_reef": 320}[sid]
    if sid == "frag_shack":
        g.box((f.cx - width / 2, y - 26, f.out), (f.cx + width / 2, y + 26, f.out + 3), mat("out_board", st["sign_board"], 0.8))
    elif sid == "big_blue":
        g.box((f.cx - width / 2, y - 34, f.out), (f.cx + width / 2, y + 34, f.out + 6), mat("out_board", st["sign_board"], 0.5))
        # the fish logo
        g.add(ellipsoid((f.cx - width / 2 + 34, y, f.out + 7), (22, 13, 2), 16, 6), mat("neon_logo", "#5FD7FF", 0.3, emit="#5FD7FF", strength=3.0))
    size = {"shellys": 26, "frag_shack": 30, "big_blue": 32, "deep_reef": 26}[sid]
    lit = mat("neon_front", st["sign"], 0.3, emit=st["sign"], strength=3.5 if sid != "deep_reef" else 2.2)
    shift = 22 if sid == "big_blue" else 0
    t = text(display_name, f.cx + shift, y, f.out + (6.5 if sid == "big_blue" else 3.2), size, lit, depth=2.0)
    if sid == "shellys":
        # a little fish beside the name
        g.add(ellipsoid((f.cx - 168, y, f.out + 3), (14, 8, 2), 12, 6), lit)
    elif sid == "deep_reef":
        # a bronze rule under the name
        g.box((f.cx - width / 2, y - 24, f.out), (f.cx + width / 2, y - 22, f.out + 2), mat("out_bronze", "#8A6A3A", 0.3))
    return g, [t]


def doors(f, sid):
    """The door leaves, each about its own pivot (render/StoreRoom.kt opens them as someone comes near)."""
    st = STYLES[sid]
    dw, dh, fl, cx = st["door_w"], st["door_h"], f.floor, f.cx
    framem = mat("out_frame", st["frame"], 0.35)
    glass = mat("glass_door", "#D6ECF2", 0.04, alpha=0.2)
    out = []
    z = f.front + WALL * 0.5
    kind = st["door"]
    if kind == "rollup":
        g = Group("DoorUp", pivot=(cx, fl, z - 9))
        slat = mat("rollup_slat", "#C8CDD2", 0.5)
        for k in range(int(dh // 8)):
            y = fl + k * 8
            g.box((cx - dw / 2, y + 0.4, z - 10), (cx + dw / 2, y + 7.6, z - 8), slat)
        g.box((cx - dw / 2, fl, z - 10.5), (cx + dw / 2, fl + 3, z - 7.5), mat("rollup_bar", "#5A6068", 0.4))
        out.append(g)
        return out
    leaves = [(-1, dw if kind == "swing1" else dw / 2)] if kind == "swing1" else [(-1, dw / 2), (1, dw / 2)]
    for s, w in leaves:
        name = "DoorL" if s < 0 else "DoorR"
        if kind in ("swing1", "swing2"):
            hx = cx + s * dw / 2
            g = Group(name, pivot=(hx, fl, z))
            x0, x1 = (hx, hx + w) if s < 0 else (hx - w, hx)
        else:
            # sliding: the leaf's centre; it slides out sideways
            x0, x1 = (cx - w, cx) if s < 0 else (cx, cx + w)
            g = Group(name, pivot=((x0 + x1) / 2, fl, z))
        t = 5 if kind != "swing2" else 7
        g.box((x0 + t, fl + t, z - 0.5), (x1 - t, fl + dh - t, z + 0.5), glass)
        frame(g, x0 + t, x1 - t, fl + t, fl + dh - t, z - 1.5, t, framem)
        # a push bar / handle
        hx = x1 - 14 if s < 0 else x0 + 14
        if kind == "swing2":
            g.box((hx - 1.5, fl + 80, z + 2), (hx + 1.5, fl + 170, z + 5), mat("gold_handle", "#C9A646", 0.25, metal=0.9))
        else:
            g.box((x0 + 12, fl + 96, z + 2), (x1 - 12, fl + 100, z + 5), mat("out_metal", "#C9CCD1", 0.3))
        if kind == "swing1":
            # OPEN sign and the bell over the door
            g.box((x0 + 26, fl + 130, z + 0.6), (x0 + 66, fl + 146, z + 1.0), mat("neon_open", "#FF3D5A", 0.3, emit="#FF3D5A", strength=3.0))
        out.append(g)
    return out


def street(f, sid):
    """The sidewalk, the road or parking lot and the street furniture in front of the store."""
    g = Group("Street")
    st = STYLES[sid]
    fl, out, cx = f.floor, f.out, f.cx
    walk = mat("out_sidewalk", "#BDB7AC" if sid != "frag_shack" else "#D8CBA8", 0.9)
    road = mat("out_asphalt", "#3B3E44", 0.9)
    paint = mat("out_paint", "#F2F2EC", 0.7)
    x0, x1 = cx - 1400, cx + 1400
    g.box((x0, fl - 20, out), (x1, fl, out + 250), walk)
    for x in np.arange(x0, x1, 120):
        g.box((x - 0.6, fl, out), (x + 0.6, fl + 0.1, out + 250), mat("out_joint", "#9E988E", 0.9))
    g.box((x0, fl - 18, out + 250), (x1, fl - 2, out + 262), mat("out_curb", "#CFC9BE", 0.8))
    g.box((x0, fl - 30, out + 262), (x1, fl - 18, out + 1600), road)
    if sid in ("shellys", "big_blue"):
        # parking bays
        for x in np.arange(cx - 1200, cx + 1300, 260):
            g.box((x - 3, fl - 17.8, out + 300), (x + 3, fl - 17.7, out + 760), paint)
    else:
        g.box((x0, fl - 17.8, out + 700), (x1, fl - 17.7, out + 712), mat("out_line", "#F2C443", 0.7))
    # a street lamp each side
    pole = mat("out_metal", "#4A4F57", 0.4)
    for s in (-1, 1):
        x = cx + s * 420
        g.add(cylinder((x, fl, out + 230), 4, 420, 10), pole)
        g.box((x - 4, fl + 410, out + 230), (x + s * -60, fl + 416, out + 236), pole)
        g.box((x - s * 60 - 14, fl + 402, out + 222), (x - s * 60 + 14, fl + 410, out + 244), mat("lamp_street", "#FFF4D8", 0.3, emit="#FFF0C8", strength=1.5))
    if sid == "shellys":
        # a bench and a little car in the lot
        g.box((cx + 150, fl + 40, out + 60), (cx + 260, fl + 46, out + 100), mat("out_wood", "#8A5A3A", 0.8))
        for x in (cx + 160, cx + 250):
            g.box((x - 3, fl, out + 64), (x + 3, fl + 40, out + 96), pole)
        _car(g, cx - 340, fl - 18, out + 520, "#C0563E")
        _car(g, cx + 480, fl - 18, out + 520, "#5B8DEF")
    elif sid == "frag_shack":
        # a potted palm, a surfboard by the door, a bike in a rack
        g.add(cylinder((cx - 160, fl, out + 60), 22, 50, 16), mat("out_pot", "#B5653E", 0.8))
        g.add(loft([(cx - 160, fl + 50, out + 60), (cx - 150, fl + 200, out + 64), (cx - 130, fl + 330, out + 70)], [6, 5, 4], [6, 5, 4], 10), mat("out_trunk", "#8A6A4A", 0.9))
        for k in range(8):
            a = k * 0.785
            g.add(_leaf((cx - 130, fl + 330, out + 70), a), mat("out_leaf", "#3E9B4A", 0.7))
        g.add(ellipsoid((cx + 115, fl + 100, out + 14), (16, 100, 4), 16, 10), mat("out_surfboard", "#FF7A5A", 0.4))
        g.box((cx + 113, fl + 8, out + 17), (cx + 117, fl + 192, out + 18.4), mat("out_stringer", "#FFFFFF", 0.5))
        for k in range(2):
            x = cx + 200 + k * 50
            g.add(loft([(x, fl, out + 120), (x, fl + 60, out + 120), (x + 40, fl + 60, out + 120), (x + 40, fl, out + 120)], [2.5] * 4, [2.5] * 4, 8), pole)
        _bike(g, cx + 240, fl, out + 140, "#3DD9B8")
        _car(g, cx + 520, fl - 30, out + 900, "#F2E6C8")
    elif sid == "big_blue":
        # bollards, a cart corral, cars in the lot
        for s in (-1, 1):
            for k in range(2):
                x = cx + s * (110 + k * 60)
                g.add(cylinder((x, fl, out + 120), 9, 90, 12), mat("out_bollard", "#F2C443", 0.5))
        _car(g, cx - 300, fl - 18, out + 520, "#E8E8E8")
        _car(g, cx + 300, fl - 18, out + 520, "#2E3440")
        _car(g, cx + 820, fl - 18, out + 520, "#E8463C")
    else:
        # a red carpet runner, a valet stand, a dark car at the kerb
        g.box((cx - 55, fl, out), (cx + 55, fl + 0.6, out + 245), mat("out_carpet", "#7A1630", 0.95))
        g.box((cx + 140, fl, out + 160), (cx + 180, fl + 110, out + 190), mat("out_bronze", "#8A6A3A", 0.3))
        _car(g, cx + 360, fl - 30, out + 330, "#141218")
    return g


def _leaf(c, a):
    x, y, z = c
    pts = [(x, y, z), (x + math.cos(a) * 40, y + 10, z + math.sin(a) * 40), (x + math.cos(a) * 80, y - 20, z + math.sin(a) * 80)]
    return loft(pts, [2, 9, 2], [1, 1.5, 0.8], 8)


def _bike(g, x, y, z, col):
    m = mat("out_bike", col, 0.4)
    tyre = mat("out_tyre", "#1A1A1C", 0.8)
    for dx in (-48, 48):
        ring = [(x + dx + math.cos(a) * 32, y + 34 + math.sin(a) * 32, z) for a in np.linspace(0, 2 * math.pi, 19)]
        g.add(loft(ring, [2.4] * 19, [2.4] * 19, 6), tyre)
    g.add(loft([(x - 48, y + 34, z), (x, y + 70, z), (x + 48, y + 34, z)], [1.8] * 3, [1.8] * 3, 6), m)
    g.add(loft([(x - 4, y + 34, z), (x, y + 70, z), (x + 40, y + 80, z)], [1.8] * 3, [1.8] * 3, 6), m)
    g.add(ellipsoid((x - 4, y + 82, z), (10, 3, 5), 10, 4), mat("out_seat", "#3A2A20", 0.6))


def _car(g, x, y, z, col):
    """A simple rounded car parked nose to the store."""
    body = mat(f"out_car_{col[1:]}", col, 0.3)
    glass = mat("out_carglass", "#22303A", 0.15)
    tyre = mat("out_tyre", "#1A1A1C", 0.8)
    g.add(ellipsoid((x, y + 62, z), (88, 34, 210), 20, 10), body)
    g.add(ellipsoid((x, y + 98, z + 10), (74, 30, 110), 18, 10), glass)
    g.box((x - 90, y + 26, z - 200), (x + 90, y + 56, z + 200), body)
    for sx in (-1, 1):
        for sz in (-1, 1):
            c = (x + sx * 80, y + 32, z + sz * 130)
            ring = [(c[0], c[1] + math.sin(a) * 32, c[2] + math.cos(a) * 32) for a in np.linspace(0, 2 * math.pi, 17)]
            g.add(loft(ring, [12] * 17, [6] * 17, 8), tyre)


def neighbor(f, sid):
    """A neighbouring building's front past each end of the store: plain wall, a dark window, a little sign."""
    st = STYLES[sid]
    g = Group("Neighbor")
    wallm = mat("out_neighbor", st["neighbor"], 0.85)
    inner = mat("wall_front", "#E8E4DC", 0.9)
    wall_with_hole(g, f, f.x0 + 30, f.x1 - 30, f.floor + 60, f.floor + 200, wallm, inner)
    g.box((f.x0 + 30, f.floor + 60, f.front + 4), (f.x1 - 30, f.floor + 200, f.front + 5), mat("out_window_dark", "#2A3238", 0.2))
    g.box((f.x0, f.floor, f.front - 400), (f.x1, f.top - 20, f.front), mat("out_dark", "#15171A", 0.9))
    g.box((f.x0 + 40, f.floor + 230, f.out), (f.x1 - 40, f.floor + 262, f.out + 3), mat("out_neighbor_sign", "#5A6E8A", 0.6))
    return g


def sky(f):
    """The backdrop behind the roofs (store.frag kind 13 paints the store's sky gradient on it)."""
    g = Group("Sky")
    g.box((f.cx - 3000, f.floor - 40, -900), (f.cx + 3000, f.floor + 3500, -898), mat("sky", "#9FD0FF", 1.0))
    # far side walls of sky so the view past the ends is never empty
    for s in (-1, 1):
        x = f.cx + s * 2600
        g.box((x - 1, f.floor - 40, -900), (x + 1, f.floor + 3500, f.out + 2600), mat("sky", "#9FD0FF", 1.0))
    return g


def roof(f):
    """The roof over the store's span (seen from the street above the parapet only as an edge)."""
    g = Group("Roof")
    g.box((f.x0, f.top - 2, -80), (f.x1, f.top, f.front), mat("out_roof", "#5A5650", 0.9))
    return g
