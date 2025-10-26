package com.mey.puzzlegame

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.mey.puzzlegame.ui.theme.PuzzleGameTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class PuzzleViewModel(private val dataStore: SettingsDataStore) : ViewModel() {
    var size by mutableStateOf(3)
    var moves by mutableStateOf(0)
    var values by mutableStateOf(Array(size) { Array(size) { 0 } })
    var imagePieces by mutableStateOf<List<ImageBitmap>>(emptyList())
    var isComplete by mutableStateOf(false)
    var isNewHighScore by mutableStateOf(false)
    var finalScore by mutableStateOf(0)
    var imageUri by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(true)

    private var emptyRow by mutableStateOf(0)
    private var emptyCol by mutableStateOf(0)
    private var startTime by mutableStateOf(0L)
    private var timeWhenPaused by mutableStateOf(0L)

    fun setup(puzzleSize: Int, uriString: String?, context: Context) {
        viewModelScope.launch {
            val savedState = dataStore.savedGameState.first()
            if (savedState != null && savedState.imageUri == uriString && savedState.size == puzzleSize) {
                loadFromState(savedState, context)
            } else {
                clearAndNewGame(puzzleSize, uriString, context)
            }
        }
    }

    private suspend fun loadFromState(gameState: GameState, context: Context) {
        size = gameState.size
        values = Array(size) { Array(size) { 0 } } // Resize array before populating
        imageUri = gameState.imageUri
        moves = gameState.moves
        timeWhenPaused = gameState.elapsedTime
        values = gameState.puzzle.chunked(size).map { it.toTypedArray() }.toTypedArray()

        for (r in 0 until size) {
            for (c in 0 until size) {
                if (values[r][c] == 0) {
                    emptyRow = r
                    emptyCol = c
                    break
                }
            }
        }

        sliceImage(context)
        startTime = System.currentTimeMillis()
        isLoading = false
    }

    suspend fun clearAndNewGame(puzzleSize: Int, uriString: String?, context: Context) {
        dataStore.clearSavedGame()
        size = puzzleSize
        values = Array(size) { Array(size) { 0 } } // FIX: Resize the array for the new game size
        imageUri = uriString
        newGame(context)
    }

    private suspend fun newGame(context: Context) {
        isLoading = true
        moves = 0
        isComplete = false
        isNewHighScore = false
        finalScore = 0
        timeWhenPaused = 0L
        sliceImage(context)
        shuffleBoard()
        startTime = System.currentTimeMillis()
        isLoading = false
        saveState()
    }

    fun solvePuzzle() {
        var n = 1
        for (r in 0 until size) {
            for (c in 0 until size) {
                values[r][c] = n++
            }
        }
        isComplete = true
        values = values.copyOf()
        viewModelScope.launch { dataStore.clearSavedGame() }
    }

    fun saveState() {
        if (isLoading || isComplete) return

        val currentElapsedTime = timeWhenPaused + (System.currentTimeMillis() - startTime)
        val gameState = GameState(
            size = size,
            moves = moves,
            elapsedTime = currentElapsedTime,
            imageUri = imageUri,
            puzzle = values.flatten()
        )
        viewModelScope.launch {
            dataStore.saveGameState(gameState)
        }
    }

    @SuppressLint("RestrictedApi")
    private suspend fun sliceImage(context: Context) {
        try {
            if (imageUri == null) return
            val request = ImageRequest.Builder(context).data(imageUri).allowHardware(false).build()
            val result = context.imageLoader.execute(request).drawable
            val sourceBitmap = (result as BitmapDrawable).bitmap
            val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, 600, 600, true)
            val pieces = mutableListOf<ImageBitmap>()
            val pieceSize = scaledBitmap.width / size
            pieces.add(ImageBitmap(1, 1))
            for (r in 0 until size) {
                for (c in 0 until size) {
                    val piece = Bitmap.createBitmap(scaledBitmap, c * pieceSize, r * pieceSize, pieceSize, pieceSize)
                    pieces.add(piece.asImageBitmap())
                }
            }
            imagePieces = pieces
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initValues() {
        var n = 1
        for (r in 0 until size) {
            for (c in 0 until size) {
                values[r][c] = n++
            }
        }
        values[size - 1][size - 1] = 0
        emptyRow = size - 1
        emptyCol = size - 1
    }

    private fun shuffleBoard() {
        initValues()
        val shuffleMoves = size * size * 100
        repeat(shuffleMoves) {
            val neighbors = mutableListOf<Pair<Int, Int>>()
            if (emptyRow > 0) neighbors.add(emptyRow - 1 to emptyCol)
            if (emptyRow < size - 1) neighbors.add(emptyRow + 1 to emptyCol)
            if (emptyCol > 0) neighbors.add(emptyRow to emptyCol - 1)
            if (emptyCol < size - 1) neighbors.add(emptyRow to emptyCol + 1)
            val (randomRow, randomCol) = neighbors.random()
            swap(emptyRow, emptyCol, randomRow, randomCol)
            emptyRow = randomRow
            emptyCol = randomCol
        }
        if (checkIfComplete()) {
            shuffleBoard()
        }
        values = values.copyOf()
    }

    fun onTileClick(r: Int, c: Int) {
        if (isComplete || isLoading) return
        val isAdjacent = (r == emptyRow && abs(c - emptyCol) == 1) || (c == emptyCol && abs(r - emptyRow) == 1)
        if (isAdjacent) {
            swap(r, c, emptyRow, emptyCol)
            emptyRow = r
            emptyCol = c
            moves++
            values = values.copyOf()
            saveState()
            if (checkIfComplete()) {
                isComplete = true
                calculateAndSaveScore()
            }
        }
    }

    private fun calculateAndSaveScore() {
        viewModelScope.launch {
            val timeElapsed = (timeWhenPaused + (System.currentTimeMillis() - startTime)) / 1000
            val difficultyMultiplier = when (size) { 3 -> 1.0; 4 -> 1.5; 5 -> 2.0; else -> 1.0 }
            val movePenalty = 10
            val timePenalty = 5
            val baseScore = 10000
            finalScore = ((baseScore * difficultyMultiplier) - (moves * movePenalty) - (timeElapsed * timePenalty)).toInt().coerceAtLeast(0)
            val oldHighScore = dataStore.getHighScore(size).first()
            if (finalScore > oldHighScore) {
                isNewHighScore = true
                dataStore.updateHighScore(size, finalScore)
            }
            dataStore.clearSavedGame()
        }
    }

    private fun swap(r1: Int, c1: Int, r2: Int, c2: Int) {
        val temp = values[r1][c1]
        values[r1][c1] = values[r2][c2]
        values[r2][c2] = temp
    }

    private fun checkIfComplete(): Boolean {
        var expected = 1
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (r == size - 1 && c == size - 1) {
                    if (values[r][c] != 0) return false
                } else {
                    if (values[r][c] != expected++) return false
                }
            }
        }
        return true
    }

    fun getElapsedTime(): Long {
        if (startTime == 0L || isComplete || isLoading) {
            return timeWhenPaused
        }
        return timeWhenPaused + (System.currentTimeMillis() - startTime)
    }
}

