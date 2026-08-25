package com.timclearscan.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FeedStore(context: Context) {
    private val prefs =
        context.getSharedPreferences("clearscan_feeds", Context.MODE_PRIVATE)

    fun load(): List<Feed> {
        if (!prefs.contains("feeds")) {
            val defaults = defaultPhoenixFeeds()
            save(defaults)
            return defaults
        }

        val raw = prefs.getString("feeds", "[]") ?: "[]"

        return runCatching {
            val arr = JSONArray(raw)

            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)

                    add(
                        Feed(
                            id = o.optString(
                                "id",
                                UUID.randomUUID().toString()
                            ),
                            name = o.optString(
                                "name",
                                "Unnamed feed"
                            ),
                            location = o.optString(
                                "location",
                                ""
                            ),
                            url = o.optString(
                                "url",
                                ""
                            ),
                            favorite = o.optBoolean(
                                "favorite",
                                false
                            ),
                            lastPlayedAt = o.optLong(
                                "lastPlayedAt",
                                0L
                            )
                        )
                    )
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    private fun defaultPhoenixFeeds(): List<Feed> = listOf(
        Feed(
            id = "phoenix-police",
            name = "Phoenix Police",
            location = "Phoenix PD dispatch",
            url = "https://broadcastify.cdnstream1.com/12145",
            favorite = true
        ),
        Feed(
            id = "phoenix-metro-fire",
            name = "Phoenix Metro Area Fire",
            location = "Phoenix / Mesa / Rural Metro / DPS East",
            url = "https://broadcastify.cdnstream1.com/1",
            favorite = true
        ),
        Feed(
            id = "az-dps-west",
            name = "Arizona DPS - Metro Phoenix West",
            location = "Highway Patrol",
            url = "https://broadcastify.cdnstream1.com/20741"
        ),
        Feed(
            id = "mcso-west",
            name = "Maricopa County Sheriff - West",
            location = "MCSO West",
            url = "https://broadcastify.cdnstream1.com/10232"
        ),
        Feed(
            id = "chandler-police-fire",
            name = "Chandler Police and Fire",
            location = "Chandler / Phoenix Fire",
            url = "https://broadcastify.cdnstream1.com/14875"
        )
    )

    fun save(feeds: List<Feed>) {
        val arr = JSONArray()

        feeds.forEach { feed ->
            arr.put(
                JSONObject().apply {
                    put("id", feed.id)
                    put("name", feed.name)
                    put("location", feed.location)
                    put("url", feed.url)
                    put("favorite", feed.favorite)
                    put("lastPlayedAt", feed.lastPlayedAt)
                }
            )
        }

        prefs.edit()
            .putString("feeds", arr.toString())
            .apply()
    }
}
