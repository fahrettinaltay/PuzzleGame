package com.mey.puzzlegame

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.*
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
class StartViewModel(private val dataStore: SettingsDataStore) : ViewModel() {

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

    val language = dataStore.language.stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    private var currentPage = 1
    private var totalHits = 0

    init {
        checkForSavedGame()

        viewModelScope.launch {
            combine(searchQuery, language) { query, lang -> query to lang }
                .debounce(500)
                .filter { (query, _) -> query.length > 2 }
                .distinctUntilChanged()
                .collect { (query, lang) ->
                    searchImages(query, lang)
                }
        }
    }

    fun checkForSavedGame() {
        viewModelScope.launch {
            _savedGameState.value = dataStore.savedGameState.first()
        }
    }

    fun clearSavedGame() {
        viewModelScope.launch {
            dataStore.clearSavedGame()
            _savedGameState.value = null
        }
    }

    private fun searchImages(query: String, lang: String) {
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
            val response = pixabayService.searchImages(_searchQuery.value, language.value, currentPage)
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
        viewModelScope.launch { dataStore.toggleTheme() }
    }

    fun onShowTileNumbersChange() {
        viewModelScope.launch { dataStore.toggleShowTileNumbers() }
    }

    fun onMoveSoundsChange() {
        viewModelScope.launch { dataStore.toggleMoveSounds() }
    }

    fun onCelebrationSoundChange() {
        viewModelScope.launch { dataStore.toggleCelebrationSound() }
    }

    fun onLanguageChange(lang: String) {
        viewModelScope.launch { dataStore.setLanguage(lang) }
    }
}

