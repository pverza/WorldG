package com.pverza.wortschatz

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

/** Starts or stops the unlock-card service to match the user's setting. */
object UnlockCards {
    fun sync(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, UnlockService::class.java)
        if (Store(app).unlockCards) {
            try {
                ContextCompat.startForegroundService(app, intent)
            } catch (e: Exception) {
                // Android refused a background start (e.g. right after boot on some devices).
                // It is retried the next time the app is opened.
            }
        } else {
            app.stopService(intent)
        }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
}

/**
 * Listens for "phone unlocked" and shows a small word card on top of whatever is on screen.
 *
 * Battery notes: this service is purely event-driven. It does nothing, uses no CPU and holds no
 * wake lock between unlocks. Android only allows listening for unlock events from a registered
 * receiver, which is why it needs a (silent, minimized) ongoing notification.
 */
class UnlockService : Service() {
    private var registered = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            UnlockCard.maybeShow(applicationContext)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
        registered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) {
            unregisterReceiver(unlockReceiver)
            registered = false
        }
        UnlockCard.dismiss()
        super.onDestroy()
    }

    private fun startInForeground() {
        Notifier.ensureServiceChannel(this)
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, Notifier.SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_word)
            .setContentTitle("Word cards on unlock are on")
            .setContentText("Tap to open Wortschatz")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                Notifier.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(Notifier.SERVICE_NOTIFICATION_ID, notification)
        }
    }
}

/** The small overlay card itself, built from plain views (no Compose needed in a service). */
object UnlockCard {
    private const val AUTO_DISMISS_MS = 12_000L
    private const val AUTO_DISMISS_AFTER_REVEAL_MS = 25_000L

    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var windowManager: WindowManager? = null
    private val autoDismiss = Runnable { dismiss() }

    fun maybeShow(context: Context) {
        val app = context.applicationContext
        val store = Store(app)
        if (!store.unlockCards) return
        if (!Settings.canDrawOverlays(app)) return

        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < store.startHour || hour >= store.endHour) return
        if (now - store.lastUnlockCardAt < store.unlockGapMinutes * 60_000L) return

        val word = Picker.next(app, store, now, allowAhead = false) ?: return
        store.lastUnlockCardAt = now
        store.markShown(word.id, now)
        show(app, store, word)
    }

    fun dismiss() {
        handler.removeCallbacks(autoDismiss)
        val view = root
        root = null
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // already gone
            }
        }
    }

    private fun show(context: Context, store: Store, word: Word) {
        dismiss()

        val themed = ContextThemeWrapper(context, android.R.style.Theme_Material_Light)
        val metrics = context.resources.displayMetrics
        fun dp(value: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), metrics).toInt()

        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val background = if (night) 0xFF2B2F36.toInt() else 0xFFFFFFFF.toInt()
        val primaryText = if (night) 0xFFF1F3F5.toInt() else 0xFF1B1F24.toInt()
        val secondaryText = if (night) 0xFFB0B7C0.toInt() else 0xFF5F6770.toInt()
        val accent = if (night) 0xFF9CC2FF.toInt() else 0xFF1F3A5F.toInt()

        fun label(value: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
            val view = TextView(themed)
            view.text = value
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            view.setTextColor(color)
            view.gravity = Gravity.CENTER_HORIZONTAL
            if (bold) view.setTypeface(view.typeface, Typeface.BOLD)
            return view
        }

        fun button(text: String): Button {
            val view = Button(themed)
            view.text = text
            view.setAllCaps(false)
            return view
        }

        val card = LinearLayout(themed)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(20), dp(12), dp(20), dp(16))
        val shape = GradientDrawable()
        shape.setColor(background)
        shape.cornerRadius = dp(20).toFloat()
        card.setBackground(shape)
        card.elevation = dp(8).toFloat()

        // header: level on the left, close on the right
        val header = FrameLayout(themed)
        val levelLabel = label(word.level, 12f, secondaryText)
        levelLabel.gravity = Gravity.START
        header.addView(
            levelLabel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL,
            ),
        )
        val close = label("✕", 18f, secondaryText)
        close.setPadding(dp(12), dp(4), dp(4), dp(4))
        close.setOnClickListener { dismiss() }
        header.addView(
            close,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL,
            ),
        )
        card.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = label(word.de, 28f, primaryText, bold = true)
        title.setPadding(0, dp(4), 0, dp(8))
        card.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // the answer, hidden until the user asks for it
        val details = LinearLayout(themed)
        details.orientation = LinearLayout.VERTICAL
        details.visibility = View.GONE
        details.addView(label(word.en, 20f, accent, bold = true))
        val example = label(word.ex, 16f, primaryText)
        example.setPadding(0, dp(8), 0, 0)
        details.addView(example)
        details.addView(label(word.exEn, 14f, secondaryText))
        card.addView(details, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val reveal = button("Show answer")
        card.addView(reveal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val actions = LinearLayout(themed)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.visibility = View.GONE
        val learning = button("Still learning")
        val knowIt = button("I know it")
        actions.addView(learning, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(knowIt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        reveal.setOnClickListener {
            details.visibility = View.VISIBLE
            reveal.visibility = View.GONE
            actions.visibility = View.VISIBLE
            handler.removeCallbacks(autoDismiss)
            handler.postDelayed(autoDismiss, AUTO_DISMISS_AFTER_REVEAL_MS)
        }
        learning.setOnClickListener {
            store.answer(word.id, false)
            dismiss()
        }
        knowIt.setOnClickListener {
            store.answer(word.id, true)
            dismiss()
        }

        val container = FrameLayout(themed)
        container.setPadding(dp(12), 0, dp(12), 0)
        container.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable and not modal: touches outside the card still reach the app below it.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = dp(24)

        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            manager.addView(container, params)
            windowManager = manager
            root = container
            handler.postDelayed(autoDismiss, AUTO_DISMISS_MS)
        } catch (e: Exception) {
            // Permission revoked or window not allowed right now: skip quietly.
        }
    }
}
