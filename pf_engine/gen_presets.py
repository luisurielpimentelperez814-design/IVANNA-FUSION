#!/usr/bin/env python3
"""
Convierte presets JSON a binario .pfp (layout struct PFParams)
struct: 14 floats + 1 int32 (PFAmpModel) = 60 bytes
"""
import struct, json, os, sys

FIELDS = [
    "alpha","beta","gamma","delta","sigma",
    "drive","wet","low_gain","mid_gain","high_gain",
    "mid_freq","presence","sag","bias"
]
FMT = "<14fi"   # 14 floats + 1 int

def convert(json_path, out_dir):
    with open(json_path) as f:
        p = json.load(f)
    name = p.get("name", os.path.splitext(os.path.basename(json_path))[0])
    vals = [float(p.get(k, 0.0)) for k in FIELDS]
    vals.append(int(p.get("amp", 0)))
    data = struct.pack(FMT, *vals)
    out_path = os.path.join(out_dir, name + ".pfp")
    with open(out_path, "wb") as f:
        f.write(data)
    print(f"  [{name}] → {out_path} ({len(data)} bytes)")

if __name__ == "__main__":
    src  = sys.argv[1] if len(sys.argv) > 1 else "../presets"
    dest = sys.argv[2] if len(sys.argv) > 2 else "../presets"
    os.makedirs(dest, exist_ok=True)
    for fn in os.listdir(src):
        if fn.endswith(".json"):
            convert(os.path.join(src, fn), dest)
    print("Done.")
