package dev.cniekirk.wikidroid.core.datastore.di

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.cniekirk.wikidroid.core.common.IoDispatcher
import dev.cniekirk.wikidroid.core.datastore.JsonSerializer
import dev.cniekirk.wikidroid.core.datastore.StoredUserData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

internal const val USER_DATA_FILE = "datastore/user_data.json"

/** Requires `Application` from the graph's factory. */
@BindingContainer
@ContributesTo(AppScope::class)
object DataStoreProviders {
    @Provides
    @SingleIn(AppScope::class)
    fun provideUserDataStore(
        application: Application,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<StoredUserData> =
        DataStoreFactory.create(
            serializer = JsonSerializer(StoredUserData.serializer(), defaultValue = StoredUserData()),
            // A file that can't be read is replaced by the defaults rather than crashing on launch.
            corruptionHandler = ReplaceFileCorruptionHandler { StoredUserData() },
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = { File(application.filesDir, USER_DATA_FILE) },
        )
}
