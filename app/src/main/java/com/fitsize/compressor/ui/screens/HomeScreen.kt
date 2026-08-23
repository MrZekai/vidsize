package com.fitsize.compressor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitsize.compressor.R
import com.fitsize.compressor.ui.components.FitsizeBannerAd
import com.fitsize.compressor.ui.theme.FitsizeAccent
import com.fitsize.compressor.ui.theme.FitsizeAccentSoft
import com.fitsize.compressor.ui.theme.FitsizeBlueSoft
import com.fitsize.compressor.ui.theme.FitsizeBorder
import com.fitsize.compressor.ui.theme.FitsizeCard
import com.fitsize.compressor.ui.theme.FitsizeInk
import com.fitsize.compressor.ui.theme.FitsizeMuted
import com.fitsize.compressor.ui.theme.FitsizeSoft
import com.fitsize.compressor.ui.theme.FitsizeSuccess
import com.fitsize.compressor.ui.theme.FitsizeSuccessSoft

@Composable
fun HomeScreen(onVideoSelected: (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) onVideoSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FitsizeSoft)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Fitsize",
                    color = FitsizeInk,
                    fontSize = 30.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Video Compressor",
                    color = FitsizeMuted,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = FitsizeSuccessSoft,
                border = BorderStroke(1.dp, Color(0xFFCFEEDD)),
            ) {
                Text(
                    text = "LOCAL",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = FitsizeSuccess,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        val heroShape = RoundedCornerShape(30.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFF2F0FF),
                            Color(0xFFF5F8FF),
                            Color(0xFFEEF7FF),
                        ),
                    ),
                    shape = heroShape,
                )
                .padding(24.dp),
        ) {
            Column {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, Color.White),
                ) {
                    Text(
                        text = "VIDEO COMPRESSOR",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        color = FitsizeAccent,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.0.sp,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Make your video\nsmaller.",
                    color = FitsizeInk,
                    fontSize = 38.sp,
                    lineHeight = 41.sp,
                    fontWeight = FontWeight.Black,
                )

                Spacer(Modifier.height(11.dp))

                Text(
                    text = "Reduce file size while keeping the quality you need.",
                    color = FitsizeMuted,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                )

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FitsizeAccent,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_video_file),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "SELECT VIDEO",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrustPill(
                text = "On-device",
                modifier = Modifier.weight(1f),
                background = FitsizeAccentSoft,
                foreground = FitsizeAccent,
            )
            TrustPill(
                text = "Fast",
                modifier = Modifier.weight(1f),
                background = FitsizeBlueSoft,
                foreground = Color(0xFF2E77D0),
            )
            TrustPill(
                text = "No watermark",
                modifier = Modifier.weight(1f),
                background = FitsizeSuccessSoft,
                foreground = FitsizeSuccess,
            )
        }

        Spacer(Modifier.height(26.dp))

        Text(
            text = "Advertisement",
            modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
            color = Color(0xFF98A2B3),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
        )

        FitsizeBannerAd(modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(26.dp))

        Text(
            text = "Your activity",
            color = FitsizeInk,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActivityCard(
                modifier = Modifier.weight(1f),
                eyebrow = "RECENT",
                value = "No videos yet",
                supporting = "Compressed videos will appear here.",
                icon = R.drawable.ic_history,
                accent = FitsizeAccent,
                accentSoft = FitsizeAccentSoft,
            )

            ActivityCard(
                modifier = Modifier.weight(1f),
                eyebrow = "TOTAL SAVED",
                value = "0 MB",
                supporting = "Your storage savings will add up here.",
                icon = null,
                accent = FitsizeSuccess,
                accentSoft = FitsizeSuccessSoft,
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun TrustPill(
    text: String,
    modifier: Modifier,
    background: Color,
    foreground: Color,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = background,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = foreground,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ActivityCard(
    modifier: Modifier,
    eyebrow: String,
    value: String,
    supporting: String,
    icon: Int?,
    accent: Color,
    accentSoft: Color,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = FitsizeCard,
        border = BorderStroke(1.dp, FitsizeBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            if (icon != null) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = accentSoft,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier
                            .padding(9.dp)
                            .size(20.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
            } else {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = accentSoft,
                ) {
                    Text(
                        text = "↓",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            Text(
                text = eyebrow,
                color = Color(0xFF98A2B3),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
            )

            Spacer(Modifier.height(5.dp))

            Text(
                text = value,
                color = FitsizeInk,
                fontSize = 18.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = supporting,
                color = FitsizeMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}
