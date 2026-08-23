package com.fitsize.compressor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitsize.compressor.ui.theme.FitsizeColor
import com.fitsize.compressor.ui.theme.FitsizeShape
import com.fitsize.compressor.ui.theme.FitsizeType
import com.fitsize.compressor.ui.theme.Sizes
import com.fitsize.compressor.ui.theme.Space

/**
 * The single primary action of a screen.
 *
 * This is the only place in the product where the brand gradient appears, and
 * the only place with a coloured shadow. Keeping both exclusive to this control
 * is what makes the screen read as "one clear action" instead of a toolbox.
 *
 * The height is a *minimum*, not a fixed value, so the control grows with the
 * system font scale rather than clipping its label.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Int? = null,
    trailingIcon: Int? = null,
) {
    val shape = FitsizeShape.medium
    Box(
        modifier = modifier
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = FitsizeColor.Indigo.copy(alpha = 0.28f),
                spotColor = FitsizeColor.Indigo.copy(alpha = 0.34f),
            )
            .clip(shape)
            .background(
                brush = if (enabled) {
                    FitsizeColor.PrimaryGradient
                } else {
                    FitsizeColor.PrimaryGradientDisabled
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = Sizes.primaryButtonMinHeight)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterHorizontally),
        ) {
            if (leadingIcon != null) {
                Icon(
                    painter = painterResource(leadingIcon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(Sizes.icon),
                )
            }
            Text(
                text = text,
                style = FitsizeType.button,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (trailingIcon != null) {
                Icon(
                    painter = painterResource(trailingIcon),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(Sizes.iconSmall),
                )
            }
        }
    }
}

/**
 * Supporting action: white surface, hairline border, no fill, no shadow.
 * Visually subordinate to [PrimaryButton] at a glance.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Int? = null,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = FitsizeShape.medium,
        color = FitsizeColor.Surface,
        border = BorderStroke(1.dp, FitsizeColor.BorderStrong),
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = Sizes.secondaryButtonMinHeight)
                .padding(horizontal = Space.lg, vertical = Space.sm),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterHorizontally),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        painter = painterResource(leadingIcon),
                        contentDescription = null,
                        tint = FitsizeColor.Ink,
                        modifier = Modifier.size(Sizes.iconSmall),
                    )
                }
                Text(
                    text = text,
                    style = FitsizeType.button,
                    color = FitsizeColor.Ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Lowest-emphasis action. Used for "Compress another video" and similar
 * third-level choices where a bordered button would compete for attention.
 */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = FitsizeColor.Indigo,
) {
    Box(
        modifier = modifier
            .clip(FitsizeShape.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = Space.md, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = FitsizeType.button,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Circular icon action used in app bars (settings, back, close).
 * 44dp keeps it above the 48dp-with-padding touch-target guidance while still
 * looking light on a minimal screen.
 */
@Composable
fun IconAction(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = FitsizeColor.InkSoft,
    background: Color = FitsizeColor.Surface,
    border: Color = FitsizeColor.Border,
) {
    Surface(
        modifier = modifier
            .size(Sizes.iconButton)
            .clickable(role = Role.Button, onClick = onClick),
        shape = FitsizeShape.chip,
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(Sizes.icon),
            )
        }
    }
}
