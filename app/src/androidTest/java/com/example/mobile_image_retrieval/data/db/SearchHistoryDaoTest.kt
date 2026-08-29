package com.example.mobile_image_retrieval.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchHistoryDaoTest {
    private lateinit var database: PhotoSearchDatabase
    private lateinit var dao: SearchHistoryDao

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PhotoSearchDatabase::class.java).build()
        dao = database.searchHistoryDao()
    }

    @After fun close() = database.close()

    @Test fun insertTrimsAndClearWorks() = runBlocking {
        repeat(105) { dao.insert(SearchHistoryEntity(query = "q$it", timestamp = it.toLong(), topResultUri = null)) }
        assertEquals(100, dao.count())
        dao.clear()
        assertEquals(0, dao.count())
    }
}
