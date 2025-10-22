package com.mey.puzzlegame

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mey.puzzlegame.ui.theme.PuzzleGameTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ViewModel for Start Screen
class StartViewModel(private val dataStore: SettingsDataStore) : ViewModel() {

    val isDarkTheme = dataStore.isDarkTheme

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

// Factory to provide DataStore to ViewModel
class StartViewModelFactory(private val dataStore: SettingsDataStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StartViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StartViewModel(dataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class StartActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val dataStore = SettingsDataStore(this)

        setContent {
            val isDark by dataStore.isDarkTheme.collectAsState(initial = isSystemInDarkTheme())

            PuzzleGameTheme(darkTheme = isDark) {
                StartScreen(
                    viewModelFactory = StartViewModelFactory(dataStore),
                    onStartPuzzle = { size, imageUri ->
                        val intent = Intent(this, PuzzleActivity::class.java).apply {
                            putExtra("SIZE", size)
                            // Pass the Uri as a string. It can be null if no image is selected.
                            putExtra("IMAGE_URI", imageUri?.toString())
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
    onStartPuzzle: (Int, Uri?) -> Unit,
    viewModel: StartViewModel = viewModel(factory = viewModelFactory)
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState(initial = isSystemInDarkTheme())
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Modern photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedImageUri = uri }
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🧩 Puzzle Game", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Zorluk seviyesi seçin", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isDarkTheme) "🌙" else "☀️", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { viewModel.onThemeChange() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Difficulty Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val easyHighScore by viewModel.getHighScore(3).collectAsState()
                val mediumHighScore by viewModel.getHighScore(4).collectAsState()
                val hardHighScore by viewModel.getHighScore(5).collectAsState()

                DifficultyButton(text = "🟢 Kolay (3×3)", score = easyHighScore, onClick = { onStartPuzzle(3, selectedImageUri) })
                DifficultyButton(text = "🟡 Orta (4×4)", score = mediumHighScore, onClick = { onStartPuzzle(4, selectedImageUri) })
                DifficultyButton(text = "🔴 Zor (5×5)", score = hardHighScore, onClick = { onStartPuzzle(5, selectedImageUri) })
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Custom Image Picker Button
            Button(
                onClick = { photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                ) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🖼️ Galeriden Resim Seç")
            }
            if (selectedImageUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Seçilen resim puzzle için kullanılacak.", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Veya zorluk seviyesine özel resmi kullan.", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun DifficultyButton(text: String, score: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
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
        // Dummy factory for preview
        val dummyDataStore = SettingsDataStore(LocalContext.current)
        StartScreen(viewModelFactory = StartViewModelFactory(dummyDataStore), onStartPuzzle = { _, _ -> })
    }
}
