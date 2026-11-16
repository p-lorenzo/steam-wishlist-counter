package dev.wishlist.counter

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class WishlistApplication : Application() {
    val store by lazy{LocalStore(this)}
    val key by lazy{SecureKey(this)}
    val preferences by lazy{Preferences(this)}
    val client by lazy{SteamClient()}
    val sync by lazy{SyncEngine(this)}
    override fun onCreate(){super.onCreate();scheduleSync()}
    fun scheduleSync(){
        if(preferences.raw.getBoolean("background",true)) {
            val hours=preferences.raw.getLong("interval",6).coerceIn(1,24)
            WorkManager.getInstance(this).enqueueUniquePeriodicWork("wishlist-sync",ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WishlistSyncWorker>(hours,TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }else WorkManager.getInstance(this).cancelUniqueWork("wishlist-sync")
    }
}
class WishlistSyncWorker(context: Context,parameters: WorkerParameters):CoroutineWorker(context,parameters){
    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        val app=applicationContext as WishlistApplication
        if(app.key.read()==null||app.store.games().isEmpty())return@withContext Result.success()
        try{app.sync.sync();Result.success()}catch(error:SteamFailure){if(error.retryable)Result.retry()else Result.failure()}
    }
}
