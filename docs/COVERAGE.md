# Coverage

This page documents the coverage status of weather sources around the world in 白い熊 天気. Before you submit a request for a new source, please check this document to see if it has been considered for coverage in the past.

In general, a weather source can be considered for inclusion in the official release of 白い熊 天気 if it meets the following requirements:

- **Geolocation availability:** A source should be able to provide data directly from geographical coordinates (latitude and longitude) directly, or return forecast locations from given coordinates. 白い熊 天気 is a mobile app, and is primary used for querying weather forecasts on the go. It is not enough to for a source to provide forecast from location names alone.
- **Data format:** A source should provide its forecast data in JSON or XML format.
- **Forecast availability:** _(for Forecast sources)_ A source should provide hourly forecast. The minimum frequency should be 6-hourly. Sources providing current observation, air pollution, pollen, alerts, or temperature normals WITHOUT hourly forecast can be implemented as a “Secondary Source.”
- **Privacy requirement:** If an API key is needed to access the data, it should not ask for personally identifiable information such as credit card or telephone number.
- **Concentration requirement:**  _(for Air Quality and Pollen sources)_ A source must provide air pollutant concentration data in µg/m³, mg/m³, ppb, or ppm. Pollen concentration must be in pollen count /m³. If the data source only provides a calculated index, it cannot be included in 白い熊 天気, since every country has its own AQI standard which is often different from others.

## Summary

