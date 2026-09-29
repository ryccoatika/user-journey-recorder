package com.ryccoatika.journeyrecorder.recorder.bubble

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Journey saved" floating card shown by the accessibility service right
 * after a recording finishes — the SQA is still inside the target app, so
 * the export shortcuts come to them. Plain Views; same window-type ladder
 * as the bubble. Auto-dismisses.
 */
class ResultCardController(private val service: AccessibilityService) {

    class Actions(
        val onOpen: () -> Unit,
        val onShare: () -> Unit,
        val onSave: () -> Unit,
        val onDelete: () -> Unit,
    )

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: View? = null
    private var scope: CoroutineScope? = null

    private val density: Float get() = service.resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).roundToInt()

    fun show(title: String, subtitle: String, actions: Actions) {
        hide()

        val card = buildCard(title, subtitle, actions)
        val screenWidth = service.resources.displayMetrics.widthPixels
        val width = min(screenWidth - dp(24), dp(380))

        val layoutParams = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(64)
        }

        val added = try {
            windowManager.addView(card, layoutParams)
            true
        } catch (_: Throwable) {
            layoutParams.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            layoutParams.alpha = 0.95f
            try {
                windowManager.addView(card, layoutParams)
                true
            } catch (_: Throwable) {
                false
            }
        }
        if (!added) return

        root = card
        card.alpha = 0f
        card.animate().alpha(1f).setDuration(200).start()

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
            it.launch {
                delay(AUTO_DISMISS_MS)
                hide()
            }
        }
    }

    fun hide() {
        scope?.cancel()
        scope = null
        root?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
                // window already gone
            }
        }
        root = null
    }

    fun dispose() = hide()

    // ------------------------------------------------------------------ views

    private fun buildCard(title: String, subtitle: String, actions: Actions): View {
        fun text(
            value: String,
            size: Float,
            color: Int,
            bold: Boolean = false,
        ): TextView = TextView(service).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        fun action(label: String, color: Int, onClick: () -> Unit): TextView =
            TextView(service).apply {
                text = label
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
                isClickable = true
                setOnClickListener {
                    hide()
                    onClick()
                }
            }

        val check = TextView(service).apply {
            text = "✓"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(GREEN)
            }
        }

        val dismiss = TextView(service).apply {
            text = "✕"
            textSize = 14f
            setTextColor(Color.argb(150, 255, 255, 255))
            setPadding(dp(8), dp(2), dp(2), dp(2))
            isClickable = true
            setOnClickListener { hide() }
        }

        val header = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(check, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(
                text("Journey saved", 15f, Color.WHITE, bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(10) },
            )
            addView(dismiss)
        }

        val buttons = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(action("Delete", Color.argb(220, 255, 138, 128)) { actions.onDelete() })
            addView(action("Save", ACCENT) { actions.onSave() })
            addView(action("Share", ACCENT) { actions.onShare() })
            addView(action("Open", ACCENT) { actions.onOpen() })
        }

        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(SURFACE)
                setStroke(dp(1), Color.argb(40, 255, 255, 255))
            }
            elevation = dp(10).toFloat()
            setPadding(dp(16), dp(14), dp(12), dp(8))
            addView(header)
            addView(
                text(title, 14f, Color.argb(235, 255, 255, 255)).apply {
                    setPadding(0, dp(10), 0, 0)
                },
            )
            addView(
                text(subtitle, 12f, Color.argb(160, 255, 255, 255)).apply {
                    setPadding(0, dp(2), 0, dp(4))
                },
            )
            addView(buttons)
        }
    }

    private companion object {
        const val AUTO_DISMISS_MS = 15_000L
        val SURFACE = 0xFF1E1F25.toInt()
        val GREEN = 0xFF2E7D32.toInt()
        val ACCENT = 0xFFBAC3FF.toInt()
    }
}