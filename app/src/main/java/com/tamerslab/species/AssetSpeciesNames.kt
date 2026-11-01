package com.tamerslab.species

import android.content.Context
import com.google.gson.Gson
import java.util.Locale

/** Immutable bundled identity map, shared by image and battle renderers. Call from IO. */
object AssetSpeciesNames {
    @Volatile private var names: Map<String, String>? = null

    fun nameFor(context: Context, assetId: String): String? {
        val index = names ?: synchronized(this) {
            names ?: context.applicationContext.assets.open("species.json").bufferedReader().use {
                Gson().fromJson(it, SpeciesDatabaseDto::class.java).species.flatMap { (card, entries) ->
                    val number = card.toIntOrNull()?.takeIf { value -> value > 0 } ?: return@flatMap emptyList()
                    entries.mapNotNull { (slot, entry) ->
                        val position = slot.toIntOrNull()?.takeIf { value -> value > 0 } ?: return@mapNotNull null
                        String.format(Locale.ROOT, "dim%03d_mon%02d", number, position) to entry.name
                    }
                }.toMap()
            }.also { names = it }
        }
        return index[assetId]
    }
}
