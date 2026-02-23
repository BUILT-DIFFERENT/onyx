package com.onyx.android.di

import android.content.Context
import com.onyx.android.data.OnyxDatabase
import com.onyx.android.data.dao.EditorSettingsDao
import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.OperationLogDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.dao.PageObjectDao
import com.onyx.android.data.dao.PageTemplateDao
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.data.serialization.StrokeSerializer
import com.onyx.android.data.thumbnail.ThumbnailGenerator
import com.onyx.android.device.DeviceIdentity
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.pdf.PdfDocumentInfoReader
import com.onyx.android.pdf.PdfPasswordStore
import com.onyx.android.pdf.PdfiumDocumentInfoReader
import com.onyx.android.recognition.MyScriptEngine
import com.onyx.android.recognition.MyScriptPageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
object AppModule {
    @Provides
    @Singleton
    fun provideOnyxDatabase(
        @ApplicationContext context: Context,
    ): OnyxDatabase = OnyxDatabase.build(context)

    @Provides
    @Singleton
    fun provideDeviceIdentity(
        @ApplicationContext context: Context,
    ): DeviceIdentity = DeviceIdentity(context)

    @Provides
    @Singleton
    fun providePdfPasswordStore(): PdfPasswordStore = PdfPasswordStore()

    @Provides
    @Singleton
    fun providePdfAssetStorage(
        @ApplicationContext context: Context,
    ): PdfAssetStorage = PdfAssetStorage(context)

    @Provides
    @Singleton
    fun providePdfDocumentInfoReader(
        @ApplicationContext context: Context,
    ): PdfDocumentInfoReader = PdfiumDocumentInfoReader(context)

    @Provides
    @Singleton
    fun provideThumbnailGenerator(
        @ApplicationContext context: Context,
        database: OnyxDatabase,
        pdfAssetStorage: PdfAssetStorage,
    ): ThumbnailGenerator =
        ThumbnailGenerator(
            context = context,
            thumbnailDao = database.thumbnailDao(),
            noteDao = database.noteDao(),
            pageDao = database.pageDao(),
            pageTemplateDao = database.pageTemplateDao(),
            pdfAssetStorage = pdfAssetStorage,
        )

    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun provideNoteRepository(
        @ApplicationContext context: Context,
        database: OnyxDatabase,
        deviceIdentity: DeviceIdentity,
        pdfAssetStorage: PdfAssetStorage,
        pdfPasswordStore: PdfPasswordStore,
        thumbnailGenerator: ThumbnailGenerator,
    ): NoteRepository =
        NoteRepository(
            noteDao = database.noteDao(),
            pageDao = database.pageDao(),
            pageObjectDao = database.pageObjectDao(),
            strokeDao = database.strokeDao(),
            recognitionDao = database.recognitionDao(),
            folderDao = database.folderDao(),
            pageTemplateDao = database.pageTemplateDao(),
            tagDao = database.tagDao(),
            deviceIdentity = deviceIdentity,
            strokeSerializer = StrokeSerializer,
            appContext = context,
            pdfAssetStorage = pdfAssetStorage,
            pdfPasswordStore = pdfPasswordStore,
            thumbnailGenerator = thumbnailGenerator,
        )

    @Provides
    @Singleton
    fun provideMyScriptEngine(
        @ApplicationContext context: Context,
    ): MyScriptEngine = MyScriptEngine(context)

    @Provides
    fun provideMyScriptPageManager(
        myScriptEngine: MyScriptEngine,
        @ApplicationContext context: Context,
    ): MyScriptPageManager =
        MyScriptPageManager(
            myScriptEngine = myScriptEngine,
            context = context,
        )

    @Provides
    fun provideNoteDao(database: OnyxDatabase): NoteDao = database.noteDao()

    @Provides
    fun providePageDao(database: OnyxDatabase): PageDao = database.pageDao()

    @Provides
    fun providePageObjectDao(database: OnyxDatabase): PageObjectDao = database.pageObjectDao()

    @Provides
    fun providePageTemplateDao(database: OnyxDatabase): PageTemplateDao = database.pageTemplateDao()

    @Provides
    fun provideEditorSettingsDao(database: OnyxDatabase): EditorSettingsDao = database.editorSettingsDao()

    @Provides
    fun provideOperationLogDao(database: OnyxDatabase): OperationLogDao = database.operationLogDao()
}
