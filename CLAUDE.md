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
- **Mentés a `Zene/Dalma` mappába** MediaStore-on át (`RELATIVE_PATH = Music/Dalma/`), `Előadó – Cím.opus`,
  soha nem ír felül semmit. Ez Dalma saját könyvtára: látható a Fájlkezelőben, más lejátszó is
  lejátszhatja, nincs titkosítva, nincs elrejtve, és túléli az app törlését. (Döntés: 2026-11-01; az
  első kiadások a `Zene` gyökerébe mentettek.) A 0.2.0 lejátszója ezt a mappát olvassa
  (`data/MusicFolder`), adatbázis nélkül.
- **Tiszta lap**: Dalma csak a `Zene/Dalma` mappát nézi és listázza; más zenéhez soha nem nyúl.
- **Engedélyek**: csak internet és értesítés. (ACCESS_NETWORK_STATE-et a WorkManager amúgy is behozza; a
  FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC és FOREGROUND_SERVICE_MEDIA_PLAYBACK telepítéskor járó,
  soha nem kérdezett engedély, az Android 14 kéri a letöltés és a lejátszás előtérszolgáltatásához.) Egyetlen kivétel: újratelepítés után, ha már van
  `Zene/Dalma` mappa, a régi dalok olvasásához a rendszer egyszer megkérdezi a zene-hozzáférést
  (READ_MEDIA_AUDIO); első telepítésnél soha. Az engedély után is csak a saját mappa.
- **Mindig sötét**, menta `#8FD3BE` és tejfehér `#F4FBF8` a mély éjszakai zöldön `#0E1B19`.
  Fraunces a mondathoz, Nunito minden máshoz. Csillagos ég: nincs csillagkép, vonal vagy hold.
- **Dalma**: lágy, vonalas, pasztell, mindig mosolygós, kedves és ártatlan. Lejátszás-jel alakú hajcsat,
  amely a letöltéssel telik. Kikapcsolt rendszer-animációnál állóképekkel jelez.
- **Lejátszási lista**: nem kérdez. Listalink → egész lista (max. 300); listából megosztott dal → csak az.
- **A kliens maradjon kliens**: nem lát naplót, hibarészletet, nem kezel semmit. A diagnosztika
  (`Diagnostics` fájl, logcat) csak a fejlesztőé; a névjegyen hibasor és naplómegosztás nem lehet.
- Terjesztés APK-val a GitHub Releases oldalról; aláírókulcs a tulajdonosnál (CI titkok).

## Lejátszó (0.2.0)

A terv a feladatleírás: `docs/spec/0.2.0/DALMA-0.2.0.md`, a képernyők és Dalma pózai mellette. Minden,
ami a `Zene/Dalma` mappában van, Dalmáé: megmutatja, lejátssza, listába rendezi, szöveg, gomb és súgó
nélkül. Lépésenként, külön commitokban, a leírás sorrendjében; az 1. lépés (könyvtár) után megálltunk.
Ami a leírásban nincs, az nincs a képernyőn. A Megosztás-lap és a letöltés változatlan; csak az nyúl
hozzá, ami a lejátszóhoz kell.

## Szerkezet

| Hol | Mi |
| --- | --- |
| `core/` | Tiszta Kotlin, Android nélkül, helyben is fordul és tesztelhető: linkfelismerés, yt-dlp parancssor, kimenet-értelmezés, hibaosztályozás és újrapróbálási létra, fájlnév, mentett dalok tára, frissítés-ellenőrzés. |
| `app/` | Az Android app: `engine/` (saját futtató a youtubedl-android binárisai fölött, ellenőrzött yt-dlp-önfrissítés), `work/` (WorkManager előtérben, értesítéssel), `data/` (MediaStore, tár, az utolsó mentés belső naplója), `share/` (a Megosztás-lap), `home/` (főképernyő, rejtett névjegy), `ui/` (téma, ég, mondat, kabala). |
| `docs/handover.md` | Tapasztalatok és buktatók az első működő változatig (szimlinkek, R8, zipapp, HyperOS); nyitott dolgok. Olvasd el, mielőtt a motorhoz nyúlsz. |
| `docs/mascot/dalma.svg` | Dalma rétegei. `tools/svg2compose.py` ebből generálja a `MascotArt.kt`-t és az ikonokat. A rétegszerződést `tools/check-mascot.py` ellenőrzi. |
| `tools/dalma-assets.py` | Ha elkészülnek az AI-s képek/videók (magenta háttér), ez vágja ki és készít átlátszó PNG-t, animált WebP-t és hajcsat-maszkot az `app/src/main/assets/dalma/` mappába. Ha a mappa létezik, az app azt használja a vektoros Dalma helyett. |
| `.github/workflows/build.yml` | Core-tesztek valódi yt-dlp-vel és ffmpeg-gel, app-tesztek, lint, Roborazzi-képernyőképek (a `ci-screenshots` ágra tolva), arm64 APK előnézeti kiadásként, `v*` tagnél aláírt kiadás, emulátoros futás. |

