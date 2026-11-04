#!/usr/bin/env python3
"""Render localized, illustrative Play listing assets with Playwright and Chromium.

Install the Python Playwright package and a Chromium browser, then run:
    python store/render.py
Set CHROMIUM_EXECUTABLE to a Chromium-compatible browser if needed.
"""
import html
import os
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "store" / "assets"
LANGS = {
    "en": ("values", "en"), "tr": ("values-tr", "tr"),
    "es": ("values-es", "es"), "pt-BR": ("values-pt-rBR", "pt-BR"),
    "id": ("values-in", "id"), "de": ("values-de", "de"),
    "hi": ("values-hi", "hi"), "ar": ("values-ar", "ar"),
}

# Every caption and caveat is localized. In-app labels are read from app/src.
COPY = {
"en": {
 "titles":["1.2 GB → ≈ 24.8 MB","Choose a target size","Three compression levels","For email","For Discord","Private by design","See your result","Your recent videos"],
 "subs":["Choose 25 MB. Vidsize aims to fit.","Pick a preset or enter your own size.","Choose the balance that works for you.","Try a 25 MB target before sharing.","Try a 10 MB target before sharing.","On-device · No upload · No account","Review the output saved to your gallery.","See previous compressions on your device."],
 "note":"Illustrative examples. Actual output size varies.","example":"Example estimate","privacy":["On-device","No upload","No account"],"result":"Example result","history":"Example history"},
"tr": {
 "titles":["1,2 GB → ≈ 24,8 MB","Hedef boyutu seç","Üç sıkıştırma seviyesi","E-posta için","Discord için","Gizlilik ön planda","Sonucu gör","Son videoların"],
 "subs":["25 MB seç. Vidsize bu hedefe ulaşmaya çalışır.","Hazır bir değer seç veya kendi boyutunu gir.","İstediğin kalite ve boyut dengesini seç.","Paylaşmadan önce 25 MB hedefini dene.","Paylaşmadan önce 10 MB hedefini dene.","Cihazda · Yükleme yok · Hesap yok","Galerine kaydedilen çıktıyı incele.","Önceki sıkıştırmaları cihazında gör."],
 "note":"Görseller örnektir. Gerçek çıktı boyutu değişir.","example":"Örnek tahmin","privacy":["Cihazda","Yükleme yok","Hesap yok"],"result":"Örnek sonuç","history":"Örnek geçmiş"},
"es": {
 "titles":["1,2 GB → ≈ 24,8 MB","Elige el tamaño","Tres niveles de compresión","Para correo","Para Discord","Privacidad por diseño","Revisa el resultado","Tus vídeos recientes"],
 "subs":["Elige 25 MB. Vidsize intenta ajustarse.","Elige una opción o introduce otro tamaño.","Elige el equilibrio que prefieras.","Prueba un objetivo de 25 MB antes de enviar.","Prueba un objetivo de 10 MB antes de enviar.","En el dispositivo · Sin subir · Sin cuenta","Revisa el archivo guardado en tu galería.","Consulta tus compresiones en el dispositivo."],
 "note":"Ejemplos ilustrativos. El tamaño real puede variar.","example":"Estimación de ejemplo","privacy":["En el dispositivo","Sin subir","Sin cuenta"],"result":"Resultado de ejemplo","history":"Historial de ejemplo"},
"pt-BR": {
 "titles":["1,2 GB → ≈ 24,8 MB","Escolha o tamanho","Três níveis de compressão","Para e-mail","Para Discord","Privacidade no aparelho","Veja o resultado","Seus vídeos recentes"],
 "subs":["Escolha 25 MB. O Vidsize tenta atingir o alvo.","Escolha uma opção ou informe outro tamanho.","Escolha o equilíbrio ideal para você.","Experimente o alvo de 25 MB antes de enviar.","Experimente o alvo de 10 MB antes de enviar.","No aparelho · Sem upload · Sem conta","Confira o arquivo salvo na sua galeria.","Veja suas compressões no próprio aparelho."],
 "note":"Exemplos ilustrativos. O tamanho real pode variar.","example":"Estimativa de exemplo","privacy":["No aparelho","Sem upload","Sem conta"],"result":"Resultado de exemplo","history":"Histórico de exemplo"},
"id": {
 "titles":["1,2 GB → ≈ 24,8 MB","Pilih ukuran target","Tiga tingkat kompresi","Untuk email","Untuk Discord","Privasi di perangkat","Lihat hasilnya","Video terbaru"],
 "subs":["Pilih 25 MB. Vidsize berusaha mencapainya.","Pilih ukuran siap pakai atau ukuran sendiri.","Pilih keseimbangan yang kamu inginkan.","Coba target 25 MB sebelum berbagi.","Coba target 10 MB sebelum berbagi.","Di perangkat · Tanpa unggah · Tanpa akun","Periksa hasil yang disimpan di galeri.","Lihat kompresi sebelumnya di perangkat."],
 "note":"Contoh ilustrasi. Ukuran hasil dapat berbeda.","example":"Contoh perkiraan","privacy":["Di perangkat","Tanpa unggah","Tanpa akun"],"result":"Contoh hasil","history":"Contoh riwayat"},
"de": {
 "titles":["1,2 GB → ≈ 24,8 MB","Zielgröße auswählen","Drei Komprimierungsstufen","Für E-Mails","Für Discord","Privat auf dem Gerät","Ergebnis ansehen","Deine letzten Videos"],
 "subs":["25 MB wählen. Vidsize versucht, das Ziel zu erreichen.","Vorgabe wählen oder eigene Größe eingeben.","Wähle die passende Balance für dein Video.","Probiere vor dem Teilen das 25-MB-Ziel.","Probiere vor dem Teilen das 10-MB-Ziel.","Auf dem Gerät · Kein Upload · Kein Konto","Prüfe die in deiner Galerie gespeicherte Datei.","Sieh frühere Komprimierungen auf deinem Gerät."],
 "note":"Beispielwerte. Die tatsächliche Größe kann abweichen.","example":"Beispielschätzung","privacy":["Auf dem Gerät","Kein Upload","Kein Konto"],"result":"Beispielergebnis","history":"Beispielverlauf"},
"hi": {
 "titles":["1.2 GB → ≈ 24.8 MB","लक्ष्य साइज़ चुनें","कंप्रेशन के तीन स्तर","ईमेल के लिए","Discord के लिए","निजता का ध्यान","नतीजा देखें","हाल के वीडियो"],
 "subs":["25 MB चुनें। Vidsize लक्ष्य पाने की कोशिश करता है।","तैयार विकल्प चुनें या अपना साइज़ डालें।","अपने लिए सही संतुलन चुनें।","भेजने से पहले 25 MB लक्ष्य आज़माएँ।","भेजने से पहले 10 MB लक्ष्य आज़माएँ।","डिवाइस पर · अपलोड नहीं · अकाउंट नहीं","गैलरी में सेव फ़ाइल देखें।","पिछले कंप्रेशन अपने डिवाइस पर देखें।"],
 "note":"उदाहरण मात्र। असली आउटपुट साइज़ अलग हो सकता है।","example":"उदाहरण अनुमान","privacy":["डिवाइस पर","अपलोड नहीं","अकाउंट नहीं"],"result":"उदाहरण नतीजा","history":"उदाहरण इतिहास"},
"ar": {
 "titles":["1.2 GB → ≈ 24.8 MB","اختر الحجم المستهدف","ثلاثة مستويات للضغط","للبريد الإلكتروني","لـ Discord","الخصوصية على جهازك","شاهد النتيجة","مقاطعك الأخيرة"],
 "subs":["اختر 25 MB ويحاول Vidsize بلوغ الهدف.","اختر حجماً جاهزاً أو أدخل حجماً مخصصاً.","اختر التوازن المناسب لك.","جرّب هدف 25 MB قبل المشاركة.","جرّب هدف 10 MB قبل المشاركة.","على الجهاز · بلا رفع · بلا حساب","راجع الملف المحفوظ في معرضك.","شاهد عمليات الضغط السابقة على جهازك."],
 "note":"أمثلة توضيحية. قد يختلف حجم الملف الفعلي.","example":"تقدير توضيحي","privacy":["على الجهاز","بلا رفع","بلا حساب"],"result":"نتيجة توضيحية","history":"سجل توضيحي"},
}