class StartViewModelFactory(private val dataStore: SettingsDataStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StartViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StartViewModel(dataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class StartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val dataStore = SettingsDataStore(this)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val language by dataStore.language.collectAsState(initial = Locale.getDefault().language)
            val isDark by dataStore.isDarkTheme.collectAsState(initial = isSystemInDarkTheme())

            val locale = Locale(language)
            Locale.setDefault(locale)
            val config = Configuration()
            config.setLocale(locale)
            LocalContext.current.resources.updateConfiguration(config, LocalContext.current.resources.displayMetrics)

            PuzzleGameTheme(darkTheme = isDark) {
                StartScreen(
                    viewModelFactory = StartViewModelFactory(dataStore),
                    widthSizeClass = windowSizeClass.widthSizeClass,
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
    widthSizeClass: WindowWidthSizeClass,
    onStartPuzzle: (Int, String?) -> Unit,
    viewModel: StartViewModel = viewModel(factory = viewModelFactory)
) {
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val savedGame by viewModel.savedGameState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkForSavedGame()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val wrappedOnStartPuzzle = { size: Int, imageUri: String? ->
        onStartPuzzle(size, imageUri)
        viewModel.onImageSelected(null) // Clear selection after starting
    }

    var showNewGameDialog by remember { mutableStateOf(false) }
    var showOverwriteImageDialog by remember { mutableStateOf(false) }
    var pendingNewGameSize by remember { mutableStateOf<Int?>(null) }
    var pendingImageUri by remember { mutableStateOf<String?>(null) }

    if (showNewGameDialog) {
        AlertDialog(
            onDismissRequest = { showNewGameDialog = false },
            title = { Text(stringResource(id = R.string.start_new_game_title)) },
            text = { Text(stringResource(id = R.string.start_new_game_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSavedGame()
                        pendingNewGameSize?.let { size ->
                            wrappedOnStartPuzzle(size, selectedImageUri)
                        }
                        showNewGameDialog = false
                    }
                ) { Text(stringResource(id = R.string.start_new_game_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewGameDialog = false }) { Text(stringResource(id = R.string.cancel)) }
            }
        )
    }

    if (showOverwriteImageDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteImageDialog = false },
            title = { Text(stringResource(id = R.string.overwrite_image_game_title)) },
            text = { Text(stringResource(id = R.string.overwrite_image_game_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSavedGame()
                        viewModel.onImageSelected(pendingImageUri)
                        showOverwriteImageDialog = false
                        pendingImageUri = null
                    }
                ) { Text(stringResource(id = R.string.start_new_game_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteImageDialog = false }) { Text(stringResource(id = R.string.cancel)) }
            }
        )
    }

    val handleDifficultyClick = { size: Int ->
        if (savedGame != null) {
            pendingNewGameSize = size
            showNewGameDialog = true
        } else {
            wrappedOnStartPuzzle(size, selectedImageUri)
        }
    }

    val isTablet = widthSizeClass != WindowWidthSizeClass.Compact
    val isImageSelected = selectedImageUri != null

    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        key(viewModel.language.collectAsState().value) { // Recompose on language change
            if (isTablet && isImageSelected) {
                // Tablet layout with image selected
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left Pane: Image Preview
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ImagePreview(
                            selectedImageUri = selectedImageUri,
                            onChangeImage = { viewModel.onImageSelected(null) }
                        )
                    }
                    // Right Pane: All other content
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppHeader()
                        Spacer(Modifier.height(16.dp))
                        SettingsPane(viewModel)
                        Spacer(Modifier.height(24.dp))
                        Text(stringResource(id = R.string.step_2_select_difficulty), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        DifficultyButtons(viewModel, handleDifficultyClick)
                    }
                }
            } else {
                // Phone layout OR Tablet layout without image selected
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppHeader()
                    Spacer(Modifier.height(16.dp))
                    SettingsPane(viewModel)
                    Spacer(Modifier.height(24.dp))

                    if (isImageSelected) {
                        ImagePreview(
                            selectedImageUri = selectedImageUri,
                            onChangeImage = { viewModel.onImageSelected(null) }
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(stringResource(id = R.string.step_2_select_difficulty), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        DifficultyButtons(viewModel, handleDifficultyClick)
                    } else {
                        ImageSelectionContent(
                            viewModel = viewModel,
                            onStartPuzzle = wrappedOnStartPuzzle,
                            onPixabayImageClick = { imageUrl ->
                                if (savedGame != null) {
                                    pendingImageUri = imageUrl
                                    showOverwriteImageDialog = true
                                } else {
                                    viewModel.onImageSelected(imageUrl)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "App Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(id = R.string.app_name), fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsPane(viewModel: StartViewModel) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState(initial = isSystemInDarkTheme())
    val showTileNumbers by viewModel.showTileNumbers.collectAsState(initial = false)
    val moveSoundsEnabled by viewModel.moveSoundsEnabled.collectAsState(initial = true)
    val celebrationSoundEnabled by viewModel.celebrationSoundEnabled.collectAsState(initial = true)
    val language by viewModel.language.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text(stringResource(id = R.string.settings_show_tile_numbers_info_title)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(id = R.string.settings_show_tile_numbers_info_desc))
                    PuzzleExample()
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text(stringResource(id = R.string.got_it)) }
            }
        )
    }
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = language,
            onLanguageSelected = { viewModel.onLanguageChange(it) },
            onDismiss = { showLanguageDialog = false }
        )
    }

    Box(contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = isDarkTheme, onCheckedChange = { viewModel.onThemeChange() })
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isDarkTheme) stringResource(R.string.settings_dark_theme_on) else stringResource(R.string.settings_dark_theme_off),
                    fontSize = 16.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = showTileNumbers, onCheckedChange = { viewModel.onShowTileNumbersChange() })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_show_tile_numbers), fontSize = 16.sp)
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(id = R.string.settings_show_tile_numbers_info_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = moveSoundsEnabled, onCheckedChange = { viewModel.onMoveSoundsChange() })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_move_sounds), fontSize = 16.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = celebrationSoundEnabled, onCheckedChange = { viewModel.onCelebrationSoundChange() })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_celebration_sound), fontSize = 16.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_language), fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { showLanguageDialog = true }) {
                    Text(language.uppercase(Locale.getDefault()))
                }
            }
        }
    }
}

