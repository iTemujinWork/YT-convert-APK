# Átadás – amit a felhős munkamenet megtanult (2026-11-01)

Ez a jegyzet a Mac-építőgép munkamenetnek és minden későbbi körnek szól. A döntések a `CLAUDE.md`-ben
vannak; itt a tapasztalat: mi romlott el, miért, és mire kell vigyázni.

## Állapot

- Ág: `claude/gracious-noether-hliuj5`, HEAD `aea1484`. A telefonon (POCO X6 Pro 5G, Android 16, HyperOS
  3.0.9) a **0.1.2** (versionCode 3) fut és ment: „sikerült… nagyon jól működik”. A **0.1.3**
  (versionCode 4, `aea1484`) a Macen az `out/` mappában kész, nincs telepítve.
- A felhasználó döntései ma: **Opus marad** (a Xiaomi Zene nem listázza a `.opus`-t, kiterjesztés szerint
  szűr; Musicolet vagy VLC a lejátszó). **A kliens maradjon kliens**: nem lát naplót, hibarészletet,
  semmit nem kezel. Ezért a névjegy „Legutóbb nem sikerült: …” sorát és a naplómegosztást
  (`AboutCard.kt`: `lastFailure`, `shareLog`; `about_last_failure`, `about_log_title` stringek hu/en)
  **el kell távolítani**. A `Diagnostics` fájl és a logcat-sorok maradhatnak, azok csak a fejlesztőé.
- GitHub Actions az egész fiókra le van tiltva („recent account payments have failed”), ezért a CI
  (`.github/workflows/build.yml`) sosem futott; a Mac az építőgép. Kiadás a Releases oldalon még nincs,
  ezért az app frissítés-ellenőrzése csendben 404-et kap (`DalmaUpdateCheck`), ez rendben van.
- A felhős munkamenet írta és tolta a kódot; a Mac eddig nem tudott pusholni. Mostantól a Macnek kell:
  `gh auth login` vagy git-hitelesítés a Macen, vagy a felhasználó pushol.

## Ami elromlott, és miért (sorrendben)

1. **A motor zipjei szimbolikus linkeket tartalmaznak** (`libpython.zip.so` 49, `libffmpeg.zip.so` 55).
   A `java.util.zip` ezeket fájlként írta ki, ezért ffmpeg és a Python C-moduljai nem töltődtek be
   („Ezt most nem érem el”). Megoldás: `Engine.unzip` commons-compress 1.12 `ZipFile` +
   `isUnixSymlink` + `Os.symlink`. Soha ne cseréld vissza `java.util.zip`-re.
2. **Az R8 kidobta a commons-compress reflexiós konstruktorait** (`ExtraFieldUtils.register` →
   `newInstance()`), a kiadási APK-ban minden kicsomagolás `NoClassDefFoundError`-ral halt. Keep-szabály a
   `proguard-rules.pro`-ban. Tanulság: amit a debug build és a Robolectric nem fog meg, azt csak a
   kiadási APK futtatása fogja; ezért `testBuildType = "release"` és `connectedReleaseAndroidTest`.
   Minden új, reflexiót használó könyvtárhoz keep-szabály kell.
3. **Az Android `java.util.zip.ZipFile` nem nyitja meg a yt-dlp zipapp-ot** (23 bájtos `#!` sor, a zip
   eltolásai a ziptől számítanak): „Entry at offset zero has invalid LFH signature”. Emiatt a verzió
   null volt, és az önfrissítés sosem települt (a letöltött fájl is „null”). `core/ZipApp` átugorja az
   előtagot és `ZipInputStream`-mel olvas. A yt-dlp fájlhoz csak ezt használd.
4. **HyperOS**: fejlesztői mód nélkül a logcat egyetlen harmadik féltől származó sort sem tart meg;
   friss `adb install` a telefonon jóváhagyást kér (`INSTALL_FAILED_USER_RESTRICTED`), az `install -r`
   frissítés megy. A felhasználó apukájának telefonja: semmit ne futtass rajta kérés nélkül
   (a `connectedReleaseAndroidTest` kétszer telepít, tesztdalt ír, a végén eltávolítja az appot →
   emulátor, arm64 rendszerkép, csak beleegyezéssel).
5. **WorkManager**: ha a `doWork` kivételt dob, a WorkInfo kimenet üres, a lap „próbáld később”-öt mond,
   és semmi nyom. A `SaveWorker.save` ezért minden `Throwable`-t elkap (az `Error` nem `Exception`!),
   naplóz és `Outcome.Failed`-et ad. Az `EngineUpdateWorker` futó mentés alatt `retry()`-t ad, és nem
   cseréli ki a yt-dlp-t.

## A motor, röviden

- youtubedl-android 0.18.1 natív libjei: `libpython.so` (futtatható), `libpython.zip.so`,
  `libffmpeg.so`, `libffprobe.so`, `libffmpeg.zip.so`, `libqjs.so` (QuickJS 2025-04-26, a yt-dlp
  ajánlott minimuma). `--js-runtimes quickjs:<nativeLibraryDir>/libqjs.so`; a deno nincs, kihagyja.