CSS = """
*{box-sizing:border-box}html,body{margin:0;width:1080px;height:1920px}
body{font-family:Arial,"Nirmala UI","Segoe UI",sans-serif;background:#f6f6fb;color:#0f1222;overflow:hidden}
.page{position:relative;width:1080px;height:1920px;display:flex;flex-direction:column;align-items:center;background:radial-gradient(circle at 50% 0,#ecebff 0,transparent 45%),#f6f6fb}
.copy{width:950px;height:310px;text-align:center;padding:76px 20px 10px;display:flex;flex-direction:column;align-items:center;justify-content:center}
.copy h1{font-size:68px;line-height:1.13;letter-spacing:-.025em;margin:0;font-weight:800;color:#17192d}
.copy p{font-size:33px;line-height:1.3;color:#5d6478;margin:20px 0 0}
.phone{width:730px;height:1320px;background:#fff;border:3px solid #e6e7f1;border-radius:58px;box-shadow:0 35px 90px #30296922;overflow:hidden;position:relative}
.top{height:92px;display:flex;align-items:center;gap:20px;padding:0 38px;border-bottom:2px solid #f1f1f7;font-size:28px;font-weight:700}
.back{color:#4f46e5;font-size:33px}.content{padding:34px 38px}.brand{color:#4f46e5;font-weight:800;font-size:25px;margin-bottom:20px}
.title{font-size:35px;font-weight:800;line-height:1.2;margin:0 0 20px}.label{font-size:20px;font-weight:700;color:#70788a;letter-spacing:.03em}
.video,.card{border:2px solid #e8e8f2;border-radius:26px;padding:24px;background:#fff;margin:18px 0}
.video{display:flex;gap:18px;align-items:center}.thumb{width:84px;height:84px;flex:none;border-radius:20px;background:#e9e8ff;display:grid;place-items:center;color:#4f46e5;font-size:34px}
.video strong{display:block;font-size:26px;line-height:1.2}.video span{font-size:21px;color:#6e7385}
.tabs{display:flex;gap:10px;margin:24px 0}.tab{flex:1;text-align:center;padding:14px 8px;border-radius:14px;background:#f4f4fa;color:#697085;font-size:22px;font-weight:700}.tab.active{background:#eeedff;color:#4f46e5}
.chips{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin:18px 0}.chip{border:2px solid #e5e5f1;border-radius:16px;padding:15px 4px;text-align:center;font-size:23px;font-weight:700;unicode-bidi:isolate;direction:ltr}.chip.on{border-color:#4f46e5;background:#eeedff;color:#4f46e5}.chip.custom{direction:inherit}
.estimate{background:#f1f0ff;border:2px solid #e2e0ff;border-radius:24px;padding:22px;margin-top:25px}.estimate strong{display:block;font-size:40px;color:#4f46e5;line-height:1.2;unicode-bidi:plaintext}.estimate p{font-size:20px;color:#5c6376;margin:10px 0 0;line-height:1.35}
.button{margin-top:26px;padding:20px;border-radius:20px;background:#4f46e5;color:white;text-align:center;font-size:23px;font-weight:800}
.level{border:2px solid #e8e8f2;border-radius:24px;padding:25px;margin:15px 0;display:flex;align-items:center;gap:20px}.level.on{border-color:#4f46e5;background:#f2f1ff}.dot{width:26px;height:26px;border:5px solid #4f46e5;border-radius:100%;flex:none}.level strong{font-size:27px}.level span{display:block;font-size:20px;color:#6e7385}
.privacy-row{display:flex;align-items:center;gap:20px;padding:25px;border:2px solid #e7e7f1;border-radius:22px;margin:16px 0;font-size:27px;font-weight:700}.check{display:grid;place-items:center;width:42px;height:42px;background:#e6f7f0;border-radius:14px;color:#0b9168;flex:none}
.result{text-align:center;padding:40px 10px}.result .check{margin:0 auto 24px;width:90px;height:90px;font-size:48px}.result h2{font-size:38px;line-height:1.2;margin:10px 0 30px}.result .big{font-size:44px;color:#4f46e5;font-weight:800;unicode-bidi:plaintext}.hint{color:#6e7385;font-size:22px;line-height:1.4}
.history-row{padding:25px 0;border-bottom:2px solid #f0f0f5}.history-row strong{font-size:25px}.history-row span{display:block;color:#6e7385;font-size:22px;margin-top:5px;unicode-bidi:plaintext}
.note{position:absolute;bottom:120px;width:920px;text-align:center;color:#646b7b;font-size:24px;line-height:1.3}
"""
LOGO = '<svg width="70" height="70" viewBox="0 0 108 108" role="img" aria-label="Vidsize"><path fill="#4F46E5" fill-rule="evenodd" d="M38,32 H70 Q78,32 78,39 Q62,54 78,69 Q78,76 70,76 H38 Q30,76 30,69 Q46,54 30,39 Q30,32 38,32 Z M48,47 Q48,44.5 50.2,45.7 L63,52.8 Q65,54 63,55.2 L50.2,62.3 Q48,63.5 48,61 Z"/></svg>'


