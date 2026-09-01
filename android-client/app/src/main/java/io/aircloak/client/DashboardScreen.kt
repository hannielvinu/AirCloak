package io.aircloak.client

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AuditLogEntry(
    val id: String,
    val timestamp: String,
    val secretType: String,
    val maskedSnippet: String,
    val action: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToVision: () -> Unit = {},
    onTriggerEmergencyPurge: () -> Unit = {}
) {
    var isInterceptorActive by remember { mutableStateOf(true) }
    var isVisionRedactorActive by remember { mutableStateOf(true) }
    var isRedLightOfflineActive by remember { mutableStateOf(false) }

    val auditEvents = remember {
        mutableStateListOf(
            AuditLogEntry("1", "19:42:10", "AWS_ACCESS_KEY", "AKIA...7EXA", "Masked & Vaulted"),
            AuditLogEntry("2", "19:43:05", "STRIPE_SECRET", "sk_live...jklm", "Replaced with Mock"),
            AuditLogEntry("3", "19:45:22", "DB_CONNECTION_URI", "postgres...app_prod", "Encrypted (AES-256-GCM)"),
            AuditLogEntry("4", "19:48:15", "SERVER_IPV4", "203.0...195", "Sanitized in Clipboard")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isRedLightOfflineActive) Color(0xFFFF3366) else Color(0xFF00FFCC))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AIRCLOAK",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "v1.0-NPU",
                            fontSize = 11.sp,
                            color = Color(0xFF58A6FF),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117)
                ),
                actions = {
                    IconButton(onClick = onNavigateToVision) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Vision Redactor HUD",
                            tint = Color(0xFF00FFCC)
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Threat Status & Hardware Acceleration Banner
            item {
                NpuStatusCard(isOfflineLocked = isRedLightOfflineActive)
            }

            // 2. Core Security Feature Toggles
            item {
                Text(
                    text = "DEFENSE PROTOCOLS",
                    color = Color(0xFF8B949E),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SecurityToggleTile(
                    title = "Active Interceptor",
                    description = "Intercepts cross-device clipboard & paste text, swapping plaintext secrets with sandbox tokens.",
                    icon = Icons.Default.Security,
                    checked = isInterceptorActive,
                    onCheckedChange = { isInterceptorActive = it },
                    accentColor = Color(0xFF00FFCC)
                )
            }

            item {
                SecurityToggleTile(
                    title = "Vision Redactor (NPU HUD)",
                    description = "Scans monitor screens via camera and puts privacy bounding boxes over credentials.",
                    icon = Icons.Default.Visibility,
                    checked = isVisionRedactorActive,
                    onCheckedChange = { isVisionRedactorActive = it },
                    accentColor = Color(0xFF58A6FF)
                )
            }

            item {
                SecurityToggleTile(
                    title = "Red Light Offline Mode",
                    description = "Severs all outbound wireless interfaces & executes fully local on-device inference isolation.",
                    icon = Icons.Default.SignalWifiOff,
                    checked = isRedLightOfflineActive,
                    onCheckedChange = { isRedLightOfflineActive = it },
                    accentColor = Color(0xFFFF3366)
                )
            }

            // 3. Emergency Gesture Purge Trigger
            item {
                EmergencyPurgeCard(
                    onPurgeClick = {
                        auditEvents.clear()
                        onTriggerEmergencyPurge()
                    }
                )
            }

            // 4. Live Interception Audit Stream
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE INTERCEPT AUDIT STREAM",
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${auditEvents.size} EVENTS",
                        color = Color(0xFF58A6FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            items(auditEvents) { event ->
                AuditLogTile(event = event)
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun NpuStatusCard(isOfflineLocked: Boolean) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161B22)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isOfflineLocked) Color(0xFFFF3366) else Color(0xFF238636),
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = Color(0xFF00FFCC),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "iQOO NPU ACCELERATOR",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
                Surface(
                    color = if (isOfflineLocked) Color(0x33FF3366) else Color(0x33238636),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isOfflineLocked) "AIR-GAPPED" else "ACTIVE 0-LEAK",
                        color = if (isOfflineLocked) Color(0xFFFF3366) else Color(0xFF3FB950),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(label = "Inference Latency", value = "1.8 ms")
                MetricColumn(label = "NPU Provider", value = "QNN / Hexagon")
                MetricColumn(label = "Encrypted Vault", value = "AES-256-GCM")
            }
        }
    }
}

@Composable
fun MetricColumn(label: String, value: String) {
    Column {
        Text(text = label, color = Color(0xFF8B949E), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SecurityToggleTile(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF21262D), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D1117)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) accentColor else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF21262D)
                )
            )
        }
    }
}

@Composable
fun EmergencyPurgeCard(onPurgeClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x22FF3366)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x66FF3366), RoundedCornerShape(12.dp))
            .clickable { onPurgeClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Vibration,
                contentDescription = null,
                tint = Color(0xFFFF3366),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "IMU Shake Gesture Purge Armed",
                    color = Color(0xFFFF7B72),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Rapidly shake device on 2 axes to instantly wipe clipboard and volatile secret keys.",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp
                )
            }
            Button(
                onClick = onPurgeClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("PURGE NOW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun AuditLogTile(event: AuditLogEntry) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF21262D), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.secretType,
                        color = Color(0xFF58A6FF),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = event.maskedSnippet,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.action,
                    color = Color(0xFF3FB950),
                    fontSize = 10.sp
                )
            }
            Text(
                text = event.timestamp,
                color = Color(0xFF8B949E),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}
