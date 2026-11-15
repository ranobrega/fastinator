package com.ruinobrega.fastinator

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec §5 (v1.5): Android OS auto-backup stays enabled and includes the session
 * database (silent restore on reinstall is accepted). Spec §3.6: a FileProvider
 * must be registered so the share action can share the exported JSON file.
 * Spec §8.1 (v1.11): POST_NOTIFICATIONS / FOREGROUND_SERVICE /
 * FOREGROUND_SERVICE_SPECIAL_USE permissions and the specialUse FastingService.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ManifestConfigTest {

  @Test
  fun osAutoBackupIsEnabled() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val info = context.packageManager.getApplicationInfo(context.packageName, 0)
    assertTrue(
      "allowBackup must stay enabled so the OS auto-backup covers the database (spec §5)",
      info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0
    )
  }

  @Test
  fun fileProviderIsRegisteredForExportSharing() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val provider = context.packageManager.resolveContentProvider(
      "${context.packageName}.fileprovider",
      PackageManager.GET_META_DATA
    )
    assertNotNull("FileProvider for the export folder must be declared (spec §3.6)", provider)
  }

  @Test
  fun notificationAndForegroundServicePermissionsAreDeclared() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    val perms = info.requestedPermissions?.toList() ?: emptyList()
    assertTrue("POST_NOTIFICATIONS required for the fasting notification (spec §8.1)",
      perms.contains("android.permission.POST_NOTIFICATIONS"))
    assertTrue("FOREGROUND_SERVICE required to run FastingService (spec §8.1)",
      perms.contains("android.permission.FOREGROUND_SERVICE"))
    assertTrue("FOREGROUND_SERVICE_SPECIAL_USE required for specialUse FGS type (spec §8.1)",
      perms.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
  }

  @Test
  fun fastingServiceIsDeclaredAsSpecialUseForegroundService() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
    val service = info.services?.firstOrNull {
      it.name == "${context.packageName}.notification.FastingService"
    }
    assertNotNull("FastingService must be declared in the manifest (spec §8.1)", service)
    assertEquals(
      "FastingService must use foregroundServiceType=specialUse (spec §8.1)",
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      service!!.foregroundServiceType
    )
    assertFalse("FastingService must not be exported", service.exported)
  }
}
