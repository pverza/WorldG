package com.pverza.wortschatz

import android.content.Context
import org.json.JSONArray

data class Word(
    val id: Int,
    val de: String,
    val en: String,
    val ex: String,
    val exEn: String,
    val level: String,
)

/** Loads the bundled vocabulary once and keeps it in memory (a few KB). */
object WordRepository {
    @Volatile
    private var cache: List<Word>? = null

    fun all(context: Context): List<Word> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: load(context).also { cache = it }
        }
    }

    private fun load(context: Context): List<Word> {
        val text = context.applicationContext.assets.open("words.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val array = JSONArray(text)
        val list = ArrayList<Word>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Word(
                    id = o.getInt("id"),
                    de = o.getString("de"),
                    en = o.getString("en"),
                    ex = o.getString("ex"),
                    exEn = o.getString("exEn"),
                    level = o.getString("lvl"),
                )
            )
        }
        return list
    }

    fun byId(context: Context, id: Int): Word? = all(context).firstOrNull { it.id == id }
}
