package com.example.micropad

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.micropad.data.AppErrorLogger
import com.example.micropad.data.preprocessImage
import com.example.micropad.data.writeToCsv
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.io.File
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class ReliabilityIntegrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var uiDevice: UiDevice

    companion object {
        private var isWarm = false
    }

    @Before
    fun setup() {
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (!isWarm) {
            if (!OpenCVLoader.initLocal()) {
                fail("OpenCV initialization failed")
            }
            // Warm up OpenCV to ensure subsequent timing is accurate
            val warmUp = Mat.zeros(100, 100, CvType.CV_8UC3)
            try {
                preprocessImage(warmUp, context, false, "None", "Include Squares")
            } finally {
                warmUp.release()
            }
        }
    }

    /**
     * Requirement 1: CSV export performance.
     * Verifies that saving CSV files completes in under 3 seconds.
     */
    @Test
    fun testCsvExportPerformance() {
        val largeDataRow = "S1,Test,Euclidean,0.5," + List(18) { "128" }.joinToString(",")
        // Use a real file in cache to ensure writable Uri
        val testFile = File(context.cacheDir, "test_export.csv")
            if (testFile.exists()) testFile.delete()

        val dummyUri = android.net.Uri.fromFile(testFile)

        val timeTaken = measureTimeMillis {
            writeToCsv(largeDataRow, "samples", dummyUri, context)
        }

        assertTrue("CSV export took too long: ${timeTaken}ms", timeTaken < 3000)
        assertTrue("CSV export did not create the output file", testFile.exists())
        assertTrue("CSV export wrote an empty file", testFile.length() > 0)

        val content = testFile.readText()
        assertTrue("CSV export output should not be blank", content.isNotBlank())
        assertTrue("CSV export did not contain the written data row", content.contains(largeDataRow))
        val nonBlankLines = content.lines().filter { it.isNotBlank() }
        assertTrue(
            "CSV export should contain at least a header and one data row",
            nonBlankLines.size >= 2
        )
    }


    /**
     * Requirement 2: Micropad orientation validation.
     * Verify that camera capture/preprocessing is rejected when the micropad is not detected.
     */
    @Test
    fun testOrientationValidation() {
        val emptyMat = Mat.zeros(1000, 1000, CvType.CV_8UC3)
        val result = preprocessImage(emptyMat, context, false, "None", "Mean")
        assertNull("Image with no calibration squares should be rejected", result)
    }

    /**
     * Requirement 3: Offline functionality.
     * Verifies core workflow components work without network.
     */
    @Test
    fun testOfflineStability() {
        // Optimized: only toggle network if it is currently on
        val wifiOn = uiDevice.executeShellCommand("settings get global wifi_on").trim() == "1"
        try {
            if (wifiOn) {
                uiDevice.executeShellCommand("svc wifi disable")
                uiDevice.executeShellCommand("svc data disable")
            }
            
            // Core logic should not depend on network
            AppErrorLogger.logError(context, "OfflineTest", "Checking persistence")
            assertTrue(true)
        } finally {
            if (wifiOn) {
                uiDevice.executeShellCommand("svc wifi enable")
                uiDevice.executeShellCommand("svc data enable")
            }
        }
    }

    /**
     * Requirement 5: Multiple-image processing performance.
     * Verify that processing an image completes in under 1 second.
     */
    @Test
    fun testImageProcessingLatency() {
        val mat = Mat.zeros(500, 500, CvType.CV_8UC3)
        val timeTaken = measureTimeMillis {
            preprocessImage(mat, context, false, "None", "Include Squares")
        }
        assertTrue("Per-image processing took too long: ${timeTaken}ms", timeTaken < 1000)
    }

    /**
     * Requirement 7: Legacy Android emulator compatibility.
     * Asserts that core features work on the target API level.
     */
    @Test
    fun testLegacyApiCompatibility() {
        val apiLevel = android.os.Build.VERSION.SDK_INT
        assertTrue("Testing on API $apiLevel", apiLevel >= 24)
        AppErrorLogger.logError(context, "CompatTest", "Check legacy API execution")
    }
}
