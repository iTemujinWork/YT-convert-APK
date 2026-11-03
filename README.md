# Dalma

Androidos app, amely egy YouTube-videóból **egyetlen koppintással** minőségi zenét ment, és le is játssza,
amit őriz. A YouTube-ban megnyomod a Megosztást, kiválasztod Dalmát, és egy kis lapon ő azonnal munkához lát:
a hajcsatja megtelik a letöltéssel, aztán örül, és egy rövid mondattal jelzi, hogy kész. Az appot megnyitva
a `Zene/Dalma` mappa dalai szólnak. Nincs beállítás, nincs gomb, nincs súgó; szöveg csak ott, ahol egy
mondat kell.

<p align="center"><img src="docs/mascot/moods.png" width="720" alt="Dalma hangulatai"></p>

## Mit tud

- **Opus, ahogy a YouTube adja** (251-es folyam, kb. 160 kbit/s), átkódolás nélkül; ha egy dalhoz nincs
  Opus, AAC (`.m4a`). Előadó, cím, album, év és **négyzetes borító** a fájlba írva, hogy bármelyik lejátszó
  rendesen mutassa.
- **A Zene/Dalma mappába ment** (`Előadó – Cím.opus`), tárhely-engedély nélkül, és soha nem ír felül semmit.
  A mappa az övé, de nem zárt: más app is látja, a fájl bárhova másolható.
- **Érti a linkeket**: Shorts, youtu.be, YouTube Music, mobillinkek, megosztott szöveg közepén is.
  Listából megosztott dalnál csak azt az egyet menti, listalinknél az egész listát (legfeljebb 300-at), és a
  végén `.m3u` lista lesz belőle.
- **Már megvan**: ugyanazt a dalt nem tölti le kétszer, csak annyit mond: *Ez már megvan.*
- **Háttérben is befejezi**, ha lehúzod a lapot vagy kilépsz; értesítésben megszakítható. Ha nincs net,
  sorba teszi, és magától elindul, amint lesz. Megszakadt letöltést folytat.
- **Megjavítja magát**: a yt-dlp letöltőmotor naponta frissül, hiba után frissítve, más YouTube-kliensekkel,
  más formátummal, egyszerűbb feldolgozással próbál újra, és csak a végén kér bocsánatot egy mondattal.
- **Lejátszó**: a mappa dalai lezárt képernyőn, fülhallgató-gombbal és Bluetoothon is szólnak. Koppintás:
  szól / megáll; húzás balra-jobbra: következő / előző; a vonal húzása: tekerés; felhúzás: lista-mód; lehúzás:
  lista és kereső; hosszú nyomás: keverés; a sor elhúzása balra: törlés, jobbra: listák. Dupla koppintás a
  dalon: a cím, az előadó és a borító szerkeszthető, és a fájl címkéibe kerül.
- **Engedélyek**: csak internet és értesítés. Újratelepítés után, ha már van `Zene/Dalma` mappa, a rendszer
  egyszer megkérdezi a zene-hozzáférést a régi dalokhoz.

## Dalma

Mindig sötét, menta és tejfehér egy mély éjszakai zöldön, csillagos ég, Fraunces és Nunito betűk.
Dalma lélegzik, pislog, köszönéskor billen a feje és kacsint, este álmosan ébred a falnak dőlésből.
Töltés közben a hajcsatja, amely lejátszás-jel alakú, üresen jön, és a valódi haladással telik,
lökésekben; közben a csat felé billenti a fejét és odapillant, a végén örül, halkan rezeg a telefon, és egy
hullócsillag fut át az égen. A lejátszóban a bal alsó sarokban hallgat: nappal nyitott szemmel, élénkebben
ringva, este csukott szemmel, gyengédebben; megállítva áll és pislog; késő este, ha semmi nem szól, a falnak
dőlve alszik. Ha a telefonon ki van kapcsolva az animáció, Dalma állóképekkel jelez.

Dalma vektoros rajz, a tervező pózaiból (`docs/spec/0.2.0/design/dalma/*.svg`) építve
(`tools/poses2mascot.py` → `docs/mascot/dalma.svg` → `tools/svg2compose.py`). Hosszan nyomva Dalmát
előjön a rejtett névjegy: verzió, motor, licencek.

