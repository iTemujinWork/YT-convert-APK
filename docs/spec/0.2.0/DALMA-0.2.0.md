# Dalma 0.2.0 — a lejátszó

Feladat a Claude Code-nak. Olvasd végig, mielőtt egy sort is írsz.
A teljes feladatleírás (a letöltő is) a „Letöltő app – feladatleírás" doksiban van; ez a 0.2.0-ra szűkített, kódolásra szánt változat.
A képernyők a `design/` mappában vannak (PNG), a mozgások képkockánként az `anim-*.png` csíkokban. Dalma pózai SVG-ben a `design/dalma/` alatt — ez még a vázlat-rajz, az igazi később jön, ugyanezekkel a pózokkal.

## Hova tartunk

Dalma (0.1.4) ma letölt: YouTube-megosztásból Opus fájlt ment a `Zene/Dalma` mappába. 0.2.0-tól le is játssza, amit őriz: minimális, reklám nélküli lejátszó, ugyanazzal az elvvel és kinézettel, mint a letöltő. A letöltőhöz csak az nyúl, ami ehhez kell.

Egy mondatban: **minden, ami a `Zene/Dalma` mappában van, Dalmáé; megmutatja, lejátssza, listába rendezi — szöveg, gomb és súgó nélkül.**

## Mielőtt bármit csinálsz — térkép

1. Olvasd el a meglévő kódot. Írd le öt mondatban: hol a letöltés, hol a Megosztás-fogadó, hol a Dalma-rajz és az animáció, hogyan íródik ki a fájl (MediaStore?), hol a verziószám.
2. Írd fel, mi hiányzik az alábbi lépésekhez a mostani kódból. Csak utána tervezz.
3. Commit, mielőtt bármihez nyúlsz. A verziószám 0.2.0 csak az utolsó lépésben lesz.

## A lépések — ebben a sorrendben, mindegyik külön commit

1. **Könyvtár.** A `Zene/Dalma` mappa beolvasása a MediaStore-ból: cím, előadó, hossz, borító, fájl-URI. Lista a memóriában, nincs adatbázis. Újraolvasás indításkor és minden visszatéréskor. Teszt: egy kézzel bemásolt `.opus` megjelenik; a máshol törölt eltűnik, hiba nélkül.
2. **Lejátszás.** Media3 ExoPlayer + MediaSessionService: lezárt képernyő, fülhallgató, Bluetooth, értesítés. Audio focus kezelve. Opus és AAC. Dal vége: a következő a lista szerint. Teszt: lezárt képernyőn a fülhallgató gombja megállít és elindít.
3. **„Most szól" képernyő.** A méretek lentebb. Dalma a bal alsó sarokban. Mozdulatok: koppintás, húzás balra-jobbra, hosszú nyomás, a vonal tekerése. Még semmi animáció.
4. **Szín.** A borítóból két szín (Palette): uralkodó és élénk. Háttér-gradiens, a vonal színe. Ha nincs borító: a két szín az előadó nevének hash-éből. Dalváltáskor 500 ms átúszás.
5. **Lista-mód.** A borító a bal felső sarokba zsugorodik, a cím mellé ül, a vonal az aljára; csipek, sorok. Vissza a kis borítóra koppintva. Kereső lehúzásra. Törlés elhúzásra: „Töröljem?" + „Igen".
6. **Keverés.** Hosszú nyomás → lista-mód, a sorok animálva átrendeződnek, aztán vissza magától. Újra: ugyanígy, visszaáll a sorrend. A szóló dal elöl marad.
7. **Listák.** `.m3u` olvasás és írás ugyanabban a mappában, relatív útvonalakkal. A „+" csip új listát csinál. YouTube-lejátszási lista megosztásából a letöltés végén magától `.m3u` lesz.
8. **Indítás, ha van zene.** A köszönés után Dalma a sarokba lép, és előjön a lejátszó (lásd a mozgásokat).
9. **Dalma a lejátszóban.** Nappal / este állapot.
10. **A letöltő animációja.** Nincs elfordulás, nincs nagyítás. A csat üresen jön, alulról, lökésekben telik; Dalma lélegzik, a fejét kicsit a csat felé billenti, oda-odapillant. A végén örül, ahogy eddig.
11. **Verzió 0.2.0.** README egy sorral, ami elindítja. CHANGELOG három sorral.

