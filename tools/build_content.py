#!/usr/bin/env python3
"""Build app/src/main/assets/content.db, the read-only content database.

Sources (downloaded into tools/.cache on first use):
  openings  lichess-org/chess-openings TSV files (CC0)
  puzzles   Lichess puzzle database (CC0), sampled while streaming
  classics  game scores picked out of pgnmentor.com player collections (see classics.json)
  endgames  tools/endgames.json

The table layouts must stay in step with the Room entities in
app/src/main/java/io/github/gambitly/data/Content.kt, and DB_VERSION with
ContentDatabase's version.

Usage: build_content.py [--skip-puzzles]   (Python 3.14+ for compression.zstd)
"""
import csv
import io
import json
import re
import sqlite3
import sys
import urllib.request
import zipfile
from pathlib import Path

TOOLS = Path(__file__).parent
CACHE = TOOLS / ".cache"
OUT = TOOLS.parent / "app/src/main/assets/content.db"
DB_VERSION = 1
USER_AGENT = "content-build-script"

OPENINGS_URL = "https://raw.githubusercontent.com/lichess-org/chess-openings/master/{}.tsv"
PUZZLES_URL = "https://database.lichess.org/lichess_db_puzzle.csv.zst"
PGN_URL = "https://www.pgnmentor.com/players/{}.zip"

# Puzzle sampling: PER_BUCKET puzzles for every (theme, rating band) pair.
THEMES = [
    "mateIn1", "mateIn2", "mateIn3", "backRankMate", "smotheredMate",
    "fork", "pin", "skewer", "discoveredAttack", "doubleCheck",
    "hangingPiece", "trappedPiece", "deflection", "attraction", "sacrifice",
    "clearance", "intermezzo", "quietMove", "xRayAttack", "zugzwang",
    "promotion", "rookEndgame", "pawnEndgame",
]
BANDS = [(600, 1000), (1000, 1400), (1400, 1800), (1800, 2200)]
PER_BUCKET = 60
MAX_PUZZLE_ROWS = 1_500_000  # stop streaming here even if rare buckets are short

SCHEMA = """
CREATE TABLE openings (
    id INTEGER NOT NULL, eco TEXT NOT NULL, name TEXT NOT NULL, family TEXT NOT NULL,
    moves TEXT NOT NULL, plies INTEGER NOT NULL, PRIMARY KEY(id));
CREATE TABLE puzzles (
    id TEXT NOT NULL, fen TEXT NOT NULL, moves TEXT NOT NULL, rating INTEGER NOT NULL,
    themes TEXT NOT NULL, PRIMARY KEY(id));
CREATE TABLE classics (
    id INTEGER NOT NULL, title TEXT NOT NULL, white TEXT NOT NULL, black TEXT NOT NULL,
    event TEXT NOT NULL, year INTEGER NOT NULL, result TEXT NOT NULL, hero TEXT NOT NULL,
    intro TEXT NOT NULL, moves TEXT NOT NULL, PRIMARY KEY(id));
CREATE TABLE endgames (
    id INTEGER NOT NULL, category TEXT NOT NULL, title TEXT NOT NULL, fen TEXT NOT NULL,
    userSide TEXT NOT NULL, goal TEXT NOT NULL, moveLimit INTEGER NOT NULL, hint TEXT NOT NULL,
    PRIMARY KEY(id));
"""


def fetch(url: str, dest: Path) -> Path:
    if not dest.exists():
        dest.parent.mkdir(parents=True, exist_ok=True)
        print(f"downloading {url}")
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request) as response:
            dest.write_bytes(response.read())
    return dest


def san_list(movetext: str) -> list[str]:
    """Strip comments, move numbers and the result from PGN move text."""
    movetext = re.sub(r"\{[^}]*\}", " ", movetext)
    movetext = re.sub(r"\d+\.(\.\.)?", " ", movetext)
    return [t for t in movetext.split() if t not in ("1-0", "0-1", "1/2-1/2", "*")]


def build_openings(db):
    rows = []
    for letter in "abcde":
        path = fetch(OPENINGS_URL.format(letter), CACHE / "openings" / f"{letter}.tsv")
        with path.open(newline="", encoding="utf-8") as handle:
            for row in csv.DictReader(handle, delimiter="\t"):
                moves = san_list(row["pgn"])
                family = row["name"].split(":")[0].strip()
                rows.append((row["eco"], row["name"], family, " ".join(moves), len(moves)))
    rows.sort(key=lambda r: (r[2], r[4], r[1]))
    db.executemany(
        "INSERT INTO openings VALUES (?,?,?,?,?,?)",
        [(i + 1, *row) for i, row in enumerate(rows)],
    )
    print(f"openings: {len(rows)}")


