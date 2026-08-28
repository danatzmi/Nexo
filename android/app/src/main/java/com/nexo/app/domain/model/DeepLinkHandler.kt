package com.nexo.app.domain.model

import java.net.URI

sealed interface DeepLink {
    data class JoinGym(val code: String) : DeepLink
}

/**
 * Parses a join-gym deep link — mirrors iOS's `DeepLinkParser`. Supports
 * (in priority order): a `?code=` query parameter, a `/join/{code}` path
 * segment (covers `https://nexo.fit/join/{code}`), and the `nexo://`
 * custom scheme (`nexo://join/{code}` or `nexo://{code}` directly).
 * Built on `java.net.URI` rather than `android.net.Uri` so it's a plain,
 * JVM-testable function with no Android framework dependency.
 */
object DeepLinkParser {
    fun parse(urlString: String): DeepLink? {
        val uri = runCatching { URI(urlString) }.getOrNull() ?: return null

        uri.query?.let { query ->
            val code = query.split("&")
                .mapNotNull { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2 && parts[0].equals("code", ignoreCase = true)) parts[1].trim() else null
                }
                .firstOrNull { it.isNotEmpty() }
            if (code != null) return DeepLink.JoinGym(code.uppercase())
        }

        val pathComponents = uri.path?.split("/")?.filter { it.isNotEmpty() } ?: emptyList()
        val joinIndex = pathComponents.indexOfFirst { it.equals("join", ignoreCase = true) }
        if (joinIndex != -1 && joinIndex + 1 < pathComponents.size) {
            val code = pathComponents[joinIndex + 1].trim()
            if (code.isNotEmpty()) return DeepLink.JoinGym(code.uppercase())
        }

        if (uri.scheme.equals("nexo", ignoreCase = true)) {
            if (uri.host.equals("join", ignoreCase = true)) {
                pathComponents.firstOrNull()?.let { return DeepLink.JoinGym(it.uppercase()) }
            } else {
                val host = uri.host
                if (host != null && host.length in 4..10) return DeepLink.JoinGym(host.uppercase())
            }
        }

        return null
    }
}
