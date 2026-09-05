package com.luxmusic.android

import android.app.Application
import com.luxmusic.android.data.LibraryStore
import com.luxmusic.android.download.LinkDownloader
import com.luxmusic.android.playback.PlaybackGateway

class LuxMusicApp : Application() {
    val messages = AppMessages()
    val libraryStore by lazy { LibraryStore(this, messages::emit) }
    val playbackGateway by lazy { PlaybackGateway(this, messages) }
    val linkDownloader by lazy { LinkDownloader(this, libraryStore) }
}
