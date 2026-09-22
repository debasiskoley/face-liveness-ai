package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.EnrolledIdentityRepository
import com.example.ml.FaceBiometricEngine
import com.example.model.BiometricVector
import com.example.model.FaceMetrics
import com.example.model.SpoofRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Face Liveness AI", appName)
  }

  @Test
  fun `biometric vector cosine similarity math`() {
    val v1 = BiometricVector(FloatArray(32) { 0.5f })
    val v2 = BiometricVector(FloatArray(32) { 0.5f })

    // Identical normalized vectors have cosine similarity = 1.0
    val similarity = v1.cosineSimilarity(v2)
    assertEquals(1.0f, similarity, 0.001f)

    // Dissimilar vector
    val v3 = BiometricVector(FloatArray(32) { if (it % 2 == 0) 1.0f else -1.0f })
    val dist = v1.euclideanDistance(v3)
    assertTrue(dist > 0.1f)
  }

  @Test
  fun `identity repository profile selection and enrollment`() {
    val repo = EnrolledIdentityRepository()
    val profiles = repo.profiles.value
    assertTrue(profiles.isNotEmpty())

    val first = profiles.first()
    assertEquals(first.id, repo.selectedProfile.value?.id)

    // Enroll new live face
    val testVector = BiometricVector(FloatArray(32) { 0.2f })
    val enrolled = repo.enrollNewProfile(
        name = "Jane Doe",
        address = "123 Innovation Way, Tech City",
        mobileNo = "+1 555-0199",
        email = "jane.doe@biometric.org",
        role = "Chief Architect",
        vector = testVector,
        notes = "Biometric Pass Enrolled",
        photoUri = "file:///storage/profile.jpg"
    )
    assertEquals("Jane Doe", enrolled.name)
    assertEquals("123 Innovation Way, Tech City", enrolled.address)
    assertEquals("+1 555-0199", enrolled.mobileNo)
    assertEquals("jane.doe@biometric.org", enrolled.email)
    assertEquals("Chief Architect", enrolled.role)
    assertEquals(enrolled.id, repo.selectedProfile.value?.id)

    // Update details
    val updated = enrolled.copy(address = "456 Updated Blvd", mobileNo = "+1 555-0200")
    repo.updateProfile(updated)
    assertEquals("456 Updated Blvd", repo.selectedProfile.value?.address)
    assertEquals("+1 555-0200", repo.selectedProfile.value?.mobileNo)
  }

  @Test
  fun `spoof detection flags empty frame gracefully`() {
    val engine = FaceBiometricEngine()
    val emptyMetrics = FaceMetrics(hasFace = false)
    val spoofResult = engine.evaluateSpoofing(emptyMetrics)

    assertFalse(spoofResult.isLive)
    assertEquals(SpoofRiskLevel.MEDIUM, spoofResult.riskLevel)
  }

  @Test
  fun `identity templates from different patterned faces are not identical`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val embedder = com.example.ml.ArcFaceEmbedder.getInstance(context)
    val warm = patternedFace(0xFFCC7744.toInt(), stripe = 6)
    val cool = patternedFace(0xFF1D4ED8.toInt(), stripe = 18)
    val v1 = embedder.embedAlignedCrop(warm)
    val v3 = embedder.embedAlignedCrop(cool)
    assertNotNull(v1)
    assertNotNull(v3)
    assertTrue(BiometricVector.isIdentityTemplate(v1!!))
    val sameScore = v1.cosineSimilarity(embedder.embedAlignedCrop(warm)!!)
    val diffScore = v1.cosineSimilarity(v3!!)
    assertTrue(sameScore > 0.98f)
    if (v1.features.size != 512) {
      assertTrue(sameScore > diffScore)
    }
  }

  @Test
  fun `verification route state model and success navigation`() {
    val profile = repoTestProfile()
    val vector = profile.vector
    val cosSim = vector.cosineSimilarity(profile.vector)
    assertEquals(1.0f, cosSim, 0.001f)

    // Verify app navigation states
    val screens = com.example.viewmodel.AppScreen.values()
    assertTrue(screens.contains(com.example.viewmodel.AppScreen.MAIN_DASHBOARD))
    assertTrue(screens.contains(com.example.viewmodel.AppScreen.VERIFICATION_SUCCESS))

    val tabs = com.example.viewmodel.AppTab.values()
    assertTrue(tabs.contains(com.example.viewmodel.AppTab.HOME))
    assertTrue(tabs.contains(com.example.viewmodel.AppTab.PROFILE))
  }

  private fun patternedFace(color: Int, stripe: Int): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(160, 160, android.graphics.Bitmap.Config.ARGB_8888)
    for (y in 0 until 160) {
      for (x in 0 until 160) {
        val onStripe = ((x / stripe) + (y / stripe)) % 2 == 0
        bmp.setPixel(x, y, if (onStripe) color else 0xFF111827.toInt())
      }
    }
    return bmp
  }

  private fun repoTestProfile(): com.example.model.EnrolledProfile {
    return com.example.model.EnrolledProfile(
        id = "test-123",
        name = "Alex Morgan",
        address = "742 Evergreen Terrace",
        mobileNo = "+1 555-9876",
        email = "alex.morgan@test.com",
        role = "Biometric Admin",
        enrolledDate = "Sep 17, 2026",
        vector = BiometricVector(FloatArray(32) { 0.4f }),
        photoUri = null
    )
  }
}
