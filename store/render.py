#!/usr/bin/env python3
"""
Renders the Google Play store assets from HTML with headless Chromium.

Why HTML: the assets use the exact same colour tokens and type scale as the app
(ui/theme/Color.kt, Type.kt), so the listing and the product cannot drift apart.
Re-run this after any brand change and every asset updates at once.

    python3 store/render.py
"""
import asyncio, os, pathlib, shutil
from playwright.async_api import async_playwright

OUT = pathlib.Path(__file__).parent / "assets"
OUT.mkdir(exist_ok=True)

BG, SURF, INK, MUTED, FAINT = "#F6F6FB", "#FFFFFF", "#0F1222", "#6E7385", "#9AA0B0"
BORDER, INDIGO, VIOLET, INDIGO_SOFT = "#EBECF3", "#5559EE", "#8250F5", "#EEEDFF"
MINT, MINT_SOFT, MINT_BORDER = "#0EA97A", "#E6F7F0", "#C9EEE0"

CSS = f"""
*{{box-sizing:border-box;margin:0;padding:0}}
body{{width:1080px;height:1920px;background:{BG};
 font-family:'Roboto','Helvetica Neue',Arial,sans-serif;-webkit-font-smoothing:antialiased;
 display:flex;flex-direction:column;align-items:center;overflow:hidden}}
.cap{{padding:110px 90px 0;text-align:center;width:100%}}
.cap h1{{font-size:82px;line-height:1.06;font-weight:800;letter-spacing:-.03em;color:{INK}}}
.cap h1 em{{font-style:normal;color:{INDIGO}}}
.cap p{{margin-top:26px;font-size:36px;line-height:1.35;color:{MUTED};font-weight:400}}
.phone{{width:660px;height:1150px;margin-top:70px;border-radius:56px;background:{SURF};
 border:2px solid {BORDER};box-shadow:0 60px 90px -50px rgba(15,18,34,.45);
 overflow:hidden;position:relative;flex:none}}
.pad{{padding:44px 40px}}
.bar{{display:flex;justify-content:space-between;align-items:flex-start;padding:44px 40px 0}}
.wm{{font-size:44px;font-weight:800;letter-spacing:-.02em;color:{INK}}}
.wmsub{{font-size:22px;color:{MUTED};margin-top:4px;font-weight:500}}
.gear{{width:74px;height:74px;border-radius:99px;border:2px solid {BORDER};background:{SURF}}}
.card{{background:{SURF};border:2px solid {BORDER};border-radius:44px;padding:36px}}
.hero{{background:{INDIGO_SOFT};border-color:#D9D7FB}}
.pill{{display:inline-block;background:#fff;border:2px solid #D9D7FB;color:{INDIGO};
 border-radius:99px;padding:12px 22px;font-size:20px;font-weight:800;letter-spacing:.12em}}
.h{{font-size:56px;line-height:1.1;font-weight:800;letter-spacing:-.02em;color:{INK};margin:26px 0 14px}}
.sub{{font-size:26px;line-height:1.4;color:{MUTED}}}
.cta{{margin-top:34px;border-radius:32px;background:linear-gradient(90deg,{INDIGO},{VIOLET});
 color:#fff;font-size:28px;font-weight:800;letter-spacing:.06em;padding:34px;text-align:center;
 box-shadow:0 26px 46px -22px rgba(85,89,238,.85)}}
.row{{display:flex;align-items:center;gap:24px}}
.thumb{{width:96px;height:96px;border-radius:26px;background:{INDIGO_SOFT};flex:none}}
.t1{{font-size:28px;font-weight:600;color:{INK}}}
.t2{{font-size:24px;color:{MUTED};margin-top:6px}}
.chip{{background:{MINT_SOFT};border:2px solid {MINT_BORDER};color:{MINT};border-radius:99px;
 padding:10px 20px;font-size:22px;font-weight:700;flex:none}}
.eyebrow{{font-size:20px;letter-spacing:.14em;color:{FAINT};font-weight:800}}
.big{{font-size:64px;font-weight:800;color:{INDIGO};letter-spacing:-.02em;margin-top:12px}}
.preset{{border:2px solid {BORDER};border-radius:36px;padding:30px;display:flex;
 align-items:center;gap:24px;margin-top:18px}}
.preset.on{{background:{INDIGO_SOFT};border-color:{INDIGO}}}
.dot{{width:38px;height:38px;border-radius:99px;background:#F1F2F7;flex:none}}
.preset.on .dot{{background:{INDIGO}}}
.est{{margin-left:auto;text-align:right;font-size:28px;font-weight:700;color:{INK}}}
.preset.on .est{{color:{INDIGO}}}
.ring{{width:260px;height:260px;border-radius:99px;margin:0 auto;
 background:conic-gradient(from 180deg,{INDIGO} 0deg,{VIOLET} 151deg,{INDIGO_SOFT} 151deg 360deg);
 display:grid;place-items:center;position:relative}}
.ring::after{{content:'';position:absolute;inset:24px;background:#fff;border-radius:99px}}
.ring b{{position:relative;z-index:1;font-size:56px;font-weight:800;color:{INK}}}
.notif{{background:#fff;border:2px solid {BORDER};border-radius:32px;padding:28px;
 box-shadow:0 24px 40px -26px rgba(15,18,34,.4)}}
.tick{{width:120px;height:120px;border-radius:99px;background:{MINT_SOFT};margin:0 auto;
 display:grid;place-items:center;font-size:64px;color:{MINT};font-weight:800}}
.fig{{background:{INDIGO_SOFT};border-radius:36px;padding:32px;text-align:center;margin-top:32px}}
.fig b{{font-size:46px;font-weight:800;color:{INK};letter-spacing:-.02em}}
.fig span{{display:block;font-size:26px;color:{INDIGO};font-weight:700;margin-top:10px}}
.trust{{display:flex;gap:16px;margin-top:24px}}
.trust div{{flex:1;text-align:center;border-radius:99px;padding:22px 8px;font-size:24px;font-weight:600}}
"""

