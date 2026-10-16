#!/usr/bin/env python3
"""Generate Stratos VPN brand icons from the 2026 "aurora planet" mark.

Brand mark: a glowing cyan-indigo planet wrapped by a metallic orbit ring with a
comet streak, inside deep space with faint stars and an aurora nebula haze.

Outputs:
  - mipmap-{mdpi..xxxhdpi}/ic_launcher.png            (legacy square icon)
  - mipmap-{mdpi..xxxhdpi}/ic_launcher_round.png      (legacy round icon)
  - fastlane/metadata/android/en-US/images/icon.png   (512px store icon)
  - fastlane/metadata/android/en-US/images/feature.png (1024x500 feature graphic)
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "..", "V2rayNG", "app", "src", "main", "res")
STORE = os.path.join(ROOT, "metadata", "android", "en-US", "images")

# ---------------- palette ----------------
SPACE_TOP = (10, 14, 40)      # deep indigo
SPACE_BOTTOM = (4, 5, 16)     # near black
NEBULA_PURPLE = (90, 60, 190)
NEBULA_CYAN = (20, 130, 190)
PLANET_LIGHT = (120, 235, 255)  # cyan highlight
PLANET_MID = (46, 170, 235)     # sky
PLANET_DARK = (88, 70, 220)     # indigo-violet shade
PLANET_NIGHT = (40, 30, 120)
RING_LIGHT = (235, 245, 255)
RING_DARK = (120, 140, 190)
COMET_CORE = (240, 252, 255)
FRAME_EDGE_A = (120, 110, 255)
FRAME_EDGE_B = (60, 200, 255)
WHITE = (240, 246, 255)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vgrad(size, top, bottom):
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        t = y / max(1, size - 1)
        c = lerp(top, bottom, t)
        for x in range(size):
            px[x, y] = c
    return img


def draw_stars(img, n=90, seed=7):
    rng = random.Random(seed)
    d = ImageDraw.Draw(img, "RGBA")
    w, h = img.size
    for _ in range(n):
        x = rng.uniform(0, w)
        y = rng.uniform(0, h)
        r = rng.uniform(w * 0.0015, w * 0.006)
        a = rng.randint(60, 220)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(200, 220, 255, a))


def draw_nebula(img):
    """Soft aurora haze blobs."""
    w = img.size[0]
    haze = Image.new("RGBA", img.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(haze, "RGBA")
    hd.ellipse(
        [w * 0.05, -w * 0.15, w * 0.75, w * 0.45],
        fill=NEBULA_PURPLE + (70,),
    )
    hd.ellipse(
        [w * 0.45, w * 0.55, w * 1.15, w * 1.2],
        fill=NEBULA_CYAN + (55,),
    )
    haze = haze.filter(ImageFilter.GaussianBlur(w // 12))
    img.alpha_composite(haze)


def planet_layers(S, cx, cy, pr):
    """Planet disc with gradient, terminator shade, gloss and atmosphere glow."""
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow, "RGBA")
    for i in range(6, 0, -1):
        a = int(26 - i * 2)
        rr = pr * (1 + i * 0.085)
        gd.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=PLANET_MID + (max(4, a),))
    glow = glow.filter(ImageFilter.GaussianBlur(S // 60))

    disc = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = disc.load()
    for y in range(int(cy - pr) - 2, int(cy + pr) + 3):
        for x in range(int(cx - pr) - 2, int(cx + pr) + 3):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d > pr + 1:
                continue
            # spherical normal for fake lighting (light from top-left)
            nz = math.sqrt(max(0.0, 1 - (d / pr) ** 2))
            nx, ny = dx / pr, dy / pr
            light = max(0.0, nx * -0.55 + ny * -0.62 + nz * 0.56)
            t = min(1.0, max(0.0, light * 1.25))
            if t < 0.45:
                col = lerp(PLANET_NIGHT, PLANET_DARK, t / 0.45)
            elif t < 0.78:
                col = lerp(PLANET_DARK, PLANET_MID, (t - 0.45) / 0.33)
            else:
                col = lerp(PLANET_MID, PLANET_LIGHT, (t - 0.78) / 0.22)
            # subtle banding (gas planet)
            band = math.sin((y - cy) / pr * 6.0 + (x - cx) / pr * 1.4) * 10
            col = tuple(max(0, min(255, int(c + band * (0.4 + t)))) for c in col)
            edge = min(1.0, (pr + 0.5 - d) / max(1.0, pr * 0.06))
            px[x, y] = col + (int(255 * edge),)

    gloss = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gl = ImageDraw.Draw(gloss, "RGBA")
    gx, gy = cx - pr * 0.38, cy - pr * 0.46
    gr = pr * 0.30
    gl.ellipse(
        [gx - gr, gy - gr * 0.70, gx + gr, gy + gr * 0.70],
        fill=(255, 255, 255, 48),
    )
    gloss = gloss.filter(ImageFilter.GaussianBlur(S // 70))

    disc.alpha_composite(gloss)
    return glow, disc


def ring_layer(S, cx, cy, rx, ry, angle, width):
    """Metallic orbit ring (full ellipse annulus) as its own RGBA layer."""
    R = S * 2
    ring = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    rd = ImageDraw.Draw(ring, "RGBA")
    steps = 360
    for i in range(steps):
        t = i / steps
        a0 = t * 2 * math.pi
        # metallic shading: bright specular around ~20deg, dark at the back
        shine = 0.5 + 0.5 * math.sin(a0 + 0.6)
        col = lerp(RING_DARK, RING_LIGHT, shine ** 2.2)
        x0 = R / 2 + math.cos(a0) * rx * 2
        y0 = R / 2 + math.sin(a0) * ry * 2
        rr = width
        rd.ellipse([x0 - rr, y0 - rr, x0 + rr, y0 + rr], fill=col + (255,))
    ring = ring.rotate(angle, resample=Image.BICUBIC, expand=False)
    ring = ring.resize((S, S), Image.LANCZOS)
    return ring


def draw_comet(S, img, cx, cy, rx, ry, angle, t=0.90):
    """Bright comet streak riding the ring at parameter t (0..1)."""
    a0 = t * 2 * math.pi
    x0 = cx + math.cos(a0) * rx
    y0 = cy + math.sin(a0) * ry
    # rotate around center to match ring angle
    ang = math.radians(angle)
    dx, dy = x0 - cx, y0 - cy
    xr = cx + dx * math.cos(ang) - dy * math.sin(ang)
    yr = cy + dx * math.sin(ang) + dy * math.cos(ang)

    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow, "RGBA")
    for i in range(7, 0, -1):
        r = S * 0.012 * i
        gd.ellipse([xr - r, yr - r, xr + r, yr + r], fill=COMET_CORE + (int(28 - i * 3),))
    glow = glow.filter(ImageFilter.GaussianBlur(S // 100))

    core = Image.new("RGBA", img.size, (0, 0, 0, 0))
    cd = ImageDraw.Draw(core, "RGBA")
    # smooth streak tail trailing behind along the ring tangent
    N = 40
    for i in range(N):
        tt = i / N
        tr = S * 0.011 * (1 - tt * 0.92)
        tx = xr + math.sin(a0) * S * 0.115 * tt
        ty = yr - math.cos(a0) * S * 0.115 * tt
        cd.ellipse([tx - tr, ty - tr, tx + tr, ty + tr], fill=COMET_CORE + (int(150 * (1 - tt) ** 1.5),))
    cd.ellipse(
        [xr - S * 0.013, yr - S * 0.013, xr + S * 0.013, yr + S * 0.013],
        fill=COMET_CORE + (255,),
    )
    core = core.filter(ImageFilter.GaussianBlur(S // 300))

    img.alpha_composite(glow)
    img.alpha_composite(core)


def draw_mark(S, with_frame=False):
    """Compose planet + ring + comet on a transparent S x S canvas (supersampled)."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cx = cy = S / 2
    pr = S * 0.21
    rx = S * 0.335
    ry = S * 0.115
    angle = -18

    ring_back = ring_layer(S, cx, cy, rx, ry, angle, S * 0.0165)
    ring_front = ring_back.copy()

    glow, disc = planet_layers(S, cx, cy, pr)

    # back half of the ring sits behind the planet
    back_mask = Image.new("L", (S, S), 0)
    bmd = ImageDraw.Draw(back_mask)
    bmd.rectangle([0, 0, S, cy], fill=255)
    ring_back.putalpha(
        Image.composite(ring_back.getchannel("A"), Image.new("L", (S, S), 0), back_mask)
    )

    # front half over the planet
    front_mask = Image.new("L", (S, S), 0)
    fmd = ImageDraw.Draw(front_mask)
    fmd.rectangle([0, cy, S, S], fill=255)
    ring_front.putalpha(
        Image.composite(ring_front.getchannel("A"), Image.new("L", (S, S), 0), front_mask)
    )

    img.alpha_composite(glow)
    img.alpha_composite(ring_back)
    img.alpha_composite(disc)
    img.alpha_composite(ring_front)
    draw_comet(S, img, cx, cy, rx, ry, angle)

    if with_frame:
        frame = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        fd = ImageDraw.Draw(frame, "RGBA")
        m = S * 0.075
        rad = S * 0.19
        for i in range(14, 0, -1):
            off = m - i * S * 0.0016
            t = i / 14
            col = lerp(FRAME_EDGE_A, FRAME_EDGE_B, 0.5 + 0.5 * math.sin(t * 3))
            fd.rounded_rectangle(
                [off, off, S - off, S - off],
                radius=rad,
                outline=col + (int(26 * (1 - t) + 10),),
                width=max(2, int(S * 0.006)),
            )
        fd.rounded_rectangle(
            [m, m, S - m, S - m],
            radius=rad,
            outline=lerp(FRAME_EDGE_B, FRAME_EDGE_A, 0.4) + (215,),
            width=max(3, int(S * 0.011)),
        )
        frame = frame.filter(ImageFilter.GaussianBlur(S // 400))
        img.alpha_composite(frame)

    return img


def compose_full_icon(size):
    S = size * 4
    bg = vgrad(S, SPACE_TOP, SPACE_BOTTOM).convert("RGBA")
    draw_nebula(bg)
    draw_stars(bg, n=110)
    mark = draw_mark(S, with_frame=True)
    bg.alpha_composite(mark)
    return bg.resize((size, size), Image.LANCZOS)


def compose_round_icon(size):
    sq = compose_full_icon(size)
    m = Image.new("L", (size * 4, size * 4), 0)
    d = ImageDraw.Draw(m)
    d.ellipse([2, 2, size * 4 - 2, size * 4 - 2], fill=255)
    m = m.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(sq, (0, 0), m)
    return out


def compose_store_icon(size=512):
    return compose_full_icon(size)


def compose_feature_graphic(w=1024, h=500):
    bg = vgrad(w * 2, SPACE_TOP, SPACE_BOTTOM).convert("RGBA")
    bg = bg.resize((w, h), Image.LANCZOS)
    draw_nebula(bg)
    draw_stars(bg, n=70)
    mark = draw_mark(h * 2, with_frame=False)
    mark = mark.resize((int(h * 0.92), int(h * 0.92)), Image.LANCZOS)
    bg.alpha_composite(mark, (int(w * 0.5 - h * 0.46), int(h * 0.04)))
    return bg.convert("RGB")


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main():
    for dpi, px in DENSITIES.items():
        d = os.path.join(RES, f"mipmap-{dpi}")
        os.makedirs(d, exist_ok=True)
        compose_full_icon(px).save(os.path.join(d, "ic_launcher.png"))
        compose_round_icon(px).save(os.path.join(d, "ic_launcher_round.png"))
        print("wrote", d)

    os.makedirs(STORE, exist_ok=True)
    compose_store_icon(512).save(os.path.join(STORE, "icon.png"))
    compose_feature_graphic().save(os.path.join(STORE, "feature.png"))
    print("wrote store images")


if __name__ == "__main__":
    main()
