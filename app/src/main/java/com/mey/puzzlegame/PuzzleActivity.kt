package com.mey.puzzlegame

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mey.puzzlegame.ui.theme.PuzzleGameTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.graphics.ImageBitmap


// ViewModel to hold the state and logic
class PuzzleViewModel(private val dataStore: SettingsDataStore) : ViewModel() {
    var size by mutableStateOf(3)
    var moves by mutableStateOf(0)
    var values by mutableStateOf(Array(size) { Array(size) { 0 } })
    var imagePieces by mutableStateOf<List<ImageBitmap>>(emptyList())
    var isComplete by mutableStateOf(false)
    var isNewHighScore by mutableStateOf(false)
    var finalScore by mutableStateOf(0)
    var imageUri by mutableStateOf<Uri?>(null)

    private var emptyRow by mutableStateOf(0)
    private var emptyCol by mutableStateOf(0)
    var startTime by mutableStateOf(0L)

    fun setup(puzzleSize: Int, uriString: String?, context: Context) {
        size = puzzleSize
        imageUri = uriString?.toUri()
        values = Array(size) { Array(size) { 0 } }
        newGame(context)
    }

    fun newGame(context: Context) {
        moves = 0
        isComplete = false
        isNewHighScore = false
        finalScore = 0
        startTime = System.currentTimeMillis()
        sliceImage(context)
        shuffleBoard()
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
    }

    @SuppressLint("RestrictedApi")
    private fun sliceImage(context: Context) {
        try {
            val sourceBitmap: Bitmap
            if (imageUri != null) {
                // Load bitmap from user-selected URI
                context.contentResolver.openInputStream(imageUri!!)?.use {
                    sourceBitmap = BitmapFactory.decodeStream(it)
                }
                ?: throw Exception("Cannot open input stream for URI")
            } else {
                // Fallback to loading bitmap from drawable resources
                val drawableId = when (size) {
                    3 -> R.drawable.puzzle_image_3x3
                    4 -> R.drawable.puzzle_image_4x4
                    5 -> R.drawable.puzzle_image_5x5
                    else -> R.drawable.puzzle_image_3x3 // Default fallback
                }
                sourceBitmap = BitmapFactory.decodeResource(context.resources, drawableId)
            }


            val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, 600, 600, true)
            val pieces = mutableListOf<ImageBitmap>()
            val pieceSize = scaledBitmap.width / size

            // Add a placeholder for the 0th tile (the empty space)
            pieces.add(ImageBitmap(1, 1)) // Dummy bitmap

            for (r in 0 until size) {
                for (c in 0 until size) {
                    val piece = Bitmap.createBitmap(scaledBitmap, c * pieceSize, r * pieceSize, pieceSize, pieceSize)
                    pieces.add(piece.asImageBitmap())
                }
            }
            imagePieces = pieces
        } catch (e: Exception) {
            // Handle exception, e.g., if image resource is not found or URI is invalid
            e.printStackTrace()
            // You might want to show an error message to the user here
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

            // Swap the empty tile with the random neighbor
            swap(emptyRow, emptyCol, randomRow, randomCol)

            // Update the new empty tile position
            emptyRow = randomRow
            emptyCol = randomCol
        }

