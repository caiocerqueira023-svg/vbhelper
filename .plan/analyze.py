from PIL import Image
import numpy as np, os
base = r"C:\Users\julye\IdeaProjects\vbhelper\.plan"
for name in ["m_a.png", "dot_filter.png", "topbar_redesign.png"]:
    p = os.path.join(base, name)
    try:
        a = np.asarray(Image.open(p).convert("RGB")).astype(np.float32)
        bands = []
        for y in range(200, 1900, 300):
            b = a[y:y + 300, :, :].mean(axis=(0, 1))
            bands.append(f"{y}:{int(b[0])},{int(b[1])},{int(b[2])}")
        print(name, " | ".join(bands))
    except Exception as e:
        print(name, "ERR", e)
