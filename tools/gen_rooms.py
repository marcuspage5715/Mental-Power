#!/usr/bin/env python3
"""Builds extra rooms for Mental Power.

A new room is a shape on a unit grid plus the pieces that fill it. The shapes here are
grown symmetrically so they look designed rather than random, and every room is kept only
if a solver can actually tile it with a subset of the twelve pentominoes (plus the square
tetromino when the area is 4 more than a multiple of five).
"""
import json
import random
import sys

# piece 0 is the 2x2 square, 1..12 are the twelve pentominoes, matching Levels.kt
SHAPES = {
    0: ["##", "##"],
    1: ["..#", "..#", "###"],
    2: ["##.", ".#.", ".##"],
    3: ["#..", "###", "#.."],
    4: [".#.", "##.", ".##"],
    5: ["#####"],
    6: ["..##", "###."],
    7: ["#..", "##.", ".##"],
    8: ["###", "#.#"],
    9: ["#.", "##", "##"],
    10: [".#.", "###", ".#."],
    11: ["...#", "####"],
    12: ["..#.", "####"],
}


def cells_of(rows):
    out = []
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '#':
                out.append((x, y))
    return out


def normalise(cells):
    mx = min(x for x, y in cells)
    my = min(y for x, y in cells)
    return frozenset((x - mx, y - my) for x, y in cells)


def variants(cells):
    out = []
    cur = list(cells)
    for _ in range(4):
        for v in (normalise(cur), normalise([(-x, y) for x, y in cur])):
            if v not in out:
                out.append(v)
        cur = [(y, -x) for x, y in cur]
    return out


PIECE_CELLS = {pid: cells_of(rows) for pid, rows in SHAPES.items()}
PIECE_VARIANTS = {pid: variants(c) for pid, c in PIECE_CELLS.items()}
PIECE_AREA = {pid: len(c) for pid, c in PIECE_CELLS.items()}


def connected(cells):
    if not cells:
        return False
    seen = {next(iter(cells))}
    stack = [next(iter(cells))]
    while stack:
        x, y = stack.pop()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n in cells and n not in seen:
                seen.add(n)
                stack.append(n)
    return len(seen) == len(cells)


def solve(shape, budget=200000, only=None):
    """Tiles shape with a subset of the pieces. Returns [(piece, cells)] or None.

    [only] restricts the pieces to the ones a room actually hands out.
    """
    cells = sorted(shape, key=lambda c: (c[1], c[0]))
    index = {c: i for i, c in enumerate(cells)}
    n = len(cells)

    area = len(cells)
    usable = []
    for pid, a in PIECE_AREA.items():
        if a <= area and (only is None or pid in only):
            usable.append(pid)

    placements = []  # (piece, mask, cells)
    for pid in usable:
        for var in PIECE_VARIANTS[pid]:
            w = max(x for x, y in var) + 1
            h = max(y for x, y in var) + 1
            xs = [x for x, y in cells]
            ys = [y for x, y in cells]
            for ox in range(min(xs) - w + 1, max(xs) + 1):
                for oy in range(min(ys) - h + 1, max(ys) + 1):
                    placed = [(x + ox, y + oy) for x, y in var]
                    if all(p in index for p in placed):
                        mask = 0
                        for p in placed:
                            mask |= 1 << index[p]
                        placements.append((pid, mask, placed))

    by_cell = [[] for _ in range(n)]
    for item in placements:
        mask = item[1]
        for i in range(n):
            if mask >> i & 1:
                by_cell[i].append(item)

    full = (1 << n) - 1
    steps = [0]

    def rec(used_mask, remaining):
        if used_mask == full:
            return []
        steps[0] += 1
        if steps[0] > budget:
            return None
        # first uncovered cell with the fewest options
        best = None
        best_opts = None
        for i in range(n):
            if used_mask >> i & 1:
                continue
            opts = [p for p in by_cell[i] if p[0] in remaining and not (p[1] & used_mask)]
            if not opts:
                return None
            if best_opts is None or len(opts) < len(best_opts):
                best, best_opts = i, opts
                if len(opts) == 1:
                    break
        rnd = list(best_opts)
        random.shuffle(rnd)
        for pid, mask, placed in rnd:
            rest = remaining - {pid}
            got = rec(used_mask | mask, rest)
            if got is not None:
                return [(pid, placed)] + got
        return None

    return rec(0, set(usable))


