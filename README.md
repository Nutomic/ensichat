ensichat
========

[![BitCoin donate button](https://img.shields.io/badge/bitcoin-donate-yellow.svg)](https://blockchain.info/address/1DmU6QVGSKXGXJU1bqmmStPDNsNnYoMJB4)

Instant messanger for Android that is fully decentralized. Messages are sent directly between
devices via Bluetooth, without any central server. A simple flood-based routing is used for
message propagation.

<img src="graphics/screenshot_phone_1.png" alt="screenshot 1" width="200" />
<img src="graphics/screenshot_phone_2.png" alt="screenshot 2" width="200" />
<img src="graphics/screenshot_phone_3.png" alt="screenshot 3" width="200" />

Building
--------

To create a debug apk, run `./gradlew assembleDebug`. Alternatively, you can use
`.gradlew thinDebug` for a faster compile, but this requires Scala libraries installed
with [Android-Scala-Installer](https://github.com/Arneball/Android-Scala-Installer) on your device.

To create a release apk, run `./gradlew assembleRelease`.

License
-------

All code is licensed under the [GPL](LICENSE), v3 or later.
