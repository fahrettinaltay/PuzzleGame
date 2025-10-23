package com.mey.puzzlegame

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mey.puzzlegame.ui.theme.PuzzleGameTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(FlowPreview::class)
class StartViewModel(private val dataStore: SettingsDataStore, private val lang: String) : ViewModel() {

    val isDarkTheme = dataStore.isDarkTheme
    private val pixabayService = PixabayService()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PixabayImage>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    // State for pagination
    private var currentPage = 1
    private var totalHits = 0

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(500)
                .filter { it.length > 2 }
                .distinctUntilChanged()
                .collect { query ->
                    searchImages(query)
                }
        }
    }

    private fun searchImages(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            currentPage = 1 // Reset for new search
            _searchResults.value = emptyList() // Clear previous results

            val response = pixabayService.searchImages(query, lang, currentPage)
            if (response != null) {
                _searchResults.value = response.hits
                totalHits = response.totalHits
            } else {
                totalHits = 0
            }
            _isLoading.value = false
        }
    }

    fun loadMoreResults() {
        // Prevent multiple simultaneous loads and loading if all results are already shown
        if (_isLoadingMore.value || _searchResults.value.size >= totalHits) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            currentPage++

            val response = pixabayService.searchImages(_searchQuery.value, lang, currentPage)
            if (response != null) {
                // Add new results to the existing list
                _searchResults.value = _searchResults.value + response.hits
            }
            _isLoadingMore.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun getHighScore(size: Int): StateFlow<Int> {
        return dataStore.getHighScore(size)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

    fun onThemeChange() {
        viewModelScope.launch {
            dataStore.toggleTheme()
        }
    }
}

class StartViewModelFactory(private val dataStore: SettingsDataStore, private val lang: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StartViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StartViewModel(dataStore, lang) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class StartActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val dataStore = SettingsDataStore(this)
        val lang = Locale.getDefault().language

        setContent {
            val isDark by dataStore.isDarkTheme.collectAsState(initial = isSystemInDarkTheme())

            PuzzleGameTheme(darkTheme = isDark) {
                StartScreen(
                    viewModelFactory = StartViewModelFactory(dataStore, lang),
                    onStartPuzzle = { size, imageUri ->
                        val intent = Intent(this, PuzzleActivity::class.java).apply {
                            putExtra("SIZE", size)
                            putExtra("IMAGE_URI", imageUri)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun StartScreen(
    viewModelFactory: StartViewModelFactory,
    onStartPuzzle: (Int, String?) -> Unit,
    viewModel: StartViewModel = viewModel(factory = viewModelFactory)
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState(initial = isSystemInDarkTheme())
    var selectedImageUri by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedImageUri = uri?.toString() }
    )

    // States from ViewModel
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🧩 Puzzle Game", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isDarkTheme) "🌙" else "☀️", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = isDarkTheme, onCheckedChange = { viewModel.onThemeChange() })
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("1. Bir Resim Seçin", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                label = { Text("Pixabay'de resim ara...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            // This box holds the search results, using weight to take up available space.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // This makes the box flexible
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (searchResults.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(searchResults) { index, image ->
                            // When the last item is about to be displayed, load more
                            if (index == searchResults.size - 1) {
                                LaunchedEffect(Unit) { // Use LaunchedEffect to call suspend functions
                                    viewModel.loadMoreResults()
                                }
                            }

                            AsyncImage(
                                model = image.webformatURL,
                                contentDescription = "Pixabay Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedImageUri = image.largeImageURL }
                                    .border(
                                        width = 3.dp,
                                        color = if (selectedImageUri == image.largeImageURL) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            )
                        }

                        // Show loading indicator at the bottom if more results are being loaded
                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                } else if (searchQuery.length > 2) {
                    Text(
                        text = "'$searchQuery' için sonuç bulunamadı.",
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Gallery Button (Now safely outside the scrollable area) ---
            Button(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🖼️ Veya Galeriden Resim Seç")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("2. Zorluk Seviyesi Seçin", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(visible = selectedImageUri == null) {
                Text(
                    text = "Lütfen oyuna başlamak için bir resim seçin",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val easyHighScore by viewModel.getHighScore(3).collectAsState()
                val mediumHighScore by viewModel.getHighScore(4).collectAsState()
                val hardHighScore by viewModel.getHighScore(5).collectAsState()

                DifficultyButton(text = "🟢 Kolay (3×3)", score = easyHighScore, enabled = selectedImageUri != null, onClick = { onStartPuzzle(3, selectedImageUri) })
                DifficultyButton(text = "🟡 Orta (4×4)", score = mediumHighScore, enabled = selectedImageUri != null, onClick = { onStartPuzzle(4, selectedImageUri) })
                DifficultyButton(text = "🔴 Zor (5×5)", score = hardHighScore, enabled = selectedImageUri != null, onClick = { onStartPuzzle(5, selectedImageUri) })
            }
        }
    }
}

@Composable
fun DifficultyButton(text: String, score: Int, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (score > 0) {
                Text("Rekor: $score", fontSize = 14.sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StartScreenPreview() {
    PuzzleGameTheme {
        val dummyDataStore = SettingsDataStore(LocalContext.current)
        // StartScreen(viewModelFactory = StartViewModelFactory(dummyDataStore, "en"), onStartPuzzle = { _, _ -> })
    }
}
