package com.kartikeya.vcradio.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartikeya.vcradio.ui.theme.RadioAmber
import com.kartikeya.vcradio.R
import com.kartikeya.vcradio.ui.theme.RadioBlack
import com.kartikeya.vcradio.ui.theme.RadioDim
import com.kartikeya.vcradio.ui.theme.RadioInk

@Composable
fun RadioScreen(
    state: RadioUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePower: () -> Unit,
) {
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    val status = when {
        state.error != null -> "SIGNAL LOST"
        state.isPlaying -> "ON AIR"
        state.powered -> "PAUSED"
        else -> "OFF"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RadioBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(onNext, onPrevious) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                    onDragEnd = {
                        when {
                            horizontalDrag > 90f -> onPrevious()
                            horizontalDrag < -90f -> onNext()
                        }
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f },
                )
            }
            .padding(horizontal = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1.15f))
            StationLogo(state.stationId, state.stationName)
            Spacer(Modifier.height(18.dp))
            AnimatedContent(targetState = state.stationName) { name ->
                Text(
                    text = name.uppercase(),
                    color = RadioInk,
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = status,
                color = if (status == "ON AIR") RadioAmber else RadioDim,
                fontSize = 12.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold,
            )
            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(state.error, color = RadioDim, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(0.85f))
            TunerScale(active = state.isPlaying)
            Spacer(Modifier.weight(0.85f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioControl("Previous station", ControlIcon.Previous, enabled = state.connected, onClick = onPrevious)
                RadioControl(
                    if (state.powered) "Turn radio off" else "Turn radio on",
                    ControlIcon.Power,
                    emphasized = true,
                    enabled = state.connected,
                    onClick = onTogglePower,
                )
                RadioControl("Next station", ControlIcon.Next, enabled = state.connected, onClick = onNext)
            }
            Spacer(Modifier.weight(0.65f))
        }
    }
}

@Composable
private fun StationLogo(stationId: String?, stationName: String) {
    val resource = when {
        stationId?.contains("wild") == true -> R.drawable.wildstyle
        stationId?.contains("flash") == true -> R.drawable.flash
        stationId?.contains("kchat") == true -> R.drawable.kchat
        stationId?.contains("fever") == true -> R.drawable.fever
        stationId?.contains("vrock") == true -> R.drawable.vrock
        stationId?.contains("vcpr") == true -> R.drawable.vcpr
        stationId?.contains("espant") == true -> R.drawable.espantoso
        stationId?.contains("emotion") == true -> R.drawable.emotion
        stationId?.contains("wave") == true -> R.drawable.wave103
        else -> null
    }
    if (resource == null) {
        Spacer(Modifier.height(56.dp))
    } else {
        Image(
            painter = painterResource(resource),
            contentDescription = "$stationName station logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(76.dp),
        )
    }
}

@Composable
private fun TunerScale(active: Boolean) {
    val color = if (active) RadioAmber else RadioDim
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics { contentDescription = "Radio tuner scale" },
    ) {
        val baseline = size.height * .58f
        drawLine(RadioDim.copy(alpha = .38f), Offset(0f, baseline), Offset(size.width, baseline), 1f)
        repeat(17) { index ->
            val x = size.width * index / 16f
            val tall = index % 4 == 0
            drawLine(
                RadioDim.copy(alpha = if (tall) .7f else .35f),
                Offset(x, baseline - if (tall) 15.dp.toPx() else 8.dp.toPx()),
                Offset(x, baseline + 4.dp.toPx()),
                1f,
            )
        }
        val center = size.width / 2
        drawLine(color, Offset(center, baseline - 22.dp.toPx()), Offset(center, baseline + 8.dp.toPx()), 2.5f, StrokeCap.Round)
        drawCircle(color, 3.dp.toPx(), Offset(center, baseline - 22.dp.toPx()))
    }
}

private enum class ControlIcon { Previous, Next, Power }

@Composable
private fun RadioControl(
    description: String,
    icon: ControlIcon,
    emphasized: Boolean = false,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(if (emphasized) 76.dp else 64.dp)
            .semantics { contentDescription = description; role = Role.Button },
        colors = IconButtonDefaults.iconButtonColors(contentColor = if (emphasized) RadioAmber else RadioInk),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        color = if (emphasized) RadioAmber.copy(alpha = .20f) else Color.Transparent,
                        radius = size.minDimension / 2,
                    )
                },
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val color = if (emphasized) RadioAmber else RadioInk
            when (icon) {
                ControlIcon.Previous -> {
                    drawLine(color, Offset(center.x + 11.dp.toPx(), center.y - 14.dp.toPx()), Offset(center.x - 9.dp.toPx(), center.y), 2.5f, StrokeCap.Round)
                    drawLine(color, Offset(center.x - 9.dp.toPx(), center.y), Offset(center.x + 11.dp.toPx(), center.y + 14.dp.toPx()), 2.5f, StrokeCap.Round)
                    drawLine(color, Offset(center.x - 15.dp.toPx(), center.y - 15.dp.toPx()), Offset(center.x - 15.dp.toPx(), center.y + 15.dp.toPx()), 2.5f, StrokeCap.Round)
                }
                ControlIcon.Next -> {
                    drawLine(color, Offset(center.x - 11.dp.toPx(), center.y - 14.dp.toPx()), Offset(center.x + 9.dp.toPx(), center.y), 2.5f, StrokeCap.Round)
                    drawLine(color, Offset(center.x + 9.dp.toPx(), center.y), Offset(center.x - 11.dp.toPx(), center.y + 14.dp.toPx()), 2.5f, StrokeCap.Round)
                    drawLine(color, Offset(center.x + 15.dp.toPx(), center.y - 15.dp.toPx()), Offset(center.x + 15.dp.toPx(), center.y + 15.dp.toPx()), 2.5f, StrokeCap.Round)
                }
                ControlIcon.Power -> {
                    drawArc(color, -50f, 280f, false, topLeft = Offset(center.x - 17.dp.toPx(), center.y - 17.dp.toPx()), size = Size(34.dp.toPx(), 34.dp.toPx()), style = Stroke(2.7f))
                    drawLine(color, Offset(center.x, center.y - 20.dp.toPx()), Offset(center.x, center.y - 2.dp.toPx()), 2.7f, StrokeCap.Round)
                }
            }
        }
    }
}