def page(caption, sub, body):
    return f"<html><head><meta charset='utf-8'><style>{CSS}</style></head><body>" \
           f"<div class='cap'><h1>{caption}</h1><p>{sub}</p></div>" \
           f"<div class='phone'>{body}</div></body></html>"

HOME = f"""
<div class='bar'><div><div class='wm'>Vidsize</div><div class='wmsub'>Video Compressor</div></div>
<div class='gear'></div></div>
<div class='pad'>
 <div class='card hero'>
  <span class='pill'>VIDEO COMPRESSOR</span>
  <div class='h'>Make your<br>video smaller.</div>
  <div class='sub'>Reduce file size while keeping the quality you need.</div>
  <div class='cta'>SELECT VIDEO</div>
 </div>
 <div class='trust'>
  <div style='background:{INDIGO_SOFT};color:{INDIGO}'>On-device</div>
  <div style='background:#E5F5F9;color:#0C93B4'>Fast</div>
  <div style='background:{MINT_SOFT};color:{MINT}'>No watermark</div>
 </div>
 <div class='card' style='margin-top:26px'>
  <div class='eyebrow'>STORAGE SAVED</div>
  <div class='big'>1.03 GB</div>
  <div class='t2'>3 videos</div>
 </div>
 <div style='margin-top:26px;border-top:2px solid {BORDER};padding-top:22px'>
  <div style='font-size:18px;color:{FAINT};font-weight:600'>Advertisement</div>
  <div style='height:96px;border-radius:16px;background:#EFEFF6;margin-top:12px'></div>
 </div>
</div>"""