@Composable
fun ImageSelectionContent(
    viewModel: StartViewModel,
    onStartPuzzle: (Int, String?) -> Unit,
    onPixabayImageClick: (String) -> Unit
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
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (savedGame != null) {
            SavedGameCard(
                gameState = savedGame,
                onContinue = onStartPuzzle,
                onDelete = { viewModel.clearSavedGame() })
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(stringResource(id = R.string.step_1_select_image), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            label = { Text(stringResource(id = R.string.search_pixabay)) },
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
            Text(stringResource(id = R.string.select_from_gallery))
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Use a fixed height for the grid to avoid nested scrolling issues
        Box(modifier = Modifier.heightIn(max = 500.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    item(span = { GridItemSpan(this.maxLineSpan) }) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (searchResults.isEmpty() && searchQuery.length > 2) {
                    item(span = { GridItemSpan(this.maxLineSpan) }) {
                        Text(
                            text = stringResource(id = R.string.search_no_results, searchQuery),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    itemsIndexed(searchResults) { _, image ->
                        AsyncImage(
                            model = image.webformatURL,
                            contentDescription = "Pixabay Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPixabayImageClick(image.largeImageURL) }
                                .border(
                                    width = 3.dp,
                                    color = if (selectedImageUri == image.largeImageURL) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        )
                    }
                    if (isLoadingMore) {
                        item(span = { GridItemSpan(this.maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImagePreview(selectedImageUri: String?, onChangeImage: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(id = R.string.step_1_select_image), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        AsyncImage(
            model = selectedImageUri,
            contentDescription = stringResource(id = R.string.selected_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onChangeImage) {
            Text(stringResource(id = R.string.change_image))
        }
    }
}

@Composable
private fun DifficultyButtons(viewModel: StartViewModel, onDifficultyClick: (Int) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val easyHighScore by viewModel.getHighScore(3).collectAsState()
        val mediumHighScore by viewModel.getHighScore(4).collectAsState()
        val hardHighScore by viewModel.getHighScore(5).collectAsState()

        DifficultyButton(
            text = stringResource(id = R.string.easy_difficulty),
            score = easyHighScore,
            enabled = true,
            onClick = { onDifficultyClick(3) })
        DifficultyButton(
            text = stringResource(id = R.string.medium_difficulty),
            score = mediumHighScore,
            enabled = true,
            onClick = { onDifficultyClick(4) })
        DifficultyButton(
            text = stringResource(id = R.string.hard_difficulty),
            score = hardHighScore,
            enabled = true,
            onClick = { onDifficultyClick(5) })
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
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = gameState.imageUri,
                    contentDescription = stringResource(id = R.string.saved_game),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(stringResource(id = R.string.saved_game), fontWeight = FontWeight.Bold)
                    Text(stringResource(id = R.string.saved_game_details, gameState.size, gameState.moves), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(id = R.string.delete_saved_game))
                }
                Button(onClick = { onContinue(gameState.size, gameState.imageUri) }) {
                    Text(stringResource(id = R.string.continue_game))
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
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (score > 0) {
                Text(stringResource(id = R.string.high_score, score), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf("en" to "English", "tr" to "Türkçe")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                languages.forEach { (code, name) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(code) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLanguage == code,
                            onClick = { onLanguageSelected(code) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 1024, name = "Tablet Preview")
@Composable
fun StartScreenTabletPreview() {
    val context = LocalContext.current
    val dummyDataStore = remember { SettingsDataStore(context) }
    val factory = remember { StartViewModelFactory(dummyDataStore) }
    val viewModel = viewModel<StartViewModel>(factory = factory)
    
    // Simulate image selected state
    LaunchedEffect(Unit) {
        viewModel.onImageSelected("https://dummyimage.com/600x400/000/fff.png&text=Sample")
    }

    PuzzleGameTheme {
        StartScreen(
            viewModelFactory = factory,
            widthSizeClass = WindowWidthSizeClass.Expanded,
            onStartPuzzle = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Phone Preview")
@Composable
fun StartScreenPhonePreview() {
    val context = LocalContext.current
    val dummyDataStore = remember { SettingsDataStore(context) }
    val factory = remember { StartViewModelFactory(dummyDataStore) }
    
    PuzzleGameTheme {
        StartScreen(
            viewModelFactory = factory,
            widthSizeClass = WindowWidthSizeClass.Compact,
            onStartPuzzle = { _, _ -> }
        )
    }
}
