package me.rerere.bettertext.text

import android.content.ClipData
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val HandleSize = 25.dp

/**
 * Makes the [BetterText]s in [content] selectable together, like Compose's
 * `SelectionContainer` (which they can't join, see issue 233102278).
 *
 * Long press selects a word, or a whole inline item, and dragging extends the
 * selection across texts. Inline items are selected whole and copied as their
 * alt text. Texts are joined with a newline when copied.
 */
@Composable
fun BetterSelectionContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val registry = remember { SelectionRegistry() }
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    val toolbar = LocalTextToolbar.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // Set while a long-press drag or a handle drag is in progress.
    var dragging by remember { mutableStateOf(false) }
    var magnifierCenter by remember { mutableStateOf(Offset.Unspecified) }
    val gestures = remember(registry) { SelectionGestures(registry) }
    gestures.onStart = {
        focusRequester.requestFocus()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    gestures.onMove = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    fun copy() {
        val text = registry.selectedText()
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("text", text))) }
        registry.selection = null
    }

    CompositionLocalProvider(LocalSelectionRegistry provides registry) {
        Box(
            modifier
                .onGloballyPositioned {
                    registry.containerCoordinates = it
                    registry.onLayoutChanged()
                }
                .focusRequester(focusRequester)
                .onFocusChanged { if (!it.hasFocus) registry.selection = null }
                // Not focusable(): it scrolls the container into view when focused.
                .focusTarget()
                .magnifier(sourceCenter = { magnifierCenter })
                .pointerInput(gestures) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) {
                            // A tap that nothing else handled clears the selection.
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed &&
                                (up.position - down.position).getDistance() < viewConfiguration.touchSlop
                            ) {
                                registry.selection = null
                            }
                            return@awaitEachGesture
                        }
                        if (!gestures.start(longPress.position)) return@awaitEachGesture
                        longPress.consume()
                        dragging = true
                        magnifierCenter = longPress.position
                        drag(longPress.id) { change ->
                            change.consume()
                            gestures.extend(change.position)
                            magnifierCenter = change.position
                        }
                        dragging = false
                        magnifierCenter = Offset.Unspecified
                    }
                },
        ) {
            content()
            val selection = registry.selection
            if (selection != null) {
                val ordered = registry.normalized(selection)
                if (ordered != null) {
                    for (stored in listOf(true, false)) {
                        val point = if (stored) selection.start else selection.end
                        SelectionHandle(
                            registry = registry,
                            point = point,
                            isLeft = point === ordered.first,
                            onDragStart = { dragging = true },
                            onDrag = { position ->
                                magnifierCenter = position
                                if (gestures.moveHandle(stored, position)) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                dragging = false
                                magnifierCenter = Offset.Unspecified
                            },
                        )
                    }
                }
            }
        }
    }

    val selection = registry.selection
    val version = registry.layoutVersion
    val handleHeight = with(LocalDensity.current) { HandleSize.toPx() }
    LaunchedEffect(selection, dragging, version) {
        val container = registry.containerCoordinates
        val bounds = registry.selectionBounds()
        if (selection == null || dragging || container == null || !container.isAttached || bounds == null) {
            if (toolbar.status == TextToolbarStatus.Shown) toolbar.hide()
            return@LaunchedEffect
        }
        val visible = container.visibleBounds().intersect(bounds)
        if (visible.width < 0 || visible.height < 0) {
            if (toolbar.status == TextToolbarStatus.Shown) toolbar.hide()
            return@LaunchedEffect
        }
        val rect = visible.translate(container.positionInRoot())
        toolbar.showMenu(
            rect = rect.copy(bottom = rect.bottom + handleHeight),
            onCopyRequested = ::copy,
            onSelectAllRequested = if (registry.isAllSelected()) null else registry::selectAll,
        )
    }
    DisposableEffect(toolbar) {
        onDispose { if (registry.selection != null && toolbar.status == TextToolbarStatus.Shown) toolbar.hide() }
    }
}

/** Long-press selection: starts at a word and extends from it, keeping the word selected. */
private class SelectionGestures(private val registry: SelectionRegistry) {
    var onStart: () -> Unit = {}
    var onMove: () -> Unit = {}
    private var anchor: SelectableText? = null
    private var word = IntRange.EMPTY