PRESETS = f"""
<div class='pad' style='padding-top:64px'>
 <div style='font-size:52px;font-weight:800;letter-spacing:-.02em;color:{INK}'>Choose compression</div>
 <div class='sub' style='margin-top:14px'>Pick the balance you want between quality and file size.</div>
 <div class='card' style='margin-top:36px'>
  <div class='row'>
   <div class='thumb' style='background:linear-gradient(135deg,#8F9BD6,#3E477F)'></div>
   <div><div class='eyebrow'>SELECTED VIDEO</div><div class='t1' style='margin-top:8px;font-size:32px'>1920 × 1080</div>
   <div class='t2'>1:24 &nbsp;·&nbsp; 1.02 GB</div></div>
  </div>
 </div>
 <div class='preset on'><div class='dot'></div><div><div class='t1'>Balanced</div>
  <div class='t2'>Smaller file, strong quality</div></div><div class='est'>≈ 612 MB</div></div>
 <div class='preset'><div class='dot'></div><div><div class='t1'>Smaller</div>
  <div class='t2'>More compression, good quality</div></div><div class='est'>≈ 320 MB</div></div>
 <div class='preset'><div class='dot'></div><div><div class='t1'>Smallest</div>
  <div class='t2'>Maximum practical reduction</div></div><div class='est'>≈ 164 MB</div></div>
</div>"""

BACKGROUND = f"""
<div class='pad' style='padding-top:120px'>
 <div class='ring'><b>42%</b></div>
 <div style='text-align:center;margin-top:44px;font-size:36px;font-weight:600;color:{INK}'>Compressing your video</div>
 <div style='text-align:center;margin-top:14px;font-size:26px;color:{MUTED}'>You can leave Vidsize —<br>compression continues in the background.</div>
 <div class='notif' style='margin-top:60px'>
  <div class='row'>
   <div class='thumb' style='width:64px;height:64px;border-radius:18px;background:{INDIGO}'></div>
   <div><div class='t1' style='font-size:26px'>Vidsize</div>
   <div class='t2'>Compressing your video · 42%</div></div>
  </div>
  <div style='height:12px;border-radius:99px;background:#EFEFF6;margin-top:24px'>
   <div style='width:42%;height:12px;border-radius:99px;background:{INDIGO}'></div></div>
 </div>
</div>"""

RESULT = f"""
<div class='pad' style='padding-top:130px'>
 <div class='tick'>✓</div>
 <div style='text-align:center;margin-top:34px;font-size:52px;font-weight:800;letter-spacing:-.02em;color:{INK};line-height:1.15'>Great! Your video<br>is ready 🎉</div>
 <div class='fig'><b>1.02 GB → 612 MB</b><span>You saved 408 MB • 41% smaller</span></div>
 <div style='text-align:center;font-size:24px;color:{FAINT};margin-top:22px'>Balanced • 18.4s</div>
 <div class='cta'>SHARE VIDEO</div>
 <div style='border:2px solid {BORDER};border-radius:32px;padding:30px;text-align:center;
  font-size:28px;font-weight:700;color:{INK};margin-top:18px'>Open Video</div>
 <div style='text-align:center;font-size:22px;color:{FAINT};margin-top:26px'>Already saved to your gallery · Movies/Vidsize</div>
</div>"""

HISTORY = f"""
<div class='pad' style='padding-top:64px'>
 <div style='font-size:44px;font-weight:800;letter-spacing:-.02em;color:{INK}'>Recent</div>
 <div class='card' style='margin-top:26px'>
  <div class='row'><div class='thumb'></div><div style='flex:1'><div class='t1'>Vidsize_1042.mp4</div>
   <div class='t2'>1.02 GB → 612 MB</div></div><div class='chip'>−41%</div></div>
  <div class='row' style='margin-top:30px'><div class='thumb'></div><div style='flex:1'><div class='t1'>Vidsize_0931.mp4</div>
   <div class='t2'>240 MB → 96 MB</div></div><div class='chip'>−60%</div></div>
  <div class='row' style='margin-top:30px'><div class='thumb'></div><div style='flex:1'><div class='t1'>Vidsize_0820.mp4</div>
   <div class='t2'>88 MB → 24 MB</div></div><div class='chip'>−73%</div></div>
 </div>
 <div class='card' style='margin-top:24px'>
  <div class='eyebrow'>STORAGE SAVED</div>
  <div class='big'>1.03 GB</div>
  <div class='t2'>3 videos</div>
 </div>
</div>"""

