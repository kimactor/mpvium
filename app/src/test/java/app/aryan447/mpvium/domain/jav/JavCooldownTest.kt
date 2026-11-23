package app.aryan447.mpvium.domain.jav

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class JavCooldownTest {
  @Test
  fun restoresKnownKindsAndFallsBackToNetwork() {
    assertEquals(JavSourceFailureKind.BLOCKED, savedJavCooldownKind("BLOCKED"))
    assertEquals(JavSourceFailureKind.AUTH_REQUIRED, savedJavCooldownKind("AUTH_REQUIRED"))
    assertEquals(JavSourceFailureKind.NETWORK, savedJavCooldownKind(""))
    assertEquals(JavSourceFailureKind.NETWORK, savedJavCooldownKind("NOT_A_KIND"))
  }
}
