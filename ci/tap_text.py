"""Prints the centre "x y" of the first UI element whose text or description matches a regex."""
import re
import sys
import xml.etree.ElementTree as ET

path, pattern = sys.argv[1], sys.argv[2]
rx = re.compile(pattern)
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
for node in root.iter("node"):
    label = node.get("text") or node.get("content-desc") or ""
    if label and rx.search(label.strip()):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