**Az 1. lépés után megállunk, és megnézzük.**

## Mi a buktató

- A MediaStore nem mindig indexeli azonnal az `.opus` fájlt. A letöltés végén `MediaScannerConnection.scanFile`, különben a friss dal nincs a listában.
- Audio focus és előtér-értesítés nélkül a rendszer megöli a lejátszást a háttérben.
- Palette sötét borítón két hasonló színt ad. Ha az élénk túl közel van az uralkodóhoz, világosítsd fehérrel, amíg olvasható a vonal.
- Shared-element átmenet Compose-ban: `SharedTransitionLayout`. Nézd meg, melyik Compose-verziótól van, és azt írd a buildbe — ne találd ki.
- Hosszú nyomás és vízszintes húzás ugyanazon a borítón ütközik. Egy `pointerInput`, a húzás küszöbe előzze meg a hosszú nyomás idejét. Teszteld, hogy húzás közben nem indul keverés.
- A kereső lehúzása ne induljon el egy sima görgetéstől: küszöb (kb. 96 dp), ellenállás, és rezgés, amikor átlépted.

## A képernyők

Alap: 390 × 844 (dp). Minden méret dp-ben ugyanez.

**Palettaállandók**

| Név | Hex | Mire |
| --- | --- | --- |
| éj (háttér) | `#0E1B19` | minden képernyő alapja |
| menta | `#8FD3BE` | Dalma, a szóló dal, a kurzor, az „Igen" |
| tej | `#F4FBF8` | címek |
| mélyteál | `#24564B` | a vonal hátravan része, csipek kerete, fogantyú |
| halvány | `#B9CFC8` | előadó, másodlagos szöveg |
| tompa | `#9FB8B0` | idő, lista-előadó |

Betű: Fraunces 500 a címekhez, Nunito 400/600/700 a szöveghez. Csillagos ég mindenhol, mint a letöltőben.

**Most szól**

- Háttér: `linear-gradient(180deg, <teteje> 0%, #0E1B19 62%, #0E1B19 100%)`, ahol `<teteje>` = az uralkodó szín 32%-a + az éj 68%-a (este 22% / 78%).
- Borító: 330 × 330, bal 30, fent 160, sarok 18, árnyék `0 24px 60px rgba(0,0,0,.45)`.
- Cím: Fraunces 24, tej, középre, fent 522, egy sor, ellipszis. Előadó: Nunito 15, halvány, fent 556.
- Vonal: bal 24 – jobb 24, fent 610, 2 px, sarok 1. A megjárt rész színe = az élénk szín 25%-a + fehér 75%-a; a hátravan rész mélyteál. **Nincs pötty, nincs fogantyú.** A tapintható sáv 44 dp magas a vonal körül. Idő csak érintés közben, a vonal alatt 10 dp-vel, Nunito 12, tompa: bal „1:48", jobb „-2:07".
- Dalma: 190 × 190, bal -6, alul 0. Hallgat (lásd Dalma-állapotok).
- Nincs borító: ugyanez az elrendezés. A négyzetben `linear-gradient(150deg, világos 0%, uralkodó 55%, sötét 100%)`, ahol világos = uralkodó 86% + fehér, sötét = uralkodó 70% + éj. Az uralkodó: HSL(hash(előadó) mod 360, 38%, 30%); az élénk: HSL(ugyanaz + 22°, 60%, 70%). Semmi ikon, semmi helyőrző. A listában ugyanez a négyzet 48-ban.

**Lista-mód** (ugyanaz a képernyő, más elrendezés)

