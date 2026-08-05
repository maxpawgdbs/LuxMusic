package com.luxmusic.android

import android.app.Application
import com.luxmusic.android.data.LibraryStore
import com.luxmusic.android.download.DownloadAccountStore
import com.luxmusic.android.download.LinkDownloader
import com.luxmusic.android.playback.PlaybackGateway

class LuxMusicApp : Application() {
    val libraryStore by lazy { LibraryStore(this) }
    val playbackGateway by lazy { PlaybackGateway(this) }
    val downloadAccountStore by lazy { DownloadAccountStore(this) }
    val linkDownloader by lazy { LinkDownloader(this, libraryStore, downloadAccountStore) }
}
