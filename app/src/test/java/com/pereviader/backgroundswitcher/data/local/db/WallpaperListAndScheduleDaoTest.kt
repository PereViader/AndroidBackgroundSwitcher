package com.pereviader.backgroundswitcher.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import com.pereviader.backgroundswitcher.data.local.db.dao.AlbumDao
import com.pereviader.backgroundswitcher.data.local.db.dao.PhotoDao
import com.pereviader.backgroundswitcher.data.local.db.dao.ScheduleDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperListDao
import com.pereviader.backgroundswitcher.data.local.db.entity.AlbumEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.ListPhotoCrossRef
import com.pereviader.backgroundswitcher.data.local.db.entity.PhotoEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.ScheduleRuleEntity
import com.pereviader.backgroundswitcher.data.local.db.entity.WallpaperListEntity
import com.pereviader.backgroundswitcher.di.DatabaseModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WallpaperListAndScheduleDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var albumDao: AlbumDao
    private lateinit var photoDao: PhotoDao
    private lateinit var listDao: WallpaperListDao
    private lateinit var scheduleDao: ScheduleDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        albumDao = db.albumDao()
        photoDao = db.photoDao()
        listDao = db.wallpaperListDao()
        scheduleDao = db.scheduleDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `WallpaperListDao CRUD and ListPhotoCrossRef cascade delete work properly`() = runTest {
        val album = AlbumEntity(id = "a1", name = "Album 1", coverUri = null, photoCount = 2, source = "LOCAL", isSelected = true)
        albumDao.insertOrUpdate(album)

        val p1 = PhotoEntity(id = "p1", albumId = "a1", uri = "content://1", dateAdded = 100L)
        val p2 = PhotoEntity(id = "p2", albumId = "a1", uri = "content://2", dateAdded = 200L)
        photoDao.insertOrUpdateAll(listOf(p1, p2))

        val listWork = WallpaperListEntity(id = "list_work", name = "Work", isDefault = false, createdAt = 1000L)
        val listNature = WallpaperListEntity(id = "list_nature", name = "Nature", isDefault = false, createdAt = 2000L)
        listDao.insertList(listWork)
        listDao.insertList(listNature)

        // Assign p1 to both lists, p2 to list_work
        listDao.insertCrossRefs(
            listOf(
                ListPhotoCrossRef(listId = "list_work", photoId = "p1", addedAt = 100L),
                ListPhotoCrossRef(listId = "list_work", photoId = "p2", addedAt = 200L),
                ListPhotoCrossRef(listId = "list_nature", photoId = "p1", addedAt = 150L)
            )
        )

        assertThat(listDao.getPhotosCountInList("list_work")).isEqualTo(2)
        assertThat(listDao.getPhotosCountInList("list_nature")).isEqualTo(1)

        val workPhotos = listDao.observePhotosInList("list_work").first()
        assertThat(workPhotos.map { it.id }).containsExactly("p2", "p1").inOrder()

        // Deleting list_work cascades and deletes its cross-refs without deleting p1 or p2
        listDao.deleteListById("list_work")
        val remainingRefs = listDao.observeAllCrossRefs().first()
        assertThat(remainingRefs.map { it.listId to it.photoId }).containsExactly("list_nature" to "p1")
        assertThat(photoDao.getPhotoById("p1")).isNotNull()

        // Deleting photo p1 cascades and removes its cross-ref from list_nature
        photoDao.deletePhotoById("p1")
        assertThat(listDao.observeAllCrossRefs().first()).isEmpty()
    }

    @Test
    fun `insertOrUpdateAll does not delete cross refs when updating photo`() = runTest {
        val album = AlbumEntity(id = "a1", name = "Album 1", coverUri = null, photoCount = 1, source = "LOCAL", isSelected = true)
        albumDao.insertOrUpdate(album)
        val p1 = PhotoEntity(id = "p1", albumId = "a1", uri = "content://1", dateAdded = 100L)
        photoDao.insertOrUpdateAll(listOf(p1))
        val listWork = WallpaperListEntity(id = "list_work", name = "Work", isDefault = false, createdAt = 1000L)
        listDao.insertList(listWork)
        listDao.insertCrossRefs(listOf(ListPhotoCrossRef(listId = "list_work", photoId = "p1", addedAt = 100L)))
        assertThat(listDao.getPhotosCountInList("list_work")).isEqualTo(1)

        // Now simulate what refreshDeviceAlbums, syncAllSelectedAlbums, or toggleAlbumSelection does:
        photoDao.insertOrUpdateAll(listOf(p1.copy(lastDisplayedAt = 500L)))

        // Verify if list_work still has p1!
        assertThat(listDao.getPhotosCountInList("list_work")).isEqualTo(1)
    }

    @Test
    fun `PhotoDao list-scoped candidate queries only select photos belonging to target list`() = runTest {
        val album = AlbumEntity(id = "a1", name = "Album 1", coverUri = null, photoCount = 3, source = "LOCAL", isSelected = true)
        albumDao.insertOrUpdate(album)

        val p1 = PhotoEntity(id = "p1", albumId = "a1", uri = "content://1", dateAdded = 100L, lastDisplayedAt = 500L)
        val p2 = PhotoEntity(id = "p2", albumId = "a1", uri = "content://2", dateAdded = 200L, lastDisplayedAt = 100L)
        val p3 = PhotoEntity(id = "p3", albumId = "a1", uri = "content://3", dateAdded = 300L, lastDisplayedAt = 10L)
        photoDao.insertOrUpdateAll(listOf(p1, p2, p3))

        val listA = WallpaperListEntity(id = "list_a", name = "List A", isDefault = false)
        val listB = WallpaperListEntity(id = "list_b", name = "List B", isDefault = false)
        listDao.insertList(listA)
        listDao.insertList(listB)

        // Assign p1 and p2 to list_a; p3 to list_b
        listDao.insertCrossRefs(
            listOf(
                ListPhotoCrossRef(listId = "list_a", photoId = "p1"),
                ListPhotoCrossRef(listId = "list_a", photoId = "p2"),
                ListPhotoCrossRef(listId = "list_b", photoId = "p3")
            )
        )

        // LRU in list_a must pick p2 (100L) rather than p3 (10L, which is in list_b)
        val lruA = photoDao.getLeastRecentlyUsedPhotoInList("list_a")
        assertThat(lruA?.id).isEqualTo("p2")

        // LRU in list_a excluding p2 must pick p1
        val lruAExcludingP2 = photoDao.getLeastRecentlyUsedPhotoInListExcluding("list_a", "p2")
        assertThat(lruAExcludingP2?.id).isEqualTo("p1")

        // Random in list_b must only return p3
        val randomB = photoDao.getRandomPhotoInList("list_b")
        assertThat(randomB?.id).isEqualTo("p3")

        // Random in list_a excluding p1 must return p2
        val randomAExcludingP1 = photoDao.getRandomPhotoInListExcluding("list_a", "p1")
        assertThat(randomAExcludingP1?.id).isEqualTo("p2")

        // Chronological in list_a after 100L must return p2 (200L), and after 200L must return null (not p3!)
        val nextAfter100 = photoDao.getNextChronologicalPhotoInList("list_a", 100L)
        assertThat(nextAfter100?.id).isEqualTo("p2")
        val nextAfter200 = photoDao.getNextChronologicalPhotoInList("list_a", 200L)
        assertThat(nextAfter200).isNull()

        // Oldest in list_a is p1
        val oldestA = photoDao.getOldestPhotoInList("list_a")
        assertThat(oldestA?.id).isEqualTo("p1")
    }

    @Test
    fun `ScheduleDao orders rules by priority and cascades when target list is deleted`() = runTest {
        val list1 = WallpaperListEntity(id = "list_1", name = "List 1")
        val list2 = WallpaperListEntity(id = "list_2", name = "List 2")
        listDao.insertList(list1)
        listDao.insertList(list2)

        val ruleLowPriority = ScheduleRuleEntity(
            id = "r_low",
            listId = "list_1",
            screenScope = "HOME",
            priority = 1,
            startMinuteOfDay = 480,
            endMinuteOfDay = 1020,
            daysOfWeekMask = 127,
            isEnabled = true
        )
        val ruleHighPriority = ScheduleRuleEntity(
            id = "r_high",
            listId = "list_2",
            screenScope = "HOME",
            priority = 0,
            startMinuteOfDay = 540,
            endMinuteOfDay = 720,
            daysOfWeekMask = 127,
            isEnabled = true
        )
        scheduleDao.insertOrUpdateRules(listOf(ruleLowPriority, ruleHighPriority))

        val homeRules = scheduleDao.observeRulesByScope("HOME").first()
        assertThat(homeRules.map { it.id }).containsExactly("r_high", "r_low").inOrder()

        // Deleting list_2 cascades and deletes r_high
        listDao.deleteListById("list_2")
        val remainingHomeRules = scheduleDao.observeRulesByScope("HOME").first()
        assertThat(remainingHomeRules.map { it.id }).containsExactly("r_low")
    }

    @Test
    fun `ScheduleDao stores and retrieves GPS location rules with geofence parameters`() = runTest {
        val list1 = WallpaperListEntity(id = "list_loc", name = "Location List")
        listDao.insertList(list1)

        val gpsRule = ScheduleRuleEntity(
            id = "r_gps",
            listId = "list_loc",
            screenScope = "SHARED",
            priority = 0,
            ruleType = "GPS",
            latitude = 41.3851,
            longitude = 2.1734,
            radiusMeters = 250.0,
            isEnabled = true
        )
        scheduleDao.insertOrUpdateRule(gpsRule)

        val retrieved = scheduleDao.getRuleById("r_gps")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.ruleType).isEqualTo("GPS")
        assertThat(retrieved.latitude).isEqualTo(41.3851)
        assertThat(retrieved.longitude).isEqualTo(2.1734)
        assertThat(retrieved.radiusMeters).isEqualTo(250.0)
    }

    @Test
    fun `MIGRATION_7_8 creates new tables, seeds default_list, and migrates selected photos into cross_ref`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // In-memory SQLite database
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `albums` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `coverUri` TEXT,
                            `photoCount` INTEGER NOT NULL,
                            `source` TEXT NOT NULL,
                            `isSelected` INTEGER NOT NULL,
                            `sourceUrl` TEXT,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `photos` (
                            `id` TEXT NOT NULL,
                            `albumId` TEXT NOT NULL,
                            `uri` TEXT NOT NULL,
                            `width` INTEGER NOT NULL,
                            `height` INTEGER NOT NULL,
                            `dateAdded` INTEGER NOT NULL,
                            `lastDisplayedAt` INTEGER NOT NULL,
                            `cropX` REAL NOT NULL,
                            `cropY` REAL NOT NULL,
                            `zoom` REAL NOT NULL,
                            `isSelected` INTEGER NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`albumId`) REFERENCES `albums`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `wallpaper_customizations` (
                            `imageUri` TEXT NOT NULL,
                            `cropX` REAL NOT NULL,
                            `cropY` REAL NOT NULL,
                            `zoom` REAL NOT NULL,
                            `isSelected` INTEGER NOT NULL,
                            PRIMARY KEY(`imageUri`)
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val sqliteDb = helper.writableDatabase

        // Seed v7 data: 1 album, 2 selected photos, 1 unselected photo
        sqliteDb.execSQL("INSERT INTO `albums` VALUES ('a1', 'Camera', NULL, 3, 'LOCAL', 1, NULL)")
        sqliteDb.execSQL("INSERT INTO `photos` VALUES ('p_sel_1', 'a1', 'content://1', 1080, 1920, 100, 0, 0.5, 0.5, 1.0, 1)")
        sqliteDb.execSQL("INSERT INTO `photos` VALUES ('p_sel_2', 'a1', 'content://2', 1080, 1920, 200, 0, 0.5, 0.5, 1.0, 1)")
        sqliteDb.execSQL("INSERT INTO `photos` VALUES ('p_unsel', 'a1', 'content://3', 1080, 1920, 300, 0, 0.5, 0.5, 1.0, 0)")

        // Execute MIGRATION_7_8
        DatabaseModule.MIGRATION_7_8.migrate(sqliteDb)

        // Verify default_list was seeded
        sqliteDb.query("SELECT id, name, isDefault FROM wallpaper_lists").use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo(WallpaperList.DEFAULT_LIST_ID)
            assertThat(cursor.getString(1)).isEqualTo(WallpaperList.DEFAULT_LIST_NAME)
            assertThat(cursor.getInt(2)).isEqualTo(1)
        }

        // Verify only selected photos were migrated into list_photo_cross_ref for default_list
        val migratedPhotoIds = mutableListOf<String>()
        sqliteDb.query("SELECT listId, photoId FROM list_photo_cross_ref ORDER BY photoId ASC").use { cursor ->
            while (cursor.moveToNext()) {
                assertThat(cursor.getString(0)).isEqualTo(WallpaperList.DEFAULT_LIST_ID)
                migratedPhotoIds.add(cursor.getString(1))
            }
        }
        assertThat(migratedPhotoIds).containsExactly("p_sel_1", "p_sel_2").inOrder()

        helper.close()
    }

    @Test
    fun `MIGRATION_8_9 adds targetScreen, rotationOrder, isDefaultRule, and seeds default schedule rules`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `wallpaper_lists` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `isDefault` INTEGER NOT NULL,
                            `orderIndex` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `schedule_rules` (
                            `id` TEXT NOT NULL,
                            `listId` TEXT NOT NULL,
                            `screenScope` TEXT NOT NULL,
                            `priority` INTEGER NOT NULL,
                            `startMinuteOfDay` INTEGER NOT NULL,
                            `endMinuteOfDay` INTEGER NOT NULL,
                            `daysOfWeekMask` INTEGER NOT NULL,
                            `isEnabled` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("INSERT INTO `wallpaper_lists` VALUES ('default_list', 'My Wallpapers', 1, 0, 100)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val sqliteDb = helper.writableDatabase

        DatabaseModule.MIGRATION_8_9.migrate(sqliteDb)

        sqliteDb.query("SELECT id, targetScreen, rotationOrder FROM wallpaper_lists WHERE id = 'default_list'").use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(cursor.getString(1)).isEqualTo("BOTH")
            assertThat(cursor.getString(2)).isEqualTo("LEAST_RECENTLY_USED")
        }

        sqliteDb.query("SELECT id, listId, screenScope, isDefaultRule FROM schedule_rules").use { cursor ->
            assertThat(cursor.count).isEqualTo(3)
        }

        helper.close()
    }

    @Test
    fun `WallpaperListDao updateListDetails updates name, targetScreen, and rotationOrder`() = runTest {
        val list = WallpaperListEntity(id = "list_1", name = "Original", targetScreen = "BOTH", rotationOrder = "LEAST_RECENTLY_USED")
        listDao.insertList(list)

        listDao.updateListDetails("list_1", "Updated Name", "HOME", "RANDOM")
        val fetched = listDao.getListById("list_1")
        assertThat(fetched?.name).isEqualTo("Updated Name")
        assertThat(fetched?.targetScreen).isEqualTo("HOME")
        assertThat(fetched?.rotationOrder).isEqualTo("RANDOM")
    }
}
