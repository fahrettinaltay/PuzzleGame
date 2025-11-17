package com.mey.puzzlegame

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(FlowPreview::class)
class StartViewModel(private val dataStore: SettingsDataStore, private val lang: String) : ViewModel() {

    val isDarkTheme = dataStore.isDarkTheme
    val showTileNumbers = dataStore.showTileNumbers
    val moveSoundsEnabled = dataStore.moveSoundsEnabled
    val celebrationSoundEnabled = dataStore.celebrationSoundEnabled
    private val pixabayService = PixabayService()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PixabayImage>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _savedGameState = MutableStateFlow<GameState?>(null)
    val savedGameState = _savedGameState.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<String?>(null)
    val selectedImageUri = _selectedImageUri.asStateFlow()

    private var currentPage = 1
    private var totalHits = 0

    init {
        viewModelScope.launch {
            dataStore.savedGameState.collect { _savedGameState.value = it }
        }

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

    fun clearSavedGame() {
        viewModelScope.launch {
            dataStore.clearSavedGame()
        }
    }

    private fun searchImages(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            currentPage = 1
            _searchResults.value = emptyList()
            val response = pixabayService.searchImages(query, lang, currentPage)
            if (response != null) {
                _searchResults.value = response.hits
                totalHits = response.totalHits.coerceAtMost(500)
            } else {
                totalHits = 0
            }
            _isLoading.value = false
        }
    }

