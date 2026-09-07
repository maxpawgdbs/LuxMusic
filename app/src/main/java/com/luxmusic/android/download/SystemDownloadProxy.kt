package com.luxmusic.android.download

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

/** Native Python does not inherit Android's Java/Wi-Fi proxy selection. */
internal object SystemDownloadProxy {
    fun forUrl(url: String, selector: ProxySelector? = ProxySelector.getDefault()): String? = runCatching {
        val uri = URI(if (url.startsWith("ytsearch", true)) "https://www.youtube.com/" else url)
        val proxy = selector?.select(uri)?.firstOrNull() ?: return null
        if (proxy.type() != Proxy.Type.HTTP) return null
        val address = proxy.address() as? InetSocketAddress ?: return null
        if (address.port !in 1..65535) return null
        URI("http", null, address.hostString, address.port, null, null, null).toASCIIString()
    }.getOrNull()
}
