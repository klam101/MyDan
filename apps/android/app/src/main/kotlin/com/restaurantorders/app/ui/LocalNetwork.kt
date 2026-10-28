package com.restaurantorders.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.restaurantorders.app.ui.theme.Warn
import com.restaurantorders.app.ui.theme.WarnSoft

/**
 * Android 17 (API 37) blocks connections to the local network, where the kitchen printer
 * lives, until the user allows it. Older Android versions allow it with INTERNET alone.
 */
private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val ANDROID_17 = 37

fun hasLocalNetwork(context: Context): Boolean =
    Build.VERSION.SDK_INT < ANDROID_17 ||
        ContextCompat.checkSelfPermission(context, LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/** Asks once when shown, and keeps a banner with an Allow button until it's granted. */
@Composable
fun LocalNetworkBanner() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocalNetwork(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(LOCAL_NETWORK) }
    if (granted) return

    Row(
        Modifier.fillMaxWidth().background(WarnSoft).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Tickets can't print until this tablet may use the local network (where the kitchen printer is).",
            color = Warn,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { launcher.launch(LOCAL_NETWORK) }) { Text("Allow") }
    }
}
