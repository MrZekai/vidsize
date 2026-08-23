package com.fitsize.compressor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitsize.compressor.ui.theme.FitsizeColor
import com.fitsize.compressor.ui.theme.FitsizeShape
import com.fitsize.compressor.ui.theme.FitsizeType
import com.fitsize.compressor.ui.theme.Space

/**
 * The one card in the product.
 *
 * White surface, hairline border, and a shadow so soft it reads as depth rather
 * than as a drop shadow. Every panel on every screen uses this, which is why
 * the app looks like one product instead of a pile of default Compose cards.
 */
@Composable
fun FitsizeCard(
    modifier: Modifier = Modifier,
    shape: Shape = FitsizeShape.extraLarge,
    color: Color = FitsizeColor.Surface,
    border: Color = FitsizeColor.Border,
    elevation: Dp = 10.dp,
    contentPadding: Dp = Space.lg,
    onClick: (() -> Unit)? = null,
    clickEnabled: Boolean = true,
    role: Role = Role.Button,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = FitsizeColor.Shadow,
            spotColor = FitsizeColor.Shadow,
        ),
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            // The click lives *inside* the Surface so the ripple is clipped to
            // the card's corner radius instead of flashing a square.
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(enabled = clickEnabled, role = role, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Uppercase micro-label above a headline or inside a hero panel. */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FitsizeColor.Faint,
) {
    Text(
        text = text,
        modifier = modifier,
        style = FitsizeType.eyebrow,
        color = color,
    )
}

/**
 * Tinted pill. Used for the hero label and the trust row.
 * Text wraps rather than truncates so long translations stay readable.
 */
@Composable
fun TintedPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    background: Color = FitsizeColor.IndigoSoft,
    border: Color = FitsizeColor.IndigoBorder,
    foreground: Color = FitsizeColor.Indigo,
    uppercase: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = FitsizeShape.chip,
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = text,
                style = if (uppercase) FitsizeType.eyebrow else FitsizeType.caption,
                color = foreground,
            )
        }
    }
}

/**
 * Section heading with an optional trailing action (e.g. "See all").
 * Baseline-aligned so the action never sits visually below the title.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = FitsizeType.section,
            color = FitsizeColor.Ink,
        )
        action()
    }
}

/**
 * One cell of the "resolution / duration / size" strip on the compression
 * screen. Label above, value below, left aligned, no dividers — the spacing
 * does the separating.
 */
@Composable
fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = FitsizeType.micro,
            color = FitsizeColor.Faint,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = FitsizeType.cardTitle,
            color = FitsizeColor.Ink,
        )
    }
}

/** Full-bleed hairline used above the anchored ad slot. */
@Composable
fun HairLine(modifier: Modifier = Modifier, color: Color = FitsizeColor.Border) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color,
    ) {
        Spacer(Modifier.height(1.dp))
    }
}
