package com.example.mobile_image_retrieval.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PeopleMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), PhotoSearchDatabase::class.java,
    )

    @Test fun migrationPreservesHistoryAndIndex() {
        val name = "people-migration-test"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO search_history (id, query, timestamp, topResultUri) VALUES (1, 'beach', 100, NULL)")
            execSQL("INSERT INTO media_embeddings (mediaId, uri, mediaType, dateModified, embedding, embeddingDimension, indexedAt) VALUES (1, 'content://photos/1', 'IMAGE', 10, X'0000803F', 1, 100)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, PhotoSearchDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT query FROM search_history").use { cursor ->
                cursor.moveToFirst()
                assertEquals("beach", cursor.getString(0))
            }
            db.query("SELECT mediaId FROM media_embeddings").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1L, cursor.getLong(0))
            }
            db.execSQL("INSERT INTO people (name, handle, thumbnail, embedding, embeddingDimension) VALUES ('Alex', 'alex', X'01', X'0000803F', 1)")
        }
    }

    @Test fun faceMigrationPreservesLegacyPeopleAndCascadesPhotoDeletion() {
        val name = "face-migration-test"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO people (name, handle, thumbnail, embedding, embeddingDimension) VALUES ('Alex', 'alex', X'01', X'0000803F', 1)")
            execSQL("INSERT INTO media_embeddings (mediaId, uri, mediaType, dateModified, embedding, embeddingDimension, indexedAt) VALUES (1, 'content://photos/1', 'IMAGE', 10, X'0000803F', 1, 100)")
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true, PhotoSearchDatabase.MIGRATION_2_3).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.query("SELECT handle, embeddingModel FROM people").use { cursor ->
                cursor.moveToFirst()
                assertEquals("alex", cursor.getString(0))
                assertEquals("mobileclip2-s0", cursor.getString(1))
            }
            db.execSQL("INSERT INTO face_index VALUES (1, 10, 'test')")
            db.execSQL("INSERT INTO face_embeddings VALUES (1, 0, X'0000803F', 1)")
            db.execSQL("DELETE FROM media_embeddings WHERE mediaId = 1")
            for (table in listOf("face_index", "face_embeddings")) db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }
}
