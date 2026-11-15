package com.ruinobrega.fastinator

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ruinobrega.fastinator.notification.FastingService
import com.ruinobrega.fastinator.notification.startFastingService
import com.ruinobrega.fastinator.notification.stopFastingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Spec §8.1 (v1.11): the app-level service controls must target FastingService —
 * starting it launches the foreground service (one persistent notification),
 * stopping it removes the notification (stopForeground REMOVE) and stops the service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FastingServiceTest {

  @Test
  fun startFastingServiceLaunchesFastingService() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.startFastingService()
    val shadow = shadowOf(context.applicationContext as Application)
    val intent = shadow.nextStartedService
    assertNotNull("startFastingService must start a service", intent)
    assertEquals(FastingService::class.java.name, intent!!.component?.className)
  }

  @Test
  fun stopFastingServiceStopsFastingService() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.stopFastingService()
    val shadow = shadowOf(context.applicationContext as Application)
    val intent = shadow.nextStoppedService
    assertNotNull("stopFastingService must stop a service", intent)
    assertEquals(FastingService::class.java.name, intent!!.component?.className)
    assertNull(shadow.nextStartedService)
  }
}
