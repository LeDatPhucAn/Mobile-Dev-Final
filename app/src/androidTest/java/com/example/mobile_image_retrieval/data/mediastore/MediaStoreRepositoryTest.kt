package com.example.mobile_image_retrieval.data.mediastore

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobile_image_retrieval.domain.model.AlbumCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MediaStoreRepositoryTest {
    @Test
    fun photosWithoutOptionalMetadataStillAppearInAllPhotos() = runBlocking {
        val cursor = MatrixCursor(arrayOf(MediaStore.Images.Media._ID)).apply {
            addRow(arrayOf<Any>(42L))
        }
        val repository = repository { cursor }

        val photos = repository.queryImages()

        assertEquals(1, photos.size)
        assertEquals("content://media/external/images/media/42", photos.single().uri)
        assertNull(photos.single().bucketName)
        assertNull(photos.single().width)
        assertEquals(0L, photos.single().dateModified)
        assertEquals(photos, AlbumCatalog.photosFor(AlbumCatalog.ALL_PHOTOS_ID, photos))
        assertEquals(1, repository.albums(photos).first().count)
    }

    @Test
    fun refreshReadsPhotosAddedAfterAnEmptyQuery() = runBlocking {
        var hasPhoto = false
        val repository = repository {
            MatrixCursor(arrayOf(MediaStore.Images.Media._ID)).apply {
                if (hasPhoto) addRow(arrayOf<Any>(7L))
            }
        }
        assertTrue(repository.queryImages().isEmpty())
        hasPhoto = true
        assertEquals(listOf(7L), repository.queryImages().map { it.mediaId })
    }

    @Test
    fun nullProviderResponseIsAReadFailureInsteadOfAnEmptyLibrary() {
        assertThrows(IOException::class.java) {
            runBlocking { repository { null }.queryImages() }
        }
    }

    @Test
    fun revokedPermissionIsNotReportedAsAnEmptyLibrary() {
        assertThrows(SecurityException::class.java) {
            runBlocking { repository { throw SecurityException("Access revoked") }.queryImages() }
        }
    }

    private fun repository(query: () -> Cursor?): MediaStoreRepository {
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?,
            ): Cursor? {
                assertEquals(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), uri)
                return query()
            }
            override fun getType(uri: Uri): String = "image/jpeg"
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unexpected insert")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Unexpected delete")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Unexpected update")
        }
        return MediaStoreRepository(ContentResolver.wrap(provider))
    }
}
