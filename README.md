# Dhomate

A simple Pomodoro timer made for Wear OS.

Dhomate keeps your focus sessions, breaks, and one-off flow sessions close at
hand. Start a timer from your watch, get an alert when it is time to switch,
and carry on without needing to keep the app open.

Built for and tested on a **Google Pixel Watch 2** running Wear OS.

## What it does

- Runs focus sessions, short breaks, long breaks, and flow sessions.
- Lets you start, pause, reset, or skip a session right from the watch.
- Shows a running countdown in the app, its Wear OS tile, and a compatible
  watch-face complication.
- Keeps the timer visible on the watch face while a session is running.
- Alerts you when a focus session or break ends.

## Configurable

Focus, short-break, long-break, and flow-session lengths are adjustable in the
app, along with the number of focus sessions before a long break.

## Building from source

You will need JDK 17 and Android SDK platform 37.

```bash
./gradlew assembleBenchmark
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
```

## Credits

The idea and the original Wear OS Pomodoro app that prompted this one come from
[Drat1x/WearOS-Pomodoro](https://github.com/Drat1x/WearOS-Pomodoro). This is a
ground-up rewrite that shares no code with it.

## License

MIT — see [LICENSE](LICENSE).