- Kis borító: 56 × 56, bal 24, fent 62, sarok 10. Mellette cím Fraunces 18 (bal 94, fent 68), előadó Nunito 13 halvány (fent 94).
- Csipek: fent 134, bal 24; „Mind" aktív (menta háttér, éj szöveg), a többi mélyteál keret, halvány szöveg, 13 px 600, 6 × 12 belső, sarok 14, köz 8. Utolsó csip: „+".
- Sorok: fent 182-től, 64 dp-nként, bal 24, szélesség 342. Kis borító 48 × 48 sarok 10; cím Nunito 16 600 (bal 62, fent 10); előadó Nunito 13 tompa (fent 33). A szóló dal címe menta, a sor jobb végén 6 dp-s menta pont.
- Vonal: fent 796.
- Háttér: a dal uralkodó színének 10%-a az éjbe keverve fent, 40%-nál már éj.
- Kereső: lehúzásra minden 96 dp-vel lejjebb csúszik, felül egy 44 dp-s sor, alul 1 px mélyteál vonal, benne 2 × 22 menta kurzor. Semmi ikon, semmi helykitöltő szöveg.
- Törlés: a sor 96 dp-vel balra csúszik; alul 250 dp-s lap, sarok 28, háttér = éj 94% + fehér. Fogantyú 36 × 4 mélyteál. „Töröljem?" Fraunces 28 menta (bal 150, fent 58). „Igen" pirula: menta háttér, éj szöveg 15 700, 10 × 26 belső, sarok 20 (bal 150, fent 112). Dalma bocsánatkérő arccal, 200-as méretben, bal -20, alul 0. Bárhova máshova koppintasz: a lap lemegy, nem történt semmi.

**Mozdulatok** — a borítón; a vonalon és Dalmán kívül bárhol a képernyőn ugyanez

| Mozdulat | Mit csinál |
| --- | --- |
| koppintás | szól / megáll |
| húzás balra / jobbra | következő / előző |
| hosszú nyomás | keverés (lásd lent) |
| felhúzás | lista-mód; vissza: koppintás a kis borítón |
| a vonal húzása | tekerés; közben látszik az idő |
| lista lehúzása (határozottan) | kereső |
| sor elhúzása balra | törlés: Töröljem? / Igen |

## Mozgás

Szabály: egyszerre egy mozgás; 200–400 ms; lassuló (`cubic-bezier(.2,.8,.2,1)`); semmi pattogás, kivéve Dalmát. Semmi nem ugrik: ami eltűnik, az elhalványul vagy elcsúszik.

**Indítás, ha van zene** (`anim-inditas-zenevel.png`)

| mikor | mi |
| --- | --- |
| 0–600 ms | Dalma nagyban (440) megjelenik: 0 → 1 opacitás, 0.96 → 1 nagyítás — pontosan mint az üres főképernyőn |
| 800–1600 ms | kacsint |
| 2000–2700 ms | a bal alsó sarokba lép és 190-esre zsugorodik (origó: bal alsó sarok); közben a háttér felveszi a dal színét |
| 2300–2900 ms | a borító beúszik: 0 → 1 opacitás, 0.92 → 1 nagyítás |
| 2600–3100 ms | cím és előadó: 0 → 1, 8 dp-ről felcsúszva |
| 2900–3500 ms | a vonal kirajzolódik balról jobbra |

Ha nincs zene: az első 1600 ms ugyanez, és ennyi — marad az üres főképernyő.

**Lista-mód be és ki** (`anim-lista-es-keveres.png`, Apple Music-féle)

- 0–400 ms: a borító a sarokba zsugorodik (330 → 56, a bal felső sarokba, ugyanaz az elem); a nagy cím elhalványul, a kis cím a borító mellett megjelenik; a vonal 610-ről 796-ra csúszik; Dalma elhalványul.
- 300–600 ms: a csipek és a sorok 18 dp-ről felcsúszva megjelennek.
- Vissza: ugyanez fordítva.

**Keverés**

- Hosszú nyomás → rezgés → lista-mód (fent).
- 500 ms-mal később a sorok átcsúsznak az új helyükre (400 ms, mindegyik a saját útján; a szóló dal elöl marad).
- 1,5 s múlva a lista-mód magától visszamegy. Ha közben hozzáérsz, fent marad.
- Újabb hosszú nyomás: ugyanez, az eredeti sorrenddel.

**Dal vége / dalváltás**

- Borító: a régi 0.98-ra zsugorodva elhalványul, az új 1.02-ről beúszik, 300 ms. Cím, előadó ugyanígy, 60 ms késéssel. Háttér és vonalszín: 500 ms átúszás.

**A letöltő animációja** (`anim-letoltes.png`; a mostanit erre cseréld)

