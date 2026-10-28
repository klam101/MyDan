# Restaurant Orders for Android

A native Android app (Kotlin) for taking orders on a tablet and printing kitchen tickets
straight to the restaurant's network receipt printer. No server is needed.

## What's here

| Folder | What it is | Needs |
| --- | --- | --- |
| `core/` | Menu rules, pricing, modifications, ticket layout, printer code. Plain Kotlin. | JDK 17 |
| `app/` | The Android app: storage, printing, screens (Setup, PIN, Settings so far). | Android Studio |
| `scripts/` | `export-starter-menu.mjs` makes `core/src/main/resources/starter-menu.json`. | Node |

The rules match the web demo in `apps/frontend/src/domain`. If you change one, change the other.

## Commands (run in `apps/android`)

```sh
./gradlew :core:test :app:testDebugUnitTest            # run the tests
./gradlew :app:installDebug                            # install on a plugged-in tablet or emulator
./gradlew :core:printTest --args="192.168.1.50"        # print sample tickets on the real printer
./gradlew :core:printTest --args="192.168.1.50 --chinese"   # also try Chinese characters
```

On Windows PowerShell or cmd, use `gradlew` instead of `./gradlew`.

## Finding the printer's IP

Turn the printer off, hold FEED, and turn it on. The self-test page lists its interface and,
if it is on the network, its IP address. Reserve that address in the router so it never
changes. The computer or tablet must be on the same network.

## Running the app

1. Install [Android Studio](https://developer.android.com/studio). It includes the Android SDK
   and an emulator.
2. In Android Studio, choose File → Open and pick the `apps/android` folder. Let the first
   Gradle sync finish.
3. Choose a device (the emulator, or a tablet plugged in by USB with USB debugging on) and
   press Run.
4. On first run, set up the restaurant and the owner's PIN. Then sign in with the PIN.
5. When Android asks to let Orders find and connect to **nearby devices**, choose Allow.
   Android 17 needs this before any app can reach a printer on the local network.
6. In Settings, enter the printer's IP address and tap **Save and print test ticket**.

On the emulator, the computer running it is `10.0.2.2`, which is handy for testing with a
fake printer. After a build, the APK to copy onto a tablet is
`app/build/outputs/apk/debug/app-debug.apk`.

## Learning Kotlin from this code

If you know TypeScript, start with `core/src/main/kotlin/com/restaurantorders/domain/`. Each
file is a port of the TypeScript file with the same name in `apps/frontend/src/domain`, so you
can read them side by side:

- `val` / `var` are `const` / `let`.
- `String?` is `string | null`.
- `data class` is a typed object.
- `listOf`, `map` and `filter` work like arrays.