- A csomagolt Python site-packages-ében ott a **mutagen** és a **Cryptodome**: az `.opus` borítóhoz
  yt-dlp-nek mutagen kell, ffmpeg-gel csak m4a/mp3 megy. (Gépen a pip-es `yt-dlp[default]` hozza.)
- A hivatalos zipapp tartalmazza a `yt_dlp_ejs` feladványmegoldó szkripteket; a szkriptverzió a yt-dlp
  verziójához kötött, ezért mindig az egész zipapp frissül (`tools/fetch-ytdlp.sh`, `EngineUpdater`).
  JS-futtató nélkül csak a `visionos` kliens marad, figyelmeztetéssel.
- Parancssor egy helyen: `core/.../YtDlpArgs.kt`. `--verbose` és figyelmeztetések bekapcsolva: minden
  stderr-re megy, a stdoutot a `[dalma-…]` jelölők tartják géppel olvashatónak. Az `Engine` a stderr
  elejét (8 KB) és utolsó 300 sorát tartja meg; az `ErrorClassifier` ERROR-sorokat néz, ha nincs, a
  WARNING/`[debug]` sorokat kihagyja.
- Környezet a futtatásnál: `LD_LIBRARY_PATH` (python/usr/lib, ffmpeg/usr/lib), `PYTHONHOME`,
  `SSL_CERT_FILE`, `HOME`, `TMPDIR` (a QuickJS ide ír ideiglenes szkriptet), `PATH` + `bin/` (ffmpeg,
  ffprobe linkek). A `sh -c 'echo $$; exec "$@"'` trükk adja a pid-et a folyamatfa leállításához.

## Építés és tesztelés

- Core: `./gradlew -p core test` (63 teszt); valódi motorral `-Ddalma.engineTests=true
  -Ddalma.ytdlp=<yt-dlp>`; a zipapp-olvasás a valódi fájllal `-Ddalma.zipapp=<fájl>`.
- App a Macen, iCloudon kívüli klónban (az iCloud „X 2.class” másolatai elrontják az R8-at, a build
  15 perc helyett 2): `-PdalmaVersionName=0.1.x -PdalmaVersionCode=N :app:testDebugUnitTest
  :app:lintDebug :app:assembleRelease`; a versionCode mindig nő. Aláírás az előnézeti kulccsal
  (`app/preview.keystore`, jelszó `preview`), amíg nincs saját kulcs; a telefonon a 0.1.0-t ezért
  kellett egyszer eltávolítani.
- macOS: `shasum -a 256` (nincs `sha256sum`), bash 3.2-ben `${arr[@]+"${arr[@]}"}`.
- A felhős környezetből a `dl.google.com` nem érhető el, ott csak a core fordul; a Maven Central néha
  429-et ad, várni kell.
- Képernyőképek: Roborazzi (`:app:recordRoborazziDebug`, 19 kép); a `src/test/assets` a debug assets
  közé van téve, mert a Robolectric csak a variáns assetjeit látja.

## Kabala és animáció

- Rajzolni csak a `docs/mascot/dalma.svg`-ben; `python3 tools/svg2compose.py` generálja a
  `MascotArt.kt`-t és az ikonokat; `tools/check-mascot.py` ellenőrzi a rétegszerződést;
  `node tools/render-mascot.js` rendereli. A felhasználó a B jelöltet választotta; az animációkat
  (lélegzés, pislogás, körbenézés, hajkésés, hajcsat-telítődés, szikrák, zz) finomítottuk.
- Ha megjönnek az AI-s képek/klipek (magenta háttér): `tools/dalma-assets.py <mappa>` →
  `app/src/main/assets/dalma/`; ha a mappa létezik, az app a képeket használja a vektoros Dalma helyett.

## Nyitott dolgok

1. A névjegy hibasorának és naplómegosztásának eltávolítása (fent), majd 0.1.4.
2. A felhasználó új tapasztalata, amit a Macnek ír le.
3. Musicolet: látszik-e a borító és az előadó–cím az elmentett dalnál.
4. Emulátoros `connectedReleaseAndroidTest` (beleegyezéssel); ha az AGP a release-tesztelésnél R8 vagy
   aláírás miatt elhasal, vissza lehet állni debugra, de akkor a kiadási APK-t más módon kell próbálni.
5. Kiadás barátoknak: saját kulcs a Macen (`keytool`, README „Aláírás”), `gh release create`, amíg a
   GitHub Actions tiltva van. A `.github/workflows/ping.yml` törölhető.
6. Az első futás kicsomagolási haladása a hajcsatban (`UNPACK_SHARE`) még nem volt látva telefonon.

## A felhasználóról

Magyarul, tegeződve, röviden; nem technikus, nem olvas naplót. Dönt: Opus, minőség, egyszerűség. A
Mac-munkamenetet kettesnek hívja. Ne szakítsd meg a hosszú háttérfolyamatokat kérdésekkel; ha kér valamit,
az a döntés.
