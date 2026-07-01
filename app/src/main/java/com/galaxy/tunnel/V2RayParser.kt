package com.galaxy.tunnel

import android.util.Base64
import org.json.JSONObject

object V2RayParser {

    fun convertUrlToJson(uri: String): String {
        return when {
            uri.startsWith("vless://") -> parseVlessUri(uri) ?: ""
            uri.startsWith("vmess://") -> parseVmessUri(uri) ?: ""
            else -> ""
        }
    }

    private fun parseVlessUri(uri: String): String? {
        if (!uri.startsWith("vless://")) return null

        try {
            val cleanUri = uri.replace("vless://", "")
            val parts = cleanUri.split("@", "?", "#")
            if (parts.size < 2) return null

            val uuid = parts[0]
            val serverPart = parts[1].split(":")
            if (serverPart.size < 2) return null

            val address = serverPart[0]
            val port = serverPart[1].toIntOrNull() ?: 443

            val queryParams = if (parts.size > 2) parseQueryString(parts[2]) else mapOf()
            val remark = if (parts.size > 3) parts[3] else "Server"

            return buildVlessJson(uuid, address, port, queryParams, remark)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseQueryString(query: String): Map<String, String> {
        return query.split("&").mapNotNull { param ->
            val kv = param.split("=")
            if (kv.size == 2) kv[0] to kv[1] else null
        }.toMap()
    }

    private fun buildVlessJson(
        uuid: String, address: String, port: Int,
        params: Map<String, String>, remark: String
    ): String {
        val security = params["security"] ?: "tls"
        val sni = params["sni"] ?: address
        val path = params["path"] ?: "/"
        val type = params["type"] ?: "tcp"
        val fp = params["fp"] ?: "chrome"

        val config = JSONObject().apply {
            put("log", JSONObject().put("loglevel", "warning"))
            put("inbounds", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("tag", "socks")
                    put("port", 10808)
                    put("protocol", "socks")
                    put("settings", JSONObject().apply {
                        put("auth", "noauth")
                        put("udp", true)
                        put("ip", "127.0.0.1")
                    })
                })
            })
            put("outbounds", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("tag", "proxy")
                    put("protocol", "vless")
                    put("settings", JSONObject().apply {
                        put("vnext", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("address", address)
                                put("port", port)
                                put("users", org.json.JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("id", uuid)
                                        put("security", "auto")
                                        put("encryption", "none")
                                    })
                                })
                            })
                        })
                    })
                    put("streamSettings", JSONObject().apply {
                        put("network", type)
                        put("security", security)
                        if (security == "tls") {
                            put("tlsSettings", JSONObject().apply {
                                put("serverName", sni)
                                put("fingerprint", fp)
                            })
                        }
                        if (type == "ws") {
                            put("wsSettings", JSONObject().apply {
                                put("path", path)
                                put("headers", JSONObject().put("Host", sni))
                            })
                        }
                    })
                })
                put(JSONObject().apply {
                    put("tag", "direct")
                    put("protocol", "freedom")
                })
                put(JSONObject().apply {
                    put("tag", "block")
                    put("protocol", "blackhole")
                })
            })
            put("routing", JSONObject().apply {
                put("domainStrategy", "IPIfNonMatch")
                put("rules", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "field")
                        put("ip", org.json.JSONArray().apply {
                            put("geoip:private")
                        })
                        put("outboundTag", "direct")
                    })
                })
            })
        }

        return config.toString()
    }

    private fun parseVmessUri(uri: String): String? {
        if (!uri.startsWith("vmess://")) return null
        return try {
            val base64 = uri.replace("vmess://", "")
            String(Base64.decode(base64, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
