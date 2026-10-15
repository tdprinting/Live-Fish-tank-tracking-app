package com.reeflive.app.data.repo

import java.io.File

/**
 * Where "My tank" lives on the phone: one versioned JSON file (`files/my_tank.json`, core `CustomTankCodec`) in app
 * storage, which an app update never touches. Written atomically (tmp + rename) with the previous good copy kept as
 * `.bak`, so a crash mid-write or a damaged file falls back to the last save instead of an empty tank. Called on the
 * sim thread only.
 */
class CustomTankStore(private val file: File) {
    private val backup get() = File(file.parentFile, file.name + ".bak")

    /** The saved text, or null for no tank yet (never set up, or started over). */
    fun read(): String? = runCatching { file.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    /** The previous save, for when [read]'s text turned out not to be a save. */
    fun readBackup(): String? = runCatching { backup.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    fun write(text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (file.isFile) file.copyTo(backup, overwrite = true)
            if (!tmp.renameTo(file)) { file.writeText(text); tmp.delete() }
        }
    }

    /** "Start over": the tank goes, its last save stays as `.bak` until the next tank is saved. */
    fun clear() {
        runCatching { if (file.isFile) { file.copyTo(backup, overwrite = true); file.delete() } }
    }

    companion object {
        const val FILE_NAME = "my_tank.json"
    }
}
