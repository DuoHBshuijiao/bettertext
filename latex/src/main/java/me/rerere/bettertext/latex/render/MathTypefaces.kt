package me.rerere.bettertext.latex.render

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import me.rerere.bettertext.latex.R
import me.rerere.bettertext.latex.layout.GlyphMeasurer
import java.util.concurrent.ConcurrentHashMap

/** Loads the KaTeX fonts bundled as resources and caches them process-wide. */
internal class MathTypefaces private constructor(private val context: Context) {
    private val cache = ConcurrentHashMap<String, Typeface>()

    fun get(font: String): Typeface {
        cache[font]?.let { return it }
        val id = fontResources[font] ?: fontResources.getValue("Main-Regular")
        val typeface = ResourcesCompat.getFont(context, id) ?: Typeface.SERIF
        cache[font] = typeface
        return typeface
    }

    /** Measures glyphs that the metric tables don't cover, using the real fonts (with system fallback). */
    val measurer: GlyphMeasurer = object : GlyphMeasurer {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = MEASURE_SIZE }

        override fun measureWidth(text: String, font: String): Float = synchronized(paint) {
            paint.typeface = get(font)
            paint.measureText(text) / MEASURE_SIZE
        }
    }

    companion object {
        private const val MEASURE_SIZE = 100f

        @Volatile
        private var instance: MathTypefaces? = null

        fun get(context: Context): MathTypefaces =
            instance ?: synchronized(this) {
                instance ?: MathTypefaces(context.applicationContext).also { instance = it }
            }

        private val fontResources = mapOf(
            "AMS-Regular" to R.font.katex_ams_regular,
            "Caligraphic-Regular" to R.font.katex_caligraphic_regular,
            "Caligraphic-Bold" to R.font.katex_caligraphic_bold,
            "Fraktur-Regular" to R.font.katex_fraktur_regular,
            "Fraktur-Bold" to R.font.katex_fraktur_bold,
            "Main-Bold" to R.font.katex_main_bold,
            "Main-BoldItalic" to R.font.katex_main_bolditalic,
            "Main-Italic" to R.font.katex_main_italic,
            "Main-Regular" to R.font.katex_main_regular,
            "Math-BoldItalic" to R.font.katex_math_bolditalic,
            "Math-Italic" to R.font.katex_math_italic,
            "SansSerif-Bold" to R.font.katex_sansserif_bold,
            "SansSerif-Italic" to R.font.katex_sansserif_italic,
            "SansSerif-Regular" to R.font.katex_sansserif_regular,
            "Script-Regular" to R.font.katex_script_regular,
            "Size1-Regular" to R.font.katex_size1_regular,
            "Size2-Regular" to R.font.katex_size2_regular,
            "Size3-Regular" to R.font.katex_size3_regular,
            "Size4-Regular" to R.font.katex_size4_regular,
            "Typewriter-Regular" to R.font.katex_typewriter_regular,
        )
    }
}
