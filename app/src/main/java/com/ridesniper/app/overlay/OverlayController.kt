package com.ridesniper.app.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ridesniper.app.model.Recommendation
import com.ridesniper.app.model.RideCalculationResult
import com.ridesniper.app.ui.MainActivity
import kotlin.math.abs

/**
 * Owns the two overlay windows: the small draggable bubble, and the result
 * card shown after an analysis. Tap = analyze, long-press = open full app,
 * drag = reposition. Neither window ever intercepts input meant for Uber;
 * the result card auto-dismisses and both windows use FLAG_NOT_TOUCH_MODAL
 * so touches outside them pass through to whatever's underneath.
 */
class OverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val lifecycleOwner = OverlayLifecycleOwner().apply { performRestore() }

    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var cardView: ComposeView? = null

    var onBubbleTapped: (() -> Unit)? = null

    private val busyState = mutableStateOf(false)
    private val lastRecommendationState = mutableStateOf<Recommendation?>(null)

    fun showBubble(sizeDp: Int, startX: Int, startY: Int) {
        if (bubbleView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Snap the INITIAL placement to whichever screen edge the requested
            // x is closer to, with a small margin, so the bubble never lands
            // over Uber's controls in the middle of the screen. This only
            // affects where it appears on launch — dragging afterward is
            // completely free and unclamped, same as before.
            x = edgeSnappedX(startX, sizeDp)
            y = startY
        }
        bubbleParams = params

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                RsBubble(sizeDp = sizeDp, isBusy = busyState.value)
            }
            setOnTouchListener(dragAndClickListener(params))
        }
        bubbleView = view

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager.addView(view, params)
    }

    /** Picks the nearer of the left/right screen edge for the given x, with an 8dp margin. */
    private fun edgeSnappedX(requestedX: Int, sizeDp: Int): Int {
        val density = context.resources.displayMetrics.density
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val bubbleSizePx = (sizeDp * density).toInt()
        val marginPx = (8 * density).toInt()

        val leftEdgeX = marginPx
        val rightEdgeX = (screenWidthPx - bubbleSizePx - marginPx).coerceAtLeast(leftEdgeX)
        val screenCenterX = screenWidthPx / 2

        return if (requestedX < screenCenterX) leftEdgeX else rightEdgeX
    }

    fun setBusy(busy: Boolean) {
        busyState.value = busy
    }

    fun showResult(result: RideCalculationResult, autoDismissMillis: Long = 9000) {
        hideResult()
        lastRecommendationState.value = result.recommendation

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 220
        }

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                ResultCard(result = result, onDismiss = { hideResult() })
            }
        }
        cardView = view
        windowManager.addView(view, params)

        view.postDelayed({ hideResult() }, autoDismissMillis)
    }

    /**
     * Immediate feedback shown the instant a genuine tap (not drag, not
     * long-press) is detected, before the analyzer pipeline has produced
     * anything. Replaced automatically once showResult() or
     * showNoOfferDetected() is called for this tap.
     */
    fun showAnalyzing() {
        showTransientMessage("Analysing…", autoDismissMillis = 6000)
    }

    /**
     * Ready for the foreground service to call once it determines OCR found
     * no usable offer on screen at all (as opposed to a partial parse that
     * goes to the manual correction sheet instead). Not yet wired up: doing
     * so requires a small addition in RideSniperForegroundService.kt, which
     * was intentionally left untouched pending your confirmation.
     */
    fun showNoOfferDetected() {
        showTransientMessage("No offer detected", autoDismissMillis = 2200)
    }

    private fun showTransientMessage(text: String, autoDismissMillis: Long) {
        hideResult()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 220
        }

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent { MessageCard(text) }
        }
        cardView = view
        windowManager.addView(view, params)

        view.postDelayed({ hideResult() }, autoDismissMillis)
    }

    fun hideResult() {
        cardView?.let { runCatching { windowManager.removeView(it) } }
        cardView = null
    }

    fun hideBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        hideResult()
        lifecycleOwner.destroy()
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dragAndClickListener(params: WindowManager.LayoutParams): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var downTimeMillis = 0L
        var moved = false
        val longPressThresholdMillis = 500L

        // Android's own density-aware standard for tap-vs-drag disambiguation,
        // instead of a hardcoded raw-pixel value that doesn't scale across
        // devices (a fixed 12px is too tight on a ~3x density phone like the
        // Galaxy S23 and made ordinary finger tremor register as a drag).
        val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

        return View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    downTimeMillis = System.currentTimeMillis()
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                        moved = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val heldMillis = System.currentTimeMillis() - downTimeMillis
                    if (!moved) {
                        if (heldMillis >= longPressThresholdMillis) {
                            openFullApp()
                        } else {
                            // Standard Android convention when a View.OnTouchListener
                            // consumes a tap: call performClick() for accessibility
                            // services / lint correctness, then run our own action.
                            view.performClick()
                            // Immediate visible feedback before the analyzer pipeline
                            // (which runs asynchronously and can take a moment) has
                            // produced anything at all.
                            showAnalyzing()
                            onBubbleTapped?.invoke()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openFullApp() {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private val NeonPink = Color(0xFFFF15C6)
private val NeonPinkGlow = Color(0xFFFF8FE0)

@Composable
private fun RsBubble(sizeDp: Int, isBusy: Boolean) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(NeonPink)
            .border(BorderStroke(3.dp, NeonPinkGlow), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isBusy) "…" else "RS",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = (sizeDp / 3.2).sp
        )
    }
}

@Composable
private fun MessageCard(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1C))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}
