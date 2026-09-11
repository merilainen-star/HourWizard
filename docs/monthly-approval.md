# Kuukausittainen tuntien hyväksyntä

Kuukauden ensimmäisenä työpäivänä klo **14.00 puhelimen paikallista aikaa** tulee
erillinen **Hyväksy tunnit** -ilmoitus. Työpäivä tarkoittaa maanantaita–perjantaita
Suomen pyhäpäivät huomioiden. Työnantajakohtaista työvuorokalenteria ei ole käytössä.
Ilmoituskanava ja tunniste ovat erilliset aamu- ja iltaleimauksista. Androidin
ilmoituslupa tarvitaan; ilman täsmällisten hälytysten lupaa käyttöjärjestelmä voi
viivästyttää toimitusta. Aikataulu palautetaan käynnistyksen, sovelluspäivityksen,
kellon tai aikavyöhykkeen muutoksen yhteydessä.

Ilmoitus avaa sovelluksen hyväksyntänäkymän. Sama näkymä löytyy etusivun
**Hyväksy edellisen kuukauden tunnit** -painikkeesta, joka näkyy vain palvelimen
vahvistettua vähintään yhden hyväksyntää odottavan jakson. Painike on piilossa
latauksen ja hakuvirheen aikana sekä kaikkien jaksojen ollessa hyväksyttyjä.
Piilotus ei itsessään ilmoita tunteja hyväksytyiksi. Tila tarkistetaan etusivulle
palatessa, tilin vaihtuessa ja onnistuneen hyväksynnän jälkeen. Näytetään edellisen
kuukauden aikana päättyneet toteumajaksot (jaksotyyppiid 2). Jos palkanmaksujakso
poikkeaa kalenterikuukaudesta, näytetään sen oikea alku- ja loppupäivä; jos jaksoja
on useita, ne voi valita yksitellen. Keskeneräiseen kuukauteen päättyviä jaksoja
ei hyväksytä. Vanha ilmoitus säilyttää alkuperäisen kohdekuukautensa.

Yhteenveto sisältää palvelimen **Toteuma**, **Luetut**, **Tase** ja **Työpäivät**
-arvot. Näitä ei lasketa puhelimen leimaushistoriasta. Näkymän avaaminen, jakson
vaihto ja Päivitä tiedot ovat vain lukuoperaatioita. Vain **Hyväksy** lähettää
hyväksynnän. Painike säilyy näkyvissä mutta pois käytöstä pyynnön aikana ja
virheen jälkeen. Vasta palvelimen vahvistama hyväksyntä korvaa sen
**Hyväksytty**-tilalla. Olemassa oleva hyväksyntä näytetään samoin; perumistoimintoa
ei ole. Tarkistetun, valmistetun tai siirretyn jakson hyväksyminen on estetty.

Ennen kirjoitusta jakso luetaan uudelleen. Jos henkilö, rajat tai yhteenveto
muuttuivat, käyttäjän tulee päivittää ja tarkistaa tiedot. Tuplapainallukset on
estetty. Epäselvää verkkovirhettä ei yritetä automaattisesti uudelleen. Käyttäjän
tulee ensin päivittää palvelimen tila. Jo hyväksyttyyn jaksoon ei lähetetä uutta
kirjoitusta. Esihenkilön valmistelu- ja siirtotoiminnot eivät kuulu näkymään.
Demotilassa oikean palvelun hyväksyntätoiminto on estetty.

## Rajapinnan lähde

Kirjautuminen sekä GraphQL-array-kuljetus käyttävät nykyistä TimecardRepositorya
ja TUNTIVELHO_API.md:n päätepisteitä. Jaksohaku ja hyväksyntä perustuvat
10.9.2026 luettuun julkiseen Finago Mobiili -sovellukseen:

- https://app.tuntivelho.com/mobiili/
- https://app.tuntivelho.com/mobiili/static/js/main.9577daff7ecc85670913.js
- `kellokorttiTyovuorot` / `tyovuorot(from, to, skipRealtimeInterval: false)`
- `jaksoById(jaksoid)` ja `hyvaksyJakso(jaksoid, value: true)`
- `JaksoFields` sekä `KertymaFields`: yhteenvedon skalaarit renderöidään
  palvelun käyttöliittymässä suoraan, joten myös Android säilyttää niiden esitysmuodon.

Julkinen lähdekoodi vahvistaa kyselyiden muodon, ei käyttäjän palvelinoikeuksia.
Palvelimen palauttamat oikeus- ja validointivirheet näytetään käyttöliittymässä.

## Testaus

`ApprovalRepositoryTest`, `ApprovalControllerTest`, `ApprovalCalendarTest`,
`ApprovalScreenTest` ja `ApprovalNotificationTest` käyttävät vain keksittyjä
jaksoja ja simuloituja vastauksia. Testit kattavat kuukauden rajat, viikonloput,
pyhäpäivät, paikallisen kellonajan, ilmoituksen erillisyyden, lukuoperaatiot,
vahvistetun hyväksynnän, virheet, puuttuvan vahvistuksen, muuttuneet tiedot,
tuplapainallukset ja epäselvän verkkotuloksen.

Oikeita tuotantotunteja ei hyväksytä eikä hyväksyntöjä peruta testauksessa.

Paikallinen tarkistus 10.9.2026: `:app:testDebugUnitTest :app:assembleDebug
:app:verifyRoborazziDebug :app:lintDebug` onnistui. 56 testiä, 0 epäonnistumista,
0 testivirhettä; lint 0 virhettä (33 varoitusta). Laiteasennusta tai oikean
käyttäjätilin hyväksyntää ei käytetty tarkistuksena.
