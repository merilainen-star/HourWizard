# Sovelluspäivitysten ilmoitus

Sovellus tarkistaa julkaistun testiversion kerran vuorokaudessa omalla Androidin
JobScheduler-taustatyöllä. Työ tarvitsee verkkoyhteyden, säilyy puhelimen
uudelleenkäynnistyksen yli ja varmistetaan sovelluksen käynnistyessä sekä päivityksen
jälkeen. Sovelluksen avaaminen tai leimausmuistutusten ajastaminen ei nollaa sen
vuorokausijaksoa. Android voi siirtää suoritusta akun säästämiseksi tai verkon puuttuessa.
Tarkistus ei riipu aamun leimausmuistutuksesta, työpäivistä tai täsmällisten hälytysten luvasta.

Uudesta julkaistusta versiosta tulee **Numbawang-päivitys saatavilla** -ilmoitus
**Sovelluspäivitykset**-kanavassa. Samasta versiosta ilmoitetaan vain kerran.
Ilmoituksen napautus avaa sovelluksen päivitysnäkymän; lataus ja asennus tapahtuvat
käyttäjän valinnasta. Sovelluksen ilmoitusluvan ja päivityskanavan täytyy olla käytössä.
Estettyä ilmoitusta ei tallenneta lähetetyksi, joten se voidaan näyttää seuraavassa
päivittäisessä tarkistuksessa luvan palauttamisen jälkeen. Epäonnistunut verkkohaku
ei tuota ilmoitusta. Asetusten käsin tehtävä päivitystarkistus on edelleen käytettävissä.

Testit käyttävät keksittyä julkaisumetadataa ja tarkistavat päivittäisen, pysyvän
ajastuksen, uudelleenkäynnistyksen/päivityksen palautuksen, ilmoituksen reitin,
versiokohtaisen kertailmoituksen ja estetyn ilmoituksen käsittelyn.
