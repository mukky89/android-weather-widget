# Deň — Android weather & calendar widget

Samostatná Android aplikácia v slovenčine: hodiny, počasie podľa GPS, predpoveď na šesť dní, meniny, budík a kalendáre. Aktuálna verzia **0.7.0**, Android **11 a novší**. Balík aplikácie: `sk.marek.den`.

## Funkcie

- Kompaktný widget s rámčekmi, živými hodinami a dátumom.
- Aktuálne počasie podľa polohy a šesťdňová predpoveď s denným maximom/minimom.
- Slovenské meniny uložené offline.
- Najbližší budík a otvorenie systémových Hodín.
- Najbližšia udalosť z Google/Gmail a Outlook kalendárov, horizont 14 dní.
- Tlačidlo **+** pri každom kalendári: názov, začiatok/koniec, celý deň, miesto a poznámka.
- Pripomenutia udalostí s predstihom 0/5/15/30/60 minút; celodenné udalosti o 9:00.
- Voliteľný tichý prehľad na zamknutej obrazovke po rozsvietení displeja.

Google/Gmail označuje Google kalendár synchronizovaný do Androidu. Aplikácia nečíta e-maily. Outlook musí exportovať kalendáre do systému; firemná politika to môže obmedziť.

## Zostavenie

Otvor koreň repozitára v Android Studio, prípadne použi JDK 17 alebo novší, Android SDK Platform 36 a Gradle wrapper v repozitári. Nastav `ANDROID_HOME` na SDK alebo vytvor lokálny `local.properties` so `sdk.dir=...`.

Windows:

```powershell
.\scripts\build.ps1
```

Skript zostaví debug APK, spustí unit testy a Android lint. APK a jeho SHA256 uloží do ignorovaného priečinka `artifacts/`.

Priame príkazy na Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Linux/macOS:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Výstup: `app/build/outputs/apk/debug/app-debug.apk`. Projekt obsahuje jediný modul `:app`; nemá závislosť na finančnej aplikácii Tok ani na pôvodnom spoločnom pracovnom priečinku.

## Inštalácia a aktualizácia

```sh
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Na Windows možno použiť pomocný skript pre Xiaomi USB inštaláciu:

```powershell
.\scripts\install-phone.ps1 -Serial SERIAL -Apk app/build/outputs/apk/debug/app-debug.apk -ExpectedLabel 'Deň'
```

Skript potvrdzuje iba USB inštalačný dialóg zodpovedajúci názvu aplikácie. Pred spustením povoľ USB ladenie a inštaláciu na svojom zariadení.

Pri oddelení projektu zostali `applicationId`, namespace, triedy komponentov a verzia nezmenené. Aktualizácia nainštalovanej aplikácie vyžaduje rovnaký podpisový kľúč. Debug zostavenie na inom počítači obvykle používa iný kľúč; podpisové kľúče nie sú súčasťou repozitára. Pre zachovanie údajov nepoužívaj odinštalovanie ako náhradu aktualizácie.

## Nastavenie v mobile

1. Povoľ čítanie kalendárov a vyber synchronizované kalendáre.
2. V Outlooku zapni **Nastavenia účtu → Kalendár → Synchronizovať kalendáre**. Pri Google účte zapni synchronizáciu kalendára.
3. Pre počasie povoľ polohu. Pre zmenu mesta na pozadí nastav v systéme **Povoliť vždy**.
4. Pridaj widget cez aplikáciu alebo systémový výber widgetov. Na Xiaomi môže byť potrebný výber **Widgety systému Android → Deň**.
5. Ak chceš pripomenutia, zapni ich v Deň a povoľ oznámenia aj systémový prístup **Budíky a pripomenutia**.
6. Tichý prehľad na lockscreen zapni samostatne. Jeho názvy udalostí môžu byť viditeľné bez odomknutia.

Widget má minimálny rozmer 250 × 290 dp. Skutočnú veľkosť a počet buniek riadi launcher. Existujúci widget môže po aktualizácii ponechať pôvodne rezervovaný priestor.

## Súkromie a oprávnenia

- Kalendáre sa čítajú z Android Calendar Provider; zápis sa vyžiada až pri uložení novej udalosti.
- Poloha sa pred požiadavkou zaokrúhli na dve desatinné miesta a posiela Open-Meteo a systémovému geokóderu pre názov mesta. Neukladá sa história polohy.
- Udalosti sa neposielajú na náš server; synchronizáciu vytvorených udalostí vykonáva existujúci Google/Outlook adaptér v mobile.
- Cache obsahuje posledné počasie a polohu; pri vypnutí počasia sa odstráni. Systémové zálohovanie aplikácie je vypnuté.
- Pripomenutia majú samostatný kanál; prehľad dňa je predvolene bez zvuku a vibrácií. Rešpektujú systémové nastavenia a režim Nerušiť.

Repozitár obsahuje zdroje, testovacie ukážky a verejný dataset menín. Prevádzkové logy, screenshoty z telefónu, exporty účtov a podpisové kľúče sem nepatria.

## Obnova a obmedzenia

Počasie sa obnovuje približne každých 30 minút; Android môže úlohu odložiť. Ikona obnovy vyžiada načítanie. Počasie je modelový odhad Open-Meteo s časom modelových údajov. Chýbajúce dni predpovede zobrazia pomlčky.

Kalendáre zahŕňajú opakovania cez systémové `Instances`, vynechajú zrušené a odmietnuté udalosti. Tlačidlá + neponúkajú kalendáre iba na čítanie. Formulár zatiaľ nepridáva hostí ani opakovanie.

Na Xiaomi sa najbližšie budenie číta aj z `next_alarm_clock_formatted`: štandardné Android rozhranie môže na tomto zariadení zameniť polnočnú údržbu kalendára za budík. Po ukončení procesu sa údaj obnoví pri ďalšej obnove widgetu.

Lockscreen prehľad je oznámenie, nie Always-on displej ani náhrada zamknutej obrazovky. Zbalený obsah ukazuje počasie, budík a oba kalendáre; rozšírený obsah aj časy a meniny. Spôsob rozbalenia určuje systém. Bez widgetu na ploche používa zapnutý prehľad samostatnú 30-minútovú úlohu.

## Testy

Unit testy pokrývajú kalendárové zdroje, celodenné dátumy, formulár udalosti, časové pásma predpovede a plánovanie/deduplikáciu pripomenutí. Android testy overujú formuláre, parser/cache počasia, render widgetu a obsah oznámenia. Podrobnosti sú v [VERIFICATION.md](VERIFICATION.md).

```sh
./gradlew :app:assembleDebugAndroidTest
adb -s SERIAL install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL shell am instrument -w sk.marek.den.test/androidx.test.runner.AndroidJUnitRunner
```

Pred Android testami musí byť na vybranom zariadení nainštalované debug APK. Testy spúšťaj na určenom testovacom zariadení. Voliteľné testy pripnutia widgetu a zapnutia GPS sa bez výslovných instrumentation argumentov preskočia.

## Zdroje a licencie dát

- [Open-Meteo](https://open-meteo.com/en/docs): počasie, atribúcia CC BY 4.0; rešpektuj aktuálne podmienky služby.
- [name-day-calendar / Peter Knežek](https://github.com/peterknezek/name-day-calendar): meniny, MIT. Zdroj a licencia sú v `app/src/main/assets/`. Dataset obsahuje 361 dátumov; pri chýbajúcom zázname sa zobrazí „Dnes bez menín“.
- `scripts/fetch-namedays.py` obnoví dataset z verejného zdroja; nie je potrebný pri bežnom zostavení.
