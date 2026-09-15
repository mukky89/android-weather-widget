# Overenie

## 0.10.0 — väčší widget, počty udalostí a natívne kalendáre

- Budík a meniny presunuté úplne hore. Zväčšené hodiny, dátum, aktuálna teplota a ikona, ikony aj teploty šesťdňovej predpovede. Výška widgetu zvýšená z 290 na 360 dp; cieľový rozmer je 4 × 5 buniek. Dátum sa na úzkom widgete automaticky zmenší.
- Počítadlo pri každom zdroji zahŕňa všetky udalosti zobrazeného dňa, aj už skončené a celodenné. Časované stretnutie má prednosť pred celodennou udalosťou v ten istý deň. Počet zahŕňa iba kalendáre vybrané v Deň a rozlišuje zdroje; opakovaný záznam rovnakej inštancie nezapočíta dvakrát.
- Pravá časť a + používajú explicitný balík podľa riadku. Outlook podporu potvrdilo rozhranie nainštalovanej aplikácie: `ACTION_VIEW` s `time/epoch` otvorí dátum a `ACTION_INSERT` s `vnd.android.cursor.dir/event` otvorí natívny formulár. Google používa rovnaké operácie vo vlastnom balíku. Chýbajúca cieľová aplikácia sa ošetrí správou bez presmerovania na iný kalendár.
- 27 unit testov, zostavenie aplikácie a testovacieho APK aj lint prešli. Päť nových testov zahŕňa celodennú udalosť spolu so stretnutím, minulé udalosti, budúci deň, duplicitné inštancie, odlišné zdroje, polnočnú hranicu, viacdňovú udalosť a deň zmeny času.
- Na Xiaomi 13 Lite prešli dva Android testy: render pri 250 × 360 dp a 350 × 360 dp s oddelenými klikacími plochami a kontrola balíkov/dátumu/MIME typov natívnych odkazov. Nové rozloženie bolo vizuálne skontrolované aj na existujúcom widgete na ploche.
- Overené otvorenie správneho dňa v Google Kalendári aj Outlooku, vrátane iného než dnešného dňa v Outlooku, a natívne vytváranie udalosti v oboch aplikáciách. Pri kontrole sa žiadna udalosť neuložila.
- Detail na ľavej strane naďalej otvára Android provider ID cez Google Kalendár aj pre exportovanú Outlook udalosť. Nová požiadavka na priamy Outlook sa týka pravej časti a tlačidla +.
- Vzhľad a viditeľnosť kalendárov v otvorenom dni riadi natívna aplikácia. Test chýbajúcej cieľovej aplikácie sa na telefóne nevykonal. Osobné snímky, logy a údaje zostávajú v ignorovaných artefaktoch.

## 0.9.0 — samostatné otvorenie udalosti a dňa

- Ľavá časť Google aj Outlook riadku otvára detail zobrazenej udalosti. Pravá časť má oddelenú plochu s časom a označením „Celý deň ›“; otvára dátum udalosti cez Calendar Provider time URI s `VIEW=DAY`. Tlačidlo + zostáva samostatné.
- Bez udalosti je cieľom dnešný deň. Dátum celodennej udalosti sa číta v UTC, časovanej udalosti v miestnom pásme. Identita odkazu na detail rozlišuje začiatok opakovanej udalosti.
- Zostavenie, všetkých 22 unit testov a lint prešli. Dva nové testy pokrývajú miestny dátum, UTC celodenné udalosti a dnešok bez udalosti.
- Verzia 0.9.0/code 9 nainštalovaná ako aktualizácia na Xiaomi 13 Lite. Render test na telefóne prešiel pri 250 × 290 dp aj 350 × 290 dp vrátane oddelenia oblastí udalosť/deň/+.
- Skutočné kliknutia overili detail Google aj Outlook udalosti, dnešný aj nasledujúci deň v jednodennom zobrazení Google Calendar a otvorenie formulára cez +. Žiadna udalosť sa pri teste neukladala.
- Denné zobrazenie rešpektuje viditeľnosť kalendárov v Google Calendar; samotné otvorenie dňa nezapína skryté kalendáre. Náhradná kalendárová aplikácia pri chýbajúcom Google Calendar nebola testovaná.
- Formát časového odkazu je popísaný v [Android Calendar Provider](https://developer.android.com/identity/providers/calendar-provider).

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

## 0.8.0 — otvorenie podrobnej predpovede

- Teplota, ikona počasia, mesto, popis počasia a celý pás predpovede otvárajú Windy.com. Hodiny naďalej otvárajú budíky a ozubené koliesko nastavenia Deň.
- Interná ForecastActivity skladá odkaz až pri kliknutí. Odovzdá dve desatinné miesta poslednej polohy počasia; pri vypnutom počasí, odobratom povolení alebo chýbajúcich/neplatných súradniciach otvorí hlavnú stránku. Chýbajúci prehliadač ošetrí správou bez pádu aplikácie.
- Zostavenie, všetkých 20 existujúcich unit testov a lint prešli. Verzia 0.8.0/code 8 nainštalovaná ako aktualizácia na testovací Xiaomi telefón.
- Reálne kliknutie na teplotu aj samostatné kliknutie na jeden deň pásu otvorilo predpoveď zodpovedajúcej lokality vo Firefoxe. Vizuálne overená viacdňová aj podrobná predpoveď s teplotou, dažďom a vetrom/modelom ECMWF. Voliteľná anonymná analytika na stránke bola odmietnutá.
- Fallback bez prehliadača a odobranie povolenia počas behu neboli testované na zariadení. Prevádzkové snímky a presná testovacia lokalita zostávajú v ignorovaných lokálnych artefaktoch.
- Formát odkazu vychádza z [oficiálnej dokumentácie Windy URL](https://community.windy.com/topic/77/windy-com-url-parameters/1).
