"""Lays the outer-screen and inner-screen recordings of the same steps side by side, in step.

    python3 tools/demo/pair.py <outer.mp4> <inner.mp4> <out.webp>

Each recording is cut into the same steps (still, open the player, still, close it, still, the
tabs); moves play twice as fast, stills get the same length on both, and the shorter move waits on
its last frame, so both phones move together. The times below are those of the 2026-11-15 Radio
takes (outer: cover_radio_final_60cfr.mp4, inner: inner_radio_final_60cfr.mp4), found from the
frame-to-frame difference.
"""
import glob, os, subprocess, sys, tempfile
from PIL import Image

outer, inner, out = sys.argv[1:4]
SPEED = 2.0
# (outer start, end), (inner start, end), kind: "move" plays at SPEED, "still" at its own pace.
STEPS = [
    ((0.85, 1.65), (4.12, 4.92), "still"),
    ((1.65, 2.65), (4.92, 6.22), "move"),   # the player opens
    ((2.65, 3.65), (6.22, 7.22), "still"),
    ((5.15, 6.48), (7.83, 9.17), "move"),   # it folds back
    ((6.48, 6.98), (9.17, 9.67), "still"),
    ((7.58, 15.10), (10.53, 17.58), "move"),  # the tabs, and back home
]
H = 1180  # canvas height of each phone before halving
here = os.path.dirname(os.path.abspath(__file__))
py = sys.executable

def part(src, a, b, speed, length):
    used = (b - a) / speed
    return (f"trim={a}:{b},setpts=(PTS-STARTPTS)/{speed},fps=60,"
            f"tpad=stop_mode=clone:stop_duration={max(0.0, length - used):.3f}")

with tempfile.TemporaryDirectory() as tmp:
    sides = []
    for i, (src, kind) in enumerate([(outer, "phone"), (inner, "fold")]):
        d = os.path.join(tmp, kind)
        os.makedirs(d)
        subprocess.run([py, os.path.join(here, "frame.py"), kind, d, str(H)], check=True)
        x, y, w, h = map(int, open(os.path.join(d, "screen.txt")).read().split())
        chains = [f"[0:v]split={len(STEPS)}" + "".join(f"[in{j}]" for j in range(len(STEPS)))]
        for j, (o, n, how) in enumerate(STEPS):
            speed = SPEED if how == "move" else 1.0
            length = max((o[1] - o[0]) / speed, (n[1] - n[0]) / speed)
            a, b = (o, n)[i]
            chains.append(f"[in{j}]{part(src, a, b, speed, length)},scale={w}:{h}:flags=lanczos[p{j}]")
        concat = "".join(f"[p{j}]" for j in range(len(STEPS))) + f"concat=n={len(STEPS)}:v=1:a=0[s]"
        graph = ";".join(chains + [concat, f"[1:v][s]overlay={x}:{y}:shortest=1[a]", "[a][2:v]overlay=0:0:shortest=1[v]"])
        video = os.path.join(tmp, kind + ".mp4")
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", src, "-loop", "1", "-framerate", "60", "-i", os.path.join(d, "bg.png"),
                        "-loop", "1", "-framerate", "60", "-i", os.path.join(d, "body.png"), "-filter_complex", graph, "-map", "[v]",
                        "-c:v", "libx264", "-crf", "12", "-pix_fmt", "yuv420p", video], check=True)
        sides.append(video)
        print(kind, subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", video], capture_output=True, text=True).stdout.strip())
    frames = os.path.join(tmp, "f%05d.png")
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", sides[0], "-i", sides[1], "-filter_complex",
                    "[0:v][1:v]hstack=shortest=1,scale=trunc(iw/4)*2:trunc(ih/4)*2:flags=lanczos", frames], check=True)
    images = [Image.open(f).convert("RGB") for f in sorted(glob.glob(os.path.join(tmp, "f*.png")))]
    images[0].save(out, save_all=True, append_images=images[1:], duration=17, loop=0, quality=82, method=4)
    print(out, len(images), "frames", images[0].size, f"{os.path.getsize(out) / 1e6:.1f} MB")
