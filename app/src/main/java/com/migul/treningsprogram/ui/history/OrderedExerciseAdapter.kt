package com.migul.treningsprogram.ui.history

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import com.migul.treningsprogram.domain.ExercisePickerSort

/**
 * AutoCompleteTextView adapter for the Progress-tab exercise picker (B03).
 *
 * The stock [ArrayAdapter] filter does a prefix match and re-orders the suggestions, which would
 * undo our most-sessions-first ordering as soon as the user types. This adapter keeps the supplied
 * [ordered] list (already sorted most-trained-first by [ExercisePickerSort.order]) and filters it
 * with [ExercisePickerSort.filterPreservingOrder] — a case-insensitive substring match that
 * PRESERVES order — so the dropdown stays most-sessions-first while typing.
 */
class OrderedExerciseAdapter(
    context: Context,
    private val ordered: List<String>
) : ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, ArrayList(ordered)) {

    private val filterImpl = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val matches = ExercisePickerSort.filterPreservingOrder(ordered, constraint?.toString() ?: "")
            return FilterResults().apply {
                values = matches
                count = matches.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            clear()
            val values = results?.values as? List<String> ?: emptyList()
            addAll(values)
            if (results != null && results.count > 0) notifyDataSetChanged() else notifyDataSetInvalidated()
        }
    }

    override fun getFilter(): Filter = filterImpl
}