def labels(folder):
    root = ET.parse(ROOT / "app" / "src" / "main" / "res" / folder / "strings.xml").getroot()
    return {e.get("name"): "".join(e.itertext()).replace("\\'", "'") for e in root.findall("string")}


def esc(value):
    return html.escape(str(value), quote=True)


def target_screen(t, c, chosen):
    chips = "".join('<div class="chip%s">%s MB</div>' % (" on" if n == chosen else "", n)
                    for n in (10, 16, 25, 50, 100))
    chips += '<div class="chip custom">%s</div>' % esc(t["size_custom"])
    return (f'<div class="brand">VIDSIZE</div><h2 class="title">{esc(t["compress_title"])}</h2>'
            f'<div class="video"><div class="thumb">▶</div><div><div class="label">{esc(t["selected_video"])}</div>'
            '<strong><bdi dir="ltr">1920 × 1080</bdi></strong><span><bdi>1.2 GB</bdi></span></div></div>'
            f'<div class="tabs"><div class="tab">{esc(t["mode_level"])}</div><div class="tab active">{esc(t["mode_size"])}</div></div>'
            f'<div class="label">{esc(t["section_size"])}</div><div class="chips">{chips}</div>'
            f'<div class="estimate"><div class="label">{esc(c["example"])}</div><strong>≈ {24.8 if chosen == 25 else 9.8} MB</strong>'
            f'<p>{esc(t["estimate_note"])}</p></div><div class="button">{esc(t["cta_compress"])}</div>')


