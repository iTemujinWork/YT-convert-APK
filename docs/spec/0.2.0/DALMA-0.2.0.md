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
7. **Listák.** `.m3u` olvasás és írás ugyanabban a mappában, relatív útvonalakkal. YouTube-lejátszási lista megosztásából a letöltés végén magától `.m3u` lesz.
7b. **Lehúzás két zónával.** Lista-módban a lehúzás rövid = vissza a „most szól"-ra, hosszú (96 dp) = kereső; a „most szól"-on a lehúzás = lista és kereső együtt. Részletek a Mozgásnál. Ez a már kész 5. lépés gesztuskódját módosítja — a hibalista után, a 8 előtt.
7c. **Listák a felületen.** Csipváltás kaszkáddal; a sor jobbra húzása = listák a sorban (tele = benne van, koppintás ki-be, + új lista ebből a dalból — lista csak így születik, üres lista nincs); hosszú nyomás a csipen = átnevezés, üres névvel törlés. Nincs lista → nincs csipsor. Részletek a Képernyőknél és a Mozgásnál. A 7b után, a 8 előtt.
8. **Indítás, ha van zene.** A köszönés után Dalma a sarokba lép, és előjön a lejátszó (lásd a mozgásokat).
9. **Dalma a lejátszóban.** Nappal / este állapot. Megállításkor este kinyitja a szemét; a borító 0.94-re megy össze.
9b. **Szerkesztés.** Dupla koppintás a dalon → cím, előadó szerkeszthető, a Borító sorra koppintva kép választható a telefon fotóalbumaiból (csak onnan). Mentés a fájl címkéibe (Opus: Vorbis comment + METADATA_BLOCK_PICTURE; AAC: iTunes-címkék), nem adatbázisba. Teszt: a módosított fájlt egy másik lejátszó is a javított adattal mutatja.
10. **A letöltő animációja.** Nincs elfordulás, nincs nagyítás. A csat üresen jön, alulról, lökésekben telik; Dalma lélegzik, a fejét kicsit a csat felé billenti, oda-odapillant. A végén örül, ahogy eddig.
11. **Verzió 0.2.0.** README egy sorral, ami elindítja. CHANGELOG három sorral.

**Az 1. lépés után megállunk, és megnézzük.**

## Mi a buktató

