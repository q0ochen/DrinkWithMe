package com.drinkwithme.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Selbst gezeichnete Linien-Symbole (eigenes Werk, CC0 – wie alle Grafiken der App; keine Icon-Sammlung).
 * 24 × 24, Strichstärke 2, runde Enden; die Farbe kommt beim Zeichnen über `tint`.
 */
object AppIcons {
    val Bar: ImageVector by lazy {
        icon("bar") {
            moveTo(6f, 3f); lineTo(18f, 3f); lineTo(16.5f, 21f); lineTo(7.5f, 21f); close()
            moveTo(6.6f, 9f); lineTo(17.4f, 9f)
        }
    }

    val History: ImageVector by lazy {
        icon("history") {
            moveTo(3f, 21f); lineTo(21f, 21f)
            moveTo(7f, 18f); lineTo(7f, 13f)
            moveTo(12f, 18f); lineTo(12f, 6f)
            moveTo(17f, 18f); lineTo(17f, 10f)
        }
    }

    val Friends: ImageVector by lazy {
        icon("friends") {
            circle(9f, 8f, 3.2f)
            moveTo(2.5f, 20f); curveTo(2.5f, 14.8f, 15.5f, 14.8f, 15.5f, 20f)
            circle(17.2f, 8.8f, 2.4f)
            moveTo(16.2f, 14.2f); curveTo(19.2f, 13.9f, 21.5f, 15.6f, 21.5f, 19f)
        }
    }

    val Settings: ImageVector by lazy {
        icon("settings") {
            moveTo(3f, 7f); lineTo(7f, 7f); moveTo(11f, 7f); lineTo(21f, 7f)
            circle(9f, 7f, 2f)
            moveTo(3f, 17f); lineTo(13f, 17f); moveTo(17f, 17f); lineTo(21f, 17f)
            circle(15f, 17f, 2f)
        }
    }

    val Mic: ImageVector by lazy {
        icon("mic") {
            moveTo(9f, 6f); arcToRelative(3f, 3f, 0f, false, true, 6f, 0f); lineTo(15f, 11f)
            arcToRelative(3f, 3f, 0f, false, true, -6f, 0f); close()
            moveTo(5f, 11f); arcToRelative(7f, 7f, 0f, false, false, 14f, 0f)
            moveTo(12f, 18f); lineTo(12f, 21f)
            moveTo(8.5f, 21f); lineTo(15.5f, 21f)
        }
    }

    val Undo: ImageVector by lazy {
        icon("undo") {
            moveTo(9f, 4f); lineTo(4f, 9f); lineTo(9f, 14f)
            moveTo(4f, 9f); lineTo(14f, 9f)
            curveTo(17.3f, 9f, 20f, 11.7f, 20f, 15f); curveTo(20f, 18.3f, 17.3f, 21f, 14f, 21f); lineTo(10f, 21f)
        }
    }

    val Speaker: ImageVector by lazy {
        icon("speaker") {
            moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 5f); lineTo(12f, 19f); lineTo(7f, 15f); lineTo(3f, 15f); close()
            moveTo(15.5f, 9f); curveTo(17f, 10.5f, 17f, 13.5f, 15.5f, 15f)
            moveTo(18.5f, 6f); curveTo(21.5f, 9f, 21.5f, 15f, 18.5f, 18f)
        }
    }

    val Add: ImageVector by lazy {
        icon("add") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    val Close: ImageVector by lazy {
        icon("close") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Check: ImageVector by lazy {
        icon("check") {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
        }
    }

    val Edit: ImageVector by lazy {
        icon("edit") {
            moveTo(4f, 20f); lineTo(5f, 15f); lineTo(15f, 5f); lineTo(19f, 9f); lineTo(9f, 19f); close()
            moveTo(13f, 7f); lineTo(17f, 11f)
        }
    }

    val Delete: ImageVector by lazy {
        icon("delete") {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(10f, 4f); lineTo(14f, 4f)
            moveTo(6.5f, 7f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 7f)
            moveTo(10f, 11f); lineTo(10f, 16f); moveTo(14f, 11f); lineTo(14f, 16f)
        }
    }

    val Share: ImageVector by lazy {
        icon("share") {
            circle(18f, 5.5f, 2.5f)
            circle(6f, 12f, 2.5f)
            circle(18f, 18.5f, 2.5f)
            moveTo(8.2f, 10.8f); lineTo(15.8f, 6.7f)
            moveTo(8.2f, 13.2f); lineTo(15.8f, 17.3f)
        }
    }

    val More: ImageVector by lazy {
        icon("more", strokeWidth = 3f) {
            moveTo(12f, 5f); lineTo(12f, 5.01f)
            moveTo(12f, 12f); lineTo(12f, 12.01f)
            moveTo(12f, 19f); lineTo(12f, 19.01f)
        }
    }

    val Back: ImageVector by lazy {
        icon("back") {
            moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
        }
    }

    val Refresh: ImageVector by lazy {
        icon("refresh") {
            moveTo(20f, 12f); arcToRelative(8f, 8f, 0f, true, true, -2.34f, -5.66f)
            moveTo(20f, 4f); lineTo(20f, 9f); lineTo(15f, 9f)
        }
    }

    val Up: ImageVector by lazy {
        icon("up") {
            moveTo(12f, 19f); lineTo(12f, 5f)
            moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
        }
    }

    val Down: ImageVector by lazy {
        icon("down") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f)
        }
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    private fun icon(name: String, strokeWidth: Float = 2f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()
}
