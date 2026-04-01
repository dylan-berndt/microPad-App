package com.example.micropad

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.micropad.data.writeToCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Test class for the CSV export helper function.
 */
class CsvExportHelperTest {

    /**
     * A temporary folder to use for testing.
     */
    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * A mock context to use for testing.
     */
    @Mock
    lateinit var mockContext: Context

    /**
     * A mock content resolver to use for testing.
     */
    @Mock
    lateinit var mockContentResolver: ContentResolver

    /**
     * A mock URI to use for testing.
     */
    @Mock
    lateinit var mockUri: Uri

    /**
     * A test file to use for testing.
     */
    private lateinit var testFile: File

    /** 
     * Set up the test environment.
     */
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.contentResolver).thenReturn(mockContentResolver)
        `when`(mockContext.cacheDir).thenReturn(tempFolder.newFolder("cache"))
        
        testFile = tempFolder.newFile("test_dataset.csv")
    }

    /**
     * Set up the mock file content.
     * 
     * @param content The content to use for the mock file.
     */
    private fun setupMockFileContent(content: String) {
        `when`(mockContentResolver.openInputStream(mockUri)).thenAnswer {
            ByteArrayInputStream(content.toByteArray())
        }
        `when`(mockContentResolver.openOutputStream(any(Uri::class.java), anyString())).thenAnswer {
            FileOutputStream(testFile)
        }
    }

    /**
     * Test adding references to an existing file.
     * 
     * Verifies that:
     * - The header remains intact.
     * - Old references are preserved.
     * - New references are correctly appended.
     * - Sample data is preserved.
     * - A blank row separates sections.
     */
    @Test
    fun `test adding references to existing file`() {
        val existingContent = """
            sample_id,reference_name,distance_calculation,similarity_score,No Dye_r,No Dye_g,No Dye_b,DMGO_r,DMGO_g,DMGO_b,XO_r,XO_g,XO_b,Phen_r,Phen_g,Phen_b,DCP_r,DCP_g,DCP_b,PAR_r,PAR_g,PAR_b
            1,H2O,,,255,255,255,255,255,255,225,225,225,255,255,255,255,255,255,255,255,200

            S1,Unknown,,,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100
        """.trimIndent()
        
        setupMockFileContent(existingContent)
        
        val newRefRow = "2,Ni(II),,,255,255,255,255,235,235,225,225,225,255,255,255,255,225,255,255,225,225"
        
        writeToCsv(newRefRow, "references", mockUri, mockContext)
        
        val resultLines = testFile.readLines()
        
        // Assert header is there
        assertTrue(resultLines[0].startsWith("sample_id"))
        // Assert old reference is there
        assertTrue(resultLines.any { it.startsWith("1,H2O") })
        // Assert new reference is there
        assertTrue(resultLines.any { it.startsWith("2,Ni(II)") })
        // Assert sample is still there
        assertTrue(resultLines.any { it.startsWith("S1,Unknown") })
        // Assert blank row exists between sections
        val blankIndex = resultLines.indexOfFirst { it.isBlank() }
        assertTrue("Blank row should exist", blankIndex != -1)
        assertTrue("Reference should be before blank row", resultLines.subList(0, blankIndex).any { it.startsWith("2,Ni(II)") })
    }

    /**
     * Test adding samples to an existing file.
     * 
     * Verifies that new sample rows are correctly appended after the blank separator row.
     */
    @Test
    fun `test adding samples to existing file`() {
        val existingContent = """
            sample_id,reference_name,distance_calculation,similarity_score,No Dye_r,No Dye_g,No Dye_b,DMGO_r,DMGO_g,DMGO_b,XO_r,XO_g,XO_b,Phen_r,Phen_g,Phen_b,DCP_r,DCP_g,DCP_b,PAR_r,PAR_g,PAR_b
            1,H2O,,,255,255,255,255,255,255,225,225,225,255,255,255,255,255,255,255,255,200

            
        """.trimIndent()
        
        setupMockFileContent(existingContent)
        
        val newSampleRow = "S2,Test,,,120,120,120,120,120,120,120,120,120,120,120,120,120,120,120,120,120,120"
        
        writeToCsv(newSampleRow, "samples", mockUri, mockContext)
        
        val resultLines = testFile.readLines()
        
        assertTrue(resultLines.any { it.startsWith("S2,Test") })
        val blankIndex = resultLines.indexOfFirst { it.isBlank() }
        assertTrue("Sample should be after blank row", resultLines.subList(blankIndex + 1, resultLines.size).any { it.startsWith("S2,Test") })
    }

    /**
     * Test duplicate prevention during CSV export.
     * 
     * Verifies that adding an identical row doesn't result in duplicate entries in the file.
     */
    @Test
    fun `test duplicate prevention`() {
        val duplicateRow = "1,H2O,,,255,255,255,255,255,255,225,225,225,255,255,255,255,255,255,255,255,200"
        val existingContent = """
            sample_id,reference_name,distance_calculation,similarity_score,No Dye_r,No Dye_g,No Dye_b,DMGO_r,DMGO_g,DMGO_b,XO_r,XO_g,XO_b,Phen_r,Phen_g,Phen_b,DCP_r,DCP_g,DCP_b,PAR_r,PAR_g,PAR_b
            $duplicateRow

            
        """.trimIndent()
        
        setupMockFileContent(existingContent)
        
        // Try adding the exact same row again
        writeToCsv(duplicateRow, "references", mockUri, mockContext)
        
        val resultLines = testFile.readLines()
        val occurrences = resultLines.count { it == duplicateRow }
        assertEquals("Should not add duplicate row", 1, occurrences)
    }
}
