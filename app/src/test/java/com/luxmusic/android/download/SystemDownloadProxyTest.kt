package com.luxmusic.android.download

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class SystemDownloadProxyTest {
    @Test fun `native requests inherit the selected system HTTP proxy`() {
        val selector = Selector(listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example", 8080))))
        assertEquals("http://proxy.example:8080", SystemDownloadProxy.forUrl("https://bandcamp.com/track/test", selector))
        assertEquals("bandcamp.com", selector.requested?.host)
    }

    @Test fun `catalog search selects the YouTube proxy`() {
        val selector = Selector(listOf(Proxy.NO_PROXY))
        assertNull(SystemDownloadProxy.forUrl("ytsearch1:Artist Song audio", selector))
        assertEquals("www.youtube.com", selector.requested?.host)
    }

    @Test fun `a direct first choice is respected including bypassed hosts`() {
        val selector = Selector(listOf(Proxy.NO_PROXY, Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example", 80))))
        assertNull(SystemDownloadProxy.forUrl("http://127.0.0.1/file", selector))
        assertNull(SystemDownloadProxy.forUrl("https://example.com", null))
    }

    @Test fun `invalid URLs and broken selectors do not crash downloads`() {
        assertNull(SystemDownloadProxy.forUrl("not a uri", Selector(emptyList())))
        assertNull(SystemDownloadProxy.forUrl("https://example.com", object : Selector(emptyList()) {
            override fun select(uri: URI): List<Proxy> = error("Proxy service unavailable")
        }))
    }

    @Test fun `IPv6 proxy addresses are correctly bracketed`() {
        val selector = Selector(listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("::1", 8080))))
        assertEquals("http://[::1]:8080", SystemDownloadProxy.forUrl("https://example.com", selector))
    }

    private open class Selector(private val proxies: List<Proxy>) : ProxySelector() {
        var requested: URI? = null
        override fun select(uri: URI): List<Proxy> { requested = uri; return proxies }
        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
    }
}