- A MediaStore nem mindig indexeli azonnal az `.opus` fájlt. A letöltés végén `MediaScannerConnection.scanFile`, különben a friss dal nincs a listában.
- Audio focus és előtér-értesítés nélkül a rendszer megöli a lejátszást a háttérben.
- Palette sötét borítón két hasonló színt ad. Ha az élénk túl közel van az uralkodóhoz, világosítsd fehérrel, amíg olvasható a vonal.
- Shared-element átmenet Compose-ban: `SharedTransitionLayout`. Nézd meg, melyik Compose-verziótól van, és azt írd a buildbe — ne találd ki.
- Hosszú nyomás és vízszintes húzás ugyanazon a borítón ütközik. Egy `pointerInput`, a húzás küszöbe előzze meg a hosszú nyomás idejét. Teszteld, hogy húzás közben nem indul keverés.
- A kereső lehúzása ne induljon el egy sima görgetéstől: küszöb (96 dp), ellenállás, és rezgés, amikor átlépted.
- A csipsor vízszintesen görgethető, és ugyanott hosszú nyomás = átnevezés: a hosszú nyomás csak akkor induljon, ha az ujj nem mozdult (slop), különben görgetés közben nyílik meg a szerkesztés.
- A sor jobbra húzása és a lista függőleges görgetése egy felületen van: az irányt az első 16 dp dönti el, utána nem vált.
- A rövid lehúzás = bezárás csak akkor élhet, ha a lista a tetején áll (görgetés 0), és csak húzásra, nem a görgetés lendületéből. Holtsáv 16 dp: ami ennél rövidebb, az semmi. Különben egy erős felgörgetés túlfutása bezárja a listát, és azt senki nem érti.

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
- Csipek: fent 134, bal 24; „Mind" aktív (menta háttér, éj szöveg), a többi mélyteál keret, halvány szöveg, 13 px 600, 6 × 12 belső, sarok 14, köz 8. Nincs „+" csip. Ha egy lista sincs, csipsor sincs: a sorok 134-től indulnak; az első lista születésekor a csipsor 18 dp-ről beúszik és a sorok lecsúsznak 182-re (300 ms).
- Sorok: fent 182-től, 64 dp-nként, bal 24, szélesség 342. Kis borító 48 × 48 sarok 10; cím Nunito 16 600 (bal 62, fent 10); előadó Nunito 13 tompa (fent 33). A szóló dal címe menta, a sor jobb végén 6 dp-s menta pont.
- Vonal: fent 796.
- Háttér: a dal uralkodó színének 10%-a az éjbe keverve fent, 40%-nál már éj.
- Kereső: lehúzásra (96 dp, lásd a Mozgásnál) minden 96 dp-vel lejjebb csúszik, felül egy 44 dp-s sor, alul 1 px mélyteál vonal, benne 2 × 22 menta kurzor. Semmi ikon, semmi helykitöltő szöveg. Bezárás: felgörgetés — a lista felmegy a helyére, a sor eltűnik (ha üres a mező; ha van benne szöveg, marad, amíg ki nem törlöd).
- Listák (`design/anim-csipvaltas.png`, `anim-hozzaadas.png`, `anim-atnevezes.png`): a csipsor vízszintesen görgethető, sorrend: „Mind", aztán a listák ábécében (a fájlnév szerint; átnevezés után is egyértelmű, nem kell dátumot őrizni). A „Mind" minden dal, a legújabb fent (fájldátum). Egy lista = egy `.m3u`, a sorrendje a hozzáadás sorrendje; átrendezés nincs a 0.2.0-ban. A csip csak nézetet vált: a szóló lista akkor lesz az, amire koppintottál, ha egy dalára koppintasz. Egy dal akárhány listában lehet. Üres lista nincs: lista csak egy dalból születik (lent), és ha az utolsó dalát kiveszed belőle, a lista eltűnik — a csipje elhalványul, az `.m3u` törlődik.
- A sor jobbra húzva (72 dp, elengedésre visszaugrik) kinyílik 60-ról 104 dp-re, az alatta lévők lejjebb csúsznak; a második sorban a listák csipjei (a „Mind" nélkül), balról 0-tól, ugyanaz a csip mint fent: tele menta = benne van, keret = nincs. Koppintás vált, azonnal írja az `.m3u`-t. A csipek végén „+": ez az egyetlen „+" az appban, és az egyetlen hely, ahol lista születik. Koppintásra a csip kinyílik beviteli csippé (menta keret, kurzor, billentyűzet), név, Enter → kész a lista, és ez a dal már benne van (tele); fent a csipsor végén megjelenik az új csip. Bezárás: koppintás bárhova máshova, vagy a sor balra húzása; az üres „+" csip visszazárul, semmi nem jön létre.
- Lista átnevezése és törlése: hosszú nyomás a csipen (rezgés) → a csipbe kurzor jön a név végére, menta keret, billentyűzet; a név szerkeszthető, a csip szélessége a gépeléssel megy, a többi csip vele csúszik. Enter: a keret visszavált, az `.m3u` átnevezve. Ha a nevet üresre törlöd és Enter: ugyanaz a lap, mint a dal törlésénél — „Töröljem?" / „Igen", Dalma bocsánatkérő arccal —, Igenre az `.m3u` törlődik, a csip elhalványul, a többi a helyére csúszik; a dalok a mappában maradnak. Máshova koppintva: a név visszaáll, semmi. A „Mind" csipre a hosszú nyomás nem csinál semmit.
- Törlés: a sor 96 dp-vel balra csúszik; alul 250 dp-s lap, sarok 28, háttér = éj 94% + fehér. Fogantyú 36 × 4 mélyteál. „Töröljem?" Fraunces 28 menta (bal 150, fent 58). „Igen" pirula: menta háttér, éj szöveg 15 700, 10 × 26 belső, sarok 20 (bal 150, fent 112). Dalma bocsánatkérő arccal, 200-as méretben, bal -20, alul 0. Bárhova máshova koppintasz: a lap lemegy, nem történt semmi.

**Mozdulatok** — a borítón; a vonalon és Dalmán kívül bárhol a képernyőn ugyanez

| Mozdulat | Mit csinál |
| --- | --- |
| koppintás | szól / megáll |
| húzás balra / jobbra | következő / előző |
| hosszú nyomás | keverés (lásd lent) |
| felhúzás | lista-mód; vissza: rövid lehúzás, vagy koppintás a kis borítón |
| a vonal húzása | tekerés; közben látszik az idő |
| lehúzás a „most szól"-on (96 dp) | lista és kereső, egyszerre |
| lista lehúzása röviden (16–96 dp), elengedés | vissza a „most szól"-ra |
| lista lehúzása hosszan (96 dp-n túl, rezgés), elengedés | kereső |
| lista felgörgetése nyitott keresővel | a kereső becsukódik |
| sor elhúzása balra | törlés: Töröljem? / Igen |
| sor elhúzása jobbra | listák: a sor kinyílik, alatta a listák csipjei (tele = benne van); koppintás ki-be; + új lista ebből a dalból |
| koppintás egy csipen | az a lista látszik; a zene nem áll meg |
| hosszú nyomás egy csipen | átnevezés helyben a csipben: kurzor, név, Enter. Üresre törölve és Enter: Töröljem? / Igen — a lista megy, a dalok maradnak. A „Mind"-re nem reagál |
| dupla koppintás (borítón, címen vagy a lista sorában) | szerkesztés (lásd lent) |

**Szerkesztés** (`design/Lejatszo-Szerkeszt.png`, `anim-szerkesztes.png`)

- Dupla koppintás. A borítón az első koppintás azonnal szól/megáll; ha 300 ms-on belül jön a második, ezt visszavonjuk és szerkesztés nyílik. A címen és a lista sorában nincs ilyen ütközés.
- Ugyanaz a képernyő alakul át, 500 ms: a borító felmegy (fent 60) és 160-asra zsugorodik; a nagy cím elhalványul; alatta három sor jön 6 dp-ről felúszva: cím (Fraunces 22, tej, fent 250), előadó (Nunito 15, halvány, fent 306), Borító (Nunito 15, tompa, fent 354 — nem beviteli mező, hanem egy sor, amire koppintva a képválasztó nyílik; a „Borító" szó az egyetlen ilyen felirat az appban). Minden sor alatt 1 px mélyteál vonal, a kurzor menta. Jobbra lent egy „Mentés" pirula (menta háttér, éj szöveg, fent 412). A billentyűzet feljön.
- A Borító sorra koppintva a rendszer saját képválasztója nyílik (`PickVisualMedia`, `ImageOnly`): csak a telefon fotóalbumai, kamera és más forrás nincs, engedélykérés sincs. A billentyűzet lemegy, a választó alulról jön fel. A terven egy rács jelzi a helyét; a valódi a rendszeré, azt nem mi rajzoljuk. A kiválasztott kép középre vágva négyzet lesz, legfeljebb 1000 × 1000, JPEG 85; a borító 500 ms alatt átúszik az újba, és vele a háttér meg a vonal színe is (Palette újra). A választó bezárása után a mezők maradnak, a billentyűzet nem jön vissza magától.
- Mentés: a billentyűzet lemegy, a sorok visszaalakulnak címmé, a borító visszanő; Dalma csatja egyenesbe áll (300 ms), a vonal alatt „Megjegyeztem." (Fraunces 20, menta) 1,2 másodpercig, aztán a csat visszacsúszik.
- Elvetés: lehúzás vagy koppintás a mezőkön kívül — minden visszaáll, nem történt semmi. Nincs „Mégse" gomb.
- A címkék a fájlba íródnak; a lista és a „most szól" azonnal a frissített adatot mutatja.

## Mozgás

Szabály: egyszerre egy mozgás; 200–400 ms; lassuló (`cubic-bezier(.2,.8,.2,1)`); semmi pattogás, kivéve Dalmát. Semmi nem ugrik: ami eltűnik, az elhalványul vagy elcsúszik.

Két mozgás között legyen levegő: a köszönés után egy teljes másodperc, és csak aztán lép a sarokba. Ha az egyik a másikba ér, mindkettő eltűnik. Szerkesztésnél is: a mezők eltűnnek, és csak utána nő vissza a borító.

Ami együtt tartozik, együtt mozog: ha a szem elnéz (2,2 jobbra, 2,4 fel), a felső szemhéj vonala ugyanazt az elmozdulást kapja, ugyanazzal az időzítéssel — különben a szem kibújik a vonal fölé. A szemöldök marad.

Dalma köszönése mozgás, nem két kép: a fej 4°-ot billen (előtte egy leheletnyi ellenmozdulat), a hátsó haj 100 ms-mal, a frufru 150 ms-mal később követi; a bal szem függőlegesen összehúzódik egy vonalig (nem átvált), a szemhéj íve akkor tűnik elő; a szemöldök és a száj átmorfol. 1,2 s az egész. Éjjel nem kacsint: álmosan, félig nyitott szemmel ébred, és a sarokban csukott szemmel hallgat.

**Indítás, ha van zene** (`anim-inditas-zenevel.png`)

| mikor | mi |
| --- | --- |
| 0–600 ms | Dalma nagyban (440) megjelenik: 0 → 1 opacitás, 0.96 → 1 nagyítás — pontosan mint az üres főképernyőn |
| 800–2000 ms | köszön: billen a fej, a haj követi, kacsint, vissza |
| 2000–3000 ms | áll, néz rád — levegő a két mozgás között |
| 3000–3700 ms | a bal alsó sarokba lép és 190-esre zsugorodik (origó: bal alsó sarok); közben a háttér felveszi a dal színét |
| 3300–3900 ms | a borító beúszik: 0 → 1 opacitás, 0.92 → 1 nagyítás |
| 3600–4100 ms | cím és előadó: 0 → 1, 8 dp-ről felcsúszva |
| 3900–4500 ms | a vonal kirajzolódik balról jobbra |
| 4200–4800 ms | a sarokban a köszönő figurából hallgató lesz (nappal nyitott szem, este csukott) |

Ha nincs zene: az első 2000 ms ugyanez, és ennyi — marad az üres főképernyő. Éjjel (`anim-inditas-zenevel-ejjel.png`): a kacsintás helyett álmos ébredés — a falnak dőlésből kiegyenesedik, félig nyitott szem, puha mosoly —, a többi ugyanaz.

**Lehúzás** (`anim-lehuzas-lista.png`, `anim-lehuzas-fo.png`)

Egy gesztus, két zóna, egy küszöb. A tartalom az ujjal megy: 96 dp-ig egy az egyben, utána ellenállással (0,4-szeres). A küszöbnél rezgés, és ugyanabban a pillanatban kibomlik a kereső sora fent (0 → 44 dp, 200 ms), a vonala egy pillanatra mentára villan — ez mondja meg, hogy most már a kereső-zónában vagy. Ami a küszöb alatt van, annak nincs külön jelzése: amit lehúzol és elengedsz, az becsukódik, ezt mindenki ismeri.

- Lista-módban, rövid húzás (16–96 dp) és elengedés: a lista-mód → „most szól" átmenet, ahogy lent, 400 ms. A sorok onnan csúsznak tovább lefelé, ahol az ujj elengedte őket, és közben halványulnak.
- Lista-módban, a küszöbön túl és elengedés: a lista beáll 96 dp-vel lejjebb, a kereső sora marad, kurzor, billentyűzet. 250 ms.
- „Most szól"-on: a borító, a cím és a vonal megy az ujjal (ugyanaz a 96 + ellenállás). A küszöb alatt elengedve minden visszaugrik, nem történt semmi. A küszöbön túl elengedve egy animációban, 400 ms: a borító a sarokba (a kereső-helyzetbe, fent 158), a nagy cím el, a kis cím mellé, csipek és sorok 18 dp-ről fel, a vonal 796-ra, Dalma el — és a kereső sora már fent van.
- Nyitott keresővel felgörgetés: a lista felmegy 96-tal a helyére, a sor közben elhalványul. Ha van szöveg a mezőben, nem csukódik.
- A küszöb után az ellenállás azért kell, hogy érezhető legyen: „innen már nincs hova". 150 dp ujj = 118 dp tartalom.

**Csipváltás** (`anim-csipvaltas.png`)

- A menta pirula átcsúszik a régi csipről az újra (250 ms), a szöveg színe vele vált.
- A régi sorok fentről lefelé, egyenként 30 ms késéssel mennek el: 180 ms alatt halványulnak, 6 dp-t lecsúsznak, és elmosódnak (blur 3 dp; Android 12 alatt nincs blur, ott csak a halványulás és a csúszás). Az új sorok ugyanígy, fentről lefelé, 30 ms-onként jönnek: −6 dp-ről, elmosódásból, 220 ms. A kettő átlapol: az új kaszkád 250 ms-mal a régi után indul, nem várja meg a végét. Az egész fél másodperc alatt lecseng, mert naponta ötvenszer nyomják.
- Ha a szóló dal benne van az új listában, a sora menta; ha nincs, a zene attól még megy tovább.

**Hozzáadás a sorban, új lista** (`anim-hozzaadas.png`)

- Jobbra húzás: a sor az ujjal megy (max 72 dp), elengedésre 250 ms alatt visszaugrik, és közben kinyílik (300 ms); az alatta lévő sorok ugyanannyi idő alatt csúsznak lejjebb. A csipek 150 ms-mal a nyílás után jönnek, balról 8 dp-ről, 40 ms-onként.
- Koppintás egy csipen: 200 ms alatt kitelik (keret → menta háttér, szöveg éj), vagy vissza.
- A „+" a sor csipjei végén: 250 ms alatt szélesedik beviteli csippé, a „+" jel eltűnik, menta keret, kurzor; a billentyűzet feljön. Enter: a keret visszavált, a név marad, a csip kitelik (benne a dal); fent a csipsor végén 8 dp-ről beúszik az új csip (ha túlér, a sor odagördül). Üres név + Enter vagy elkattintás: a csip visszazárul, semmi.
- A nyitott sor bezárása: a csipek 150 ms alatt el, a sor 300 ms alatt összezár, a többi visszacsúszik.

**Átnevezés, törlés** (`anim-atnevezes.png`)

- Hosszú nyomás (500 ms, a végén rezgés): a csip kerete 150 ms alatt mentára vált, a kurzor a név végén villog, a billentyűzet feljön. A csip szélessége minden betűvel ugrik (nincs animáció, mint egy beviteli mezőnél), a mögötte lévő csipek 200 ms alatt követik.
- Enter: a keret 200 ms alatt visszavált mélyteálra, a billentyűzet lemegy. Üres név + Enter: a „Töröljem?" lap feljön (ugyanaz, mint a dalnál, 300 ms). Igen: a lap lemegy, a csip 250 ms alatt 0 szélességre zárul és elhalványul, a többi csip a helyére csúszik.
- Máshova koppintva szerkesztés közben: a régi név 150 ms alatt visszaáll, semmi nem történt.

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
- Nincs beállítások menü. Nincs ikon a képernyőn; az egyetlen „+" a sor listái között, és csak akkor látszik, ha a sort jobbra húztad.
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
