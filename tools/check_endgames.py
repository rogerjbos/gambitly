#!/usr/bin/env python3
"""Check tools/endgames.json against a UCI engine.

For every position it prints the engine's verdict and flags entries whose goal
does not match it (a "mate"/"promote" position that is not winning for the user,
a "draw" position that is not level, or a mate longer than the move limit).

Usage: check_endgames.py /path/to/stockfish [movetime_ms]
"""
import json
import subprocess
import sys
from pathlib import Path


def analyse(engine, fen, movetime):
    engine.stdin.write(f"position fen {fen}\ngo movetime {movetime}\n")
    engine.stdin.flush()
    score = None
    while True:
        tokens = engine.stdout.readline().split()
        if not tokens:
            continue
        if tokens[0] == "info" and "score" in tokens:
            at = tokens.index("score")
            score = (tokens[at + 1], int(tokens[at + 2]))
        elif tokens[0] == "bestmove":
            return score


def main():
    movetime = int(sys.argv[2]) if len(sys.argv) > 2 else 2000
    engine = subprocess.Popen([sys.argv[1]], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    engine.stdin.write("setoption name Threads value 4\nsetoption name Hash value 256\n")
    failures = 0
    for entry in json.loads((Path(__file__).parent / "endgames.json").read_text()):
        kind, value = analyse(engine, entry["fen"], movetime)
        side_to_move = entry["fen"].split()[1]
        if side_to_move != entry["userSide"]:
            value = -value  # report from the user's point of view
        problem = ""
        if entry["goal"] == "draw":
            if kind == "mate" or abs(value) > 80:
                problem = "NOT LEVEL"
        elif kind == "mate":
            if value <= 0:
                problem = "USER IS LOSING"
            elif entry["goal"] == "mate" and value > entry["moveLimit"]:
                problem = f"MATE IN {value} EXCEEDS LIMIT"
        elif value < 200:
            # Long theoretical wins (bishop and knight) show as a plain advantage at short search times.
            problem = "NOT WINNING"
        failures += bool(problem)
        print(f"{entry['title'][:44]:44} {entry['goal']:8} limit {entry['moveLimit']:3}  {kind} {value:+d}  {problem}")
    engine.stdin.write("quit\n")
    engine.stdin.flush()
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