SHOTS = [
    ("01-hero", "1.02 GB → <em>612 MB</em>", "Compress video on your phone. Keep the quality you need.", HOME),
    ("02-presets", "Three levels.<br>No settings to learn.", "See the estimated size before you start.", PRESETS),
    ("03-background", "Keeps going when<br>you <em>switch apps</em>", "Compression keeps running when you switch apps.", BACKGROUND),
    ("04-result", "Saved straight to<br>your gallery", "No download step. No watermark. Ever.", RESULT),
    ("05-history", "See every<br>gigabyte you saved", "Your history stays on your device.", HISTORY),
]

FEATURE = f"""<html><head><meta charset='utf-8'><style>
*{{box-sizing:border-box;margin:0}}
body{{width:1024px;height:500px;background:linear-gradient(120deg,{INDIGO_SOFT} 0%,#F6F6FB 55%,#E9F6F1 100%);
 font-family:'Roboto',Arial,sans-serif;display:flex;align-items:center;padding:0 70px;overflow:hidden}}
.l{{flex:1}}
.mark{{display:flex;align-items:center;gap:18px}}
.ic{{width:74px;height:74px;border-radius:20px;background:linear-gradient(135deg,{INDIGO},{VIOLET});
 display:grid;place-items:center}}
.name{{font-size:52px;font-weight:800;letter-spacing:-.03em;color:{INK}}}
.tag{{font-size:31px;line-height:1.3;color:{MUTED};margin-top:26px;font-weight:400}}
.num{{margin-top:30px;font-size:44px;font-weight:800;letter-spacing:-.02em;color:{INK}}}
.num span{{color:{INDIGO}}}
.r{{width:300px;display:flex;flex-direction:column;gap:14px;align-items:flex-end}}
.b{{height:54px;border-radius:14px;background:{INDIGO};opacity:.9}}
</style></head><body>
<div class='l'>
 <div class='mark'>
  <div class='ic' style='background:#EDEBFD'><svg width='50' height='50' viewBox='0 0 108 108'><path fill='#4F46E5' fill-rule='evenodd' d='M38,32 H70 Q78,32 78,39 Q62,54 78,69 Q78,76 70,76 H38 Q30,76 30,69 Q46,54 30,39 Q30,32 38,32 Z M48,47 Q48,44.5 50.2,45.7 L63,52.8 Q65,54 63,55.2 L50.2,62.3 Q48,63.5 48,61 Z'/></svg></div>
  <div class='name'>Vidsize</div>
 </div>
 <div class='tag'>Compress video on your phone.<br>Keep the quality you need.</div>
 <div class='num'>1.02 GB <span>→ 612 MB</span></div>
</div>
<div class='r'>
 <div class='b' style='width:100%'></div>
 <div class='b' style='width:58%;opacity:.55'></div>
 <div class='b' style='width:31%;opacity:.35'></div>
 <div class='b' style='width:16%;opacity:.2'></div>
</div>
</body></html>"""


async def main():
    async with async_playwright() as pw:
        browser_path = os.environ.get("CHROMIUM_EXECUTABLE") or shutil.which("chromium") or shutil.which("google-chrome")
        browser = await pw.chromium.launch(executable_path=browser_path) if browser_path else await pw.chromium.launch()
        page_ = await browser.new_page(viewport={"width": 1080, "height": 1920},
                                       device_scale_factor=1)
        for name, cap, sub, body in SHOTS:
            await page_.set_content(page(cap, sub, body))
            await page_.screenshot(path=str(OUT / f"screenshot-{name}.png"))
            print("screenshot-" + name)
        fp = await browser.new_page(viewport={"width": 1024, "height": 500})
        await fp.set_content(FEATURE)
        await fp.screenshot(path=str(OUT / "feature-graphic-1024x500.png"))
        print("feature-graphic")
        await browser.close()

asyncio.run(main())
