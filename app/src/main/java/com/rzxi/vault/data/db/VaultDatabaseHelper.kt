package com.rzxi.vault.data.db

import android.content.ContentValues
import android.content.Context
import com.rzxi.vault.data.model.VaultItem
import com.rzxi.vault.data.model.VaultType
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import java.io.File
import java.security.SecureRandom
import java.util.Arrays

class VaultDatabaseHelper private constructor(
    private val context: Context,
    private val dbPassphrase: ByteArray
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "rzxi_vault_encrypted.db"
        private const val DATABASE_VERSION = 1
        const val MASTER_KEY_FILE = "rzxi_master_key.enc"

        const val TABLE_VAULT = "vault_records"
        const val COL_ID = "id"
        const val COL_UUID = "uuid"
        const val COL_TYPE = "type"
        const val COL_TITLE = "title"
        const val COL_USERNAME = "username"
        const val COL_SECRET = "secret"
        const val COL_WEBSITE = "website"
        const val COL_METADATA = "metadata"
        const val COL_UPDATED_AT = "updated_at"

        @Volatile
        private var activeHelper: VaultDatabaseHelper? = null

        fun init(context: Context, key: ByteArray): VaultDatabaseHelper {
            return activeHelper ?: synchronized(this) {
                activeHelper ?: VaultDatabaseHelper(context.applicationContext, key).also {
                    activeHelper = it
                }
            }
        }

        fun get(): VaultDatabaseHelper {
            return activeHelper ?: throw IllegalStateException("Vault is locked! Unlock with biometrics first.")
        }

        fun isUnlocked(): Boolean = activeHelper != null

        fun isInitialized(context: Context): Boolean {
            val keyFile = File(context.filesDir, MASTER_KEY_FILE)
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            return keyFile.exists() && dbFile.exists()
        }

        fun getKeyStorageFile(context: Context): File {
            return File(context.filesDir, MASTER_KEY_FILE)
        }

        fun generateRandomKey(size: Int = 32): ByteArray {
            val buf = ByteArray(size)
            SecureRandom().nextBytes(buf)
            return buf
        }
    }

    init {
        SQLiteDatabase.loadLibs(context)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_VAULT (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_UUID TEXT UNIQUE NOT NULL,
                $COL_TYPE TEXT NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_USERNAME TEXT,
                $COL_SECRET TEXT NOT NULL,
                $COL_WEBSITE TEXT,
                $COL_METADATA TEXT,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_vault_type ON $TABLE_VAULT ($COL_TYPE)")
        db.execSQL("CREATE INDEX idx_vault_title ON $TABLE_VAULT ($COL_TITLE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema upgrade logic if needed in future releases
    }

    private fun getDb(): SQLiteDatabase {
        return getWritableDatabase(dbPassphrase)
    }

    fun getAllItems(): List<VaultItem> {
        val list = mutableListOf<VaultItem>()
        val db = getDb()
        val cursor = db.query(
            TABLE_VAULT,
            null,
            null,
            null,
            null,
            null,
            "$COL_UPDATED_AT DESC"
        )

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ID)
            val uuidIdx = c.getColumnIndexOrThrow(COL_UUID)
            val typeIdx = c.getColumnIndexOrThrow(COL_TYPE)
            val titleIdx = c.getColumnIndexOrThrow(COL_TITLE)
            val userIdx = c.getColumnIndexOrThrow(COL_USERNAME)
            val secretIdx = c.getColumnIndexOrThrow(COL_SECRET)
            val webIdx = c.getColumnIndexOrThrow(COL_WEBSITE)
            val metaIdx = c.getColumnIndexOrThrow(COL_METADATA)
            val updatedIdx = c.getColumnIndexOrThrow(COL_UPDATED_AT)

            while (c.moveToNext()) {
                val typeStr = c.getString(typeIdx)
                val type = try {
                    VaultType.valueOf(typeStr)
                } catch (e: Exception) {
                    VaultType.PASSWORD
                }

                list.add(
                    VaultItem(
                        id = c.getLong(idIdx),
                        uuid = c.getString(uuidIdx),
                        type = type,
                        title = c.getString(titleIdx),
                        username = c.getString(userIdx) ?: "",
                        secret = c.getString(secretIdx) ?: "",
                        website = c.getString(webIdx) ?: "",
                        metadata = c.getString(metaIdx) ?: "",
                        updatedAt = c.getLong(updatedIdx)
                    )
                )
            }
        }
        return list
    }

    fun insertItem(item: VaultItem): Long {
        val db = getDb()
        val cv = ContentValues().apply {
            put(COL_UUID, item.uuid)
            put(COL_TYPE, item.type.name)
            put(COL_TITLE, item.title)
            put(COL_USERNAME, item.username)
            put(COL_SECRET, item.secret)
            put(COL_WEBSITE, item.website)
            put(COL_METADATA, item.metadata)
            put(COL_UPDATED_AT, item.updatedAt)
        }
        return db.insertWithOnConflict(TABLE_VAULT, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateItem(item: VaultItem): Int {
        val db = getDb()
        val cv = ContentValues().apply {
            put(COL_TITLE, item.title)
            put(COL_USERNAME, item.username)
            put(COL_SECRET, item.secret)
            put(COL_WEBSITE, item.website)
            put(COL_METADATA, item.metadata)
            put(COL_UPDATED_AT, System.currentTimeMillis())
        }
        return db.update(TABLE_VAULT, cv, "$COL_UUID = ?", arrayOf(item.uuid))
    }

    fun deleteItem(uuid: String): Int {
        val db = getDb()
        return db.delete(TABLE_VAULT, "$COL_UUID = ?", arrayOf(uuid))
    }

    fun lockAndWipe() {
        Arrays.fill(dbPassphrase, 0.toByte())
        activeHelper?.close()
        activeHelper = null
    }
}
