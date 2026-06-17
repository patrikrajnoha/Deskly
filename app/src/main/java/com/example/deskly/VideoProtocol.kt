package com.example.deskly

import org.json.JSONArray
import org.json.JSONObject

object VideoProtocol {
    const val TYPE_LIST = "video_list"
    const val RESPONSE_TYPE = "video_list_response"

    data class VideoItem(
        val id: String,
        val title: String,
        val source: String,
        val playbackState: String,
        val controllable: Boolean
    )

    data class ListResult(
        val supported: Boolean,
        val videos: List<VideoItem>,
        val fallback: String
    )

    fun refreshPayload(): JSONObject =
        JSONObject().put("action", "refresh")

    fun unsupportedFallback(reason: String = "Automatic video detection is not available on this host."): JSONObject =
        JSONObject()
            .put("supported", false)
            .put("videos", JSONArray())
            .put("fallback", "media_remote")
            .put("reason", reason)

    fun parseListResult(data: JSONObject?): ListResult {
        if (data == null) {
            return ListResult(supported = false, videos = emptyList(), fallback = "media_remote")
        }

        val videosJson = data.optJSONArray("videos") ?: JSONArray()
        val videos = buildList {
            for (index in 0 until videosJson.length()) {
                val item = videosJson.optJSONObject(index) ?: continue
                add(
                    VideoItem(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        source = item.optString("source"),
                        playbackState = item.optString("playbackState", "unknown"),
                        controllable = item.optBoolean("controllable", false)
                    )
                )
            }
        }

        return ListResult(
            supported = data.optBoolean("supported", false),
            videos = videos,
            fallback = data.optString("fallback", "media_remote")
        )
    }
}
