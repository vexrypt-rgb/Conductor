#!/usr/bin/env python3
"""Summarise a live harness run (harness.jsonl): per cancel trial, ticks until each signal settles."""
import json
import sys

SIGNALS = {
    "segment": lambda o: o.get("PATH_SEGMENT_PRESENT") == "true",
    "next": lambda o: o.get("NEXT_SEGMENT_PRESENT") == "true",
    "calc": lambda o: o.get("PATH_CALCULATION_PENDING") == "true",
    "goal": lambda o: o.get("TRAVEL_GOAL_ACTIVE") == "true",
    "inputs": lambda o: o.get("FORCED_INPUTS") not in ("[]", None),
    "process": lambda o: o.get("CONTROLLING_PROCESS") not in ("Optional.empty", None),
}
MOVING = 0.003


def main(path):
    ticks, events = [], []
    for line in open(path):
        d = json.loads(line)
        (ticks if d["type"] == "tick" else events).append(d)
    for e in events:
        if e["type"] == "event" and not e["name"].startswith("E "):
            print(f'[{e["tick"]:>5}] {e["name"]}: ' + ", ".join(f"{k}={v}" for k, v in e.items() if k not in ("type", "name", "tick")))

    cancels = [e for e in events if e["type"] == "event" and e["name"] in ("E cancel",)]
    cancels += [{"k": "S3", "tick": e["tick"]} for e in events if e["type"] == "event" and e["name"] == "S3 cancel"]
    print()
    header = ("trial            " + "  ".join(f"{s:>7}" for s in SIGNALS)
              + "  composite  physical  rearm  moved  to_target  phys_after_composite")
    print(header)
    for c in cancels:
        start = c["tick"]
        window = ticks[start:start + c.get("window", 200)]
        settle = {}
        for name, active in SIGNALS.items():
            last = max((i for i, t in enumerate(window) if active(t["obs"])), default=-1)
            settle[name] = last + 1
        composite = max(settle.values())
        moving_last = max((i for i, t in enumerate(window) if t["hspeed"] >= MOVING), default=-1)
        seg = [SIGNALS["segment"](t["obs"]) for t in window]
        rearm = any(not seg[i] and any(seg[i + 1:]) for i in range(len(seg)))
        moved = 0.0
        to_target = float("nan")
        if window:
            a, b = window[0], window[-1]
            moved = ((b["x"] - a["x"]) ** 2 + (b["z"] - a["z"]) ** 2) ** 0.5
            if "tx" in c:
                to_target = ((b["x"] - c["tx"] - 0.5) ** 2 + (b["z"] - c["tz"] - 0.5) ** 2) ** 0.5
        # movement more than 10 ticks after the control signals cleared (momentum takes ~6)
        late = sum(1 for i, t in enumerate(window) if i > composite + 10 and t["hspeed"] >= MOVING)
        label = f'{c.get("course", "")} k={c["k"]}'
        print(f'{label:<16} ' + "  ".join(f"{settle[s]:>7}" for s in SIGNALS)
              + f"  {composite:>9}  {moving_last + 1:>8}  {str(rearm):>5}  {moved:>5.1f}  {to_target:>9.1f}  {late:>20}")
    print("\n(settle values are ticks after cancel() until the signal was last active; window 200 ticks)")


if __name__ == "__main__":
    main(sys.argv[1])
