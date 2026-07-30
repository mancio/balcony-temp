# Balcony Temp

A tiny native Android **app + home‑screen widget** that shows a balcony thermometer's
readings (temperature, last update, battery) from a **Firebase Realtime Database**.

It mirrors the temperature buckets and artwork of the companion web dashboard:
🐫 hot (> 29 °C), a temperate scene (13–29 °C) and a frozen scene (< 13 °C).

## Download

**[⬇️ Download BalconyTemp.apk](https://github.com/mancio/balcony-temp/releases/download/v1.1/BalconyTemp.apk)**
&nbsp;·&nbsp; or browse all [Releases](https://github.com/mancio/balcony-temp/releases/latest).

Sideload it (enable *Install unknown apps* for your browser/file manager).

> **Upgrading from v1.0?** v1.1 is the first build signed with a real release key
> (v1.0 was debug‑signed), so Android will refuse to install it over the old version.
> Uninstall v1.0 first. Future updates will install normally.

<p align="center"><img src="docs/screenshot.png" width="320" alt="Balcony Temp app"/></p>

## How it works

It reads a fixed **public** Firebase Realtime Database endpoint over REST
(`.../Casina.json`) — no account, login, or configuration needed. Just install and open.

## Features

- Live temperature with a weather icon chosen from the reading.
- "Last update … ago" plus the absolute time (Warsaw time).
- Battery % derived from the sensor voltage, shown **in red when the battery is low**.
- The sensor wakes from deep sleep once an hour, so **if the last update is older than 5 hours a red "no update" warning appears** — the battery stays visible, since a flat battery is usually the reason it stopped reporting.
- Home‑screen widget that refreshes on tap and every 15 minutes in the background (WorkManager), and keeps showing the last known reading across reboots instead of blanking out.
- Pull to refresh; "Add widget to home screen" button.

## Tests

```bash
./gradlew testDebugUnitTest          # JVM + Robolectric (no device needed)
./gradlew connectedDebugAndroidTest  # on a device/emulator
```

## Build from source

Requirements: JDK 17 and the Android SDK (API 34).

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Tech

Kotlin · View Binding · Coroutines · `AppWidgetProvider` · `HttpURLConnection` + `org.json`.
