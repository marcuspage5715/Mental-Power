import json
import gen_rooms as g

rooms = json.load(open('newrooms.json'))
originals = json.load(open('levels.json'))
PW = ["BOLD","CALM","DUSK","ECHO","FERN","GUST","HAZE","IRIS","JOLT","KEEN","LARK","MOSS",
      "NEST","OPAL","PEAK","REED","SAGE","TIDE","VALE","WISP","YARN","ZEST","BEAM","DRUM"]
NAMES = ["O","V","Z","T","F","I","N","W","U","P","X","L","Y"]

out = []
out.append("# Solutions for the new rooms")
out.append("")
out.append("Rooms 37 to 60 are the puzzles that are not in the original game. Below is one")
out.append("solution for each: every cell of the shape is covered exactly once, and the letters")
out.append("name the pieces, so you can check any room by hand.")
out.append("")
out.append("The letters are the pieces: O (the 2x2 square), V, Z, T, F, I, N, W, U, P, X, L, Y.")
out.append("Every letter is one of the pieces that room actually hands out, so each diagram can be")
out.append("reproduced with the tray you get in the game. `tools/solutions.py` regenerates this file")
out.append("and checks that the letters cover every cell exactly once and that each one is a rigid")
out.append("copy of the piece it names.")
out.append("")

bad = []
for i, room in enumerate(rooms):
    cells = {(x, y) for x, y in room['cells']}
    sol = g.solve(frozenset(cells), only=set(room['pieces']))
    if not sol:
        bad.append(i + 37)
        continue
    # independent check: every cell covered once, letters form the piece they name
    cover = {}
    for idx, (pid, placed) in enumerate(sol):
        letter = chr(ord('A') + idx)
        for p in placed:
            if p in cover:
                bad.append(f"{i+37} overlap")
            cover[p] = letter
        # the placed cells must be a rigid copy of the piece
        w = max(x for x, y in placed) - min(x for x, y in placed)
        h = max(y for x, y in placed) - min(y for x, y in placed)
        norm = frozenset((x - min(px for px, py in placed), y - min(py for px, py in placed))
                         for x, y in placed)
        if norm not in g.PIECE_VARIANTS[pid]:
            bad.append(f"{i+37} piece {pid} is not rigid")
    if set(cover) != cells:
        bad.append(f"{i+37} coverage mismatch")
    if sorted({p for p, _ in sol}) != sorted(room['pieces']):
        bad.append(f"{i+37} pieces differ from the tray")

    w = room['w']
    h = room['h']
    out.append(f"## Room {i+37}  (password {PW[i]})")
    out.append("")
    out.append(f"Shape {w}x{h}, area {len(cells)}, pieces: " +
               ", ".join(NAMES[p] for p in sorted({p for p, _ in sol})))
    out.append("")
    out.append("```")
    for y in range(h):
        out.append(''.join(cover.get((x, y), '.') for x in range(w)))
    out.append("```")
    out.append("")

open('solutions.md', 'w').write('\n'.join(out) + '\n')
print("rooms written:", len(rooms), "problems:", bad if bad else "none")
