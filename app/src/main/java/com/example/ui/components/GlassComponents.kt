package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF131826), Color(0xFF090C15)),
                    center = androidx.compose.ui.geometry.Offset(x = 0f, y = 0f),
                    radius = 3000f
                )
            )
    ) {
        content()
    }
}

@Composable
fun GlassSectionCard(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = Color(0xBF181E2E),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title, 
                    color = Color.White, 
                    fontSize = 12.sp, 
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                trailing?.invoke()
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    var showPassword by remember { mutableStateOf(!isPassword) }
    val clipboard = LocalClipboardManager.current
    
    Column(modifier = modifier) {
        Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Surface(
            color = Color(0xFF0D111C),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2A324B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    cursorBrush = Brush.verticalGradient(listOf(Color.Cyan, Color.Cyan))
                )
                if (isPassword) {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (trailingIcon != null) {
                    trailingIcon()
                } else {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PillSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                color = if (isSelected) Color(0x336366F1) else Color.Transparent,
                shape = CircleShape,
                border = if (isSelected) {
                    BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8))))
                } else {
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label, 
                        color = if (isSelected) Color.White else Color.Gray, 
                        fontSize = 10.sp, 
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlowingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Box(contentAlignment = Alignment.TopCenter) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                activeTrackColor = Color(0xFF38BDF8),
                inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                thumbColor = Color.White
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .shadow(8.dp, CircleShape, ambientColor = Color.Cyan, spotColor = Color.Cyan)
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color(0xFF38BDF8), CircleShape)
                )
            }
        )
    }
}

@Composable
fun GlowingToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(if (checked) 20.dp else 0.dp)
    val background by animateColorAsState(if (checked) Color(0xFF6366F1) else Color(0xFF2A324B))
    
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(background)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(20.dp)
                .shadow(if (checked) 6.dp else 0.dp, CircleShape, ambientColor = Color(0xFFA855F7), spotColor = Color(0xFFA855F7))
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun GlassDropdown(label: String, value: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box {
            Surface(
                onClick = { expanded = true },
                color = Color(0xFF0D111C),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF2A324B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(value, color = Color.White, fontSize = 13.sp)
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.White.copy(alpha = 0.5f))
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF1E2330))
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color.White) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
@Composable
fun FeatureToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        GlowingToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}