    /** Selects the word at [position]; returns false if there's no text there. */
    fun start(position: Offset): Boolean {
        val (text, local) = registry.textAt(position) ?: return false
        val range = text.wordAt(local)
        anchor = text
        word = range
        registry.selection = Selection(SelectionPoint(text, range.first), SelectionPoint(text, range.last + 1))
        onStart()
        return true
    }

    fun extend(position: Offset) {
        val text = anchor ?: return
        val point = registry.pointAt(position) ?: return
        val texts = registry.ordered()
        val before = compare(texts, point, SelectionPoint(text, word.first)) < 0
        val next = if (before) {
            Selection(SelectionPoint(text, word.last + 1), point)
        } else {
            val end = if (compare(texts, point, SelectionPoint(text, word.last + 1)) > 0) point else SelectionPoint(text, word.last + 1)
            Selection(SelectionPoint(text, word.first), end)
        }
        if (!sameAs(registry.selection, next)) {
            registry.selection = next
            onMove()
        }
    }

    /** Moves the stored start ([start]) or end of the selection; returns whether it changed. */
    fun moveHandle(start: Boolean, position: Offset): Boolean {
        val s = registry.selection ?: return false
        val point = registry.pointAt(position) ?: return false
        val other = if (start) s.end else s.start
        if (point.text === other.text && point.offset == other.offset) return false
        val next = if (start) Selection(point, s.end) else Selection(s.start, point)
        if (sameAs(s, next)) return false
        registry.selection = next
        return true
    }

    private fun compare(texts: List<SelectableText>, a: SelectionPoint, b: SelectionPoint): Int {
        val i = texts.indexOf(a.text).compareTo(texts.indexOf(b.text))
        return if (i != 0) i else a.offset.compareTo(b.offset)
    }

    private fun sameAs(a: Selection?, b: Selection): Boolean =
        a != null && a.start.text === b.start.text && a.start.offset == b.start.offset &&
            a.end.text === b.end.text && a.end.offset == b.end.offset
}

/**
 * A teardrop handle below the selection edge at [point]. [isLeft] handles
 * point up-right at the start of the selection, others up-left at its end.
 * [onDrag] gets the dragged position in container coordinates, at the middle
 * of the line the handle started on.
 */
@Composable
private fun SelectionHandle(
    registry: SelectionRegistry,
    point: SelectionPoint,
    isLeft: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    registry.layoutVersion // Reposition when texts move.
    val container = registry.containerCoordinates?.takeIf { it.isAttached } ?: return
    val coordinates = point.text.coordinates?.takeIf { it.isAttached } ?: return
    val handle = point.text.handlePosition(point.offset, isStart = isLeft) ?: return
    val anchor = container.localPositionOf(coordinates, handle.bottom)
    val lineMiddle = (anchor.y - container.localPositionOf(coordinates, Offset(0f, handle.lineTop)).y) / 2
    // Hide the handle when its text is scrolled out of view.
    val visible = container.visibleBounds()
    if (anchor.x < visible.left - 1 || anchor.x > visible.right + 1 || anchor.y < visible.top || anchor.y > visible.bottom + 1) return

    val color = LocalTextSelectionColors.current.handleColor
    val window = container.localToWindow(anchor)
    val size = with(LocalDensity.current) { HandleSize.toPx() }
    val provider = HandlePositionProvider(
        IntOffset((window.x - if (isLeft) size else 0f).roundToInt(), window.y.roundToInt()),
    )
    val currentAnchor by rememberUpdatedState(anchor)
    val currentOnDrag by rememberUpdatedState(onDrag)
    Popup(popupPositionProvider = provider, properties = PopupProperties(clippingEnabled = false)) {
        Spacer(
            Modifier
                .size(HandleSize)
                .pointerInput(Unit) {
                    var position = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            position = currentAnchor - Offset(0f, lineMiddle)
                            onDragStart()
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    ) { change, delta ->
                        change.consume()
                        position += delta
                        currentOnDrag(position)
                    }
                }
                .drawBehind {
                    drawCircle(color, radius = this.size.width / 2)
                    val corner = Size(this.size.width / 2, this.size.height / 2)
                    drawRect(color, topLeft = if (isLeft) Offset(corner.width, 0f) else Offset.Zero, size = corner)
                },
        )
    }
}

private data class HandlePositionProvider(val position: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = position
}

/** The visible part of this layout, in its own coordinates. */
private fun LayoutCoordinates.visibleBounds(): Rect {
    val window = boundsInWindow()
    return Rect(windowToLocal(window.topLeft), windowToLocal(window.bottomRight))
}
