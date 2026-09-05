package com.example.mobile_image_retrieval.data.mediastore

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException

class PhotoCopyRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val original = File.createTempFile("copy-test-original-", ".png", context.cacheDir).apply { writeBytes(ByteArray(150_000) { (it % 251).toByte() }) }
    private val copied = File.createTempFile("copy-test-destination-", ".png", context.cacheDir)
    private val source = Uri.parse("content://copy-test/original")
    private val destination = Uri.parse("content://media/external_primary/images/media/999")
    private var inserted: ContentValues? = null
    private var published = false
    private var deleted: Uri? = null
    private var failPublish = false
    private var failWrite = false
    private val photo = MediaItem(1, source.toString(), MediaType.IMAGE, "old.png", 1, 1, 1, 32, 32, "image/png", null, null)
    private val provider = object : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri) = "image/png"
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            assertEquals(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), uri)
            inserted = ContentValues(values!!)
            return destination
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (uri == source) return ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY)
            assertEquals(destination, uri)
            if (failWrite) throw FileNotFoundException("Storage unavailable")
            return ParcelFileDescriptor.open(copied, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            assertEquals(destination, uri)
            assertArrayEquals(original.readBytes(), copied.readBytes())
            assertEquals(0, values!!.getAsInteger(MediaStore.Images.Media.IS_PENDING).toInt())
            published = !failPublish
            return if (failPublish) 0 else 1
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            assertEquals(destination, uri)
            deleted = uri
            copied.delete()
            return 1
        }
    }
    private val repository = PhotoCopyRepository(ContentResolver.wrap(provider))

    @After fun cleanup() { original.delete(); copied.delete() }

    @Test fun copyPreservesBytesAndPublishesOnlyAfterWriting() = runBlocking {
        val before = original.readBytes()
        assertEquals(destination, repository.saveCopy(photo))
        assertArrayEquals(before, original.readBytes())
        assertArrayEquals(before, copied.readBytes())
        assertEquals("Pictures/Photo Search", inserted!!.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals(1, inserted!!.getAsInteger(MediaStore.Images.Media.IS_PENDING).toInt())
        assertTrue(inserted!!.getAsString(MediaStore.Images.Media.DISPLAY_NAME).endsWith(".png"))
        assertTrue(published)
        assertNull(deleted)
    }

    @Test fun publishFailureRemovesOnlyTheNewCopy() = runBlocking {
        failPublish = true
        assertTrue(runCatching { repository.saveCopy(photo) }.isFailure)
        assertEquals(destination, deleted)
        assertFalse(copied.exists())
        assertTrue(original.exists())
    }

    @Test fun writeFailureRemovesPendingCopy() = runBlocking {
        failWrite = true
        assertTrue(runCatching { repository.saveCopy(photo) }.isFailure)
        assertEquals(destination, deleted)
        assertFalse(published)
        assertTrue(original.exists())
    }

    @Test fun unreadableOriginalCreatesNoCopy() = runBlocking {
        original.delete()
        assertTrue(runCatching { repository.saveCopy(photo) }.isFailure)
        assertNull(inserted)
        assertNull(deleted)
    }
}