## Telepítés

1. A [Releases](https://github.com/iTemujinWork/YT-convert-APK/releases) oldalról töltsd le a
   `Dalma-x.y.z.apk` fájlt (vagy az **előnézeti** kiadást, amely minden commitból készül).
2. Engedélyezd az ismeretlen forrásból telepítést, és telepítsd. Android 10 vagy újabb kell.
3. A YouTube-ban: Megosztás → Dalma. Az első alkalommal a rendszer egyszer megkérdezi, küldhet-e
   értesítést; ez kell ahhoz, hogy a háttérben is menthessen.

Az app naponta egyszer megnézi, van-e újabb kiadás, és egy sorral szól: *Van újabb Dalma.*

## Fejlesztés

Friss clone-ból egy parancs építi meg (az Android SDK-n kívül semmi nem kell előre; a yt-dlp-t a build
tölti le és ellenőrzi):

```bash
./gradlew :app:assembleRelease
```

- `core/`: tiszta Kotlin, Android SDK nélkül fordul és tesztelhető: `./gradlew -p core test`.
  A valódi yt-dlp + ffmpeg futtatásával: `./gradlew -p core test -Ddalma.engineTests=true -Ddalma.ytdlp=$(which yt-dlp)`;
  a címkeírás tesztje egy mutagenes Pythonnal és ffmpeg-gel: `-Ddalma.python=$(which python3)`.
- `app/`: Jetpack Compose, Media3, WorkManager, MediaStore; a letöltőmotor a
  [youtubedl-android](https://github.com/yausername/youtubedl-android) csomagolta Python + yt-dlp +
  QuickJS + FFmpeg, és a címkéket ugyanennek a Pythonnak a mutagenje írja.
- Tesztek: Robolectric + Roborazzi képernyőképek (`./gradlew :app:recordRoborazziDebug`), emulátoron vagy
  USB-re dugott telefonon a motor és a Zene mappa, a kicsinyített kiadási APK-n
  (`./gradlew :app:connectedReleaseAndroidTest`).
- A tervező feladatleírása és képernyői: `docs/spec/0.2.0/`; a döntések: `CLAUDE.md`; a motor buktatói:
  `docs/handover.md`.
- CI: a `.github/workflows/build.yml` minden pushra lefuttatja a teszteket, a lintet, elkészíti a
  képernyőképeket (a `ci-screenshots` ágra tolja), és feltölti az arm64 APK-t előnézeti kiadásként;
  `v*` tagnél aláírt kiadás készül.

### Aláírás

A kulcs nálad marad. Egyszer, a gépeden:

```bash
keytool -genkeypair -v -keystore dalma.jks -alias dalma -keyalg RSA -keysize 4096 -validity 36500
```

Készíts róla biztonsági másolatot (elveszett kulccsal a barátok csak újratelepítéssel tudnak frissíteni),
majd add meg a repó *Settings → Secrets and variables → Actions* oldalán:

| Titok | Érték |
| --- | --- |
| `DALMA_KEYSTORE_BASE64` | `base64 -w0 dalma.jks` kimenete |
| `DALMA_KEYSTORE_PASSWORD` | a keystore jelszava |
| `DALMA_KEY_ALIAS` | `dalma` |
| `DALMA_KEY_PASSWORD` | a kulcs jelszava |
| `DALMA_UPDATE_READ_TOKEN` | csak privát repónál: finomhangolt token, csak erre a tárolóra, Contents: Read-only (az APK-ba kerül, bárki kiolvashatja; klasszikus `ghp_` token soha) |

Titkok nélkül a CI és a helyi build a tárolóban lévő, nyilvános előnézeti kulccsal (`app/preview.keystore`)
ír alá, így az előnézetek frissítik egymást; a saját kulcsos verzióra csak törlés után lehet váltani.

## Licencek

yt-dlp (Unlicense), youtubedl-android (GPL-3.0), FFmpeg (LGPL/GPL), Python (PSF), mutagen (GPL-2.0+),
QuickJS (MIT), Fraunces és Nunito (OFL 1.1), Jetpack Compose és AndroidX (Apache 2.0). A youtubedl-android
GPL-3.0 licence miatt aki megkapja az appot, kérésre a forrását is megkaphatja: ez a tároló az.
