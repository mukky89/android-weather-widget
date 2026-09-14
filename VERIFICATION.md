# Overenie

## Funkčný základ 0.7.0

Pred oddelením bol rovnaký zdroj aplikácie overený na Xiaomi 13 Lite s Androidom 15:

- 20 unit testov, úspešné zostavenie a Android lint.
- Render widgetu pri 250 × 290 dp a 350 × 290 dp bez orezania kontrolovaných prvkov.
- Obsah tichého verejného oznámenia obsahuje Google aj Outlook, vynechá miestny kalendár.
- Widget na ploche zobrazuje dva kalendáre, predpoveď, čas, meniny a systémový budík.
- Prehľad je viditeľný na skutočnej zamknutej obrazovke po rozsvietení. Xiaomi zobrazovalo zbalený obsah; rozšírený obsah bol overený v objekte oznámenia.
- Otvorenie systémových Hodín a formulárov pre pridanie udalosti bolo overené. Skutočný zápis novej udalosti a následná synchronizácia na server neboli testované.
- Skúšobné pripomenutie sa doručilo aj po ukončení procesu aplikácie. Dlhodobé doručenie cez noc, po reštarte a v Doze nebolo overené.

Pri tomto oddelení sa prevádzkové screenshoty, osobné názvy udalostí, sériové čísla zariadení a lokálne logy neprenášajú do verejného repozitára.

## Samostatný projekt

Modul sa presunul z `:day` do štandardného `:app`. Identita `sk.marek.den`, Android komponenty a verzia 0.7.0/code 7 zostávajú rovnaké. Kopírovaný je iba modul Deň a spoločná konfigurácia nástrojov; finančná aplikácia nie je závislosťou projektu.

Overenie samostatného zostavenia a testov sa vykonáva príkazom `scripts/build.ps1`; zostavenie instrumentation APK cez `:app:assembleDebugAndroidTest`. Výsledky vznikajú v `app/build/` a zostavovacie artefakty v ignorovanom `artifacts/`.

Pri oddelení 14. 9. 2026 úspešne prešlo zostavenie z nového priečinka, všetkých 20 unit testov, lint aj zostavenie instrumentation APK. Všetkých 46 prenesených súborov modulu (zdroje, zdroje testov, resources, assets a modulový build súbor) bolo pred zápisom do Git porovnaných pomocou SHA256 s pôvodným modulom; boli zhodné. Testy na telefóne neboli znovu spúšťané, pretože kód aplikácie sa pri oddelení nemenil.
