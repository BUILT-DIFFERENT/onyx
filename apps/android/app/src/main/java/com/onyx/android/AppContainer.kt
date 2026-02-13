package com.onyx.android

import android.content.Context
import com.onyx.android.data.OnyxDatabase
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.device.DeviceIdentity
import com.onyx.android.pdf.PdfPasswordStore
import com.onyx.android.recognition.MyScriptEngine

interface AppContainer {
    val database: OnyxDatabase
    val noteRepository: NoteRepository
    val deviceIdentity: DeviceIdentity
    val pdfPasswordStore: PdfPasswordStore
    val myScriptEngine: MyScriptEngine
}

fun Context.requireAppContainer(): AppContainer {
    val appContext = applicationContext
    return appContext as? AppContainer
        ?: error("Application does not implement AppContainer: ${appContext::class.java.name}")
}
