package org.koitharu.kotatsu.ui.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.app.NotificationCompat
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.Manga
import org.koitharu.kotatsu.ui.details.MangaDetailsActivity
import org.koitharu.kotatsu.utils.ext.clearActions
import org.koitharu.kotatsu.utils.ext.getDisplayMessage
import kotlin.math.roundToInt

class DownloadNotification(private val context: Context) {

	private val builder = NotificationCompat.Builder(context, CHANNEL_ID)
	private val manager =
		context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

	init {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
			&& manager.getNotificationChannel(CHANNEL_ID) == null) {
			val channel = NotificationChannel(
				CHANNEL_ID,
				context.getString(R.string.downloads),
				NotificationManager.IMPORTANCE_LOW
			)
			channel.enableVibration(false)
			manager.createNotificationChannel(channel)
		}
		builder.setOnlyAlertOnce(true)
	}

	fun fillFrom(manga: Manga) {
		builder.setContentTitle(manga.title)
		builder.setContentText(context.getString(R.string.manga_downloading_))
		builder.setProgress(1, 0, true)
		builder.setSmallIcon(android.R.drawable.stat_sys_download)
		builder.setLargeIcon(null)
		builder.setContentIntent(null)
	}

	fun setCancelId(startId: Int) {
		if (startId == 0) {
			builder.clearActions()
		} else {
			val intent = DownloadService.getCancelIntent(context, startId)
			builder.addAction(
				R.drawable.ic_cross,
				context.getString(android.R.string.cancel),
				PendingIntent.getService(
					context,
					startId,
					intent,
					PendingIntent.FLAG_CANCEL_CURRENT
				)
			)
		}
	}

	fun setError(e: Throwable) {
		builder.setProgress(0, 0, false)
		builder.setSmallIcon(android.R.drawable.stat_notify_error)
		builder.setSubText(context.getString(R.string.error))
		builder.setContentText(e.getDisplayMessage(context.resources))
		builder.setAutoCancel(true)
		builder.setContentIntent(null)
	}

	fun setLargeIcon(icon: Drawable?) {
		builder.setLargeIcon((icon as? BitmapDrawable)?.bitmap)
	}

	fun setProgress(chaptersTotal: Int, pagesTotal: Int, chapter: Int, page: Int) {
		val max = chaptersTotal * PROGRESS_STEP
		val progress =
			chapter * PROGRESS_STEP + (page / pagesTotal.toFloat() * PROGRESS_STEP).roundToInt()
		val percent = (progress / max.toFloat() * 100).roundToInt()
		builder.setProgress(max, progress, false)
		builder.setContentText("%d%%".format(percent))
	}

	fun setPostProcessing() {
		builder.setProgress(1, 0, true)
		builder.setContentText(context.getString(R.string.processing_))
	}

	fun setDone(manga: Manga) {
		builder.setProgress(0, 0, false)
		builder.setContentText(context.getString(R.string.download_complete))
		builder.setContentIntent(createIntent(context, manga))
		builder.setAutoCancel(true)
		builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
	}

	fun setCancelling() {
		builder.setProgress(1, 0, true)
		builder.setContentText(context.getString(R.string.cancelling_))
		builder.setContentIntent(null)
	}

	fun update(id: Int = NOTIFICATION_ID) {
		manager.notify(id, builder.build())
	}

	fun dismiss(id: Int = NOTIFICATION_ID) {
		manager.cancel(id)
	}

	operator fun invoke(): Notification = builder.build()

	companion object {

		const val NOTIFICATION_ID = 201
		const val CHANNEL_ID = "download"

		private const val PROGRESS_STEP = 20

		@JvmStatic
		private fun createIntent(context: Context, manga: Manga) = PendingIntent.getActivity(
			context,
			manga.hashCode(),
			MangaDetailsActivity.newIntent(context, manga),
			PendingIntent.FLAG_CANCEL_CURRENT
		)
	}
}