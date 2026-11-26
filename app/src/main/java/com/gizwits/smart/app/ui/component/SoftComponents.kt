package com.gizwits.smart.app.ui.component

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gizwits.smart.app.R
import kotlin.math.ceil

/** CSS-like shadow, cached per size/style at logical-pixel resolution.
 * Software bitmap canvas supports blur on API 26 without changing hardware acceleration. */
fun Modifier.softShadow(radius: Dp = 19.dp, blur: Dp = 16.dp, offset: Dp = 5.dp,
                        color: Color = Color(0x08504124)): Modifier = drawWithCache {
    val scale = density
    val padding = ceil(blur.value * 2 + kotlin.math.abs(offset.value)).toInt()
    val width = ceil(size.width / scale).toInt().coerceAtLeast(1)
    val height = ceil(size.height / scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width + padding * 2, height + padding * 2, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(blur.value, BlurMaskFilter.Blur.NORMAL)
    }
    val canvas = android.graphics.Canvas(bitmap)
    val rect = RectF(padding.toFloat(), padding + offset.value, (padding + width).toFloat(), padding + height + offset.value)
    canvas.drawRoundRect(rect, radius.value, radius.value, paint)
    val target = RectF(-padding * scale, -padding * scale, (width + padding) * scale, (height + padding) * scale)
    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    onDrawBehind { drawIntoCanvas { it.nativeCanvas.drawBitmap(bitmap, null, target, bitmapPaint) } }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, featured: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    val shape = RoundedCornerShape(if (featured) 23.dp else 19.dp)
    val surface = MaterialTheme.colorScheme.surface
    val brush = if (featured) Brush.linearGradient(if (dark) listOf(Color(0xFF3B3428), Color(0xFF2D2A24))
        else listOf(Color(0xFFFFFAF0), Color(0xFFEAE5DA))) else Brush.linearGradient(listOf(surface, surface))
    Surface(modifier.fillMaxWidth().softShadow(radius = if (featured) 23.dp else 19.dp,
        blur = if (featured) 20.dp else 16.dp, offset = if (featured) 7.dp else 5.dp,
        color = if (dark) Color(0x30000000) else if (featured) Color(0x10504124) else Color(0x08504124)),
        shape = shape, color = Color.Transparent, tonalElevation = 0.dp, shadowElevation = 0.dp,
        border = BorderStroke(1.dp, if (dark) Color(0xFF423C32) else Color(0xFFFFFDFA))) {
        Column(Modifier.background(brush).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun SoftHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SoftAction(text: String, onClick: () -> Unit, enabled: Boolean = true, secondary: Boolean = false, loading: Boolean = false) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    val shadow = if (!enabled) Color.Transparent else if (dark) Color(0x24000000)
        else if (secondary) Color(0x06504124) else Color(0x20B37719)
    val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .softShadow(15.dp, if (secondary) 8.dp else 10.dp, if (secondary) 3.dp else 4.dp, shadow)
    Button(onClick, modifier, enabled = enabled && !loading, shape = RoundedCornerShape(15.dp),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        border = BorderStroke(1.dp, if (!enabled) Color.Transparent else if (secondary)
            MaterialTheme.colorScheme.outlineVariant else Color(0xFFF4AB29)),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
            contentColor = if (secondary) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SoftTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (onBack == null) 22.dp else 8.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_return_24), "返回", Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Gizwits / Developer demo", fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                letterSpacing = .6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SoftSectionLabel(text: String) {
    Text(text, Modifier.padding(start = 2.dp, top = 2.dp), style = MaterialTheme.typography.titleSmall)
}

@Composable
fun SoftSettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onClick != null) Text("›", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp)
    }
}

@Composable
fun SoftMessage(text: String, error: Boolean = false) {
    Surface(shape = RoundedCornerShape(13.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