def levels_screen(t):
    items = []
    for key, on in (("balanced", True), ("smaller", False), ("smallest", False)):
        title = esc(t["preset_" + key + "_title"])
        body = esc(t["preset_" + key + "_body"])
        items.append(f'<div class="level{" on" if on else ""}"><div class="dot"></div><div><strong>{title}</strong><span>{body}</span></div></div>')
    return (f'<div class="brand">VIDSIZE</div><h2 class="title">{esc(t["compress_title"])}</h2>'
            f'<div class="tabs"><div class="tab active">{esc(t["mode_level"])}</div><div class="tab">{esc(t["mode_size"])}</div></div>'
            f'<div class="label">{esc(t["section_level"])}</div>{"".join(items)}'
            f'<div class="estimate"><p>{esc(t["estimate_note"])}</p></div><div class="button">{esc(t["cta_compress"])}</div>')


def privacy_screen(t, c):
    rows = "".join(f'<div class="privacy-row"><span class="check">✓</span><span>{esc(label)}</span></div>'
                   for label in c["privacy"])
    return f'<div class="brand">VIDSIZE</div><h2 class="title">{esc(t["settings_privacy_title"])}</h2><div class="card"><p class="hint">{esc(t["settings_privacy_body"])}</p></div>{rows}'


def result_screen(t, c):
    return (f'<div class="result"><div class="check">✓</div><h2>{esc(t["result_title"])}</h2>'
            f'<div class="estimate"><div class="label">{esc(c["result"])}</div><div class="big"><bdi dir="ltr">1.2 GB → 24.8 MB</bdi></div></div>'
            f'<p class="hint">{esc(t["result_location"])}</p><div class="button">{esc(t["result_share"])}</div></div>')


def history_screen(t, c):
    rows = "".join(f'<div class="history-row"><strong>Vidsize_{name}.mp4</strong><span><bdi dir="ltr">{sizes}</bdi></span></div>'
                   for name,sizes in (("1042","1.2 GB → 24.8 MB"),("0931","240 MB → 96 MB"),("0820","88 MB → 24 MB")))
    return (f'<div class="brand">VIDSIZE</div><h2 class="title">{esc(t["section_recent"])}</h2>'
            f'<div class="label">{esc(c["history"])}</div><div class="card">{rows}</div>'
            f'<div class="estimate"><div class="label">{esc(t["storage_saved_label"])}</div><strong>1.38 GB</strong></div>')


