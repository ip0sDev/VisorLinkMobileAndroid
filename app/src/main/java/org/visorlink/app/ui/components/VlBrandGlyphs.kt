package org.visorlink.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Фирменные глифы в единой иконографии настроек (без рамки, вектором, красятся тинтом
 * как material-набор). Имена «ic_*» — чтобы vlGlyphHueOf/illustrationOf в Forge
 * находили оттенок и иллюстрацию по имени, как у material-иконок.
 */

private fun brandGlyph(
    name: String,
    pathData: String,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString(pathData).toNodes(),
    fill = SolidColor(Color.Black),
    stroke = null,
).build()

/** Бумажный самолёт Telegram (путь из ic_telegram.xml, без диска-рамки). */
val IcTelegram: ImageVector by lazy {
    brandGlyph(
        name = "ic_telegram",
        pathData = "M9.78,18.65l0.28,-4.23l7.68,-6.92c0.34,-0.31 -0.07,-0.46 -0.52,-0.19L7.74,13.3l-4.1,-1.28c-0.88,-0.27 -0.89,-0.86 0.2,-1.3l16.03,-6.18c0.73,-0.33 1.43,0.18 1.15,1.3l-2.72,12.81c-0.19,0.91 -0.74,1.13 -1.5,0.71L12.6,17.77l-1.99,1.93c-0.23,0.23 -0.42,0.42 -0.83,0.42v-1.47z",
    )
}

/** Буква G Google (контур simple-icons, без белой плашки). */
val IcGoogle: ImageVector by lazy {
    brandGlyph(
        name = "ic_google",
        pathData = "M12.48 10.92v3.28h7.84c-.24 1.84-.853 3.187-1.787 4.133-1.147 1.147-2.933 2.4-6.053 2.4-4.827 0-8.6-3.893-8.6-8.72s3.773-8.72 8.6-8.72c2.6 0 4.507 1.027 5.907 2.347l2.307-2.307C18.747 1.44 16.133 0 12.48 0 5.867 0 .307 5.387.307 12s5.56 12 12.173 12c3.573 0 6.267-1.173 8.373-3.36 2.16-2.16 2.84-5.213 2.84-7.667 0-.76-.053-1.467-.173-2.053H12.48z",
    )
}

/**
 * «Сигнал»: маячок — точка и две дуги с каждой стороны (скруглённые концы),
 * та же пластика, что у material-rounded глифов настроек.
 */
val IcSignal: ImageVector by lazy {
    ImageVector.Builder(
        name = "ic_signal",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Центральная точка
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
        ) {
            moveTo(14.3f, 12f)
            arcTo(2.3f, 2.3f, 0f, true, true, 9.7f, 12f)
            arcTo(2.3f, 2.3f, 0f, true, true, 14.3f, 12f)
            close()
        }
        // Внутренние дуги (r 5.5)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8.11f, 15.89f)
            arcTo(5.5f, 5.5f, 0f, false, false, 8.11f, 8.11f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(15.89f, 8.11f)
            arcTo(5.5f, 5.5f, 0f, false, true, 15.89f, 15.89f)
        }
        // Внешние дуги (r 8.6)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5.92f, 18.08f)
            arcTo(8.6f, 8.6f, 0f, false, false, 5.92f, 5.92f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(18.08f, 5.92f)
            arcTo(8.6f, 8.6f, 0f, false, true, 18.08f, 18.08f)
        }
    }.build()
}
