package com.songly.android.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.songly.android.SonglyApplication
import com.songly.shared.network.SonglyApiClient

/**
 * Wejście do zalogowanej części aplikacji: zgłoszenie urządzenia do pushy i — na
 * Androidzie 13+ — prośba o zgodę na powiadomienia.
 *
 * Prośba dopiero po zalogowaniu, nie przy starcie: na ekranie logowania nie ma
 * jeszcze czego powiadamiać, a odmowa w ciemno zostaje na zawsze (system po dwóch
 * odmowach przestaje pytać). Bez projektu Firebase nie pytamy wcale — zgoda na
 * coś, co nigdy nie przyjdzie, byłaby tylko uciążliwością.
 */
@Composable
fun PushSetup(client: SonglyApiClient) {
	val context = LocalContext.current
	val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

	LaunchedEffect(Unit) {
		if (!PushRegistration.isAvailable(context)) return@LaunchedEffect
		val app = context.applicationContext as SonglyApplication
		PushRegistration.registerCurrentDevice(context, client, app.appScope)
		if (Build.VERSION.SDK_INT >= 33 &&
			ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
			PackageManager.PERMISSION_GRANTED
		) {
			permission.launch(Manifest.permission.POST_NOTIFICATIONS)
		}
	}
}