def build_puzzles(db):
    import compression.zstd as zstd

    wanted = set(THEMES)
    buckets = {(theme, band): 0 for theme in THEMES for band in range(len(BANDS))}
    open_buckets = len(buckets)
    kept = 0
    request = urllib.request.Request(PUZZLES_URL, headers={"User-Agent": USER_AGENT})
    print(f"streaming {PUZZLES_URL}")
    with urllib.request.urlopen(request) as response:
        text = io.TextIOWrapper(zstd.ZstdFile(response), encoding="utf-8", newline="")
        for n, row in enumerate(csv.DictReader(text)):
            if open_buckets == 0 or n >= MAX_PUZZLE_ROWS:
                break
            rating = int(row["Rating"])
            band = next((i for i, (lo, hi) in enumerate(BANDS) if lo <= rating < hi), None)
            if band is None:
                continue
            # Keep only well-tested puzzles with a settled rating.
            if int(row["Popularity"]) < 90 or int(row["NbPlays"]) < 1000 or int(row["RatingDeviation"]) > 80:
                continue
            themes = row["Themes"].split()
            target = next((t for t in themes if t in wanted and buckets[(t, band)] < PER_BUCKET), None)
            if target is None:
                continue
            buckets[(target, band)] += 1
            open_buckets -= buckets[(target, band)] == PER_BUCKET
            # Themes are stored space-padded so one can be matched with LIKE '% name %'.
            db.execute(
                "INSERT INTO puzzles VALUES (?,?,?,?,?)",
                (row["PuzzleId"], row["FEN"], row["Moves"], rating, " " + " ".join(themes) + " "),
            )
            kept += 1
    short = {f"{t}@{BANDS[b][0]}": c for (t, b), c in buckets.items() if c < PER_BUCKET}
    print(f"puzzles: {kept} from {n} rows" + (f"; short buckets: {short}" if short else ""))


def pgn_games(path: Path):
    text = path.read_text(encoding="latin-1")
    for block in re.split(r"\n\s*\n(?=\[Event )", text):
        headers = dict(re.findall(r'\[(\w+) "([^"]*)"\]', block))
        yield headers, san_list(re.sub(r"\[[^\]]*\]\s*", "", block))


def build_classics(db):
    spec = json.loads((TOOLS / "classics.json").read_text())
    for i, game in enumerate(spec, start=1):
        archive = fetch(PGN_URL.format(game["file"]), CACHE / "pgn" / f"{game['file']}.zip")
        pgn = CACHE / "pgn" / f"{game['file']}.pgn"
        if not pgn.exists():
            with zipfile.ZipFile(archive) as z:
                z.extract(f"{game['file']}.pgn", pgn.parent)
        white, black, year, plies = game["match"]
        found = [
            (headers, moves)
            for headers, moves in pgn_games(pgn)
            if white in headers.get("White", "") and black in headers.get("Black", "")
            and headers.get("Date", "").startswith(year) and len(moves) == plies
        ]
        # Collections sometimes hold the same game twice; identical scores are fine.
        if not found or any(moves != found[0][1] for _, moves in found):
            sys.exit(f"classics: expected one score for {game['title']}, found {len(found)}")
        headers, moves = found[0]
        db.execute(
            "INSERT INTO classics VALUES (?,?,?,?,?,?,?,?,?,?)",
            (i, game["title"], game["white"], game["black"], game["event"], game["year"],
             headers["Result"], game["hero"], game["intro"], " ".join(moves)),
        )
    print(f"classics: {len(spec)}")


def build_endgames(db):
    spec = json.loads((TOOLS / "endgames.json").read_text())
    db.executemany(
        "INSERT INTO endgames VALUES (?,?,?,?,?,?,?,?)",
        [(i, e["category"], e["title"], e["fen"], e["userSide"], e["goal"], e["moveLimit"], e["hint"])
         for i, e in enumerate(spec, start=1)],
    )
    print(f"endgames: {len(spec)}")


def main():
    skip_puzzles = "--skip-puzzles" in sys.argv
    tmp = OUT.with_suffix(".db.tmp")
    tmp.unlink(missing_ok=True)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(tmp)
    db.executescript(SCHEMA)
    build_openings(db)
    build_classics(db)
    build_endgames(db)
    if skip_puzzles:
        if not OUT.exists():
            sys.exit("--skip-puzzles needs an existing content.db to copy puzzles from")
        db.execute("ATTACH DATABASE ? AS old", (str(OUT),))
        db.execute("INSERT INTO puzzles SELECT * FROM old.puzzles")
        db.commit()
        db.execute("DETACH DATABASE old")
    else:
        build_puzzles(db)
    db.execute(f"PRAGMA user_version = {DB_VERSION}")
    db.commit()
    db.execute("VACUUM")
    db.close()
    tmp.replace(OUT)
    print(f"wrote {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
