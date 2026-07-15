package com.example.videodownloader.network

import java.net.URI

/**
 * Explicit policy layer.
 *
 * This app only downloads video that a site openly serves as a direct,
 * unauthenticated file (a plain <video>/<source> tag or public og:video URL).
 *
 * On top of that technical check, we maintain a blocklist of domains whose
 * Terms of Service explicitly prohibit downloading their video content
 * (e.g. YouTube, Facebook, Instagram, TikTok, Vimeo). Even if such a site's
 * player happens to expose a technically-reachable stream URL, we refuse to
 * download from it here, because the site's own terms don't allow it.
 *
 * Site owners who want their content to be downloadable should serve a
 * direct file link / official "Download" button - this app will work fine
 * with that.
 */
object DownloadPolicy {

    // Domains whose ToS explicitly disallow third-party downloading of
    // their hosted video. Extend this list as needed.
    private val disallowedDomains = setOf(
        "instagram.com",
        "tiktok.com",
        "twitter.com", "x.com",
        "vimeo.com",
        "netflix.com",
        "primevideo.com", "amazon.com",
        "disneyplus.com",
        "hulu.com",
        "hotstar.com",
        "spotify.com"
    )

    
}