def grow(width, height, target):
    """Grows a vertically symmetric polyomino one mirrored pair at a time."""
    axis = width // 2
    cells = {(axis, height // 2)}

    def add(x, y):
        cells.add((x, y))
        cells.add((2 * axis - x, y))

    while len(cells) < target:
        frontier = set()
        for (x, y) in cells:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < width and 0 <= ny < height and (nx, ny) not in cells:
                    frontier.add((nx, ny))
        if not frontier:
            break
        # prefer to keep the shape compact: pick from the cells touching the most filled
        # neighbours, so it grows as a blob rather than a spider
        def score(c):
            x, y = c
            n = sum(1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                    if (x + dx, y + dy) in cells)
            return n
        if random.random() < 0.35:
            x, y = random.choice(sorted(frontier))
        else:
            scored = sorted(frontier, key=score, reverse=True)[:3]
            x, y = random.choice(scored)
        add(x, y)
    return normalise(cells)


def perimeter(cells):
    p = 0
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in cells:
                p += 1
    return p


def fill(cells):
    xs = [x for x, y in cells]
    ys = [y for x, y in cells]
    return len(cells) / ((max(xs) - min(xs) + 1) * (max(ys) - min(ys) + 1))


def mirror_pairs(cells):
    xs = [x for x, y in cells]
    axis = max(xs)
    return all((axis - x, y) in cells for x, y in cells)


def mutate(cells, width, height, tries=14):
    """Slides a mirrored pair of border cells somewhere else, keeping symmetry."""
    best = None
    best_p = None
    for _ in range(tries):
        cand = set(cells)
        border = [c for c in cand if any((c[0] + dx, c[1] + dy) not in cand
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
        if not border:
            continue
        x, y = random.choice(border)
        cand.discard((x, y))
        if x != max(c[0] for c in cells):
            cand.discard((max(c[0] for c in cells) - x, y))
        # a neighbour of what is left, inside the grid
        frontier = []
        for (cx, cy) in cand:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = cx + dx, cy + dy
                if 0 <= nx < width and 0 <= ny < height and (nx, ny) not in cand:
                    frontier.append((nx, ny))
        if not frontier:
            continue
        nx, ny = random.choice(frontier)
        cand.add((nx, ny))
        mx = max(c[0] for c in cand)
        cand.add((mx - nx, ny))
        cand = normalise(cand)
        if not connected(cand) or len(cand) != len(cells):
            continue
        if not mirror_pairs(cand):
            continue
        if not (0.5 <= fill(cand) <= 0.82):
            continue
        best, best_p = cand, 0
    return best


def build(seed):
    random.seed(seed)
    out = []
    for attempt in range(400):
        if len(out) >= 24:
            break
        width = random.choice([9, 11, 11, 13])
        height = random.choice([9, 11, 13])
        target = random.choice([40, 45, 45, 50, 50, 55, 60])
        shape = grow(width, height, target)
        area = len(shape)
        if area % 5 not in (0, 4):
            continue
        xs = [x for x, y in shape]
        ys = [y for x, y in shape]
        if max(xs) - min(xs) < 5 or max(ys) - min(ys) < 5:
            continue
        if perimeter(shape) > area * 1.25:
            continue
        if not (0.5 <= fill(shape) <= 0.78):
            continue
        cur = shape
        for _ in range(60):
            got = solve(cur)
            if got:
                break
            nxt = mutate(cur, width, height)
            if nxt is None:
                break
            cur = nxt
        else:
            continue
        if not got:
            continue
        if len(got) < 7:
            continue
        if any(set(cur) == set(o) for o in out):
            continue
        out.append(cur)
        print(f"room {len(out)}: area {len(cur)} pieces {len(got)} "
              f"({sorted(p for p, _ in got)})", file=sys.stderr)
    return out


def collect(seeds):
    pool = []
    for seed in seeds:
        for s in build(seed):
            if any(set(s) == set(o) for o in pool):
                continue
            pool.append(s)
    return pool


def pick(pool, count=24):
    def score(s):
        f = fill(s)
        area = len(s)
        xs = [x for x, y in s]
        ys = [y for x, y in s]
        aspect = (max(xs) - min(xs) + 1) / (max(ys) - min(ys) + 1)
        return -abs(f - 0.66) - abs(area - 52) / 60 - abs(aspect - 1.15) / 4

    chosen = []
    by_area = sorted(pool, key=score, reverse=True)
    while len(chosen) < count and by_area:
        # take the best shape whose area is not already well represented
        counts = {}
        for c in chosen:
            counts[len(c)] = counts.get(len(c), 0) + 1
        pickable = [s for s in by_area if counts.get(len(s), 0) < 4]
        if not pickable:
            pickable = by_area
        chosen.append(pickable[0])
        by_area.remove(pickable[0])
    return chosen


if __name__ == "__main__":
    if sys.argv[1] == 'pool':
        shapes = pick(collect([3, 5, 11, 17, 23, 29]))
    else:
        shapes = build(int(sys.argv[1]))
    for i, s in enumerate(shapes, 1):
        w = max(x for x, y in s) + 1
        h = max(y for x, y in s) + 1
        print(f"--- new {i}  {w}x{h}  area {len(s)}")
        for y in range(h):
            print(''.join('#' if (x, y) in s else '.' for x in range(w)))
    json.dump([sorted(s) for s in shapes], open('/scratch/work/newshapes.json', 'w'))
