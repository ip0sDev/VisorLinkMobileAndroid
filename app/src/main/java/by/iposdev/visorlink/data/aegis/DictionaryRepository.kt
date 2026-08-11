package by.iposdev.visorlink.data.aegis

import by.iposdev.visorlink.data.model.aegis.HeuristicDictionary
import kotlinx.serialization.json.Json
import android.content.Context
import by.iposdev.visorlink.R

class DictionaryRepository(private val context: Context) {

    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    private fun loadDefaultDictionary(): String {
        return try {
            context.resources.openRawResource(R.raw.heuristic_dictionary)
                .bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            "{}"
        }
    }

    fun getActiveDictionary(): HeuristicDictionary {
        // TODO: Implement remote sync via feature flag URL (heuristic_dict_url).
        // Download JSON, validate schema version, save to context.filesDir, fallback to bundled assets if offline.
        val dictJson = loadDefaultDictionary()
        return json.decodeFromString(dictJson)
    }
}