        if (checkIfComplete()) {
            shuffleBoard()
        }
        values = values.copyOf() // Trigger UI update
    }

    fun onTileClick(r: Int, c: Int) {
        if (isComplete) return
        val isAdjacent = (r == emptyRow && abs(c - emptyCol) == 1) || (c == emptyCol && abs(r - emptyRow) == 1)
        if (isAdjacent) {
            swap(r, c, emptyRow, emptyCol)
            emptyRow = r
            emptyCol = c
            moves++
            values = values.copyOf() // Trigger recomposition

            if (checkIfComplete()) {
                isComplete = true
                calculateAndSaveScore()
            }
        }
    }

    private fun calculateAndSaveScore() {
        viewModelScope.launch {
            val timeElapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        val size = intent.getIntExtra("SIZE", 3).coerceAtLeast(3)
        val imageUriString = intent.getStringExtra("IMAGE_URI")
        val dataStore = SettingsDataStore(this)

        setContent {
            val isDark by dataStore.isDarkTheme.collectAsState(initial = isSystemInDarkTheme())
            PuzzleGameTheme(darkTheme = isDark) {
                PuzzleScreen(
                    size = size,
                    imageUriString = imageUriString,
                    viewModelFactory = PuzzleViewModelFactory(dataStore),
                    onMenuClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    size: Int,
    imageUriString: String?,
    viewModelFactory: PuzzleViewModelFactory,
    onMenuClick: () -> Unit,
    viewModel: PuzzleViewModel = viewModel(factory = viewModelFactory)
) {
    val context = LocalContext.current
    LaunchedEffect(size, imageUriString) {
        viewModel.setup(size, imageUriString, context)
    }

    // --- Timer State and Logic ---
    var time by remember { mutableStateOf(0L) }

    LaunchedEffect(key1 = viewModel.isComplete, key2 = viewModel.startTime) {
        if (!viewModel.isComplete && viewModel.startTime > 0L) {
            while (true) {
                time = (System.currentTimeMillis() - viewModel.startTime) / 1000
                delay(1000)
            }
        }
    }

    val formattedTime = remember(time) {
        val minutes = time / 60
        val seconds = time % 60
        "%02d:%02d".format(minutes, seconds)
    }

    // --- Sound & Haptic Feedback ---
    val view = LocalView.current

    val clickSoundPlayer = remember {
        try {
            MediaPlayer.create(context, R.raw.tile_click)
        } catch (e: Exception) { null }
    }
    val winSoundPlayer = remember {
        try {
            MediaPlayer.create(context, R.raw.win_sound)
        } catch (e: Exception) { null }
    }

    DisposableEffect(Unit) {
        onDispose {
            clickSoundPlayer?.release()
            winSoundPlayer?.release()
        }
    }

    LaunchedEffect(viewModel.moves) {
        if (viewModel.moves > 0) {
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

    // --- Win Screen Bottom Sheet ---
    val sheetState = rememberModalBottomSheetState()
    if (viewModel.isComplete) {
        ModalBottomSheet(
            onDismissRequest = onMenuClick, // Go to menu if dismissed
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(), // Ensures content is above navigation bar
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
                    Button(onClick = {
                        viewModel.newGame(context)
                    }) { Text("Yeni Oyun") }
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hamle: ${viewModel.moves}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Süre: $formattedTime", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            PuzzleBoard(viewModel)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onMenuClick) { Text("Menü") }
                ShuffleButton(viewModel)
                if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Button(onClick = { viewModel.solvePuzzle() }) { Text("Çöz") }
                }
            }
        }
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

        // Create a map of number to its current position for quick lookup
        val tilePositions = remember(viewModel.values) {
            Array(viewModel.size * viewModel.size + 1) { 0 to 0 }.also { array ->
                viewModel.values.forEachIndexed { r, row ->
                    row.forEachIndexed { c, number ->
                        if (number >= 0 && number < array.size) { // Safety check
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

                // Find the current position of this specific tile
                val (r, c) = tilePositions[number]
                if (number >= viewModel.imagePieces.size) return@forEach // Safety check
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
                    // The onClick is now correctly associated with the tile's logical position
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
fun ShuffleButton(viewModel: PuzzleViewModel) {
    val context = LocalContext.current
    var isShuffling by remember { mutableStateOf(false) }
    val buttonText = if (isShuffling) "✅ Karıştırıldı" else "🔀 Karıştır"

    Button(onClick = {
        isShuffling = true
        viewModel.newGame(context)
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
        val dummyDataStore = SettingsDataStore(LocalContext.current)
        PuzzleScreen(
            size = 4, 
            imageUriString = null,
            viewModelFactory = PuzzleViewModelFactory(dummyDataStore),
            onMenuClick = {}
        )
    }
}