- [Africa](#africa)
- [Asia &amp; the Middle East](#asia--the-middle-east)
- [Europe](#europe)
- [North America](#north-america)
- [South America](#south-america)
- [Oceania](#oceania)
- [Other sources](#other-sources)

## Africa
> [ClimWeb](https://github.com/wmo-raf/climweb) is an open source web CMS created by WMO Regional Office For Africa for use by National Meteorological and Hydrological Services in Africa. As of v0.9.4, ClimWeb offers alerts and temperature normals in JSON format, but weather forecasts are in HTML only. ClimWeb sources are implemented as Secondary Sources for Alerts and Temperature Normals from 白い熊 天気 v5.3.0.

| Country/Territory                                 | Agency                                               | Status                           | Last Checked |
|---------------------------------------------------|------------------------------------------------------|----------------------------------|--------------|
| 🇩🇿 Algeria                                      | [Météo Algérie](https://www.meteo.dz/)               |                                  |              |
| 🇦🇴 Angola                                       | [INAMET](http://inamet.gov.ao/)                      |                                  |              |
| 🇧🇯 Benin                                        | [Météo Benin](http://www.meteobenin.bj/) (ClimWeb)   | ⚠️📊 Alerts, Normals from v5.3.0 | 2024-11-30   |
| 🇧🇼 Botswana                                     | BMS                                                  | ❌ only on Facebook               |              |
| 🇮🇴 British Indian Ocean Territory               |                                                      |                                  |              |
| 🇧🇫 Burkina Faso                                 | [ANAM-BF](https://www.meteoburkina.bf/) (ClimWeb)    | ⚠️ Alerts from v5.3.0            | 2024-11-30   |
| 🇧🇮 Burundi                                      | [IGEBU](https://www.igebu.bi/) (ClimWeb)             | ⚠️ Alerts from v5.3.0            | 2024-11-30   |
| 🇨🇻 Cabo Verde                                   | [INMG](https://www.inmg.gov.cv/)                     |                                  |              |
| 🇨🇲 Cameroon                                     |                                                      |                                  |              |
| 🇨🇫 Central African Republic                     |                                                      |                                  |              |
| 🇹🇩 Chad                                         | [Météo Tchad](https://www.meteotchad.org/) (ClimWeb) | ⚠️📊 Alerts, Normals from v5.3.0 | 2024-11-30   |
| 🇰🇲 Comoros                                      |                                                      |                                  |              |
| 🇨🇬 Congo                                        | [DirMet](https://www.dirmet.cg/)                     |                                  |              |
| 🇨🇮 Côte d’Ivoire                                | [Sodexam](https://sodexam.com/)                      |                                  |              |
| 🇨🇩 Democratic Republic of Congo                 | [Mettelsat](https://www.meteordcongo.cd/) (ClimWeb)  | ⚠️ Alerts from v5.3.0            | 2024-11-30   |
| 🇩🇯 Djibouti                                     | ANM                                                  | ❌ only on Facebook               |              |
| 🇪🇬 Egypt                                        |                                                      |                                  |              |
| 🇬🇶 Equatorial Guinea                            |                                                      |                                  |              |
| 🇪🇷 Eritrea                                      |                                                      |                                  |              |
| 🇸🇿 Eswatini                                     | [Swazimet](http://www.swazimet.gov.sz/)              |                                  |              |
| 🇪🇹 Ethiopia                                     | [EMI](https://www.ethiomet.gov.et/) (ClimWeb)        | ⚠️ Alerts from v5.3.0            | 2024-11-30   |
| 🇹🇫 French Southern Territories                  | –                                                    | Uninhabited                      |              |
| 🇬🇦 Gabon                                        |                                                      |                                  |              |
| 🇬🇲 Gambia                                       | [DWR](https://meteogambia.org/) (ClimWeb)            | ⚠️ Alerts from v5.3.0            | 2024-11-30   |
| 🇬🇭 Ghana                                        | [GMet](https://www.meteo.gov.gh/) (ClimWeb)          | ⚠️ Alerts from v5.3.0            | 2024-11-30   |
| 🇬🇳 Guinea                                       |                                                      |                                  |              |
| 🇬🇼 Guinea-Bissau                                | [INM](https://www.meteoguinebissau.org/) (ClimWeb)   | ⚠️ Alerts from v5.3.0            | 2024-11-30   |
| 🇰🇪 Kenya                                        | [KMD](https://meteo.go.ke/)                          |                                  |              |
| 🇱🇸 Lesotho                                      | [Lesmet](https://www.lesmet.org.ls/)                 |                                  |              |
| 🇱🇷 Liberia                                      |                                                      |                                  |              |
| 🇱🇾 Libya                                        | [LNMC](https://www.lnmc.ly/)                         |                                  |              |
| 🇲🇬 Madagascar                                   | [Météo Madagascar](https://www.meteomadagascar.mg/)  |                                  |              |
| 🇲🇼 Malawi                                       | [DCCMS](https://www.metmalawi.gov.mw/) (ClimWeb)     | ⚠️📊 Alerts, Normals from v5.3.0 | 2024-11-30   |
| 🇲🇱 Mali                                         | [Mali-Météo](https://malimeteo.ml/) (ClimWeb)        | ⚠️ Alerts from v5.3.0            | 2024-11-30   |
| 🇲🇷 Mauritania                                   |                                                      |                                  |              |
| 🇲🇺 Mauritius                                    | [MMS](http://metservice.intnet.mu/)                  |                                  |              |
| 🇾🇹 Mayotte                                      | [Météo-France](https://meteofrance.com/)             | ✅ included                       |              |
| 🇲🇦 Morocco                                      | [Maroc Météo](https://www.marocmeteo.ma/)            |                                  |              |
| 🇲🇿 Mozambique                                   | [INM](https://www.inam.gov.mz/)                      |                                  |              |
| 🇳🇦 Namibia                                      | [NMS](http://www.meteona.com/)                       |                                  |              |
| 🇳🇪 Niger                                        | [DMN](https://www.niger-meteo.ne/) (ClimWeb)         | ⚠️📊 Alerts, Normals from v5.3.0 | 2024-11-30   |
| 🇳🇬 Nigeria                                      | [NiMet](https://www.nimet.gov.ng/)                   |                                  |              |
| 🇷🇪 Réunion                                      | [Météo-France](https://meteofrance.com/)             | ✅ included                       |              |
| 🇷🇼 Rwanda                                       | [Meteo Rwanda](https://www.meteorwanda.gov.rw/)      |                                  |              |
| 🇸🇹 Sao Tome &amp; Principe                      | [INM](https://inm.st/)                               |                                  |              |
| 🇸🇳 Senegal                                      | [ANACIM](https://www.anacim.sn/)                     |                                  |              |
| 🇸🇨 Seychelles                                   | [SMA](https://www.meteo.gov.sc/) (ClimWeb)           | ⚠️📊 Alerts, Normals from v5.3.0 | 2024-11-30   |
| 🇸🇱 Sierra Leone                                 | [SLMet](https://slmet.gov.sl/)                       |                                  |              |
| 🇸🇴 Somalia                                      |                                                      |                                  |              |
| 🇿🇦 South Africa                                 | [SAWS](https://www.weathersa.co.za/)                 |                                  |              |
| 🇸🇸 South Sudan                                  | [SSMS](https://meteosouthsudan.com.ss/) (ClimWeb)    | ⚠️ Alerts from v5.3.0            | 2024-11-30   |
| 🇸🇭 St. Helena, Ascension &amp; Tristan da Cunha |                                                      |                                  |              |
| 🇸🇩 Sudan                                        | [SMA](https://meteosudan.sd/) (ClimWeb)              | ⚠️ Alerts from v5.3.0            | 2024-11-30   |
| 🇹🇿 Tanzania                                     | [TMA](https://www.meteo.go.tz/)                      |                                  |              |
| 🇹🇬 Togo                                         | [Météo Togo](https://www.anamet-togo.com/) (ClimWeb) | ⚠️ Alerts from v5.3.0            | 2024-11-30   |
| 🇹🇳 Tunisia                                      | [INM](https://www.meteo.tn/)                         |                                  |              |
| 🇺🇬 Uganda                                       | [UNMA](https://www.unma.go.ug/)                      |                                  |              |
| 🇪🇭 Western Sahara                               |                                                      |                                  |              |
| 🇿🇲 Zambia                                       | ZMD                                                  | ❌ only on Facebook               | 2024-12-05   |
| 🇿🇼 Zimbabwe                                     | [MSD](https://www.weatherzw.org.zw/) (ClimWeb)       | ⚠️ Alerts from v5.3.0            | 2024-11-30   |

## Asia &amp; the Middle East
| Country/Territory         | Agency                                        | Status                                                                                 | Last Checked |
|---------------------------|-----------------------------------------------|----------------------------------------------------------------------------------------|--------------|
| 🇦🇫 Afghanistan          | [AMD](https://www.amd.gov.af/)                | ❌ no geolocation, no hourly                                                            | 2024-11-30   |
| 🇦🇲 Armenia              | [ARM](https://www.armmonitoring.am/)          |                                                                                        |              |
| 🇦🇿 Azerbaijan           | [Azerbaijan](https://www.eco.gov.az/)         |                                                                                        |              |
| 🇧🇭 Bahrain              | [Bahrain](https://www.bahrainweather.gov.bh/) |                                                                                        |              |
| 🇧🇩 Bangladesh           | [BMD](https://live6.bmd.gov.bd/)              | ✅ included from v5.3.0                                                                 |              |
| 🇧🇹 Bhutan               | [NCHM](https://www.nchm.gov.bt/)              | ❌ no geolocation, no hourly                                                            | 2024-11-30   |
| 🇧🇳 Brunei Darussalam    | [BDMD](https://www.met.gov.bn/)               | 🚧 _(in progress)_                                                                     | 2024-11-30   |
| 🇰🇭 Cambodia             | [Cambodia](http://www.cambodiameteo.com/)     | ❌ no geolocation, no JSON                                                              | 2024-11-30   |
| 🇨🇳 China                | Mixed China sources                           | ✅ included                                                                             |              |
| 🇭🇰 Hong Kong            | [HKO](https://www.hko.gov.hk/)                | ✅ included from v5.3.0                                                                 |              |
| 🇮🇳 India                | [IMD](https://mausam.imd.gov.in/)             | ✅ included from v5.3.0                                                                 |              |
| 🇮🇩 Indonesia            | [BMKG](https://www.bmkg.go.id/)               | ✅ included from v5.3.0                                                                 |              |
| 🇮🇷 Iran                 | [IRIMO](https://www.irimo.ir/)                |                                                                                        |              |
| 🇮🇶 Iraq                 | [Iraq](http://www.meteoseism.gov.iq/)         |                                                                                        |              |
| 🇮🇱 Israel               | [IMS](https://ims.gov.il/)                    | ✅ included from v5.1.0                                                                 |              |
| 🇯🇵 Japan                | [JMA](https://www.jma.go.jp/)                 | ✅ included from v5.3.0                                                                 |              |
| 🇯🇴 Jordan               | [Jordan](http://jometeo.gov.jo/)              |                                                                                        |              |
| 🇰🇿 Kazakhstan           | [Kazhydromet](https://www.kazhydromet.kz/)    |                                                                                        |              |
| 🇰🇼 Kuwait               | [Kuwait](https://www.met.gov.kw/)             |                                                                                        |              |
| 🇰🇬 Kyrgyzstan           | [KyrgyzHydromet](https://meteo.kg/)           |                                                                                        |              |
| 🇱🇦 Laos                 | [DMH](https://dmhlao.la/)                     | ❌ no geolocation, no JSON                                                              | 2024-11-30   |
| 🇱🇧 Lebanon              | [Météo Liban](https://meteo.gov.lb/)          |                                                                                        |              |
| 🇲🇴 Macao                | [SMG](https://www.smg.gov.mo/)                | ✅ included from v5.3.0                                                                 |              |
| 🇲🇾 Malaysia             | [METMalaysia](https://www.met.gov.my/)        | ❌ no geolocation, no hourly                                                            | 2024-11-30   |
| 🇲🇲 Myanmar              | [Moezala](https://www.moezala.gov.mm/)        | ❌ no geolocation, no hourly                                                            | 2024-11-30   |
| 🇲🇻 Maldives             | [MMS](http://www.meteorology.gov.mv/)         | ❌ no JSON                                                                              | 2024-11-30   |
| 🇲🇳 Mongolia             | [NAMEM](https://www.weather.gov.mn/)          | ✅ included from v5.3.0                                                                 |              |
| 🇳🇵 Nepal                | [DHM](http://www.dhm.gov.np/)                 | ❌ no hourly                                                                            | 2024-11-30   |
| 🇰🇵 North Korea          | SHMA                                          | ❌ no website                                                                           | 2024-11-30   |
| 🇴🇲 Oman                 | [Oman](https://met.gov.om/)                   |                                                                                        |              |
| 🇵🇰 Pakistan             | [PMD](http://www.pmd.gov.pk/)                 | ❌ no geolocation, no JSON                                                              | 2024-11-30   |
| 🇵🇸 Palestine            | [PMD](https://www.pmd.ps/)                    | ❌ no hourly, no JSON                                                                   | 2024-12-05   |
| 🇵🇭 Philippines          | [PAGASA](https://www.pagasa.dost.gov.ph/)     | ✅ included from v5.3.0                                                                 |              |
| 🇶🇦 Qatar                | [QWeather](http://qweather.gov.qa/)           |                                                                                        |              |
| 🇸🇦 Saudi Arabia         | [NCM](https://ncm.gov.sa/)                    |                                                                                        |              |
| 🇸🇬 Singapore            | [MSS](https://www.weather.gov.sg/)            | ❌ no hourly                                                                            | 2024-11-30   |
| 🇱🇰 Sri Lanka            | [Sri Lanka](https://meteo.gov.lk/)            | ❌ no geolocation, no hourly                                                            | 2024-11-30   |
| 🇰🇷 South Korea          | [KMA](https://data.kma.go.kr/)                | ❌ [requires phone number](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/593) | 2023-12-09   |
| 🇸🇾 Syria                | SGDM                                          | ❌ only on Facebook                                                                     |              |
| 🇹🇼 Taiwan               | [CWA](https://www.cwa.gov.tw/)                | ✅ included from v5.2.5                                                                 |              |
| 🇹🇯 Tajikistan           | [Tajikistan](https://www.meteo.tj/)           |                                                                                        |              |
| 🇹🇭 Thailand             | [TMD](https://www.tmd.go.th/)                 | ❌ requires phone number                                                                | 2024-11-30   |
| 🇹🇱 Timor-Leste          | [DNMG](https://www.dnmg.gov.tl)               | ❌ website doesn't work                                                                 | 2024-11-30   |
| 🇹🇷 Türkiye              | [MGM](https://www.mgm.gov.tr/)                | ✅ included from v5.3.0                                                                 |              |
| 🇹🇲 Turkmenistan         | [TGBG](https://meteo.gov.tm/)                 |                                                                                        |              |
| 🇦🇪 United Arab Emirates | [NCM](https://www.ncm.gov.ae/)                |                                                                                        |              |
| 🇺🇿 Uzbekistan           | [O‘zgidromet](https://hydromet.uz/)           |                                                                                        |              |
| 🇻🇳 Vietnam              | [NCHMF](https://nchmf.gov.vn/)                | ❌ no geolocation, no hourly                                                            | 2024-11-30   |
| 🇾🇪 Yemen                | [CAMA](https://cama.gov.ye/)                  |                                                                                        |              |

## Europe
| Country/Territory             | Agency                                                | Status                                                                                | Last Checked |
|-------------------------------|-------------------------------------------------------|---------------------------------------------------------------------------------------|--------------|
| 🇦🇽 Åland Is.                | [FMI](https://en.ilmatieteenlaitos.fi/)               | ✅ included from v6.2.0                                                                | 2025-08-26   |
| 🇦🇱 Albania                  |                                                       |                                                                                       |              |
| 🇦🇩 Andorra                  | [Météo-France](https://meteofrance.com/)              | ✅ included                                                                            |              |
| 🇦🇹 Austria                  | [GeoSphere Austria](https://www.geosphere.at/)        | ✅ included from v5.2.0                                                                |              |
| 🇧🇾 Belarus                  | [BelHydromet](https://belgidromet.by/)                |                                                                                       |              |
| 🇧🇪 Belgium                  | [IRM](https://www.meteo.be/)                          | ❌ API not available to the public                                                     | 2024-11-30   |
| 🇧🇦 Bosnia &amp; Herzegovina | [FHMZBIH](https://www.fhmzbih.gov.ba/latinica/)       |                                                                                       |              |
|                               | [RHMZRS](https://rhmzrs.com/)                         |                                                                                       |              |
| 🇧🇬 Bulgaria                 | [NIMH](https://www.meteo.bg/)                         |                                                                                       |              |
| 🇭🇷 Croatia                  | [DHMZ](https://meteo.hr/)                             |                                                                                       |              |
| 🇨🇾 Cyprus                   | [Cyprus](https://www.moa.gov.cy/)                     |                                                                                       |              |
| 🇨🇿 Czechia                  | [CHMI](https://www.chmi.cz/)                          | ▶️😶‍🌫️⚠️📊 Current, Air quality, Alerts, Normals from 6.2.1+011                        | 2026-08-10   |
| 🇩🇰 Denmark                  | [DMI](https://www.dmi.dk)                             | ✅ included from v5.0.0                                                                |              |
| 🇪🇪 Estonia                  | [Ilmateenistus](https://www.ilmateenistus.ee/)        | ✅ included from v5.4.0                                                                | 2024-12-24   |
| 🇫🇴 Faroe Is.                | [DMI](https://www.dmi.dk)                             | ✅ included from v5.0.0                                                                |              |
| 🇫🇮 Finland                  | [FMI](https://en.ilmatieteenlaitos.fi/)               | ✅ included from v6.2.0                                                                | 2025-08-26   |
| 🇫🇷 France                   | [Météo-France](https://meteofrance.com/)              | ✅ included                                                                            |              |
| 🇬🇪 Georgia                  | [Georgia](https://meteo.gov.ge/)                      |                                                                                       |              |
| 🇩🇪 Germany                  | [Bright Sky](https://brightsky.dev/)                  | ✅ included from v5.0.0                                                                |              |
| 🇬🇮 Gibraltar                | [Met Office](https://www.metoffice.gov.uk/)           | ✅ included from v5.3.0                                                                |              |
| 🇬🇷 Greece                   | [EMY](http://www.emy.gr/)                             |                                                                                       |              |
| 🇬🇬 Guernsey                 | [Met Office](https://www.metoffice.gov.uk/)           | ✅ included from v5.3.0                                                                |              |
| 🇭🇺 Hungary                  | [HungaroMet](https://www.met.hu/)                     | ❌ [no hourly](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/821)            | 2024-03-19   |
| 🇮🇸 Iceland                  | [IMO](https://en.vedur.is/)                           | ✅ included from v6.0.5                                                                | 2025-08-23   |
| 🇮🇪 Ireland                  | [MET Éireann](https://www.met.ie/)                    | ✅ included from v5.0.0                                                                |              |
| 🇮🇲 Isle of Man              | [Met Office](https://www.metoffice.gov.uk/)           | ✅ included from v5.3.0                                                                |              |
| 🇮🇹 Italy                    | [Meteo AM](https://www.meteoam.it/)                   | ✅ Included from v5.2.6                                                                |              |
| 🇯🇪 Jersey                   | [Met Office](https://www.metoffice.gov.uk/)           | ✅ included from v5.3.0                                                                |              |
| 🇽🇰 Kosovo                   | [IHMK](https://ihmk-rks.net/)                         |                                                                                       |              |
| 🇱🇻 Latvia                   | [Latvia](https://videscentrs.lvgmc.lv/)               | ✅ included from v5.3.0                                                                | 2024-11-30   |
| 🇱🇮 Liechtenstein            | [MeteoSwiss](https://www.meteoswiss.admin.ch/)        | ❌ no geolocation                                                                      | 2024-11-30   |
| 🇱🇹 Lithuania                | [Lithuania](http://www.meteo.lt/)                     | ✅ included from v5.3.0                                                                | 2024-11-30   |
| 🇱🇺 Luxembourg               | [MeteoLux](https://www.meteolux.lu/)                  | ✅ included from v5.3.0                                                                |              |
| 🇲🇹 Malta                    | [Malta Airport](https://www.maltairport.com/weather/) |                                                                                       |              |
| 🇲🇩 Moldova                  | [SHS](https://www.meteo.md/)                          |                                                                                       |              |
| 🇲🇨 Monaco                   | [Météo-France](https://meteofrance.com/)              | ✅ included                                                                            |              |
| 🇲🇪 Montenegro               | [ZHMS](https://www.meteo.co.me/)                      |                                                                                       |              |
| 🇳🇱 Netherlands              | [KNMI](https://www.knmi.nl/)                          | ❌ [no geolocation](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/1025)      | 2024-05-03   |
| 🇲🇰 North Macedonia          | [UHMR](https://uhmr.gov.mk/)                          |                                                                                       |              |
| 🇳🇴 Norway                   | [MET Norway](https://www.met.no/)                     | ✅ included from v4.0.0                                                                |              |
| 🇵🇱 Poland                   | [IMGW](https://www.imgw.pl/)                          | [open to contributions](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/998)  | 2024-12-01   |
| 🇵🇹 Portugal                 | [IPMA](https://www.ipma.pt/)                          | ✅ Included from v5.3.0                                                                |              |
| 🇷🇴 Romania                  | [Meteo România](https://www.meteoromania.ro/)         |                                                                                       |              |
| 🇷🇺 Russia                   |                                                       |                                                                                       |              |
| 🇸🇲 San Marino               | [Meteo AM](https://www.meteoam.it/)                   | ✅ Included from v5.2.6                                                                |              |
| 🇷🇸 Serbia                   | [Hidmet](https://www.hidmet.gov.rs/)                  |                                                                                       |              |
| 🇸🇰 Slovakia                 | [SHMÚ](https://www.shmu.sk/)                          |                                                                                       |              |
| 🇸🇮 Slovenia                 | [ARSO](https://www.arso.gov.si/)                      |                                                                                       |              |
| 🇪🇸 Spain                    | [AEMET](https://www.aemet.es/)                        | ✅ included from v5.3.0                                                                |              |
|                               | [Meteogalicia](https://www.meteogalicia.gal/)         | [open to contributions](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/1066) | 2024-05-23   |
| 🇸🇯 Svalbard &amp; Jan Mayen | [MET Norway](https://www.met.no/)                     | ✅ included from v4.0.0                                                                |              |
| 🇸🇪 Sweden                   | [SMHI](https://www.smhi.se/)                          | ✅ included from v5.0.0                                                                |              |
| 🇨🇭 Switzerland              | [MeteoSwiss](https://www.meteoswiss.admin.ch/)        | ❌ no geolocation                                                                      | 2024-11-30   |
| 🇺🇦 Ukraine                  | [UHC](https://www.meteo.gov.ua/)                      |                                                                                       |              |
| 🇬🇧 United Kingdom           | [Met Office](https://www.metoffice.gov.uk/)           | ✅ included from v5.3.0                                                                |              |
| 🇻🇦 Vatican City             | [Meteo AM](https://www.meteoam.it/)                   | ✅ included from v5.2.6                                                                |              |

## North America
| Country/Territory                       | Agency                                   | Status                 | Last Checked |
|-----------------------------------------|------------------------------------------|------------------------|--------------|
| 🇦🇮 Anguilla                           | [ABMS](http://www.antiguamet.com/)       |                        |              |
| 🇦🇬 Antigua &amp; Barbuda              | [ABMS](http://www.antiguamet.com/)       |                        |              |
| 🇦🇼 Aruba                              | [DMA](http://www.meteo.aw/)              |                        |              |
| 🇧🇸 Bahamas                            | [BDM](https://met.gov.bs/)               |                        |              |
| 🇧🇧 Barbados                           | [BMS](https://www.barbadosweather.org/)  |                        |              |
| 🇧🇿 Belize                             | [NMS](https://www.nms.gov.bz/)           |                        |              |
| 🇧🇲 Bermuda                            | [Bermuda Weather](https://weather.bm/)   | ❌ no hourly, no JSON   | 2024-11-29   |
| 🇧🇶 Bonaire, Sint Eustatius &amp; Saba | [KNMIDC](https://www.knmidc.org/)        |                        |              |
| 🇻🇬 British Virgin Is.                 | [ABMS](http://www.antiguamet.com/)       |                        |              |
| 🇨🇦 Canada                             | [ECCC](https://weather.gc.ca/)           | ✅ included from v5.0.0 |              |
| 🇰🇾 Cayman Is.                         | [CINWS](https://www.weather.gov.ky/)     |                        |              |
| 🇨🇷 Costa Rica                         | [IMN](https://www.imn.ac.cr/)            |                        |              |
| 🇨🇺 Cuba                               | [Insmet](http://www.insmet.cu/)          |                        |              |
| 🇨🇼 Curaçao                            | [Curaçao](https://www.meteo.cw/)         |                        |              |
| 🇩🇲 Dominica                           | [DMS](https://www.weather.gov.dm/)       |                        |              |
| 🇩🇴 Dominican Republic                 | [Indomet](https://onamet.gob.do/)        |                        |              |
| 🇸🇻 El Salvador                        | [MARN](https://www.snet.gob.sv/)         |                        |              |
| 🇬🇱 Greenland                          | [DMI](https://www.dmi.dk/)               | ✅ included from v5.0.0 |              |
| 🇬🇩 Grenada                            | [GAA](https://www.weather.gd/)           |                        |              |
| 🇬🇵 Guadeloupe                         | [Météo-France](https://meteofrance.com/) | ✅ included             |              |
| 🇬🇹 Guatemala                          | [Insivumeh](https://insivumeh.gob.gt/)   |                        |              |
| 🇭🇹 Haiti                              | [UHM](https://www.meteo-haiti.gouv.ht/)  |                        |              |
| 🇭🇳 Honduras                           |                                          |                        |              |
| 🇯🇲 Jamaica                            | [Jamaica](https://metservice.gov.jm/)    |                        |              |
| 🇲🇶 Martinique                         | [Météo-France](https://meteofrance.com/) | ✅ included             |              |
| 🇲🇽 Mexico                             | [SMN](https://smn.conagua.gob.mx/)       |                        |              |
| 🇲🇸 Montserrat                         | [ABMS](http://www.antiguamet.com/)       |                        |              |
| 🇳🇮 Nicaragua                          | [Ineter](https://www.ineter.gob.ni/)     |                        |              |
| 🇵🇦 Panama                             | [IMHPA](https://www.imhpa.gob.pa/)       |                        |              |
| 🇵🇷 Puerto Rico                        | [NWS](https://www.weather.gov/)          | ✅ included from v5.0.0 |              |
| 🇧🇱 St. Barthélemy                     | [Météo-France](https://meteofrance.com/) | ✅ included             |              |
| 🇰🇳 St. Kitts &amp; Nevis              | [ABMS](http://www.antiguamet.com/)       |                        |              |
| 🇱🇨 St. Lucia                          | [SLUMET](https://www.slumet.gov.lc/)     |                        |              |
| 🇲🇫 St. Martin (French Part)           | [Météo-France](https://meteofrance.com/) | ✅ included             |              |
| 🇵🇲 St. Pierre &amp; Miquelon          | [Météo-France](https://meteofrance.com/) | ✅ included             |              |
| 🇻🇨 St. Vincent &amp; the Grenadines   | [SVGMET](https://www.meteo.gov.vc/)      |                        |              |
| 🇸🇽 Sint Maarten (Dutch part)          | [MDS](https://www.meteosxm.com/)         |                        |              |
| 🇹🇹 Trinidad &amp; Tobago              | [TTMS](https://www.metoffice.gov.tt/)    |                        |              |
| 🇹🇨 Turks &amp; Caicos Is.             | [DDME](https://gov.tc/ddme/)             |                        |              |
| 🇺🇸 United States                      | [NWS](https://www.weather.gov/)          | ✅ included from v5.0.0 |              |
| 🇻🇮 U.S. Virgin Is.                    | [NWS](https://www.weather.gov/)          | ✅ included from v5.0.0 |              |

## South America
| Country/Territory                               | Agency                                      | Status                 | Last Checked |
|-------------------------------------------------|---------------------------------------------|------------------------|--------------|
| 🇦🇷 Argentina                                  | [SMN](https://www.smn.gob.ar/)              |                        |              |
| 🇧🇴 Bolivia                                    | [Senamhi](https://senamhi.gob.bo/)          |                        |              |
| 🇧🇻 Bouvet Island                              | –                                           | Uninhabited            |              |
| 🇧🇷 Brazil                                     | [INMET](https://portal.inmet.gov.br/)       |                        |              |
| 🇨🇱 Chile                                      | [Chile](https://www.meteochile.gob.cl/)     |                        |              |
| 🇨🇴 Colombia                                   | [IDEAM](https://www.ideam.gov.co/)          |                        |              |
| 🇪🇨 Ecuador                                    | [INAMHI](https://www.inamhi.gob.ec/)        |                        |              |
| 🇫🇰 Falkland Islands (Malvinas)                | [Met Office](https://www.metoffice.gov.uk/) | ✅ included from v5.3.0 |              |
| 🇬🇫 French Guiana                              | [Météo-France](https://meteofrance.com/)    | ✅ included             |              |
| 🇬🇾 Guyana                                     | [Hydromet](https://hydromet.gov.gy/)        |                        |              |
| 🇵🇾 Paraguay                                   | [DMH](https://www.meteorologia.gov.py/)     |                        |              |
| 🇵🇪 Peru                                       | [Senamhi](https://www.gob.pe/senamhi)       |                        |              |
| 🇬🇸 South Georgia &amp; the South Sandwich Is. | –                                           | Uninhabited            |              |
| 🇸🇷 Suriname                                   | [Suriname](https://hydromet.sr/)            |                        |              |
| 🇺🇾 Uruguay                                    | [Inumet](https://www.inumet.gub.uy/)        |                        |              |
| 🇻🇪 Venezuela                                  | [Inameh](http://inameh.gob.ve/)             |                        |              |

## Oceania
| Country/Territory                    | Agency                                     | Status                                                                           | Last Checked |
|--------------------------------------|--------------------------------------------|----------------------------------------------------------------------------------|--------------|
| 🇦🇸 American Samoa                  | [NWS](https://www.weather.gov/)            | ❌ NWS API does not support American Samoa                                        | 2024-11-21   |
| 🇦🇺 Australia                       | [BOM](http://www.bom.gov.au/)              | ❌ [restricted API](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/1299) | 2024-09-14   |
| 🇨🇽 Christmas Island                | [BOM](http://www.bom.gov.au/)              | ❌ [restricted API](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/1299) | 2024-09-14   |
| 🇨🇨 Cocos (Keeling) Is.             | [BOM](http://www.bom.gov.au/)              | ❌ [restricted API](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/1299) | 2024-09-14   |
| 🇨🇰 Cook Is.                        | [CIMS](https://met.gov.ck/)                |                                                                                  |              |
| 🇫🇯 Fiji                            | [FMS](https://www.met.gov.fj/)             | ❌ no geolocation, no JSON                                                        | 2024-11-30   |
| 🇵🇫 French Polynesia                | [Météo-France](https://meteofrance.com/)   | ✅ included                                                                       |              |
| 🇬🇺 Guam                            | [NWS](https://www.weather.gov/)            | ✅ included from v5.0.0                                                           |              |
| 🇭🇲 Heard Island &amp; McDonald Is. | –                                          | Uninhabited                                                                      |              |
| 🇰🇮 Kiribati                        | [KMS](https://www.met.gov.ki/)             | ❌ no geolocation, no JSON                                                        | 2024-11-30   |
| 🇲🇭 Marshall Is.                    | [NWS](https://www.weather.gov/)            | ❌ NWS API does not support Marshall Is.                                          | 2024-11-21   |
| 🇫🇲 Micronesia                      | [NWS](https://www.weather.gov/)            | ❌ NWS API does not support Micronesia                                            | 2024-11-21   |
| 🇳🇷 Nauru                           | [FMS](https://www.met.gov.fj/)             | ❌ no geolocation, no JSON                                                        | 2024-11-30   |
| 🇳🇨 New Caledonia                   | [Météo-France](https://meteofrance.com/)   | ✅ included                                                                       |              |
| 🇳🇿 New Zealand                     | [Met Service](https://www.metservice.com/) | ❌ free tier requires credit card info                                            | 2024-12-01   |
| 🇳🇺 Niue                            | [FMS](https://www.met.gov.fj/)             | ❌ no geolocation, no JSON                                                        | 2024-11-30   |
| 🇳🇫 Norfolk Island                  | [Met Service](https://www.metservice.com/) | ❌ free tier requires credit card info                                            | 2024-12-01   |
| 🇲🇵 Northern Mariana Is.            | [NWS](https://www.weather.gov/)            | ✅ included from v5.0.0                                                           |              |
| 🇵🇼 Palau                           | [NWS](https://www.weather.gov/)            | ❌ NWS API does not support Palau                                                 | 2024-11-21   |
| 🇵🇬 Papua New Guinea                | [PNGNWS](https://www.pngmet.gov.pg/)       |                                                                                  |              |
| 🇵🇳 Pitcairn                        |                                            |                                                                                  |              |
| 🇼🇸 Samoa                           | [SMD](http://www.samet.gov.ws/)            |                                                                                  |              |
| 🇸🇧 Solomon Is.                     | [SIMS](https://met.gov.sb/)                |                                                                                  |              |
| 🇹🇰 Tokelau                         | [FMS](https://www.met.gov.fj/)             | ❌ no geolocation, no JSON                                                        | 2024-11-30   |
| 🇹🇴 Tonga                           | [TMS](http://met.gov.to/)                  |                                                                                  |              |
| 🇹🇻 Tuvalu                          | [TMS](https://tuvmet.tv/)                  |                                                                                  |              |
| 🇺🇲 U.S. Minor Outlying Is.         | –                                          | Uninhabited                                                                      |              |
| 🇻🇺 Vanuatu                         | [VMGD](https://www.vmgd.gov.vu/)           |                                                                                  |              |
| 🇼🇫 Wallis &amp; Futuna             | [Météo-France](https://meteofrance.com/)   | ✅ included                                                                       |              |

## Other sources
| Source             | Status                                                                                     | Last Checked |
|--------------------|--------------------------------------------------------------------------------------------|--------------|
| Atmo France        | ✅ included from v5.4.6                                                                     |              |
| EKUK               | ✅ included from v5.4.0                                                                     | 2024-12-24   |
| EPD HK             | ✅ included from v5.4.0                                                                     | 2024-12-19   |
| FOSS Public Alert  | ✅ included from v6.0.5-alpha                                                               |              |
| GeoNames           | ✅ included from v4.5.0                                                                     |              |
| HERE               | ✅ included from v4.5.0                                                                     |              |
| Natural Earth      | ✅ included from v5.0.3                                                                     |              |
| NCDR               | ✅ included from v6.0.5-alpha                                                               |              |
| NCEI               | ✅ included from v6.0.5-alpha                                                               |              |
| NLSC               | ✅ included from v6.0.5-alpha                                                               |              |
| Pirate Weather     | ✅ included from v5.0.0                                                                     |              |
| Recosanté          | ✅ included from v5.1.1                                                                     |              |
| WMO Severe Weather | ✅ included from v5.1.4                                                                     |              |
| Apple WeatherKit   | ❌ no free tier                                                                             |              |
| AROME PIAF         | [data in `.grid` format](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/847)      | 2024-03-25   |
| Azure Maps         | ❌ [requires credit card](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/265)      | 2023-08-04   |
| EPA AirNow         | ❌ [no concentration](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/929)          | 2024-04-12   |
| Graphcast          | ❌ [no API server](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/576)             | 2023-11-27   |
| HERE               | ❌ [removed from v6.0.6](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/1849)      | 2025-08-24   |
| Microsoft Azure    | ❌ free tier requires credit card info                                                      |              |
| Pollenrapporten    | ❌ [no geolocation](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/758)            | 2024-03-08   |
| QWeather           | ❌ [privacy concern](https://github.com/ShiroiKuma0/shiroikuma-tenki/pull/574)             | 2024-04-11   |
| Seniverse          | ❌ [no geolocation, no hourly](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/508) | 2023-10-05   |
| Tomorrow.io        | [open to contribution](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/469)        | 2023-09-28   |
| WeatherAPI         | [open to contribution](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/453)        | 2023-09-08   |
| WeatherBit         | ❌ free tier does not have hourly forecasts                                                 |              |
| WetterOnline       | ❌ [no geolocation](https://github.com/ShiroiKuma0/shiroikuma-tenki/issues/1068)           | 2024-05-23   |
