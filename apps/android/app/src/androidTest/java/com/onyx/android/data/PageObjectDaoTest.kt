package com.onyx.android.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.entity.PageObjectEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageObjectDaoTest {
    private lateinit var db: OnyxDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db =
            Room
                .inMemoryDatabaseBuilder(context, OnyxDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun pageObjects_crud_ordersByZIndex_andSupportsSoftDelete() {
        runBlocking {
            val now = 1700000000000L
            val noteId = "11111111-1111-1111-1111-111111111111"
            val pageId = "22222222-2222-2222-2222-222222222222"
            val objectAId = "33333333-3333-3333-3333-333333333333"
            val objectBId = "44444444-4444-4444-4444-444444444444"

            db.noteDao().insert(
                NoteEntity(
                    noteId = noteId,
                    ownerUserId = "device",
                    title = "Objects",
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            )
            db.pageDao().insert(
                PageEntity(
                    pageId = pageId,
                    noteId = noteId,
                    kind = "ink",
                    geometryKind = "fixed",
                    indexInNote = 0,
                    width = 612f,
                    height = 792f,
                    updatedAt = now,
                    contentLamportMax = 0,
                ),
            )

            db.pageObjectDao().insert(
                PageObjectEntity(
                    objectId = objectAId,
                    pageId = pageId,
                    noteId = noteId,
                    kind = "shape",
                    zIndex = 10,
                    x = 20f,
                    y = 30f,
                    width = 100f,
                    height = 120f,
                    rotationDeg = 0f,
                    payloadJson = "{\"shapeType\":\"rectangle\"}",
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            )
            db.pageObjectDao().insert(
                PageObjectEntity(
                    objectId = objectBId,
                    pageId = pageId,
                    noteId = noteId,
                    kind = "shape",
                    zIndex = 2,
                    x = 40f,
                    y = 50f,
                    width = 60f,
                    height = 70f,
                    rotationDeg = 0f,
                    payloadJson = "{\"shapeType\":\"ellipse\"}",
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            )

            val ordered = db.pageObjectDao().getByPageId(pageId)
            assertEquals(2, ordered.size)
            assertEquals(objectBId, ordered[0].objectId)
            assertEquals(objectAId, ordered[1].objectId)

            db.pageObjectDao().updateGeometry(
                objectId = objectBId,
                x = 90f,
                y = 110f,
                width = 150f,
                height = 160f,
                rotationDeg = 0f,
                updatedAt = now + 1,
            )

            val updated = db.pageObjectDao().getById(objectBId)
            assertEquals(90f, updated?.x)
            assertEquals(160f, updated?.height)

            db.pageObjectDao().markDeleted(
                objectId = objectAId,
                deletedAt = now + 2,
                updatedAt = now + 2,
            )
            val visible = db.pageObjectDao().getByPageId(pageId)
            assertEquals(1, visible.size)
            assertEquals(objectBId, visible.first().objectId)

            db.pageDao().delete(pageId)
            val remaining = db.pageObjectDao().getByPageId(pageId)
            assertTrue(remaining.isEmpty())
        }
    }
}