- Dalma ugyanakkora, nem fordul el, nincs nagyítás. Lélegzik (1,2 dp fel-le, 4–5 s).
- A csat a lap feljövetelétől üres (mélyteál belső, menta körvonal). A letöltéssel alulról telik, lökésekben: a valódi haladást mutatja, nem egyenletes animációt.
- Amíg telik: a feje 2,5°-kal a csat felé billen, a szeme felnéz a csatra; közben egyszer visszanéz, egyszer pislog. Amikor tele: szem és fej vissza, aztán az eddigi „örül" + „Elmentettem neked." + hullócsillag + a lap lemegy.

## Dalma állapotai a lejátszóban

| mikor | mit csinál |
| --- | --- |
| nappal, szól a zene | nyitott szem, kicsit élénkebb ringás: ±2°, 3 dp fel-le, 2,4 s |
| este (20:00–06:00), szól a zene | csukott szem, a feje gyengéden ring: ±1,2°, 4,2 s |
| megállítva | áll, lélegzik, pislog |
| késő este, semmi nem szól | a falnak dőlve alszik (almos póz) |

Dalma mentája sosem változik a dal színével.

## Könyvtár és listák — a szabályok

- Mappa: `Zene/Dalma` (MediaStore, `Music/Dalma`). Nem zárt: más app is látja, a fájl bárhova másolható.
- Ami a mappában van, az az övé. Bemásolt fájl: lejátssza. Máshol törölt: szó nélkül elengedi.
- Sérült vagy nem hang fájl: kihagyja, nem száll el, nem ír ki semmit.
- `.m3u`: egy sor egy fájlnév (relatív a mappához), `#EXTM3U` fejléc, `#EXTINF` sor a címmel. A lista neve a fájlnév. Más lejátszó is olvashassa.
- YouTube-lejátszási lista megosztása: a letöltés végén `<lista címe>.m3u` a mappában, a letöltött dalokkal, a lista sorrendjében.
- Nincs adatbázis. Ha valamit meg kell jegyezni (utoljára szólt dal, pozíció): egy kis `DataStore`, és ennyi.

## Amit nem csinálunk

- Nincs súgó, tipp, üres-állapot szöveg, „Nincs zene" felirat. Ha nincs zene: az üres főképernyő, Dalma, ennyi.
- Nincs beállítások menü. Nincs ikon a képernyőn, csak a „+" csip.
- Nem kódolunk át: Opus marad, AAC csak tartalék.
- Nem rendezzük át a letöltőt. Ami működik, marad.

## Kész (0.2.0)

- A mappában lévő Opus és AAC fájlok szólnak, lezárt képernyővel is, fülhallgató-gombbal is.
- A letöltő változatlanul működik, és amit letölt, azonnal a listában van.
- Minden mozdulat a táblázat szerint megy; sehol nincs szöveg, ami magyaráz.
- Rossz fájlra nem száll el.
- Fut tiszta környezetben (friss clone, egy parancs a README-ből). Be van commitolva. Nincs benne titok.
- APK a GitHub Actions-ből, aláírva, mint eddig.

## Verzió

- 0.1.4: a letöltő, kész.
- 0.2.0: ez. Új képesség, ezért a középső szám lép (SemVer: 1.0 előtt a középső szám az új képesség, az utolsó a javítás).
- 0.2.x: javítások, csiszolás, az igazi Dalma-rajz.
- 1.0.0: amikor a család és a barátok használják, és egy hétig nem kellett hozzányúlni.

## Szabályok

1. Előbb térkép, utána kód. Mindig a legkisebb következő lépés.
2. Egy lépés = egy commit. Az üzenet mondja meg, mit és miért.
3. Bizonyítsd, ne hidd. Ha eltört valami: előbb teszt, ami megmutatja, aztán javítás.
4. Semmi titok a kódban. Kulcs, jelszó csak környezeti változóban; `.env` a `.gitignore`-ban.
5. Minden sort el kell tudni mondani. Ha nem tudod, mit csinál, nem kerül be.
6. Nem találsz ki verziószámot, csomagnevet, API-t. Hivatalos doksi, és írd le, hol nézted.
7. Minden új függőség adósság. Csak az, aminek a licence engedi, és ami nélkül tényleg nem megy.
8. A felületen nincs magyarázó szöveg. Ami nincs a tervben, nincs a képernyőn.
9. Ha 20–30 perce ugyanott állsz: állj meg, írd le, mit akartál, mit csináltál, mi történt, mit próbáltál — és kérdezz.