class PuzzleViewModelFactory(private val dataStore: SettingsDataStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PuzzleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PuzzleViewModel(dataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class PuzzleActivity : ComponentActivity() {
    private lateinit var viewModel: PuzzleViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        val size = intent.getIntExtra("SIZE", 3).coerceAtLeast(3)
        val imageUriString = intent.getStringExtra("IMAGE_URI")

        if (imageUriString == null) {
            finish()
            return
        }

        val dataStore = SettingsDataStore(this)
        val viewModelFactory = PuzzleViewModelFactory(dataStore)

        setContent {
            val isDark by dataStore.isDarkTheme.collectAsState(initial = isSystemInDarkTheme())
            viewModel = viewModel(factory = viewModelFactory)

            PuzzleGameTheme(darkTheme = isDark) {
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                PuzzleScreen(
                    size = size,
                    imageUriString = imageUriString,
                    viewModel = viewModel,
                    onMenuClick = {
                        finish() // onStop will save the state
                    },
                    onNewGameClick = {
                        scope.launch { viewModel.clearAndNewGame(size, imageUriString, context) }
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::viewModel.isInitialized) {
            viewModel.saveState()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    size: Int,
    imageUriString: String?,
    viewModel: PuzzleViewModel,
    onMenuClick: () -> Unit,
    onNewGameClick: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(size, imageUriString) {
        viewModel.setup(size, imageUriString, context)
    }

    var time by remember { mutableStateOf(0L) }
    var showHintDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = viewModel.isComplete, key2 = viewModel.isLoading) {
        while (!viewModel.isComplete && !viewModel.isLoading) {
            time = viewModel.getElapsedTime()
            delay(1000)
        }
    }

    val formattedTime = remember(time) {
        val minutes = (time / 1000) / 60
        val seconds = (time / 1000) % 60
        "%02d:%02d".format(minutes, seconds)
    }

    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val clickSoundPlayer = remember {
        try { MediaPlayer.create(context, R.raw.tile_click) } catch (e: Exception) { null }
    }
    val winSoundPlayer = remember {
        try { MediaPlayer.create(context, R.raw.win_sound) } catch (e: Exception) { null }
    }

    DisposableEffect(Unit) {
        onDispose {
            clickSoundPlayer?.release()
            winSoundPlayer?.release()
        }
    }

    LaunchedEffect(viewModel.moves) {
        if (viewModel.moves > 0 && !viewModel.isLoading) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            clickSoundPlayer?.start()
        }
    }

    LaunchedEffect(viewModel.isComplete) {
        if (viewModel.isComplete) {
            delay(300)
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            winSoundPlayer?.start()
        }
    }

    val sheetState = rememberModalBottomSheetState()
    if (viewModel.isComplete) {
        ModalBottomSheet(
            onDismissRequest = onMenuClick,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("🎉 Tebrikler!", style = MaterialTheme.typography.headlineMedium)
                if (viewModel.isNewHighScore) {
                    Text("🏆 Yeni Rekor: ${viewModel.finalScore} Puan!", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                } else {
                    Text("Sadece ${viewModel.moves} hamlede tamamladın!\nPuan: ${viewModel.finalScore}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onMenuClick) { Text("Menü") }
                    Button(onClick = onNewGameClick) { Text("Yeni Oyun") }
                }
            }
        }
    }

    if (showHintDialog) {
        HintDialog(
            imageUri = viewModel.imageUri,
            onDismiss = { showHintDialog = false }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Hamle",
                        value = viewModel.moves.toString(),
                        icon = Icons.Default.SyncAlt
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Süre",
                        value = formattedTime,
                        icon = Icons.Default.Timer
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                PuzzleBoard(viewModel)
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onMenuClick) { Text("Menü") }
                    ShuffleButton(onClick = onNewGameClick)
                    Button(onClick = { showHintDialog = true }) { Text("💡 İpucu") }
                    if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                        Button(onClick = { viewModel.solvePuzzle() }) { Text("Çöz") }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, label: String, value: String, icon: ImageVector) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HintDialog(imageUri: String?, onDismiss: () -> Unit) {
    if (imageUri != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Kapat")
                }
            }
        )
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun PuzzleBoard(viewModel: PuzzleViewModel) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
    ) {
        val tileSize = maxWidth / viewModel.size

        val tilePositions = remember(viewModel.values) {
            Array(viewModel.size * viewModel.size + 1) { 0 to 0 }.also { array ->
                viewModel.values.forEachIndexed { r, row ->
                    row.forEachIndexed { c, number ->
                        if (number >= 0 && number < array.size) {
                            array[number] = r to c
                        }
                    }
                }
            }
        }

        if (viewModel.imagePieces.isNotEmpty()) {
            val hasEmptyTile = viewModel.values.flatten().any { it == 0 }
            val lastTile = viewModel.size * viewModel.size
            val range = if (hasEmptyTile) (1 until lastTile) else (1..lastTile)

            range.forEach { number ->
                val (r, c) = tilePositions[number]
                if (number >= viewModel.imagePieces.size) return@forEach
                val imageBitmap = viewModel.imagePieces[number]

                val animatedX by animateDpAsState(
                    targetValue = tileSize * c,
                    animationSpec = tween(300),
                    label = "tile_x_$number"
                )
                val animatedY by animateDpAsState(
                    targetValue = tileSize * r,
                    animationSpec = tween(300),
                    label = "tile_y_$number"
                )

                PuzzleTile(
                    modifier = Modifier
                        .offset(x = animatedX, y = animatedY)
                        .width(tileSize)
                        .height(tileSize)
                        .padding(2.dp),
                    imageBitmap = imageBitmap,
                    onClick = { viewModel.onTileClick(r, c) }
                )
            }
        }
    }
}

@Composable
fun PuzzleTile(modifier: Modifier = Modifier, imageBitmap: ImageBitmap, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Puzzle Piece",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun ShuffleButton(onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isShuffling by remember { mutableStateOf(false) }
    val buttonText = if (isShuffling) "✅ Karıştırıldı" else "🔀 Karıştır"

    Button(onClick = {
        isShuffling = true
        onClick()
    }) {
        Text(buttonText)
    }

    if (isShuffling) {
        LaunchedEffect(isShuffling) {
            delay(1000)
            isShuffling = false
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PuzzleScreenPreview() {
    PuzzleGameTheme {
        // This preview is simplified and won't have a real ViewModel
        // You can create a dummy ViewModel or pass dummy data for preview purposes
    }
}
