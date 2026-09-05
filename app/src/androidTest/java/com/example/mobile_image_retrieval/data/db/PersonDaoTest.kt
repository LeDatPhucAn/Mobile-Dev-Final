package com.example.mobile_image_retrieval.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), PhotoSearchDatabase::class.java,
    ).build()

    @After fun close() = database.close()

    @Test fun referenceSurvivesReadingAndCanBeRemoved() = runBlocking {
        val dao = database.personDao()
        val embedding = EmbeddingCodec.encode(floatArrayOf(1f, 0f))
        dao.insert(PersonEntity(name = "Alex", handle = "alex", thumbnail = byteArrayOf(1, 2), embedding = embedding, embeddingDimension = 2))
        val saved = dao.byHandles(listOf("alex")).single()
        assertEquals("Alex", saved.name)
        assertArrayEquals(embedding, saved.embedding)
        assertArrayEquals(byteArrayOf(1, 2), dao.observePeople().first().single().thumbnail)
        assertTrue(dao.byHandles(listOf("missing")).isEmpty())
        dao.delete(saved.id)
        assertTrue(dao.observePeople().first().isEmpty())
    }

    @Test fun duplicateHandleCannotOverwriteAReference() = runBlocking {
        val dao = database.personDao()
        val person = PersonEntity(name = "Alex", handle = "alex", thumbnail = byteArrayOf(1), embedding = EmbeddingCodec.encode(floatArrayOf(1f)), embeddingDimension = 1)
        dao.insert(person)
        val duplicate = runCatching { dao.insert(person.copy(name = "Another Alex")) }
        assertTrue(duplicate.isFailure)
        assertEquals("Alex", dao.byHandles(listOf("alex")).single().name)
    }
}
