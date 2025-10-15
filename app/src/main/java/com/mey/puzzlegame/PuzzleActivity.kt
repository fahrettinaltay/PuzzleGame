package com.mey.puzzlegame

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mey.puzzlegame.ui.theme.PuzzleGameTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

// ViewModel to hold the state and logic
class PuzzleViewModel(private val dataStore: SettingsDataStore) : ViewModel() {
    var size by mutableStateOf(3)
    var moves by mutableStateOf(0)
    var values by mutableStateOf(Array(size) { Array(size) { 0 } })
    var isComplete by mutableStateOf(false)
    var isNewHighScore by mutableStateOf(false)
    var finalScore by mutableStateOf(0)

    private var emptyRow by mutableStateOf(0)
    private var emptyCol by mutableStateOf(0)
    private var startTime by mutableStateOf(0L)

    fun setup(puzzleSize: Int) {
        size = puzzleSize
        values = Array(size) { Array(size) { 0 } }
        newGame()
    }

    fun newGame() {
        moves = 0
        isComplete = false
        isNewHighScore = false
        finalScore = 0
        startTime = System.currentTimeMillis()
        shuffleBoard()
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
        var lastMove: Pair<Int, Int>? = null
        repeat(shuffleMoves) {
            val possibleMoves = mutableListOf<Pair<Int, Int>>().apply {
                if (emptyRow > 0 && (lastMove == null || lastMove != emptyRow - 1 to emptyCol)) add(emptyRow - 1 to emptyCol)
                if (emptyRow < size - 1 && (lastMove == null || lastMove != emptyRow + 1 to emptyCol)) add(emptyRow + 1 to emptyCol)
                if (emptyCol > 0 && (lastMove == null || lastMove != emptyRow to emptyCol - 1)) add(emptyRow to emptyCol - 1)
                if (emptyCol < size - 1 && (lastMove == null || lastMove != emptyRow to emptyCol + 1)) add(emptyRow to emptyCol + 1)
            }
            val (nr, nc) = possibleMoves.ifEmpty {
                buildList {
                    if (emptyRow > 0) add(emptyRow - 1 to emptyCol)
                    if (emptyRow < size - 1) add(emptyRow + 1 to emptyCol)
                    if (emptyCol > 0) add(emptyRow to emptyCol - 1)
                    if (emptyCol < size - 1) add(emptyRow to emptyCol + 1)
                }
            }.random()
            swap(emptyRow, emptyCol, nr, nc)
            lastMove = emptyRow to emptyCol
            emptyRow = nr
            emptyCol = nc
        }
        if (checkIfComplete()) {
            shuffleBoard()
        }
        values = values.copyOf()
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
        super.onCreate(savedInstanceState)
        val size = intent.getIntExtra("SIZE", 3).coerceAtLeast(3)
        val dataStore = SettingsDataStore(this)
        setContent {
            val isDark by dataStore.isDarkTheme.collectAsState(initial = isSystemInDarkTheme())
            PuzzleGameTheme(darkTheme = isDark) {
                PuzzleScreen(
                    size = size,
                    viewModelFactory = PuzzleViewModelFactory(dataStore),
                    onMenuClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun PuzzleScreen(
    size: Int,
    viewModelFactory: PuzzleViewModelFactory,
    onMenuClick: () -> Unit,
    viewModel: PuzzleViewModel = viewModel(factory = viewModelFactory)
) {
    LaunchedEffect(size) {
        viewModel.setup(size)
    }

    if (viewModel.isComplete) {
        WinDialog(
            isNewHighScore = viewModel.isNewHighScore,
            moves = viewModel.moves,
            score = viewModel.finalScore,
            onDismiss = onMenuClick, // Go to menu on dismiss
            onNewGame = { viewModel.newGame() }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Hamle: ${viewModel.moves}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            PuzzleBoard(viewModel)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onMenuClick) { Text("Menü") }
                ShuffleButton(viewModel)
            }
        }
    }
}

@Composable
fun PuzzleBoard(viewModel: PuzzleViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        viewModel.values.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { c, value ->
                    PuzzleTile(
                        modifier = Modifier.weight(1f),
                        value = value,
                        onClick = { viewModel.onTileClick(r, c) }
                    )
                }
            }
        }
    }
}

@Composable
fun PuzzleTile(modifier: Modifier = Modifier, value: Int, onClick: () -> Unit) {
    val isVisible = value != 0
    val tileColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = isVisible, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isVisible) tileColor else emptyColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isVisible) 4.dp else 0.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isVisible) {
                Text(
                    text = value.toString(),
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ShuffleButton(viewModel: PuzzleViewModel) {
    var isShuffling by remember { mutableStateOf(false) }
    val buttonText = if (isShuffling) "✅ Karıştırıldı" else "🔀 Karıştır"

    Button(onClick = {
        isShuffling = true
        viewModel.newGame()
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

@Composable
fun WinDialog(isNewHighScore: Boolean, moves: Int, score: Int, onDismiss: () -> Unit, onNewGame: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("🎉 Tebrikler!", style = MaterialTheme.typography.headlineMedium)
                if (isNewHighScore) {
                    Text("🏆 Yeni Rekor: $score Puan!", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                } else {
                    Text("Sadece $moves hamlede tamamladın!\nPuan: $score")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDismiss) { Text("Menü") }
                    Button(onClick = {
                        onNewGame()
                        // Keep dialog open until user clicks menu
                    }) { Text("Yeni Oyun") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PuzzleScreenPreview() {
    PuzzleGameTheme {
        val dummyDataStore = SettingsDataStore(androidx.compose.ui.platform.LocalContext.current)
        PuzzleScreen(
            size = 4, 
            viewModelFactory = PuzzleViewModelFactory(dummyDataStore),
            onMenuClick = {}
        )
    }
}
