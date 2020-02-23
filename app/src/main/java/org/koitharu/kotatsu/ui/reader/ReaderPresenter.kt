package org.koitharu.kotatsu.ui.reader

import android.content.ContentResolver
import android.webkit.URLUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moxy.InjectViewState
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.core.model.MangaPage
import org.koitharu.kotatsu.domain.MangaProviderFactory
import org.koitharu.kotatsu.domain.history.HistoryRepository
import org.koitharu.kotatsu.ui.common.BasePresenter
import org.koitharu.kotatsu.utils.MediaStoreCompat
import org.koitharu.kotatsu.utils.ext.await
import org.koitharu.kotatsu.utils.ext.contentDisposition
import org.koitharu.kotatsu.utils.ext.mimeType

@InjectViewState
class ReaderPresenter : BasePresenter<ReaderView>() {

	fun loadChapter(state: ReaderState) {
		launch {
			viewState.onLoadingStateChanged(isLoading = true)
			try {
				val pages = withContext(Dispatchers.IO) {
					val repo = MangaProviderFactory.create(state.manga.source)
					val chapter = state.chapter ?: repo.getDetails(state.manga).chapters
						?.first { it.id == state.chapterId }
					?: throw RuntimeException("Chapter ${state.chapterId} not found")
					repo.getPages(chapter)
				}
				viewState.onPagesReady(pages, state.page)
			} catch (e: Exception) {
				if (BuildConfig.DEBUG) {
					e.printStackTrace()
				}
				viewState.onError(e)
			} finally {
				viewState.onLoadingStateChanged(isLoading = false)
			}
		}
	}

	fun saveState(state: ReaderState) {
		launch(Dispatchers.IO) {
			HistoryRepository().addOrUpdate(
				manga = state.manga,
				chapterId = state.chapterId,
				page = state.page
			)
		}
	}

	fun savePage(resolver: ContentResolver, page: MangaPage) {
		launch(Dispatchers.IO) {
			try {
				val repo = MangaProviderFactory.create(page.source)
				val url = repo.getPageFullUrl(page)
				val request = Request.Builder()
					.url(url)
					.get()
					.build()
				val uri = getKoin().get<OkHttpClient>().newCall(request).await().use { response ->
					val fileName =
						URLUtil.guessFileName(url, response.contentDisposition, response.mimeType)
					MediaStoreCompat.insertImage(resolver, fileName) {
						response.body!!.byteStream().copyTo(it)
					}
				}
				withContext(Dispatchers.Main) {
					viewState.onPageSaved(uri)
				}
			} catch (e: CancellationException) {
			} catch (e: Exception) {
				withContext(Dispatchers.Main) {
					viewState.onPageSaved(null)
				}
			}
		}
	}

}