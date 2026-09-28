package com.ryccoatika.journeyrecorder.recorder.bubble

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
 * Floating recording bubble hosted inside the accessibility service. Plain
 * Views only — no Compose. Primary window type TYPE_ACCESSIBILITY_OVERLAY
 * (no permission, trusted touch); falls back to TYPE_APPLICATION_OVERLAY /
 * TYPE_PHONE with 0.8 alpha when the primary add fails.
 */
class BubbleController(
    private val service: AccessibilityService,
    private val stateHolder: RecorderStateHolder,
    private val dao: JourneyDao,
    private val onStopRequested: () -> Unit,
) {
    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var dot: FrameLayout? = null
    private var pulseAnimator: android.animation.ValueAnimator? = null
    private var pill: LinearLayout? = null
    private var countText: TextView? = null
    private var uiScope: CoroutineScope? = null
    private var revertJob: Job? = null
    private var disposed = false

    private val density: Float get() = service.resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).roundToInt()
    private val screenWidth: Int get() = service.resources.displayMetrics.widthPixels
    private val screenHeight: Int get() = service.resources.displayMetrics.heightPixels

    /** Show the bubble for the currently recording journey. Safe to call twice. */
    fun show() {
        if (disposed) return
        val recording = stateHolder.current
            as? RecorderStateHolder.RecorderState.Recording ?: return
        hide()

        val count = TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            text = "0"
        }
        countText = count

        val dotView = FrameLayout(service).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(RED)
            }
            addView(
                count,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        dot = dotView

        // Soft alpha pulse — unmistakable "recording" signal even at a glance.
        pulseAnimator = android.animation.ValueAnimator.ofFloat(1f, 0.55f).apply {
            duration = 700
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { dotView.alpha = it.animatedValue as Float }
            start()
        }

        val pillView = buildPill()
        pill = pillView

        val container = FrameLayout(service).apply {
            addView(dotView, FrameLayout.LayoutParams(dp(DOT_DP), dp(DOT_DP)))
            addView(
                pillView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        pillView.visibility = View.GONE

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - dp(DOT_DP + 8)
            y = dp(96)
        }

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
            layoutParams.alpha = 0.8f
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
        dotView.setOnTouchListener(DragListener())

        uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch {
                dao.observeEventCount(recording.journeyId).collect { total ->
                    count.text = total.toString()
                }
            }
        }
    }

    fun hide() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        revertJob?.cancel()
        revertJob = null
        uiScope?.cancel()
        uiScope = null
        root?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
                // window already gone
            }
        }
        root = null
        params = null
        dot = null
        pill = null
        countText = null
    }

    fun dispose() {
        hide()
        disposed = true
    }

    // ------------------------------------------------------------------ private

    private fun buildPill(): LinearLayout {
        fun label(text: String, bold: Boolean = false): TextView = TextView(service).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            if (bold) paint.isFakeBoldText = true
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        val confirm = label("✓", bold = true).apply {
            isClickable = true
            setOnClickListener {
                revertPill()
                onStopRequested()
            }
        }
        val cancel = label("✕", bold = true).apply {
            isClickable = true
            setOnClickListener { revertPill() }
        }

        return LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(RED)
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            addView(label("Stop?"))
            addView(confirm)
            addView(cancel)
        }
    }

    /** Tap on the dot: morph into the stop-confirm pill, auto-revert in 3 s. */
    private fun showPill() {
        dot?.visibility = View.GONE
        pill?.visibility = View.VISIBLE
        revertJob?.cancel()
        revertJob = uiScope?.launch {
            delay(PILL_REVERT_MS)
            revertPill()
        }
    }

    private fun revertPill() {
        revertJob?.cancel()
        revertJob = null
        pill?.visibility = View.GONE
        dot?.visibility = View.VISIBLE
    }

    private fun snapToEdge() {
        val layoutParams = params ?: return
        val view = root ?: return
        val width = if (view.width > 0) view.width else dp(DOT_DP)
        val height = if (view.height > 0) view.height else dp(DOT_DP)
        layoutParams.x = if (layoutParams.x + width / 2 < screenWidth / 2) {
            0
        } else {
            (screenWidth - width).coerceAtLeast(0)
        }
        // Rest Y clamped to the top 40% of the screen.
        val maxY = (screenHeight * 2 / 5 - height).coerceAtLeast(0)
        layoutParams.y = layoutParams.y.coerceIn(0, maxY)
        safeUpdate(view, layoutParams)
    }

    private fun safeUpdate(view: View, layoutParams: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(view, layoutParams)
        } catch (_: Throwable) {
            // window went away mid-drag
        }
    }

    private inner class DragListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val layoutParams = params ?: return false
            val view = root ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    dragging = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        layoutParams.x = (startX + dx).roundToInt()
                        layoutParams.y = (startY + dy).roundToInt()
                        safeUpdate(view, layoutParams)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) snapToEdge() else showPill()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) snapToEdge()
                    return true
                }
            }
            return false
        }
    }

    private companion object {
        const val DOT_DP = 48
        const val PILL_REVERT_MS = 3000L
        val RED = 0xFFD32F2F.toInt()
    }
}
