# Vidsize v0.8.8 — Git Bash komutları

Depon: `C:\Users\limno\vidsize-v087-ci-fix`
İndirdiğin dosya: `vidsize-v0.8.8-QA-FIXED.zip`

> **Not:** Deponda şu an `versionCode = 15 / 0.8.7` var, bana verdiğin zip ise
> `0.8.6` idi. Kontrol ettim: 0.8.7'de uygulama kaynak kodunda değişiklik yok,
> fark sadece sürüm numarası ve `closedTest` bloğundaki `ENABLE_ADS = true`
> satırıydı — ki BUG-01'in tam sebebi de o satır. Aşağıdaki adımlar her ikisini
> de `0.8.8 / versionCode 16` ile değiştiriyor.

---

## 1. Yedek al ve yeni dal aç

```bash
cd ~/vidsize-v087-ci-fix
git status                     # kaydedilmemiş bir şey varsa önce onu halle
git checkout main
git pull
git checkout -b fix/qa-v087-v088
```

## 2. Düzeltilmiş dosyaları depoya kopyala

Zip'i `~/Downloads` içine indirdiğini varsayıyorum:

```bash
cd ~/Downloads
unzip -o vidsize-v0.8.8-QA-FIXED.zip
cp -r vidsize/. ~/vidsize-v087-ci-fix/
```

## 3. Neyin değiştiğini gör

```bash
cd ~/vidsize-v087-ci-fix
git status
git diff --stat
```

Beklenen: 38 dosya değişmiş, 4 yeni dosya eklenmiş
(`AdSlots.kt`, `EncoderSupport.kt`, `FrameAlignment.kt`, `FrameAlignmentTest.kt`
ve `docs/QA_V087_FIX_REPORT.md`).

Satır sonu (CRLF/LF) farkından dolayı bazı dosyalar tümüyle değişmiş görünebilir;
bu zararsızdır. İstersen gürültüyü şöyle azaltabilirsin:

```bash
git config core.autocrlf true
```

## 4. Commit ve push

```bash
git add -A
git commit -m "v0.8.8: QA v0.8.7 raporundaki tum hatalari duzelt

BUG-05 (kritik): sessiz sikistirma hatasi. Cihaz kodlayici yeteneklerine
gore kare boyutu secimi, 4 kademeli yeniden deneme merdiveni ve hatanin
artik kacirilmasi imkansiz modal diyalogla gosterilmesi.
BUG-01/BUG-02: test reklamlari ve AdMob validator popup'i tamamen kaldirildi;
gercek AdMob kimligi olmadan reklam acilamiyor (derleme zamani kontrolu).
BUG-03: her cikista kaybolan 4x2 px ve bozulan en-boy orani duzeltildi.
BUG-04: okunamayan video icin net hata mesaji.
BUG-06/BUG-07: Son listesi satirlari tiklanabilir; silinmis dosyalar
listeden ve 'Kazanilan alan' toplamindan dusuluyor.
BUG-08: sonuc ekrani ilk 450 ms dokunma kabul etmiyor.
BUG-09: boyut tahmini olculen degerlere gore yeniden kalibre edildi.

versionCode 16, versionName 0.8.8"
git push -u origin fix/qa-v087-v088
```

## 5. main'e al

GitHub'da Pull Request aç ve merge et, ya da doğrudan:

```bash
git checkout main
git merge --no-ff fix/qa-v087-v088
git push origin main
```

## 6. İmzalı AAB'yi üret

**AAB'yi ben üretemiyorum** — sebebi net: Play yükleme anahtarın (upload key)
yalnızca GitHub Secrets içinde duruyor (`VIDSIZE_UPLOAD_KEYSTORE_B64`), bende
yok ve olmaması da doğru. v0.8.7 AAB'si de bu workflow ile üretilmiş. Ayrıca bu
oturumun çalıştığı ortamda Android SDK yok ve Google Maven erişimi kapalı.

`main` güncellendikten sonra:

1. GitHub → deponun **Actions** sekmesi
2. Soldan **"Play Closed Test - Signed AAB"**
3. Sağ üstten **Run workflow** → branch: `main` → **Run workflow**
4. Koşu bitince **Artifacts** bölümünden `vidsize-v088-closed-test-SIGNED-aab`
   indir. İçinde:
   - `Vidsize-v0.8.8-closed-test-SIGNED.aab` → Play Console'a yüklediğin dosya
   - `SIGNING-REPORT.txt` → sürüm, commit, sertifika parmak izi, SHA256

Bu workflow AAB'yi üretmeden önce şunları zorunlu kılıyor (hepsi geçmezse
derleme durur):

- birim testler (`testDebugUnitTest`)
- lint (`lintClosedTest`)
- imza yapılandırması ve sertifika parmak izi doğrulaması
- `verifyAdsOffWithoutRealIds` — **yeni**: gerçek AdMob kimliği yoksa reklamın
  açık kalmasını imkânsız kılar
- imzalanmış AAB'nin sertifikasının beklenen parmak iziyle eşleşmesi

Ayrıca her `push` işleminde çalışan **"Android QA + Closed-Test Audit"**
workflow'u, bu düzeltmelerin geri gelmesini engelleyen yeni denetimleri de
içeriyor (örneğin `FORCE_TRANSCODE_DELTA_PX` sabitinin geri eklenmesi veya
Google örnek reklam kimliğiyle release paketlenmesi artık derlemeyi kırar).

---

## Yerelde hızlı kontrol (isteğe bağlı)

Android Studio / SDK'nın kuruluysa, push etmeden önce:

```bash
cd ~/vidsize-v087-ci-fix
./gradlew :app:testDebugUnitTest        # birim testler
./gradlew :app:lintDebug                # lint
./gradlew :app:assembleDebug            # QA APK (reklamsız)
./gradlew :app:bundleClosedTest         # imzasiz AAB (denetim)
```

Cihazda test etmek için:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Bu debug APK'da reklam **kapalı** (artık QA testçisi "Test Ad" görmüyor).
Reklam yerleşimini yerelde görmek istersen:

```bash
./gradlew :app:assembleDebug -PVIDSIZE_ENABLE_TEST_ADS=true
```

## Reklamları gerçekten açmak istediğinde

Kod değişikliği gerekmiyor, sadece 5 kimliği ver:

```bash
./gradlew :app:bundleRelease \
  -PVIDSIZE_ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY \
  -PVIDSIZE_HOME_BANNER_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/1111111111 \
  -PVIDSIZE_COMPRESSION_BANNER_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/2222222222 \
  -PVIDSIZE_NATIVE_RESULT_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/3333333333 \
  -PVIDSIZE_APP_OPEN_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/4444444444
```

Kalıcı olsun istersen bunları `~/.gradle/gradle.properties` içine yaz (depoya
commit etme).
