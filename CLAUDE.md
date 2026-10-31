# Dalma – a megbízás

Androidos app, amely egy YouTube-videóból **egyetlen koppintással minőségi zenét ment, videót nem**.
A YouTube-ban Megosztás → Dalma, és egy kis lapon Dalma munkához lát. A teljes feladatleírás és a
képernyőtervek: `docs/spec/` (PDF). Ez a fájl a döntéseket rögzíti, hogy minden későbbi kör ugyanazt
építse tovább.

## Ami nem változhat

- **Egyszerűség 1-es**: nincs beállítás, nincs gomb, nincs beviteli mező, nincs letöltési lista.
  Szöveg csak a legvégén, egyetlen rövid, tegeződő mondat (`app/src/main/res/values/strings.xml`).
- **Csak zene**: a YouTube 251-es Opus-folyama átkódolás nélkül, `.opus`; ha nincs, AAC 140 `.m4a`.
  Tagek (előadó, cím, album, év) és négyzetes borító a fájlba írva. Formátumváltás tilos.
- **Mentés a Zene mappába** MediaStore-on át, `Előadó – Cím.opus`, soha nem ír felül semmit.
- **Engedélyek**: csak internet és értesítés. (ACCESS_NETWORK_STATE-et a WorkManager amúgy is behozza.)
- **Mindig sötét**, menta `#8FD3BE` és tejfehér `#F4FBF8` a mély éjszakai zöldön `#0E1B19`.
  Fraunces a mondathoz, Nunito minden máshoz. Csillagos ég: nincs csillagkép, vonal vagy hold.
- **Dalma**: lágy, vonalas, pasztell, mindig mosolygós, kedves és ártatlan. Lejátszás-jel alakú hajcsat,
  amely a letöltéssel telik. Kikapcsolt rendszer-animációnál állóképekkel jelez.
- **Lejátszási lista**: nem kérdez. Listalink → egész lista (max. 300); listából megosztott dal → csak az.
- Terjesztés APK-val a GitHub Releases oldalról; aláírókulcs a tulajdonosnál (CI titkok).

## Szerkezet

| Hol | Mi |
| --- | --- |
| `core/` | Tiszta Kotlin, Android nélkül, helyben is fordul és tesztelhető: linkfelismerés, yt-dlp parancssor, kimenet-értelmezés, hibaosztályozás és újrapróbálási létra, fájlnév, mentett dalok tára, frissítés-ellenőrzés. |
| `app/` | Az Android app: `engine/` (saját futtató a youtubedl-android binárisai fölött, ellenőrzött yt-dlp-önfrissítés), `work/` (WorkManager előtérben, értesítéssel), `data/` (MediaStore, tár), `share/` (a Megosztás-lap), `home/` (főképernyő, rejtett névjegy), `ui/` (téma, ég, mondat, kabala). |
| `docs/mascot/dalma.svg` | Dalma rétegei. `tools/svg2compose.py` ebből generálja a `MascotArt.kt`-t és az ikonokat. A rétegszerződést `tools/check-mascot.py` ellenőrzi. |
| `tools/dalma-assets.py` | Ha elkészülnek az AI-s képek/videók (magenta háttér), ez vágja ki és készít átlátszó PNG-t, animált WebP-t és hajcsat-maszkot az `app/src/main/assets/dalma/` mappába. Ha a mappa létezik, az app azt használja a vektoros Dalma helyett. |
| `.github/workflows/build.yml` | Core-tesztek valódi yt-dlp-vel és ffmpeg-gel, app-tesztek, lint, Roborazzi-képernyőképek (a `ci-screenshots` ágra tolva), arm64 APK előnézeti kiadásként, `v*` tagnél aláírt kiadás, emulátoros futás. |

## Hogyan dolgozz

- Helyben: `./gradlew -p core test` (gyors), motorteszt: `-Ddalma.engineTests=true -Ddalma.ytdlp=<yt-dlp>`.
  Az app csak a CI-ban fordul (a dl.google.com nem elérhető ebből a környezetből); a CI
  képernyőképeit a `ci-screenshots` ágon nézd meg, hibáit a futás naplójából javítsd.
- A yt-dlp parancssor egyetlen helyen van: `core/.../YtDlpArgs.kt`. Minden változást a
  `EngineIntegrationTest` valódi futással igazol (Opus marad Opus, borító négyzetes, tagek jók).
- A hibák először `ErrorClassifier`-be kerülnek, utána a `RetryPlanner` dönt: motorfrissítés,
  más YouTube-kliensek, bármilyen formátum, egyszerű feldolgozás. Végleges hibánál Dalma egy
  mondattal bocsánatot kér; a mondatokat a `Sentences` választja.
- A kabalát a `docs/mascot/dalma.svg`-ben rajzold, soha nem a generált Kotlinban; futtasd a
  `python3 tools/svg2compose.py`-t és nézd meg a `node tools/render-mascot.js` renderét.
- Commit-üzenetek angolul, a felhasználói szövegek magyarul (alap) és angolul (`values-en`).

## Kiadás

1. Titkok a GitHubon: `DALMA_KEYSTORE_BASE64`, `DALMA_KEYSTORE_PASSWORD`, `DALMA_KEY_ALIAS`,
   `DALMA_KEY_PASSWORD` (opcionális: `DALMA_UPDATE_READ_TOKEN` privát repóhoz). Nélkülük a CI
   debug-kulccsal ír alá, és ezt az előnézeti kiadás szövege jelzi.
2. `git tag v1.0.0 && git push origin v1.0.0` → a CI elkészíti és feltölti a `Dalma-1.0.0.apk`-t.
3. Az app naponta egyszer megnézi a legújabb kiadást, és egy sorral szól: „Van újabb Dalma.”
