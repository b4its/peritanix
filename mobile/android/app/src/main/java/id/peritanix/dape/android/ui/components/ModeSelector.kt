package id.peritanix.dape.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.mode.AppMode

/**
 * Compact mode chip shown in the app bar: shows the active mode and toggles
 * Simulasi ⇄ Riil on tap.
 */
@Composable
fun ModeChip(mode: AppMode, onToggle: () -> Unit) {
    val isReal = mode.isReal
    val container = if (isReal) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (isReal) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = AppIcons.SwapHoriz,
                contentDescription = "Ganti mode",
                tint = content,
                modifier = Modifier.size(14.dp)
            )
            Text(
                if (isReal) "Riil" else "Simulasi",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Persistent banner clarifying which mode is active, with a one-line
 * explanation of what that mode means for the data the user is seeing.
 */
@Composable
fun ModeBanner(mode: AppMode, onToggle: () -> Unit) {
    val isReal = mode.isReal
    val bg = if (isReal) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val fg = if (isReal) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(8.dp).background(
                    if (isReal) Color(0xFF2E7D32) else Color(0xFFEF6C00), CircleShape
                )
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "${mode.label} · ${mode.tagline}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = fg
                )
                Text(mode.description, fontSize = 10.sp, color = fg.copy(alpha = 0.8f), maxLines = 2)
            }
            TextButton(onClick = onToggle) {
                Text(if (isReal) "Ke Simulasi" else "Ke Riil", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
            }
        }
    }
}
