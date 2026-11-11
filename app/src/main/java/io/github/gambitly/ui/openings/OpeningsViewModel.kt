package io.github.gambitly.ui.openings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gambitly.data.FamilyCount
import io.github.gambitly.data.Opening
import io.github.gambitly.data.ProgressKind
import io.github.gambitly.gambitly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class OpeningsUi(
    val query: String = "",
    /** Family being browsed, or null when showing the family list or search results. */
    val family: String? = null,
    val families: List<FamilyCount> = emptyList(),
    /** Lines of the selected family, or search results; empty when showing families. */
    val lines: List<Opening> = emptyList(),
    /** Ids of lines the user has completed at least once. */
    val completed: Set<Int> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class OpeningsViewModel(app: Application) : AndroidViewModel(app) {
    private val content = app.gambitly.content
    private val query = MutableStateFlow("")
    private val family = MutableStateFlow<String?>(null)

    /**
     * Text in the search box. Held as synchronous Compose state: feeding the field
     * from [ui] would lag behind typing (the listing is loaded asynchronously) and
     * scramble the input.
     */
    var searchText by mutableStateOf("")
        private set

    private val listing = combine(query, family) { q, f -> q to f }.mapLatest { (q, f) ->
        when {
            q.isNotBlank() -> OpeningsUi(query = q, lines = content.searchOpenings(q.trim()))
            f != null -> OpeningsUi(family = f, lines = content.openingsInFamily(f))
            else -> OpeningsUi(families = content.openingFamilies())
        }
    }

    private val completed = app.gambitly.progress.observeKind(ProgressKind.OPENING)
        .map { rows -> rows.filter { it.completions > 0 }.mapNotNull { it.itemId.toIntOrNull() }.toSet() }

    val ui: StateFlow<OpeningsUi> = combine(listing, completed) { list, done -> list.copy(completed = done) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OpeningsUi())

    fun setQuery(text: String) {
        searchText = text
        query.value = text
    }

    fun openFamily(name: String?) {
        family.value = name
    }
}
