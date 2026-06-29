package com.galaxy.tunnel.fmt

import android.util.Base64
import java.net.URLDecoder

object V2RayConfigParser {

    /**
     * Parse various V2Ray URLs (vless://, trojan://, vmess://)
     */
    fun parseUrl(url: String): Map<String, String>? {
        return when {
            url.startsWith("vless://") -> parseVless(url)
            url.startsWith("trojan://") -> parseTrojan(url)
            url.startsWith("vmess://") -> parseVmess(url)
            else -> null
        }
    }

    private fun parseVless(url: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val uri = url.substring(8)
        val atIndex = uri.indexOf("@")
        val queryIndex = uri.indexOf("?")
        val fragmentIndex = uri.indexOf("#")

        result["id"] = uri.substring(0, atIndex)
        val hostPort = uri.substring(atIndex + 1, if (queryIndex != -1) queryIndex else fragmentIndex)
        val parts = hostPort.split(":")
        result["address"] = parts[0]
        result["port"] = if (parts.size > 1) parts[1] else "443"

        if (queryIndex != -1) {
            val query = uri.substring(queryIndex + 1, if (fragmentIndex != -1) fragmentIndex else uri.length)
            query.split("&").forEach {
                val pair = it.split("=")
                if (pair.size == 2) result[pair[0]] = URLDecoder.decode(pair[1], "UTF-8")
            }
        }
        
        if (fragmentIndex != -1) {
            result["remark"] = URLDecoder.decode(uri.substring(fragmentIndex + 1), "UTF-8")
        }

        result["protocol"] = "vless"
        return result
    }

    private fun parseTrojan(url: String): Map<String, String> {
        // Similar to VLESS but protocol is trojan
        val result = parseVless(url.replace("trojan://", "vless://")).toMutableMap()
        result["protocol"] = "trojan"
        return result
    }

    private fun parseVmess(url: String): Map<String, String>? {
        return try {
            val jsonBase64 = url.substring(8)
            val json = String(Base64.decode(jsonBase64, Base64.DEFAULT))
            // Parse VMess JSON (Simplified)
            val result = mutableMapOf<String, String>()
            result["protocol"] = "vmess"
            // Add JSON parsing logic here (using Gson or Kotlin Serialization)
            result
        } catch (e: Exception) {
            null
        }
    }
}
