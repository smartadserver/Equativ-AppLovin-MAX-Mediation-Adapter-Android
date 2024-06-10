Equativ - AppLovin MAX SDK Adapter for Display SDK v8.x
==============================================

Introduction
------------
The _Equativ Display SDK v8.x_ can be used through _AppLovin MAX_ using the adapter provided in this repository for banner and interstitial. This adapter is compatible with:
* _Equativ Display SDK_ v8.1+
* _AppLovin MAX SDK_ v11.9.0

Setup
-----

1) Install the _AppLovin MAX SDK_ according to the official documentation https://dash.applovin.com/documentation/mediation/android/getting-started/integration.

2) Install the _Equativ Display SDK_ by adding the `equativ-display-sdk` dependency to your _gradle_ file (more info in [the documentation](https://documentation.smartadserver.com/displaySDK/android/gettingstarted.html)).

3) Checkout this repository and copy the `EquativMediationAdapter.java` class into your Android project. Keep the whole folders structure to be sure to validate the package name of the class.

4) In your _AppLovin MAX_ interface, create a custom network to be able to target this mediation adapter. The _AppLovin MAX_ documentation about custom adapters can be found [here](https://dash.applovin.com/documentation/mediation/ui-max/networks/connect-networks), and custom adapter can be created [here](https://dash.applovin.com/o/mediation/networks/580541/customNetwork/create).

In this custom network settings interface, you need to setup 4 parameters:
* __Network Type__: `SDK`.
* __Custom Network Name__: `Equativ` (This parameter is not critical, you can name it differently, but be sure to name it well to have an accurate reporting by _AppLovin MAX_).
* __iOS Adapter Class Name__: If you want to do mediation through _AppLovin MAX_ for iOS also, please have a look to [this repository](https://github.com/smartadserver/Equativ-AppLovin-MAX-Mediation-Adapter-iOS).
* __Android / Fire OS Adapter Class Name__: `com.applovin.mediation.adapters.EquativMediationAdapter`

5) To deliver _Equativ_ ads on your _AppLovin MAX_ ad units, you will have to activate the network named `Equativ` in the Custom Network section.

In this custom network activation interface, there are several parameters:
* __Status__: to activate.
* __App ID (optional)__: Not applicable. Will be ignored.
* __Placement ID__: set your Equativ IDs concatenated as a string using slash separator `[siteID]/[pageID]/[formatID]/[targeting string]`. Note that the targeting string is optional, therefore both of the following example are valid: `123/456/789/targeting=true`, `123/456/879`.
* __Custom Parameters__: Not applicable. Will be ignored.
* __CMP Price__: To be set according to your needs and to validate with your account manager.
* __Country targeting__: To be set according to your needs.

More infos
----------
You can find more informations about the _Equativ Display SDK_ and the _AppLovin MAX SDK_ in the official documentation:

https://documentation.smartadserver.com/displaySDK

https://dash.applovin.com/documentation/mediation/max/get-started-with-max
