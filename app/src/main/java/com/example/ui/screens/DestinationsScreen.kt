package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.PlatformEndpoint
import com.example.core.repository.DestinationsRepository
import com.example.ui.MainViewModel
import com.example.ui.components.GlowingToggle
import com.example.ui.components.SettingsBackground
import kotlinx.coroutines.launch

sealed class TestStatus {
    data object Idle : TestStatus()
    data object Testing : TestStatus()
    data class Success(val message: String) : TestStatus()
    data class Error(val message: String) : TestStatus()
}

@Composable
fun DestinationsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showAddCustomDialog by remember { mutableStateOf(false) }

    // Split pre-configured vs custom destinations
    val majorEndpoints = uiState.endpoints.filter { !it.isCustom }
    val customEndpoints = uiState.endpoints.filterIsInstance<PlatformEndpoint.CustomRTMP>()

    val configuredCount = uiState.endpoints.count { it.streamKey.isNotBlank() }
    val activeCount = uiState.endpoints.count { it.isEnabled && it.streamKey.isNotBlank() }

    SettingsBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "TARGETS",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 2.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (activeCount > 0) Color(0xFF10B981) else Color(0xFFF59E0B),
                                            CircleShape
                                        )
                                )
                                Text(
                                    if (activeCount > 0) "$activeCount ACTIVE" else "STANDBY • NO TARGET",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (activeCount > 0) Color(0xFF34D399) else Color(0xFFFBBF24),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        // Quick enable / reset chip
                        Surface(
                            onClick = {
                                val anyActive = uiState.endpoints.any { it.isEnabled }
                                uiState.endpoints.forEach { ep ->
                                    viewModel.toggleDestination(context, ep.id, !anyActive)
                                }
                                Toast.makeText(
                                    context,
                                    if (anyActive) "All destinations disabled" else "All destinations enabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            color = Color(0x336366F1),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    if (activeCount > 0) "MUTE ALL" else "ENABLE ALL",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Summary Status Card
                    Surface(
                        color = Color(0x99131A2A),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "READINESS",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (activeCount > 0) "Ready ($activeCount active)"
                                    else "Key Required",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    color = Color(0x3310B981),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        "$configuredCount Configured",
                                        color = Color(0xFF34D399),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    color = Color(0x336366F1),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        "$activeCount Live",
                                        color = Color(0xFFA5B4FC),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 1: Pre-configured Major Platforms
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MAJOR PLATFORMS",
                        color = Color(0xFF818CF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        "PRE-CONFIGURED RTMP/RTMPS",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(majorEndpoints, key = { it.id }) { endpoint ->
                PlatformExpandableCard(
                    endpoint = endpoint,
                    onToggle = { isEnabled ->
                        viewModel.toggleDestination(context, endpoint.id, isEnabled)
                        if (isEnabled && endpoint.streamKey.isBlank()) {
                            Toast.makeText(context, "${endpoint.name}: Enter stream key to go live", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSave = { updatedUrl, updatedKey ->
                        val updated = when (endpoint) {
                            is PlatformEndpoint.YouTube -> endpoint.copy(url = updatedUrl, streamKey = updatedKey)
                            is PlatformEndpoint.Facebook -> endpoint.copy(url = updatedUrl, streamKey = updatedKey)
                            is PlatformEndpoint.TikTok -> endpoint.copy(url = updatedUrl, streamKey = updatedKey)
                            is PlatformEndpoint.Twitch -> endpoint.copy(url = updatedUrl, streamKey = updatedKey)
                            is PlatformEndpoint.Twitter -> endpoint.copy(url = updatedUrl, streamKey = updatedKey)
                            is PlatformEndpoint.Instagram -> endpoint.copy(url = updatedUrl, streamKey = updatedKey)
                            is PlatformEndpoint.Kick -> endpoint.copy(url = updatedUrl, streamKey = updatedKey)
                            is PlatformEndpoint.Telegram -> endpoint.copy(url = updatedUrl, streamKey = updatedKey)
                            is PlatformEndpoint.CustomRTMP -> endpoint.copy(url = updatedUrl, streamKey = updatedKey)
                        }
                        viewModel.saveDestination(context, updated)
                        Toast.makeText(context, "${endpoint.name} config saved!", Toast.LENGTH_SHORT).show()
                    },
                    onReset = {
                        viewModel.resetDestination(context, endpoint.id)
                        Toast.makeText(context, "${endpoint.name} restored to default", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Section 2: Custom RTMP Destinations Manager
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "CUSTOM RTMP SERVERS",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            "UNLIMITED CUSTOM ENDPOINTS & RELAYS",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilledTonalButton(
                        onClick = { showAddCustomDialog = !showAddCustomDialog },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0x330284C7),
                            contentColor = Color(0xFF38BDF8)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            if (showAddCustomDialog) Icons.Default.Close else Icons.Default.Add,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (showAddCustomDialog) "CANCEL" else "ADD SERVER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Inline Add Custom Server Card
            if (showAddCustomDialog) {
                item {
                    AddCustomServerForm(
                        onAdd = { name, url, key ->
                            viewModel.addCustomDestination(context, name, url, key)
                            showAddCustomDialog = false
                            Toast.makeText(context, "Custom server '$name' added!", Toast.LENGTH_SHORT).show()
                        },
                        onCancel = { showAddCustomDialog = false }
                    )
                }
            }

            // List of Custom Servers
            if (customEndpoints.isEmpty() && !showAddCustomDialog) {
                item {
                    Surface(
                        color = Color(0x40131B2E),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Dns,
                                null,
                                tint = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No Custom Servers Added",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Add custom RTMP, RTMPS, or SRT relays for Telegram, private servers, or CDN ingests.",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                            )
                        }
                    }
                }
            } else {
                items(customEndpoints, key = { it.id }) { custom ->
                    CustomServerExpandableCard(
                        custom = custom,
                        onToggle = { isEnabled ->
                            viewModel.toggleDestination(context, custom.id, isEnabled)
                        },
                        onSave = { updatedName, updatedUrl, updatedKey ->
                            val updated = custom.copy(
                                name = updatedName.trim(),
                                url = updatedUrl.trim(),
                                streamKey = updatedKey.trim()
                            )
                            viewModel.saveDestination(context, updated)
                            Toast.makeText(context, "'${updated.name}' saved!", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            viewModel.deleteCustomDestination(context, custom.id)
                            Toast.makeText(context, "'${custom.name}' deleted", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// PRE-CONFIGURED PLATFORM CARD
// -------------------------------------------------------------

@Composable
fun PlatformExpandableCard(
    endpoint: PlatformEndpoint,
    onToggle: (Boolean) -> Unit,
    onSave: (url: String, key: String) -> Unit,
    onReset: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var urlText by remember(endpoint.url) { mutableStateOf(endpoint.url) }
    var keyText by remember(endpoint.streamKey) { mutableStateOf(endpoint.streamKey) }
    var testStatus by remember { mutableStateOf<TestStatus>(TestStatus.Idle) }

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val meta = getPlatformMeta(endpoint.id)
    val isConfigured = keyText.isNotBlank()

    Surface(
        color = Color(0xCC131A2B),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (endpoint.isEnabled && isConfigured) meta.accentColor.copy(alpha = 0.4f)
            else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row (Clickable to expand)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Platform Badge & Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Brand Icon
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(8.dp, CircleShape, ambientColor = meta.accentColor, spotColor = meta.accentColor)
                            .background(meta.accentColor.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, meta.accentColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            meta.icon,
                            contentDescription = endpoint.name,
                            tint = meta.accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                endpoint.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Status Badge: Configured vs Not Set
                            Surface(
                                color = if (isConfigured) Color(0x2610B981) else Color(0x269CA3AF),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isConfigured) Color(0xFF10B981).copy(alpha = 0.5f)
                                    else Color(0xFF6B7280).copy(alpha = 0.4f)
                                )
                            ) {
                                Text(
                                    if (isConfigured) "Configured" else "Not Set",
                                    color = if (isConfigured) Color(0xFF34D399) else Color(0xFF9CA3AF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (endpoint.viewerCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0x3338BDF8),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(Icons.Default.Person, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(10.dp))
                                        Text(
                                            "${endpoint.viewerCount} Viewers",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Subtitle preview
                        Text(
                            endpoint.url.removePrefix("rtmp://").removePrefix("rtmps://").substringBefore("/"),
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                // Controls on the right: Glowing Toggle + Expand Chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlowingToggle(
                        checked = endpoint.isEnabled,
                        onCheckedChange = onToggle
                    )

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }
            }

            // Expandable Body
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // 1. Dedicated Server / Ingest URL
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "SERVER / INGEST URL",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            if (urlText != endpoint.defaultUrl) {
                                Text(
                                    "RESTORE DEFAULT",
                                    color = meta.accentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { urlText = endpoint.defaultUrl }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        StreamInputField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            placeholder = endpoint.defaultUrl,
                            trailingAction = {
                                IconButton(onClick = { clipboard.setText(AnnotatedString(urlText)) }) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        "Copy URL",
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }

                    // 2. Dedicated Stream Key
                    Column {
                        var showKey by remember { mutableStateOf(false) }
                        Text(
                            "STREAM KEY",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StreamInputField(
                            value = keyText,
                            onValueChange = { keyText = it },
                            placeholder = "Paste your ${endpoint.name} Stream Key",
                            isPassword = !showKey,
                            trailingAction = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Show / Hide Key
                                    IconButton(onClick = { showKey = !showKey }) {
                                        Icon(
                                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            "Toggle visibility",
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Instant Clipboard Paste button
                                    Surface(
                                        onClick = {
                                            val clip = clipboard.getText()?.text
                                            if (!clip.isNullOrBlank()) {
                                                keyText = clip.trim()
                                                Toast.makeText(context, "Stream Key pasted!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        color = Color(0x336366F1),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text(
                                            "PASTE",
                                            color = Color(0xFFA5B4FC),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }

                    // Test Connection Result Badge (if tested)
                    if (testStatus !is TestStatus.Idle) {
                        ConnectionStatusBanner(status = testStatus)
                    }

                    // 3. Action Row: Save Config, Reset, Test Connection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Save Config Button
                        Button(
                            onClick = {
                                onSave(urlText, keyText)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = meta.accentColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SAVE CONFIG", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }

                        // Test Connection Button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    testStatus = TestStatus.Testing
                                    val (success, message) = DestinationsRepository.testEndpointConnection(urlText)
                                    testStatus = if (success) TestStatus.Success(message) else TestStatus.Error(message)
                                }
                            },
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1.2f),
                            enabled = testStatus !is TestStatus.Testing
                        ) {
                            if (testStatus is TestStatus.Testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("TESTING...", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Speed, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("TEST PING", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Clear / Reset Button
                        IconButton(
                            onClick = {
                                onReset()
                                urlText = endpoint.defaultUrl
                                keyText = ""
                                testStatus = TestStatus.Idle
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x26EF4444), RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reset Platform",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CUSTOM RTMP SERVER CARD
// -------------------------------------------------------------

@Composable
fun CustomServerExpandableCard(
    custom: PlatformEndpoint.CustomRTMP,
    onToggle: (Boolean) -> Unit,
    onSave: (name: String, url: String, key: String) -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var nameText by remember(custom.name) { mutableStateOf(custom.name) }
    var urlText by remember(custom.url) { mutableStateOf(custom.url) }
    var keyText by remember(custom.streamKey) { mutableStateOf(custom.streamKey) }
    var testStatus by remember { mutableStateOf<TestStatus>(TestStatus.Idle) }

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val isConfigured = keyText.isNotBlank()

    Surface(
        color = Color(0xCC111827),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (custom.isEnabled && isConfigured) Color(0xFF06B6D4).copy(alpha = 0.4f)
            else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(8.dp, CircleShape, ambientColor = Color(0xFF06B6D4), spotColor = Color(0xFF06B6D4))
                            .background(Color(0xFF06B6D4).copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = custom.name,
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                custom.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isConfigured) Color(0x2610B981) else Color(0x269CA3AF),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isConfigured) Color(0xFF10B981).copy(alpha = 0.5f)
                                    else Color(0xFF6B7280).copy(alpha = 0.4f)
                                )
                            ) {
                                Text(
                                    if (isConfigured) "Configured" else "Not Set",
                                    color = if (isConfigured) Color(0xFF34D399) else Color(0xFF9CA3AF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            if (custom.url.isNotBlank()) custom.url else "rtmp://...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlowingToggle(
                        checked = custom.isEnabled,
                        onCheckedChange = onToggle
                    )

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }
            }

            // Expandable Body
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // Platform Name
                    Column {
                        Text(
                            "CUSTOM SERVER LABEL",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StreamInputField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            placeholder = "e.g. My Telegram Stream, Nginx Relay"
                        )
                    }

                    // Server URL
                    Column {
                        Text(
                            "RTMP / RTMPS / SRT INGEST URL",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StreamInputField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            placeholder = "rtmp://my-server.com/live"
                        )
                    }

                    // Stream Key
                    Column {
                        var showKey by remember { mutableStateOf(false) }
                        Text(
                            "STREAM KEY",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StreamInputField(
                            value = keyText,
                            onValueChange = { keyText = it },
                            placeholder = "Stream Key or channel token",
                            isPassword = !showKey,
                            trailingAction = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { showKey = !showKey }) {
                                        Icon(
                                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            null,
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = {
                                            val clip = clipboard.getText()?.text
                                            if (!clip.isNullOrBlank()) {
                                                keyText = clip.trim()
                                                Toast.makeText(context, "Pasted!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        color = Color(0x3306B6D4),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text(
                                            "PASTE",
                                            color = Color(0xFF67E8F9),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }

                    // Connection Test Banner
                    if (testStatus !is TestStatus.Idle) {
                        ConnectionStatusBanner(status = testStatus)
                    }

                    // Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onSave(nameText, urlText, keyText) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF06B6D4),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SAVE CHANGES", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    testStatus = TestStatus.Testing
                                    val (success, message) = DestinationsRepository.testEndpointConnection(urlText)
                                    testStatus = if (success) TestStatus.Success(message) else TestStatus.Error(message)
                                }
                            },
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1.2f),
                            enabled = testStatus !is TestStatus.Testing
                        ) {
                            if (testStatus is TestStatus.Testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PROBING...", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Speed, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("TEST RTMP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Delete Trash Icon Button
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x33EF4444), RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Custom Server",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ADD CUSTOM SERVER FORM
// -------------------------------------------------------------

@Composable
fun AddCustomServerForm(
    onAdd: (name: String, url: String, key: String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("rtmp://") }
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Surface(
        color = Color(0xFF131D33),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8)))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ADD CUSTOM RTMP DESTINATION",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.6f))
                }
            }

            // Platform Name
            Column {
                Text("NAME / LABEL", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                StreamInputField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "e.g. My Telegram Bot Stream, Nginx Relay, Restream.io"
                )
            }

            // Server URL
            Column {
                Text("SERVER URL (rtmp://, rtmps://, or srt://)", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                StreamInputField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "rtmp://live.my-server.com/app"
                )
            }

            // Stream Key
            Column {
                Text("STREAM KEY", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                StreamInputField(
                    value = key,
                    onValueChange = { key = it },
                    placeholder = "Stream Key (optional for open relays)",
                    isPassword = !showKey,
                    trailingAction = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Surface(
                                onClick = {
                                    val clip = clipboard.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        key = clip.trim()
                                    }
                                },
                                color = Color(0x336366F1),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    "PASTE",
                                    color = Color(0xFFA5B4FC),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                )
            }

            // Submit Buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (name.isBlank()) {
                            Toast.makeText(context, "Please enter a server name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (url.isBlank() || url == "rtmp://") {
                            Toast.makeText(context, "Please enter a valid server URL", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onAdd(name, url, key)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF38BDF8),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.5f)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SAVE SERVER", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CONNECTION STATUS BANNER
// -------------------------------------------------------------

@Composable
fun ConnectionStatusBanner(status: TestStatus) {
    val (bgColor, borderColor, textColor, icon, message) = when (status) {
        is TestStatus.Success -> arrayOf(
            Color(0x2610B981),
            Color(0xFF10B981),
            Color(0xFF34D399),
            Icons.Default.Check,
            status.message
        )
        is TestStatus.Error -> arrayOf(
            Color(0x26EF4444),
            Color(0xFFEF4444),
            Color(0xFFF87171),
            Icons.Default.Warning,
            status.message
        )
        else -> arrayOf(
            Color(0x2638BDF8),
            Color(0xFF38BDF8),
            Color(0xFF7DD3FC),
            Icons.Default.Speed,
            "Testing connection..."
        )
    }

    Surface(
        color = bgColor as Color,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, (borderColor as Color).copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon as ImageVector,
                null,
                tint = textColor as Color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                message as String,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// -------------------------------------------------------------
// STREAM INPUT FIELD
// -------------------------------------------------------------

@Composable
fun StreamInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    trailingAction: @Composable (() -> Unit)? = null
) {
    Surface(
        color = Color(0xFF090D18),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF222B42)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Box(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    cursorBrush = SolidColor(Color.Cyan),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            trailingAction?.invoke()
        }
    }
}

// -------------------------------------------------------------
// PLATFORM METADATA HELPER
// -------------------------------------------------------------

data class PlatformMeta(
    val icon: ImageVector,
    val accentColor: Color
)

fun getPlatformMeta(id: String): PlatformMeta {
    return when (id.lowercase()) {
        "youtube" -> PlatformMeta(Icons.Default.PlayArrow, Color(0xFFFF0033))
        "facebook" -> PlatformMeta(Icons.Default.Share, Color(0xFF1877F2))
        "tiktok" -> PlatformMeta(Icons.Default.Sensors, Color(0xFF00F2FE))
        "twitch" -> PlatformMeta(Icons.Default.Tv, Color(0xFF9146FF))
        "twitter" -> PlatformMeta(Icons.Default.AlternateEmail, Color(0xFF38BDF8))
        "instagram" -> PlatformMeta(Icons.Default.CameraAlt, Color(0xFFE1306C))
        "kick" -> PlatformMeta(Icons.Default.Bolt, Color(0xFF53FC18))
        "telegram" -> PlatformMeta(Icons.Default.Send, Color(0xFF229ED9))
        else -> PlatformMeta(Icons.Default.Dns, Color(0xFF06B6D4))
    }
}
