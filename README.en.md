# Retro Link · Old Phone Revival Project

[简体中文](README.md)

**Turn a retired Android phone into a handheld companion for your primary phone.** Control music, check your calendar, jot down a thought, or take a photo from the older device. The modern phone handles background work and can stay locked in your pocket. The two phones connect directly over paired Bluetooth Classic. Daily use requires neither a computer nor a shared Wi-Fi network.

Retro Link has two Android apps. The reference pair is **Sony Ericsson Xperia pro MK16i (Android 2.3.4) + Nothing Phone (3a) (Android 16)**.

| App | Role | Current version | Minimum install version |
| --- | --- | --- | --- |
| `legacy` | Companion for the older phone, with a landscape UI, touch, and hardware keys | 1.0.0 | Android 2.3 (API 9) |
| `host` | Primary-phone app for Bluetooth, background reception, and Android integrations | 1.0.0 | Android 8 (API 26) |

The minimum versions come from the APK configuration. Feature compatibility depends on device testing. Results from phones beyond the reference pair are welcome.

## What works today

- **Music remote** — View track details and album art, play or pause, skip tracks, and control primary-phone volume with the companion's hardware keys. Album art and available actions depend on the player.
- **Calendar** — Browse the primary phone's system calendar. With permission, create events or edit writable events from the companion.
- **Quick notes** — Send text and recordings from the companion. The host can receive and store them while locked, play back recordings, and convert them to M4A locally. WeChat handoff starts from a notification on the primary phone and opens text and audio separately.
- **Photo backup** — After setup, Photos on the companion home screen opens the system camera. New JPEGs in the camera folder go over Bluetooth to the primary phone's **Retro Link** album. Queued photos were delivered with the reference host locked. Camera folders and storage mounting need testing on other phones. Gallery writes use Android APIs introduced in Android 10.
- **Primary-phone status** — Check battery, charging, memory, storage, thermal status, and now playing. Ring the primary phone to find it, or switch between ring, vibration, and Do Not Disturb. The companion's Silent button enables Do Not Disturb.
- **Clock sync on connection** — The host sends the date, time, and time zone when a Bluetooth connection is established. Retro Link uses this information for its display clock. The companion's system clock and native camera keep using the device clock.
- **Experiments** — View notifications from selected apps and hand off text to the ChatGPT app on the host. Notification replies depend on actions exposed by the original app. Automatic ChatGPT submission and response return with the host locked still need verification.

## Getting started

1. Install the `host` and `legacy` APKs on their respective phones and pair the devices in Android Bluetooth settings.
2. In Retro Link on the primary phone, select the companion, grant the permissions needed for the features you use, and start the connection.
3. In Retro Link on the older phone, select the primary phone. Try music controls first, then lock the primary phone to test notes and photos.
4. To test photo backup on an Xperia pro, insert a memory card. If the phone is plugged into a computer, turn off USB mass storage on the phone so it can read the photos on the card. Photos transfer over Bluetooth, so you can unplug the USB cable for daily use.

## Help test more phones

If you want to try a different companion or replace the primary phone with another Android device, please open an issue. Even a pairing test and one feature result can help someone decide whether to try their phone. Include:

- Both phone models, Android versions, Retro Link versions, and which phone you changed.
- The features you tested and what happened. Useful checks include pairing, reconnection, music with the host locked, calendar, notes, and photos. For a long-running connection test, include its duration.
- Steps to reproduce, expected behavior, and actual behavior. Redact names, message contents, device addresses, and other private information from logs and screenshots.

## Help build more variants

Submit shared protocol improvements and general fixes to `main`. For device-specific key mappings, screen layouts, or storage fixes, start with a `device/<model>` branch in your fork. Use `experiment/<feature>` for independent interaction or feature experiments. These are suggested branch names for future contributions.

If you have a hardware-keyboard phone, an older Android phone with a different screen shape, or another primary phone you can test with the screen off, open an issue describing what you can try. Include on-device results with adaptation pull requests and say whether protocol changes require both apps to update together.

## Build from source

Use JDK 17, Gradle 8.11.1, Android SDK 35, and Build Tools 35.0.0. The project uses Android Gradle Plugin 8.9.1. Set `ANDROID_HOME` locally and run this from the repository root:

```sh
gradle :host:assembleDebug :legacy:assembleDebug :host:lintDebug :legacy:lintDebug
```

The APKs are generated under `host/build/outputs/apk/debug/` and `legacy/build/outputs/apk/debug/`. Install the companion APK by sideloading it.
