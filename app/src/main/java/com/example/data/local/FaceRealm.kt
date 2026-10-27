package com.example.data.local

import android.content.Context
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration

object FaceRealm {
    @Volatile
    private var instance: Realm? = null

    fun get(context: Context): Realm {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: Realm.open(
                RealmConfiguration.Builder(schema = setOf(FaceTemplate::class))
                    .name("face_cache.realm")
                    .directory(context.applicationContext.filesDir.path)
                    .deleteRealmIfMigrationNeeded()
                    .build()
            ).also { instance = it }
        }
    }
}