def page_document(lang, tag, index):
    t, c = labels(LANGS[lang][0]), COPY[lang]
    if index in (0,1,3,4):
        body = target_screen(t, c, 10 if index == 4 else 25)
    elif index == 2:
        body = levels_screen(t)
    elif index == 5:
        body = privacy_screen(t, c)
    elif index == 6:
        body = result_screen(t, c)
    else:
        body = history_screen(t, c)
    direction = "rtl" if lang == "ar" else "ltr"
    heading = f'<bdi dir="ltr">{esc(c["titles"][index])}</bdi>' if index == 0 else esc(c["titles"][index])
    return (f'<!doctype html><html lang="{tag}" dir="{direction}"><head><meta charset="utf-8">'
            f'<style>{CSS}</style></head><body><div class="page"><div class="copy">'
            f'<h1>{heading}</h1><p>{esc(c["subs"][index])}</p></div>'
            f'<div class="phone"><div class="top"><span class="back">‹</span><span>Vidsize</span></div>'
            f'<div class="content">{body}</div></div><div class="note">{esc(c["note"])}</div></div></body></html>')


def feature_document():
    return f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><style>
    *{{box-sizing:border-box}}body{{margin:0;width:1024px;height:500px;background:#f6f6fb;
    font-family:Arial,"Segoe UI",sans-serif;color:#0f1222;display:flex;align-items:center;padding:56px 70px}}
    .left{{flex:1}}.brand{{display:flex;align-items:center;gap:12px;font-size:44px;font-weight:800}}
    .icon{{background:#eeedff;border-radius:20px;width:78px;height:78px;display:grid;place-items:center}}
    h1{{font-size:52px;line-height:1.1;letter-spacing:-.035em;margin:30px 0 12px}}
    h1 span{{color:#4f46e5}}p{{color:#60677a;font-size:25px;margin:0}}
    .right{{width:230px;background:#eeedff;border-radius:36px;padding:32px;display:grid;gap:18px}}
    .line{{height:30px;border-radius:15px;background:#4f46e5}}
    </style></head><body><div class="left"><div class="brand"><div class="icon">{LOGO}</div>Vidsize</div>
    <h1>Compress video<br><span>to any size</span></h1><p>On-device · Offline · Free</p></div>
    <div class="right"><div class="line"></div><div class="line" style="width:70%"></div>
    <div class="line" style="width:42%"></div></div></body></html>"""


def browser_executable():
    override = os.environ.get("CHROMIUM_EXECUTABLE")
    if override:
        return override
    for name in ("chromium", "chromium-browser", "google-chrome", "msedge"):
        found = shutil.which(name)
        if found:
            return found
    for name in ("C:/Program Files/Google/Chrome/Application/chrome.exe",
                 "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"):
        if Path(name).is_file():
            return name
    return None


def main():
    with sync_playwright() as pw:
        executable = browser_executable()
        browser = pw.chromium.launch(executable_path=executable) if executable else pw.chromium.launch()
        page = browser.new_page(viewport={"width": 1080, "height": 1920}, device_scale_factor=1)
        names = ("hero","sizes","levels","email","discord","privacy","result","history")
        for lang, (_, tag) in LANGS.items():
            destination = OUT / lang
            destination.mkdir(parents=True, exist_ok=True)
            for i, name in enumerate(names):
                page.set_content(page_document(lang, tag, i), wait_until="load")
                page.evaluate("document.fonts.ready")
                bounds = page.evaluate("""() => {
                    const copy = document.querySelector('.copy'), phone = document.querySelector('.phone');
                    return {copyOverflow: copy.scrollHeight > copy.clientHeight, phoneOverflow: phone.scrollHeight > phone.clientHeight};
                }""")
                if bounds["copyOverflow"] or bounds["phoneOverflow"]:
                    raise RuntimeError(f"Text overflow in {lang} screenshot {name}: {bounds}")
                page.screenshot(path=str(destination / f"screenshot-{i+1:02d}-{name}.png"))
                print(f"{lang} {i+1}/8 {name}", flush=True)
        feature = browser.new_page(viewport={"width":1024,"height":500}, device_scale_factor=1)
        feature.set_content(feature_document(), wait_until="load")
        feature.screenshot(path=str(OUT / "feature-graphic-1024x500.png"))
        browser.close()


if __name__ == "__main__":
    main()
