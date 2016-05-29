Ensichat
========

Instant messenger for Android that is fully decentralized. Messages are encrypted and sent directly
between devices via Bluetooth or Internet, without any central server. A simple flood-based routing
is used for message propagation.

<img src="graphics/screenshot_phone_1.png" alt="screenshot 1" width="200" />
<img src="graphics/screenshot_phone_2.png" alt="screenshot 2" width="200" />
<img src="graphics/screenshot_phone_3.png" alt="screenshot 3" width="200" />

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=com.nutomic.ensichat) [![Get it on F-Droid](https://f-droid.org/wiki/images/0/06/F-Droid-button_get-it-on.png)](https://f-droid.org/repository/browse/?fdid=com.nutomic.ensichat)

Building
--------

To setup a development environment, just install [Android Studio](https://developer.android.com/sdk/)
and import the project.

Alternatively, you can use the command line. To create a debug apk, run `./gradlew assembleDevDebug`.
This requires at least Android Lollipop on your development device. If you don't have 5.0 or higher,
you have to use `./gradlew assembleRelDebug`. However, this results in considerably slower
incremental builds. To create a release apk, run `./gradlew assembleRelRelease`.

Testing
-------

You can run the unit tests with `./gradlew test`. After connecting an Android device, you can run
the Android tests with `./gradlew connectedDevDebugAndroidTest` (or
`./gradlew connectedRelDebugAndroidTest` if your Android version is  lower than 5.0).

To run integration tests for the core module, use `./gradlew integration:run`. If this fails (or
is very slow), try changing the value of Crypto#PublicKeySize to 512 (in the core module).

License
-------

All code is licensed under the [GPL](LICENSE), v3 or later.

The launcher icon is based on the [Bubbles Icon](https://www.iconfinder.com/icons/285667/bubbles_icon) created by [Paomedia](https://www.iconfinder.com/paomedia) which is available under [CC BY 3.0](http://creativecommons.org/licenses/by/3.0/).
