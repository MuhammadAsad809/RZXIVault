package com.rzxi.vault

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

class RZXIVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize SQLCipher native encryption binaries
        SQLiteDatabase.loadLibs(this)
    }
}