    fun loadMoreResults() {
        if (_isLoadingMore.value || _searchResults.value.size >= totalHits) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            currentPage++
            val response = pixabayService.searchImages(_searchQuery.value, lang, currentPage)
            if (response != null) {
                _searchResults.value = _searchResults.value + response.hits
            }
            _isLoadingMore.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onImageSelected(uri: String?) {
        _selectedImageUri.value = uri
    }

    fun onGalleryImageSelected(uri: Uri?, context: Context) {
        viewModelScope.launch {
            uri?.let {
                val permanentUri = copyUriToInternalStorage(it, context)
                _selectedImageUri.value = permanentUri
            }
        }
    }

    private fun copyUriToInternalStorage(uri: Uri, context: Context): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = "puzzle_image_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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

    fun onShowTileNumbersChange() {
        viewModelScope.launch {
            dataStore.toggleShowTileNumbers()
        }
    }

    fun onMoveSoundsChange() {
        viewModelScope.launch {
            dataStore.toggleMoveSounds()
        }
    }

    fun onCelebrationSoundChange() {
        viewModelScope.launch {
            dataStore.toggleCelebrationSound()
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
    val showTileNumbers by viewModel.showTileNumbers.collectAsState(initial = false)
    val moveSoundsEnabled by viewModel.moveSoundsEnabled.collectAsState(initial = true)
    val celebrationSoundEnabled by viewModel.celebrationSoundEnabled.collectAsState(initial = true)
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val savedGame by viewModel.savedGameState.collectAsState()

    var showNewGameDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var pendingNewGameSize by remember { mutableStateOf<Int?>(null) }

    if (showNewGameDialog) {
        AlertDialog(
            onDismissRequest = { showNewGameDialog = false },
            title = { Text("Yeni Oyuna Başla?") },
            text = { Text("Devam eden bir oyununuz var. Yeni bir oyuna başlamak mevcut ilerlemenizi silecek. Emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSavedGame()
                        pendingNewGameSize?.let { size ->
                            onStartPuzzle(size, selectedImageUri)
                        }
                        showNewGameDialog = false
                    }
                ) {
                    Text("Yeni Başla")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewGameDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("Numaraları Göster Nedir?") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Bu ayar aktif olduğunda, bulmaca parçalarının üzerinde orijinal konumlarını gösteren sayılar belirir. Bu, özellikle zorlu bulmacalarda doğru parçayı bulmanıza yardımcı olur.")
                    PuzzleExample()
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Anladım")
                }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            // App Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "App Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Puzzle Game", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Settings Section
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isDarkTheme, onCheckedChange = { viewModel.onThemeChange() })
                        Spacer(Modifier.width(8.dp))
                        Text(if (isDarkTheme) "Koyu Tema 🌙" else "Açık Tema ☀️", fontSize = 16.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = showTileNumbers,
                            onCheckedChange = { viewModel.onShowTileNumbersChange() })
                        Spacer(Modifier.width(8.dp))
                        Text("Numaraları Göster", fontSize = 16.sp)
                        IconButton(
                            onClick = { showInfoDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Numaraları Göster hakkında bilgi",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = moveSoundsEnabled,
                            onCheckedChange = { viewModel.onMoveSoundsChange() })
                        Spacer(Modifier.width(8.dp))
                        Text("Hareket Sesleri", fontSize = 16.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = celebrationSoundEnabled,
                            onCheckedChange = { viewModel.onCelebrationSoundChange() })
                        Spacer(Modifier.width(8.dp))
                        Text("Kutlama Sesi", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val currentImageUri = selectedImageUri
            if (currentImageUri == null) {
                ImageSelectionContent(viewModel, onStartPuzzle)
            } else {
                DifficultySelectionContent(viewModel, currentImageUri, onStartPuzzle)
            }
        }
    }
}

@Composable
fun ImageSelectionContent(
    viewModel: StartViewModel,
    onStartPuzzle: (Int, String?) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val savedGame by viewModel.savedGameState.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? -> viewModel.onGalleryImageSelected(uri, context) }
    )

    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull() }
            .filterNotNull()
            .collect { lastVisibleItem ->
                if (lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 5) {
                    viewModel.loadMoreResults()
                }
            }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- FIXED CONTENT ---
        if (savedGame != null) {
            SavedGameCard(
                gameState = savedGame,
                onContinue = onStartPuzzle,
                onDelete = { viewModel.clearSavedGame() })
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("1. Bir Resim Seçin", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            label = { Text("Pixabay'de resim ara...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
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
        Spacer(modifier = Modifier.height(16.dp))

        // --- SCROLLABLE CONTENT ---
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = Modifier.weight(1f),
            state = gridState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                item(span = { GridItemSpan(this.maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (searchResults.isEmpty() && searchQuery.length > 2) {
                item(span = { GridItemSpan(this.maxLineSpan) }) {
                    Text(
                        text = "'$searchQuery' için sonuç bulunamadı.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                itemsIndexed(searchResults) { index, image ->
                    AsyncImage(
                        model = image.webformatURL,
                        contentDescription = "Pixabay Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.onImageSelected(image.largeImageURL) }
                            .border(
                                width = 3.dp,
                                color = if (selectedImageUri == image.largeImageURL) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                }
                if (isLoadingMore) {
                    item(span = { GridItemSpan(this.maxLineSpan) }) {
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
        }
    }
}

@Composable
fun DifficultySelectionContent(
    viewModel: StartViewModel,
    selectedImageUri: String,
    onStartPuzzle: (Int, String?) -> Unit,
) {
    val savedGame by viewModel.savedGameState.collectAsState()
    var showNewGameDialog by remember { mutableStateOf(false) }
    var pendingNewGameSize by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("1. Seçilen Resim", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        AsyncImage(
            model = selectedImageUri,
            contentDescription = "Seçilen Resim",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { viewModel.onImageSelected(null) }) {
            Text("Resmi Değiştir")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("2. Zorluk Seviyesi Seçin", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        val handleDifficultyClick = { size: Int ->
            if (savedGame != null) {
                pendingNewGameSize = size
                showNewGameDialog = true
            } else {
                onStartPuzzle(size, selectedImageUri)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val easyHighScore by viewModel.getHighScore(3).collectAsState()
            val mediumHighScore by viewModel.getHighScore(4).collectAsState()
            val hardHighScore by viewModel.getHighScore(5).collectAsState()

            DifficultyButton(
                text = "🟢 Kolay (3×3)",
                score = easyHighScore,
                enabled = true,
                onClick = { handleDifficultyClick(3) })
            DifficultyButton(
                text = "🟡 Orta (4×4)",
                score = mediumHighScore,
                enabled = true,
                onClick = { handleDifficultyClick(4) })
            DifficultyButton(
                text = "🔴 Zor (5×5)",
                score = hardHighScore,
                enabled = true,
                onClick = { handleDifficultyClick(5) })
        }
    }
}


@Composable
fun PuzzleExample() {
    val pieces = listOf("1", "3", "4", "2") // A shuffled 2x2 grid
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            PuzzlePieceExample(text = pieces[0])
            PuzzlePieceExample(text = pieces[1])
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            PuzzlePieceExample(text = pieces[2])
            PuzzlePieceExample(text = pieces[3])
        }
    }
}

@Composable
fun PuzzlePieceExample(text: String) {
    Surface(
        modifier = Modifier.size(50.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}


@Composable
fun SavedGameCard(gameState: GameState?, onContinue: (Int, String?) -> Unit, onDelete: () -> Unit) {
    gameState ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = gameState.imageUri,
                    contentDescription = "Saved Game Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Kaydedilmiş Oyun", fontWeight = FontWeight.Bold)
                    Text("${gameState.size}x${gameState.size} | ${gameState.moves} hamle", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Saved Game")
                }
                Button(onClick = { onContinue(gameState.size, gameState.imageUri) }) {
                    Text("Devam Et")
                }
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
