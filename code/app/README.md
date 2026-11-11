# App

The remote control for the Open Roadster. There are two native versions with the same controls and behavior:

| Platform | Folder | Built with | Needs |
| --- | --- | --- | --- |
| iOS | [`ios/RoadsterRemote/`](ios/RoadsterRemote/) | SwiftUI + CoreBluetooth | iOS 18 or newer |
| Android | [`android/`](android/) | Kotlin + Jetpack Compose | Android 8.0 (API 26) or newer, with Bluetooth Low Energy |

## Driving

Hold the phone in landscape.

- **Steering:** tilt the phone like a steering wheel.
- **Throttle:** slide the stick on the right.
- **Gear selector (P / R / D):** sets Park, Reverse or Drive.
- **Headlights:** toggle them on and off.
- **Fuel gauge:** shows the car's battery level and whether it's charging.

On launch the app scans for nearby cars and lists every Open Roadster in range with its signal strength. Tap one to connect. Tap the connection indicator any time to switch cars or disconnect.

If the connection drops (the car goes out of range or is switched off), the app shifts to Park and goes back to the car list. The car shows up under Nearby again once it's back in range.

The screen is locked to one landscape orientation (the top of the phone on the left), so tilting always steers the same way.

## Building for iOS

1. Open `ios/RoadsterRemote/RoadsterRemote.xcodeproj` in Xcode
2. In the project settings, under **Signing & Capabilities**, select your own **Team** (or create a free Apple Developer account)
3. Change the **Bundle Identifier** to one of your own (e.g., `com.yourname.RoadsterRemote`). Bundle IDs are unique per Apple account, so the default `org.openroadster.RoadsterRemote` may already be taken
4. Build and run on your device. It needs iOS 18 or newer

The app needs Bluetooth to find the car and motion sensing for tilt steering. iOS asks for both on first launch.

## Building for Android

You need:

- **JDK 17 or 21.** Newer JDKs aren't supported by the Android build tools yet. Check with `java -version`, and set `JAVA_HOME` if your default is different
- **The Android SDK**, with the Android 15 (API 35) platform. The easiest way to get it is [Android Studio](https://developer.android.com/studio). For the command line only, install the [command-line tools](https://developer.android.com/studio#command-line-tools-only) and run `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`

Gradle doesn't need to be installed. The `gradlew` wrapper downloads the right version on first run.

### With Android Studio

Open the `android/` folder, wait for the Gradle sync, plug in your phone and press **Run**.

### From the command line

Tell Gradle where the SDK is, either with `export ANDROID_HOME=$HOME/Android/Sdk` or a `local.properties` file in `android/` containing `sdk.dir=/path/to/Android/Sdk`. Then:

```sh
cd code/app/android
./gradlew installDebug
```

This builds the app and installs it on the connected phone. To build the APK without installing, run `./gradlew assembleDebug`; it lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Setting up the phone

1. Turn on **Developer options**: in **Settings → About phone**, tap **Build number** seven times
2. In **Settings → System → Developer options**, turn on **USB debugging**
3. Plug the phone in and tap **Allow** on the "Allow USB debugging?" prompt
4. Check that `adb devices` lists the phone as `device`. If it says `unauthorized`, unlock the phone and look for the prompt

On Linux, you may also need udev rules so your user can access the phone over USB. Many distributions package them as `android-udev` or `android-sdk-platform-tools-common`.

### Permissions

On first launch the app asks for **Nearby devices** permission, which it needs to find and connect to the car. It doesn't use your location: on Android 12 and newer the permission is declared as not used for location.

Android 11 and older can only scan for Bluetooth devices with the **Location** permission and with Location turned on. The app asks for the permission, and the car list tells you if Location is off.

If you deny the permission twice, Android stops asking. The car list then has a button that opens the app's settings so you can allow it there.

### Behavior differences from iOS

- When you leave the app (home button, app switcher, screen off), it shifts to Park and lets go of the throttle. iOS suspends the app instead, which stops the packets and lets the car's watchdog stop the motors
- Each car is identified by the last four characters of its Bluetooth address, which is the same on every phone. iOS shows part of an identifier that it assigns separately on each iPhone, so the two apps show different IDs for the same car
- The car list uses your system light or dark theme, with your wallpaper colors on Android 12 and newer

## Protocol

The app sends a 5-byte control packet (steering, throttle, headlight) to the car's Nordic UART service. It reads the standard Battery Level characteristic and a Power Source characteristic for the charging indicator. The full protocol is in the [firmware README](../firmware/README.md#bluetooth-interface), which is useful if you want to write your own controller.

## Project layout

Both apps follow the same structure, so a change to one is easy to carry over to the other:

| Role | iOS | Android |
| --- | --- | --- |
| Bluetooth: scanning, connecting, sending packets | `BLEController.swift` | `BleController.kt` |
| Tilt steering | `Accelerometer.swift` | `TiltSensor.kt` |
| App state and the 20 Hz control loop | `CentralControl.swift` | `CentralControl.kt` |
| Screens and controls | `UI/` | `ui/` |
