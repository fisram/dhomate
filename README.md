# Dhomate

A Pomodoro timer for Wear OS - "dhomate" after the tomato it is named for -
built to behave like a system timer rather than a foreground animation.

Built for and tested on a **Google Pixel Watch 2** (Wear OS, API 37).

## Why it works the way it does

The timer is stored as a **deadline**, never as a running total. Remaining time is
always computed as `deadline - now`:

```kotlin
fun remainingMillis(nowEpochMillis: Long): Long =
    if (running) (deadlineEpochMillis - nowEpochMillis).coerceAtLeast(0L)
    else pausedRemainingMillis
```

This is the difference between a watch timer that works and one that does not.
An implementation that subtracts a fixed amount per tick loses any tick the
system defers — in ambient mode, in doze, or when the process is killed — and
replays them in a burst on wake, so the clock both drifts and jumps. Here a late
or missed repaint is purely cosmetic: the next read is correct again.

Everything else follows from that:

| Concern | Approach |
| --- | --- |
| Firing on time | `AlarmManager.setAlarmClock` — not deferred by doze or battery saver |
| Surviving the crown / app exit | State is persisted on every transition; nothing needs to stay running |
| Surviving reboot | Deadline is wall-clock, and `BootReceiver` re-arms the alarm |
| Live countdown off-app | `OngoingActivity` + complication `TimeDifferenceComplicationText`, both rendered by the system |
| Being heard | The alert is an **alarm** (`USAGE_ALARM`), not a notification. STREAM_NOTIFICATION sits at 1/7 on this watch while STREAM_ALARM sits at 5/7, so a notification-stream tone plays correctly and is inaudible |

No foreground service is used. The ongoing notification and the alarm are both
held by the platform, so they outlive our process on their own — which also keeps
the app clear of background-start restrictions.

## Surfaces

- **App** — full timer with progress ring, session dots, and settings.
- **Tile** — swipe left/right from the watch face. The countdown is published as
  a protolayout dynamic expression bound to the deadline, so the renderer ticks
  it on the watch without our process running.
- **Complication** — remaining time in a watch face's own data slots, as
  `SHORT_TEXT` or `RANGED_VALUE`. Both its countdown and progress value update
  from platform time without polling the app.
- **Ongoing chip** — a live, system-rendered countdown on the watch face while a
  session runs.

## Configurable

Focus, short break, long break, flow-session length, and sessions-before-long-break
are all adjustable in-app. A length change applies to the phase on screen only if
that phase has not started yet — rescaling a session already under way would be
surprising.

## Building

Requires JDK 17 and Android SDK platform 37.

For everyday use on the watch, build the local `benchmark` variant. It is signed
with the debug key so `adb install -r` works, but uses release-mode R8/resource
shrinking and packages the merged baseline profile. Keep `assembleDebug` for
sessions where a debugger or `run-as` access is actually needed.

```bash
./gradlew assembleBenchmark
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
```

The Pixel Watch charging puck is power-only, so installation uses wireless
debugging.

## Credits

The idea and the original Wear OS Pomodoro app that prompted this one come from
[Drat1x/WearOS-Pomodoro](https://github.com/Drat1x/WearOS-Pomodoro). This is a
ground-up rewrite that shares no code with it.

## License

MIT — see [LICENSE](LICENSE).
