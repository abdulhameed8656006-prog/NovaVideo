package com.abdulhameed.novavideo

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

// Clean light theme: soft grey-white background, white cards, blue -> violet accent.
object Style {
    val BG = 0xFFF6F7FB.toInt()
    val CARD = 0xFFFFFFFF.toInt()
    val BORDER = 0xFFE4E7F0.toInt()
    val FIELD = 0xFFF3F4F8.toInt()
    val TEXT = 0xFF14142B.toInt()
    val DIM = 0xFF6B7280.toInt()
    val ACCENT1 = 0xFF3B82F6.toInt()
    val ACCENT2 = 0xFF7C3AED.toInt()
    val SOFT = 0xFFEDE9FE.toInt()
    val ERROR = 0xFFDC2626.toInt()

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun fill(c: Context, color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(c, radiusDp).toFloat()
        return g
    }

    fun gradient(c: Context, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(ACCENT1, ACCENT2))
        g.cornerRadius = dp(c, radiusDp).toFloat()
        return g
    }

    fun cardBackground(c: Context): GradientDrawable {
        val g = fill(c, CARD, 20)
        g.setStroke(dp(c, 1), BORDER)
        return g
    }

    fun cardBox(c: Context): LinearLayout {
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        box.background = cardBackground(c)
        box.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18))
        return box
    }

    fun text(c: Context, s: String, size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    fun label(c: Context, s: String): TextView {
        val t = text(c, s.uppercase(), 12f, DIM, true)
        t.letterSpacing = 0.08f
        t.setPadding(0, dp(c, 14), 0, dp(c, 8))
        return t
    }

    fun primaryButton(c: Context, s: String, onClick: () -> Unit): Button {
        val b = Button(c)
        b.text = s
        b.isAllCaps = false
        b.textSize = 17f
        b.typeface = Typeface.DEFAULT_BOLD
        b.stateListAnimator = null
        b.minHeight = dp(c, 54)
        b.setTextColor(Color.WHITE)
        b.background = gradient(c, 16)
        b.setOnClickListener { onClick() }
        return b
    }

    fun softButton(c: Context, s: String, onClick: () -> Unit): Button {
        val b = Button(c)
        b.text = s
        b.isAllCaps = false
        b.textSize = 16f
        b.typeface = Typeface.DEFAULT_BOLD
        b.stateListAnimator = null
        b.minHeight = dp(c, 50)
        b.setTextColor(ACCENT2)
        b.background = fill(c, SOFT, 16)
        b.setOnClickListener { onClick() }
        return b
    }
}
