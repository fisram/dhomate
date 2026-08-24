package io.github.fisram.dhomate.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.TypeBuilders.StringProp
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import io.github.fisram.dhomate.R
import io.github.fisram.dhomate.domain.TimerMode
import io.github.fisram.dhomate.domain.TimerPhase
import io.github.fisram.dhomate.domain.TimerState
import io.github.fisram.dhomate.domain.formatRemaining
import io.github.fisram.dhomate.timerEngine
import io.github.fisram.dhomate.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * A glanceable, native-style companion to the full timer screen.
 *
 * The Tile never counts ticks. Its text and progress ring are dynamic
 * expressions derived from `deadline - platformTime`, so both keep moving when
 * Dhomate's process is gone. Tapping either the card or the primary button opens
 * the app, where timer controls remain large and unambiguous.
 */
class PomodoroTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onRecentInteractionEventsAsync(
        events: List<EventBuilders.TileInteractionEvent>,
    ): ListenableFuture<Void> {
        if (events.any { it.eventType == EventBuilders.TileInteractionEvent.ENTER }) {
            requestUpdate()
        }
        return super.onRecentInteractionEventsAsync(events)
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                try {
                    timerEngine.ensureLoaded()
                    completer.set(buildTile(timerEngine.state.value))
                } catch (t: Throwable) {
                    completer.setException(t)
                }
            }
            "Dhomate Tile layout"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion(RESOURCES_VERSION)
                    .addIdToImageMapping(
                        RESOURCE_APP_ICON,
                        imageResource(R.drawable.tile_app_icon),
                    )
                    .addIdToImageMapping(
                        RESOURCE_TOMATO_SLICE,
                        imageResource(R.drawable.tomato_slice),
                    )
                    .build(),
            )
            "Dhomate Tile resources"
        }

    private fun buildTile(state: TimerState): TileBuilders.Tile {
        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(root(state))
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder().setLayout(layout).build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun root(state: TimerState): LayoutElementBuilders.LayoutElement {
        val accent = accentFor(state)
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(image(RESOURCE_APP_ICON, 20f))
            .addContent(spacer(1f))
            .addContent(text(getString(R.string.app_name), 14f, COLOR_TEXT))
            .addContent(spacer(4f))
            .addContent(timerCard(state, accent))
            .addContent(spacer(5f))
            .addContent(openButton())
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(background(COLOR_BACKGROUND))
            .addContent(column)
            .build()
    }

    private fun timerCard(
        state: TimerState,
        accent: Int,
    ): LayoutElementBuilders.LayoutElement {
        val phaseAndTotal = buildString {
            append(getString(phaseLabelRes(state)))
            append(" · ")
            append(formatRemaining(state.phaseTotalMillis))
        }

        val content = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(progressGraphic(state, accent))
            .addContent(hSpacer(5f))
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(dp(85f))
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(countdown(state))
                    .addContent(spacer(2f))
                    .addContent(text(phaseAndTotal, 11f, COLOR_SECONDARY_TEXT))
                    .build(),
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(160f))
            .setHeight(dp(70f))
            .setModifiers(
                roundedClickableBackground(
                    color = COLOR_CARD,
                    radius = 22f,
                    id = ID_CARD,
                ),
            )
            .addContent(content)
            .build()
    }

    private fun progressGraphic(
        state: TimerState,
        accent: Int,
    ): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(44f))
            .setHeight(dp(44f))
            .addContent(ringTrack())
            .addContent(progressArc(state, accent))
            .addContent(image(RESOURCE_TOMATO_SLICE, 21f))
            .build()

    private fun ringTrack(): LayoutElementBuilders.Arc {
        val track = LayoutElementBuilders.ArcLine.Builder()
            .setLength(degrees(RING_SWEEP_DEGREES))
            .setThickness(dp(4f))
            .setColor(argb(COLOR_RING_TRACK))
            .setStrokeCap(LayoutElementBuilders.STROKE_CAP_ROUND)
            .build()

        return LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(RING_START_ANGLE))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .setArcDirection(LayoutElementBuilders.ARC_DIRECTION_CLOCKWISE)
            .addContent(track)
            .build()
    }

    private fun progressArc(
        state: TimerState,
        accent: Int,
    ): LayoutElementBuilders.Arc {
        val now = System.currentTimeMillis()
        val length = DimensionBuilders.DegreesProp.Builder(
            (1f - state.progress(now)) * RING_SWEEP_DEGREES,
        )

        if (state.running) {
            val remaining = DynamicInstant.platformTimeWithSecondsPrecision()
                .durationUntil(
                    DynamicInstant.withSecondsPrecision(
                        Instant.ofEpochMilli(state.deadlineEpochMillis),
                    ),
                )
            val totalSeconds = (state.phaseTotalMillis / 1_000f).coerceAtLeast(1f)
            length.setDynamicValue(
                remaining.toIntSeconds()
                    .asFloat()
                    .div(totalSeconds)
                    .times(RING_SWEEP_DEGREES),
            )
        }

        val progress = LayoutElementBuilders.ArcLine.Builder()
            .setLength(length.build())
            .setLayoutConstraintsForDynamicLength(
                DimensionBuilders.AngularLayoutConstraint.Builder(RING_SWEEP_DEGREES).build(),
            )
            .setThickness(dp(4f))
            .setColor(argb(accent))
            .setStrokeCap(LayoutElementBuilders.STROKE_CAP_ROUND)
            .build()

        return LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(RING_START_ANGLE))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .setArcDirection(LayoutElementBuilders.ARC_DIRECTION_CLOCKWISE)
            .addContent(progress)
            .build()
    }

    /** Keeps the colon at the exact center while either side changes width. */
    private fun countdown(state: TimerState): LayoutElementBuilders.LayoutElement {
        val static = formatRemaining(state.remainingMillis(System.currentTimeMillis()))
        var minutes: DynamicString? = null
        var seconds: DynamicString? = null

        if (state.running) {
            val remaining = DynamicInstant.platformTimeWithSecondsPrecision()
                .durationUntil(
                    DynamicInstant.withSecondsPrecision(
                        Instant.ofEpochMilli(state.deadlineEpochMillis),
                    ),
                )
            val twoDigits = DynamicInt32.IntFormatter.Builder()
                .setMinIntegerDigits(2)
                .build()
            minutes = remaining.toIntMinutes().format(twoDigits)
            seconds = remaining.getSecondsPart().format(twoDigits)
        }

        return LayoutElementBuilders.Row.Builder()
            .setWidth(dp(75f))
            .setHeight(dp(29f))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                numberSlot(
                    fallback = static.substringBefore(':'),
                    value = minutes,
                    textAlignment = LayoutElementBuilders.TEXT_ALIGN_END,
                    boxAlignment = LayoutElementBuilders.HORIZONTAL_ALIGN_END,
                ),
            )
            .addContent(text(":", 24f, COLOR_TEXT))
            .addContent(
                numberSlot(
                    fallback = static.substringAfter(':'),
                    value = seconds,
                    textAlignment = LayoutElementBuilders.TEXT_ALIGN_START,
                    boxAlignment = LayoutElementBuilders.HORIZONTAL_ALIGN_START,
                ),
            )
            .build()
    }

    private fun numberSlot(
        fallback: String,
        value: DynamicString?,
        textAlignment: Int,
        boxAlignment: Int,
    ): LayoutElementBuilders.LayoutElement {
        val property = StringProp.Builder(fallback)
        if (value != null) property.setDynamicValue(value)

        val number = LayoutElementBuilders.Text.Builder()
            .setText(property.build())
            .setLayoutConstraintsForDynamicText(
                StringLayoutConstraint.Builder("000")
                    .setAlignment(textAlignment)
                    .build(),
            )
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(24f))
                    .setColor(argb(COLOR_TEXT))
                    .build(),
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(32f))
            .setHeight(dp(29f))
            .setHorizontalAlignment(boxAlignment)
            .addContent(number)
            .build()
    }

    private fun openButton(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(90f))
            .setHeight(dp(32f))
            .setModifiers(
                roundedClickableBackground(
                    color = COLOR_ACTION,
                    radius = 16f,
                    id = ID_OPEN,
                ),
            )
            .addContent(text(getString(R.string.action_open), 16f, COLOR_ON_ACTION))
            .build()

    private fun roundedClickableBackground(
        color: Int,
        radius: Float,
        id: String,
    ): ModifiersBuilders.Modifiers = ModifiersBuilders.Modifiers.Builder()
        .setBackground(
            ModifiersBuilders.Background.Builder()
                .setColor(argb(color))
                .setCorner(
                    ModifiersBuilders.Corner.Builder().setRadius(dp(radius)).build(),
                )
                .build(),
        )
        .setClickable(
            ModifiersBuilders.Clickable.Builder()
                .setId(id)
                .setOnClick(openAppAction())
                .build(),
        )
        .build()

    private fun background(color: Int): ModifiersBuilders.Modifiers =
        ModifiersBuilders.Modifiers.Builder()
            .setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(argb(color))
                    .build(),
            )
            .build()

    private fun openAppAction(): ActionBuilders.LaunchAction =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build(),
            )
            .build()

    private fun imageResource(drawableId: Int): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(drawableId)
                    .build(),
            )
            .build()

    @Suppress("DEPRECATION")
    private fun image(resourceId: String, sizeDp: Float): LayoutElementBuilders.Image =
        LayoutElementBuilders.Image.Builder()
            .setResourceId(resourceId)
            .setWidth(dp(sizeDp))
            .setHeight(dp(sizeDp))
            .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FIT)
            .build()

    private fun text(value: String, sizeSp: Float, color: Int): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(sizeSp))
                    .setColor(argb(color))
                    .build(),
            )
            .build()

    private fun spacer(heightDp: Float): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(heightDp)).build()

    private fun hSpacer(widthDp: Float): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setWidth(dp(widthDp)).build()

    private fun requestUpdate() {
        runCatching { getUpdater(this).requestUpdate(PomodoroTileService::class.java) }
    }

    private fun accentFor(state: TimerState): Int = when {
        state.phase != TimerPhase.FOCUS -> COLOR_LEAF
        state.mode == TimerMode.DEEP_WORK -> COLOR_AMBER
        else -> COLOR_TOMATO
    }

    private fun phaseLabelRes(state: TimerState): Int = when {
        state.mode == TimerMode.DEEP_WORK && state.phase == TimerPhase.FOCUS ->
            R.string.phase_deep_work
        state.phase == TimerPhase.FOCUS -> R.string.phase_focus
        state.phase == TimerPhase.SHORT_BREAK -> R.string.phase_short_break
        else -> R.string.phase_long_break
    }

    private companion object {
        const val RESOURCES_VERSION = "4"

        const val RESOURCE_APP_ICON = "dhomate_logo"
        const val RESOURCE_TOMATO_SLICE = "tomato_slice"
        const val ID_CARD = "open_timer_card"
        const val ID_OPEN = "open_timer_button"

        const val RING_SWEEP_DEGREES = 336f
        const val RING_START_ANGLE = 12f

        const val COLOR_BACKGROUND = 0xFF000000.toInt()
        const val COLOR_CARD = 0xFF22243A.toInt()
        const val COLOR_RING_TRACK = 0xFF5C5E70.toInt()
        const val COLOR_TEXT = 0xFFF6F2F1.toInt()
        const val COLOR_SECONDARY_TEXT = 0xFFD4CFD0.toInt()
        const val COLOR_TOMATO = 0xFFFF5A4D.toInt()
        const val COLOR_AMBER = 0xFFFFB36B.toInt()
        const val COLOR_LEAF = 0xFF74D784.toInt()
        const val COLOR_ACTION = 0xFF83F1A7.toInt()
        const val COLOR_ON_ACTION = 0xFF07130B.toInt()
    }
}
