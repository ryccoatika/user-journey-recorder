package com.ryccoatika.journeyrecorder.recorder.bubble

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import android.widget.ImageView
import com.ryccoatika.journeyrecorder.data.db.JourneyDao
import com.ryccoatika.journeyrecorder.recorder.RecorderStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * XRecorder-style always-on floating launcher. Plain Views only.
 *
 * Collapsed: a circular orb — app icon when idle, pulsing red + elapsed timer
 * while recording. Tap fans out a radial menu of circular action buttons:
 *  - idle:      Record · Home · Settings · Close
 *  - recording: Stop · Discard · Settings · Close
 *
 * One overlay window; it grows to hold the fan when expanded and the main orb
 * is kept pinned to its collapsed screen position.
 */
class BubbleController(
    private val service: AccessibilityService,
    private val stateHolder: RecorderStateHolder,
    private val dao: JourneyDao,
    private val onRecord: () -> Unit,
    private val onStop: () -> Unit,
    private val onDiscard: () -> Unit,
    private val onTogglePause: () -> Unit,
    private val onHome: () -> Unit,
    private val onSettings: () -> Unit,
    private val onExit: () -> Unit,
) {
    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var pulseAnimator: ValueAnimator? = null
    private var uiScope: CoroutineScope? = null
    private var collapseJob: Job? = null
    private var timerJob: Job? = null
    private var countJob: Job? = null
    private var enabled = false
    private var expanded = false

    // Drag-to-close trash target (a separate, non-touchable window).
    private var trashRoot: FrameLayout? = null
    private var trashIcon: TextView? = null
    private var overTrash = false

    // Collapsed orb view (lives in [root], which never resizes/moves except by drag).
    private var orbView: View? = null

    // Expanded fan lives in its OWN window so the orb window never has to
    // resize/reposition (that resize was the source of the collapse "jump").
    private var fanRoot: FrameLayout? = null
    private var fanParams: WindowManager.LayoutParams? = null
    private var mainButtonView: View? = null
    private var fanViews: List<Triple<View, Int, Int>> = emptyList() // view, dx, dy from orb

    // Collapsed orb position, kept stable across expand/collapse.
    private var orbX = 0
    private var orbY = 0

    private val density: Float get() = service.resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).roundToInt()
    private val screenWidth: Int get() = service.resources.displayMetrics.widthPixels
    private val screenHeight: Int get() = service.resources.displayMetrics.heightPixels

    private val recording: RecorderStateHolder.RecorderState.Recording?
        get() = stateHolder.current as? RecorderStateHolder.RecorderState.Recording

    /** Show the bubble (idempotent). Renders the mode for the current state. */
    fun enable() {
        if (enabled) {
            renderCollapsed()
            return
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - dp(ORB_DP + 8)
            y = dp(120)
            // No OS window transition on resize/reposition — expand/collapse
            // animate their own content, so the window itself must not slide.
            windowAnimations = 0
        }
        orbX = layoutParams.x
        orbY = layoutParams.y

        val container = FrameLayout(service)
        val added = try {
            windowManager.addView(container, layoutParams)
            true
        } catch (_: Throwable) {
            layoutParams.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            layoutParams.alpha = 0.9f
            try {
                windowManager.addView(container, layoutParams)
                true
            } catch (_: Throwable) {
                false
            }
        }
        if (!added) return

        root = container
        params = layoutParams
        enabled = true
        uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        renderCollapsed()
    }

    fun disable() {
        cancelJobs()
        hideTrashTarget()
        removeFan()
        uiScope?.cancel()
        uiScope = null
        orbView = null
        root?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
            }
        }
        root = null
        params = null
        enabled = false
        expanded = false
    }

    /** Called when the recorder state flips between idle and recording. */
    fun onStateChanged() {
        if (!enabled) return
        if (expanded) collapse() else renderCollapsed()
    }

    fun dispose() = disable()

    private fun cancelJobs() {
        pulseAnimator?.cancel(); pulseAnimator = null
        collapseJob?.cancel(); collapseJob = null
        timerJob?.cancel(); timerJob = null
        countJob?.cancel(); countJob = null
    }

    // ------------------------------------------------------------- collapsed orb

    private fun renderCollapsed() {
        val container = root ?: return
        cancelJobs()
        expanded = false
        removeFan()
        container.removeAllViews()

        // The orb window stays WRAP-sized and only moves by drag, so there is
        // nothing to resize/reposition here — no collapse "jump".
        val orb = buildOrb()
        orbView = orb
        container.addView(orb, FrameLayout.LayoutParams(dp(ORB_DP), dp(ORB_DP)))
        orb.setOnTouchListener(DragListener())
    }

    private fun buildOrb(): View {
        val rec = recording
        val ring = FrameLayout(service).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(SURFACE)
                setStroke(dp(2), Color.argb(70, 255, 255, 255))
            }
            elevation = dp(6).toFloat()
        }
        if (rec == null) {
            // Idle: app icon.
            val icon = ImageView(service).apply {
                runCatching {
                    setImageBitmap(
                        service.packageManager
                            .getApplicationIcon(service.packageName)
                            .toBitmap(dp(28), dp(28)),
                    )
                }
            }
            ring.addView(
                icon,
                FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER),
            )
        } else {
            // Recording: red core + live timer, pulsing. Paused: amber, static.
            val paused = stateHolder.isPaused
            val timer = TextView(service).apply {
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                text = "0:00"
            }
            val core = FrameLayout(service).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (paused) AMBER else RED)
                }
                addView(timer, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ))
            }
            val inset = dp(5)
            ring.addView(
                core,
                FrameLayout.LayoutParams(dp(ORB_DP) - inset * 2, dp(ORB_DP) - inset * 2, Gravity.CENTER),
            )
            if (!paused) {
                pulseAnimator = ValueAnimator.ofFloat(1f, 0.55f).apply {
                    duration = 700
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { core.alpha = it.animatedValue as Float }
                    start()
                }
            }
            startTimer(timer, rec.journeyId)
        }
        return ring
    }

    private fun startTimer(timer: TextView, journeyId: Long) {
        timerJob?.cancel()
        timerJob = uiScope?.launch {
            val startedAt = dao.getJourney(journeyId)?.startedAt ?: System.currentTimeMillis()
            while (true) {
                val elapsed = stateHolder.recordedElapsedMs(startedAt) / 1000
                timer.text = "%d:%02d".format(elapsed / 60, elapsed % 60)
                delay(1000)
            }
        }
    }

    // -------------------------------------------------------------- expanded fan

    private fun expand() {
        if (expanded) return
        cancelJobs()
        expanded = true

        val fanDown = orbY < screenHeight / 2
        val fanLeft = orbX + dp(ORB_DP) / 2 > screenWidth / 2

        val exp = dp(EXP_DP)
        val orbSize = dp(ORB_DP)
        // Fan window overlays the orb: its orb-slot lines up with the orb's
        // screen position; it grows toward the fan direction. The orb's own
        // window is left untouched (just hidden), so nothing resizes/jumps.
        val orbLeft = if (fanLeft) exp - orbSize else 0
        val orbTop = if (fanDown) 0 else exp - orbSize
        val fp = WindowManager.LayoutParams(
            exp,
            exp,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = orbX - orbLeft
            y = orbY - orbTop
            windowAnimations = 0
        }
        val container = FrameLayout(service)
        if (!addOverlay(container, fp)) {
            expanded = false
            return
        }
        fanRoot = container
        fanParams = fp
        orbView?.visibility = View.INVISIBLE

        val orbCx = orbLeft + orbSize / 2
        val orbCy = orbTop + orbSize / 2

        // Main button (tap to collapse) sits exactly over the hidden orb.
        val main = circleButton("✕", SURFACE) { collapse() }
        addAt(container, main, orbCx, orbCy, orbSize)
        mainButtonView = main
        // Grow in place from the orb.
        main.scaleX = 0.5f
        main.scaleY = 0.5f
        main.alpha = 0f
        main.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150)
            .setInterpolator(DecelerateInterpolator()).start()

        val actions = if (recording != null) {
            val paused = stateHolder.isPaused
            listOf(
                Action("■", RED) { collapse(); onStop() },
                if (paused) {
                    Action("▶", SURFACE) { collapse(); onTogglePause() }
                } else {
                    Action("❙❙", SURFACE) { collapse(); onTogglePause() }
                },
                Action("🗑", SURFACE) { collapse(); onDiscard() },
            )
        } else {
            // Close/stop the idle bubble by dragging it to the trash target,
            // not from the fan.
            listOf(
                Action("●", RED, dot = true) { collapse(); onRecord() },
                Action("⌂", SURFACE) { collapse(); onHome() },
                Action("⚙", SURFACE) { collapse(); onSettings() },
            )
        }

        val radius = dp(RADIUS_DP)
        val n = actions.size
        val views = mutableListOf<Triple<View, Int, Int>>()
        actions.forEachIndexed { i, action ->
            // Fan across a quarter arc from horizontal to vertical, into the
            // grow direction.
            val t = if (n == 1) 0.0 else i.toDouble() / (n - 1)
            val angle = Math.toRadians(90.0 * t) // 0 = horizontal, 90 = vertical
            val dx = (radius * Math.cos(angle)).roundToInt() * if (fanLeft) -1 else 1
            val dy = (radius * Math.sin(angle)).roundToInt() * if (fanDown) 1 else -1
            val btn = if (action.dot) {
                recordDotButton(action.onClick)
            } else {
                circleButton(action.glyph, action.bg, action.onClick)
            }
            addAt(container, btn, orbCx + dx, orbCy + dy, dp(BTN_DP))
            views += Triple(btn, dx, dy)
            // Start each button collapsed onto the orb, then spring outward.
            btn.translationX = -dx.toFloat()
            btn.translationY = -dy.toFloat()
            btn.scaleX = 0.3f
            btn.scaleY = 0.3f
            btn.alpha = 0f
            btn.animate()
                .translationX(0f).translationY(0f)
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setStartDelay(i * 25L)
                .setDuration(200)
                .setInterpolator(OvershootInterpolator(1.6f))
                .start()
        }
        fanViews = views

        armAutoCollapse()
    }

    private fun collapse() {
        if (!expanded) {
            removeFan()
            orbView?.visibility = View.VISIBLE
            return
        }
        expanded = false
        collapseJob?.cancel()
        val views = fanViews
        val main = mainButtonView
        if (views.isEmpty() && main == null) {
            removeFan()
            orbView?.visibility = View.VISIBLE
            return
        }
        // Retract each button back into the (hidden) orb, then drop the fan
        // window and reveal the orb — the orb window never moved, so no jump.
        views.forEach { (btn, dx, dy) ->
            btn.animate()
                .translationX(-dx.toFloat()).translationY(-dy.toFloat())
                .scaleX(0.3f).scaleY(0.3f).alpha(0f)
                .setDuration(140)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }
        main?.animate()?.scaleX(0.5f)?.scaleY(0.5f)?.alpha(0f)?.setDuration(130)?.start()
        uiScope?.launch {
            delay(150)
            removeFan()
            orbView?.visibility = View.VISIBLE
        }
    }

    private fun removeFan() {
        mainButtonView = null
        fanViews = emptyList()
        fanRoot?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
            }
        }
        fanRoot = null
        fanParams = null
    }

    /** Add an overlay window, falling back to non-a11y types if the first fails. */
    private fun addOverlay(view: View, lp: WindowManager.LayoutParams): Boolean = try {
        windowManager.addView(view, lp)
        true
    } catch (_: Throwable) {
        lp.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        lp.alpha = 0.9f
        try {
            windowManager.addView(view, lp)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun armAutoCollapse() {
        collapseJob?.cancel()
        collapseJob = uiScope?.launch {
            delay(AUTO_COLLAPSE_MS)
            collapse()
        }
    }

    private data class Action(
        val glyph: String,
        val bg: Int,
        val dot: Boolean = false,
        val onClick: () -> Unit,
    )

    private fun addAt(container: FrameLayout, view: View, cx: Int, cy: Int, size: Int) {
        container.addView(
            view,
            FrameLayout.LayoutParams(size, size).apply {
                leftMargin = cx - size / 2
                topMargin = cy - size / 2
            },
        )
    }

    private fun circleButton(glyph: String, bg: Int, onClick: () -> Unit): View =
        TextView(service).apply {
            text = glyph
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bg)
                setStroke(dp(1), Color.argb(40, 255, 255, 255))
            }
            elevation = dp(6).toFloat()
            isClickable = true
            setOnClickListener { onClick() }
        }

    /** White circle with a red dot — the Record affordance. */
    private fun recordDotButton(onClick: () -> Unit): View =
        FrameLayout(service).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            elevation = dp(6).toFloat()
            val dot = View(service).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(RED)
                }
            }
            addView(dot, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
            isClickable = true
            setOnClickListener { onClick() }
        }

    // -------------------------------------------------------------- drag-to-trash

    /** Trash catch target that slides up from the bottom while dragging. */
    private fun showTrashTarget() {
        if (trashRoot != null) return
        overTrash = false
        val icon = TextView(service).apply {
            text = "🗑"
            textSize = 26f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(TRASH_BG)
                setStroke(dp(2), Color.argb(60, 255, 255, 255))
            }
            elevation = dp(8).toFloat()
        }
        trashIcon = icon
        val container = FrameLayout(service).apply {
            addView(icon, FrameLayout.LayoutParams(dp(TRASH_DP), dp(TRASH_DP), Gravity.CENTER))
        }
        val lp = WindowManager.LayoutParams(
            dp(TRASH_DP + 24),
            dp(TRASH_DP + 24),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(72)
        }
        try {
            windowManager.addView(container, lp)
            trashRoot = container
            container.alpha = 0f
            container.translationY = dp(40).toFloat()
            container.animate().alpha(1f).translationY(0f).setDuration(180).start()
        } catch (_: Throwable) {
            trashRoot = null
            trashIcon = null
        }
    }

    private fun hideTrashTarget() {
        overTrash = false
        trashRoot?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
            }
        }
        trashRoot = null
        trashIcon = null
    }

    /**
     * Decide whether the orb is in catch range of the trash target, working in
     * true screen coordinates. On entering: highlight the target, buzz, and
     * return the window params (x, y) that snap the orb's center onto the
     * target's center. Returns null when not over (orb follows the finger).
     *
     * [orbScreenCx]/[orbScreenCy] are the orb center in screen space; [originX]/
     * [originY] convert screen space back to window-param space (they differ by
     * the window origin, e.g. the status bar inset — the source of the earlier
     * misalignment). [orbSize] is the orb's on-screen size.
     */
    private fun updateTrashHighlight(
        orbScreenCx: Int,
        orbScreenCy: Int,
        originX: Int,
        originY: Int,
        orbSize: Int,
    ): Pair<Int, Int>? {
        val target = trashRoot ?: return null
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        val targetCx = loc[0] + target.width / 2
        val targetCy = loc[1] + target.height / 2
        val dist = kotlin.math.hypot(
            (orbScreenCx - targetCx).toDouble(),
            (orbScreenCy - targetCy).toDouble(),
        )
        val nowOver = dist < dp(TRASH_CATCH_DP)
        if (nowOver != overTrash) {
            overTrash = nowOver
            if (nowOver) vibrate()
            trashIcon?.apply {
                (background as? GradientDrawable)?.setColor(if (nowOver) RED else TRASH_BG)
                animate().scaleX(if (nowOver) 1.3f else 1f)
                    .scaleY(if (nowOver) 1.3f else 1f).setDuration(120).start()
            }
        }
        return if (nowOver) {
            // Convert the target's screen center back to window-param space and
            // offset by half the orb so the orb centers exactly on the target.
            (targetCx - originX - orbSize / 2) to (targetCy - originY - orbSize / 2)
        } else {
            null
        }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = service.getSystemService(android.os.VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        30,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE,
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                val v = service.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(
                        android.os.VibrationEffect.createOneShot(
                            30,
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE,
                        ),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(30)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    // ------------------------------------------------------------------- helpers

    private fun snapToEdge() {
        val p = params ?: return
        val view = root ?: return
        val size = dp(ORB_DP)
        p.x = if (p.x + size / 2 < screenWidth / 2) 0 else (screenWidth - size).coerceAtLeast(0)
        val maxY = (screenHeight - size - dp(80)).coerceAtLeast(0)
        p.y = p.y.coerceIn(dp(24), maxY)
        orbX = p.x
        orbY = p.y
        safeUpdate(view, p)
    }

    private fun safeUpdate(view: View, layoutParams: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(view, layoutParams)
        } catch (_: Throwable) {
        }
    }

    private inner class DragListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        // screenCoord = paramCoord + origin. Measured once per drag.
        private var originX = 0
        private var originY = 0

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val p = params ?: return false
            val view = root ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = p.x
                    startY = p.y
                    dragging = false
                    val loc = IntArray(2)
                    view.getLocationOnScreen(loc)
                    originX = loc[0] - p.x
                    originY = loc[1] - p.y
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        showTrashTarget()
                    }
                    if (dragging) {
                        val fingerX = (startX + dx).roundToInt()
                        val fingerY = (startY + dy).roundToInt()
                        orbX = fingerX
                        orbY = fingerY
                        val orbSize = if (view.width > 0) view.width else dp(ORB_DP)
                        // Over-trash test in true screen coords; when caught, the
                        // window snaps so the orb centers on the target.
                        val snap = updateTrashHighlight(
                            orbScreenCx = fingerX + originX + orbSize / 2,
                            orbScreenCy = fingerY + originY + orbSize / 2,
                            originX = originX,
                            originY = originY,
                            orbSize = orbSize,
                        )
                        if (snap != null) {
                            p.x = snap.first
                            p.y = snap.second
                        } else {
                            p.x = fingerX
                            p.y = fingerY
                        }
                        safeUpdate(view, p)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    when {
                        dragging && overTrash -> {
                            hideTrashTarget()
                            onExit()
                        }
                        dragging -> {
                            hideTrashTarget()
                            snapToEdge()
                        }
                        else -> expand()
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        hideTrashTarget()
                        snapToEdge()
                    }
                    return true
                }
            }
            return false
        }
    }

    private companion object {
        const val ORB_DP = 52
        const val BTN_DP = 48
        const val RADIUS_DP = 78
        const val EXP_DP = 200
        const val AUTO_COLLAPSE_MS = 8000L
        const val TRASH_DP = 60
        const val TRASH_CATCH_DP = 80
        val RED = 0xFFD32F2F.toInt()
        val AMBER = 0xFFF6A609.toInt()
        val SURFACE = 0xFF2A2B31.toInt()
        val TRASH_BG = 0xFF3A3B41.toInt()
    }
}