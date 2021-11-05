package ooo.akito.webmon.worker


import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ooo.akito.webmon.data.db.WebSiteEntry
import ooo.akito.webmon.data.repository.WebSiteEntryRepository
import ooo.akito.webmon.utils.ExceptionCompanion.msgErrorTryingToFetchData
import ooo.akito.webmon.utils.ExceptionCompanion.msgWebsiteEntriesUnavailable
import ooo.akito.webmon.utils.ExceptionCompanion.msgWebsitesNotReachable
import ooo.akito.webmon.utils.Log
import ooo.akito.webmon.utils.Utils
import ooo.akito.webmon.utils.Utils.associateByUrl
import ooo.akito.webmon.utils.Utils.getStringNotWorking
import ooo.akito.webmon.utils.Utils.joinToStringDescription


class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
  CoroutineWorker(appContext, workerParams) {
  private lateinit var repository: WebSiteEntryRepository

  override suspend fun doWork(): Result {
    /*
      Running according to set interval.
      Shows Notifications for failed Websites, when App is in Background.
    */

    if (Utils.appIsVisible()) {
      /*
        We only want to send notifications, when App is in Background.
      */
      return Result.success()
    }

    val applicationContext: Context = applicationContext

    repository = WebSiteEntryRepository(applicationContext)

    Log.info("Fetching Data from Remote hosts...")
    return try {
      val urlToWebsite: Map<String, WebSiteEntry> = repository.getAllWebSiteEntryList().value?.associateByUrl()
        ?: throw IllegalStateException(msgWebsiteEntriesUnavailable)
      val entriesWithFailedConnection =
        repository.checkWebSiteStatus().filter {
          val currentWebSite = urlToWebsite[it.url] ?: return@filter false
          Utils.mayNotifyStatusFailure(currentWebSite)
        }
      if (entriesWithFailedConnection.size == 1) {
        val entryWithFailedConnection = entriesWithFailedConnection.first()
        Utils.showNotification(
          applicationContext,
          entryWithFailedConnection.name,
          applicationContext.getStringNotWorking(entryWithFailedConnection.url)
        )
      } else {
        Utils.showNotification(
          applicationContext,
          msgWebsitesNotReachable,
          entriesWithFailedConnection.joinToStringDescription()
        )
      }
      Result.success()
    } catch (e: Throwable) {
      e.printStackTrace()
      Log.error(msgErrorTryingToFetchData + e.message)
      Result.failure()
    }
  }
}