## Hogyan dolgozz

- **Csendben, ami csak ad** (a felhasználó kérése, 2026-11-01): biztonság, megbízhatóság, önjavítás,
  háttérben futó karbantartás és kényelem kérdezés nélkül épül be, ha semmit nem vesz el, és a
  kliensnek nem kell tudnia róla („hip-hop, és töltődik a kedvenc szám”). Ami elvenne valamit, a
  felhasználó elé kerülne, pénzbe kerül, vagy apukája telefonján futna, az továbbra is az ő döntése.
- Helyben: `./gradlew -p core test` (gyors), motorteszt: `-Ddalma.engineTests=true -Ddalma.ytdlp=<yt-dlp>`.
  Az app csak a CI-ban fordul (a dl.google.com nem elérhető ebből a környezetből); a CI
  képernyőképeit a `ci-screenshots` ágon nézd meg, hibáit a futás naplójából javítsd.
- A yt-dlp parancssor egyetlen helyen van: `core/.../YtDlpArgs.kt`. Minden változást a
  `EngineIntegrationTest` valódi futással igazol (Opus marad Opus, borító négyzetes, tagek jók).
- A hibák először `ErrorClassifier`-be kerülnek, utána a `RetryPlanner` dönt: motorfrissítés,
  más YouTube-kliensek, bármilyen formátum, egyszerű feldolgozás. Végleges hibánál Dalma egy
  mondattal bocsánatot kér; a mondatokat a `Sentences` választja. Minden próbálkozás, a yt-dlp
  bőbeszédű kimenete és a váratlan kivételek a `Diagnostics` belső naplójába (`files/last-save.log`) és
  a logcatbe kerülnek (`DalmaSave`, `DalmaEngine`, `DalmaUpdater`); a felhasználó ebből semmit nem lát.
- Az app csak a Macen vagy a CI-ban fordul; a telefonra kerülő APK-t a Mac készíti (`out/Dalma-x.y.z.apk`),
  a felhasználó a Download mappából telepíti. Apukája telefonján semmi nem fut kérés nélkül.
- Az R8 kicsinyítő csak a kiadási buildben fut, ezért a műszeres tesztek (`connectedReleaseAndroidTest`)
  a kiadási APK-n futnak; a reflexióval példányosított osztályokhoz keep-szabály kell (`proguard-rules.pro`).
- A kabalát a tervező pózaiban rajzold (`docs/spec/0.2.0/design/dalma/*.svg`, 200×200, az `alap.svg` az alap),
  soha nem a generált fájlokban: `python3 tools/poses2mascot.py` építi belőlük a rétegezett `docs/mascot/dalma.svg`-t,
  `python3 tools/check-mascot.py docs/mascot/dalma.svg` ellenőrzi, `tools/svg2compose.py` (a `svgelements` csomag kell
  hozzá) generálja a `MascotArt.kt`-t és az ikonokat; a Roborazzi-képernyőképek mutatják az eredményt.
- Commit-üzenetek angolul, a felhasználói szövegek magyarul (alap) és angolul (`values-en`).

## Kiadás

1. Titkok a GitHubon: `DALMA_KEYSTORE_BASE64`, `DALMA_KEYSTORE_PASSWORD`, `DALMA_KEY_ALIAS`,
   `DALMA_KEY_PASSWORD`. Nélkülük a CI és a helyi build a tárolóban lévő, nyilvános előnézeti kulccsal
   (`app/preview.keystore`) ír alá, és ezt az előnézeti kiadás szövege jelzi. Opcionális
   `DALMA_UPDATE_READ_TOKEN` csak privát repónál: finomhangolt (fine-grained) token, csak erre az egy
   tárolóra, egyetlen joggal (Contents: Read-only), legfeljebb 1 évre; az APK-ba kerül, bárki
   kiolvashatja, ezért klasszikus `ghp_` token soha.
2. `git tag v1.0.0 && git push origin v1.0.0` → a CI elkészíti és feltölti a `Dalma-1.0.0.apk`-t.
3. Az app naponta egyszer megnézi a legújabb kiadást, és egy sorral szól: „Van újabb Dalma.”
