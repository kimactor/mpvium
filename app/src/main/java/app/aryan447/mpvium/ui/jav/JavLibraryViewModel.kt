package app.aryan447.mpvium.ui.jav

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.aryan447.mpvium.R
import app.aryan447.mpvium.domain.jav.JavLibraryItem
import app.aryan447.mpvium.domain.jav.JavLibraryRepository
import app.aryan447.mpvium.domain.streaming.LibraryIndexRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

data class JavLibraryUiState(
  val isLoading: Boolean = true,
  val items: List<JavLibraryItem> = emptyList(),
  val error: String? = null,
)

class JavLibraryViewModel(
  application: Application,
) : AndroidViewModel(application) {
  private val libraryRepository: JavLibraryRepository by inject(JavLibraryRepository::class.java)
  private val libraryIndexRepository: LibraryIndexRepository by inject(LibraryIndexRepository::class.java)

  private val _uiState = MutableStateFlow(JavLibraryUiState())
  val uiState: StateFlow<JavLibraryUiState> = _uiState.asStateFlow()

  fun refresh(forceScan: Boolean = false) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      val items = withContext(Dispatchers.IO) {
        runCatching {
          if (forceScan) libraryIndexRepository.getLibrary(force = true)
          libraryRepository.loadItems()
        }
      }
      _uiState.update { state ->
        items.fold(
          onSuccess = { state.copy(isLoading = false, items = it) },
          onFailure = {
            val message = it.message
              ?: getApplication<Application>().getString(R.string.jav_library_load_failed)
            state.copy(isLoading = false, error = message)
          },
        )
      }
    }
  }

  companion object {
    fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        JavLibraryViewModel(application)
      }
    }
  }
}
