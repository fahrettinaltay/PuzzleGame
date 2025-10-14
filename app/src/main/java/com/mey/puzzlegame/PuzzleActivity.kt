package com.mey.puzzlegame

import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.GridLayout
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import kotlin.random.Random

class PuzzleActivity : AppCompatActivity() {

    private lateinit var gridLayout: GridLayout
    private lateinit var tvMoves: TextView
    private lateinit var btnMenu: androidx.appcompat.widget.AppCompatButton
    private lateinit var btnShuffle: androidx.appcompat.widget.AppCompatButton

    private var size = 3
    private var moves = 0
    private var startTime = 0L
    private var emptyRow = 0
    private var emptyCol = 0

    private lateinit var tiles: Array<Array<CardView?>>
    private lateinit var values: Array<Array<Int>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_puzzle)

        gridLayout = findViewById(R.id.gridLayout)
        tvMoves = findViewById(R.id.tvMoves)
        btnMenu = findViewById(R.id.btnMenu)
        btnShuffle = findViewById(R.id.btnShuffle)

        size = intent.getIntExtra("SIZE", 3).coerceAtLeast(3)

        buildGrid()
        newGame()

        btnMenu.setOnClickListener { finish() }
        btnShuffle.setOnClickListener { shufflePuzzle() }
    }

    private fun newGame() {
        shuffleBoard()
        moves = 0
        startTime = System.currentTimeMillis()
        updateUI()
    }

    private fun buildGrid() {
        gridLayout.removeAllViews()
        gridLayout.columnCount = size

        tiles = Array(size) { arrayOfNulls<CardView>(size) }
        values = Array(size) { Array(size) { 0 } }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val margin = dp(4)
        // Correctly calculate total horizontal margin: size * (left_margin + right_margin)
        val totalHorizontalMargin = margin * size * 2
        val tileSize = (screenWidth - (dp(padding) * 2) - totalHorizontalMargin) / size

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val tileColor = if (isDark) Color.parseColor("#FF6B35") else Color.parseColor("#6366F1")
        val textColor = Color.WHITE
        val textSp = when (size) { 3 -> 24f; 4 -> 20f; else -> 18f }

        for (r in 0 until size) {
            for (c in 0 until size) {
                val card = CardView(this).apply {
                    radius = dp(8).toFloat()
                    cardElevation = dp(4).toFloat()
                    setCardBackgroundColor(tileColor)

                    val params = GridLayout.LayoutParams().apply {
                        width = tileSize
                        height = tileSize
                        setMargins(margin, margin, margin, margin)
                        rowSpec = GridLayout.spec(r)
                        columnSpec = GridLayout.spec(c)
                    }
                    layoutParams = params
                }

                val number = TextView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
                    setTextColor(textColor)
                }

                card.addView(number)
                card.setOnClickListener { onTileClick(r, c) }

                gridLayout.addView(card)
                tiles[r][c] = card
            }
        }
    }

    private fun initValues() {
        var n = 1
        for (r in 0 until size) {
            for (c in 0 until size) {
                values[r][c] = n; n++
            }
        }
        values[size - 1][size - 1] = 0
        emptyRow = size - 1; emptyCol = size - 1
    }

    private fun shuffleBoard() {
        initValues()
        val shuffleMoves = size * size * 100
        var lastMove: Pair<Int, Int>? = null

        repeat(shuffleMoves) {
            val possibleMoves = mutableListOf<Pair<Int, Int>>()
            val (er, ec) = emptyRow to emptyCol

            if (er > 0 && (lastMove == null || lastMove != er - 1 to ec)) possibleMoves.add(er - 1 to ec)
            if (er < size - 1 && (lastMove == null || lastMove != er + 1 to ec)) possibleMoves.add(er + 1 to ec)
            if (ec > 0 && (lastMove == null || lastMove != er to ec - 1)) possibleMoves.add(er to ec - 1)
            if (ec < size - 1 && (lastMove == null || lastMove != er to ec + 1)) possibleMoves.add(er to ec + 1)
            
            val (nr, nc) = if (possibleMoves.isNotEmpty()) {
                possibleMoves.random()
            } else {
                buildList {
                    if (er > 0) add(er - 1 to ec)
                    if (er < size - 1) add(er + 1 to ec)
                    if (ec > 0) add(er to ec - 1)
                    if (ec < size - 1) add(er to ec + 1)
                }.random()
            }

            swap(er, ec, nr, nc)
            lastMove = emptyRow to emptyCol
            emptyRow = nr
            emptyCol = nc
        }

        if (isPuzzleComplete()) {
            shuffleBoard()
        }
    }

    private fun shufflePuzzle() {
        moves = 0
        startTime = System.currentTimeMillis()
        shuffleBoard()
        updateUI()
        btnShuffle.text = "✅ Karıştırıldı"
        btnShuffle.postDelayed({ btnShuffle.text = "🔀 Karıştır" }, 1000)
    }

    private fun onTileClick(r: Int, c: Int) {
        val adj = (r == emptyRow && kotlin.math.abs(c - emptyCol) == 1) ||
                (c == emptyCol && kotlin.math.abs(r - emptyRow) == 1)
        if (adj) {
            animateTileMove(r, c)
            swap(r, c, emptyRow, emptyCol)
            emptyRow = r; emptyCol = c
            moves++
            updateUI()

            if (isPuzzleComplete()) {
                showWinDialog()
            }
        }
    }

    private fun swap(r1: Int, c1: Int, r2: Int, c2: Int) {
        val t = values[r1][c1]; values[r1][c1] = values[r2][c2]; values[r2][c2] = t
    }

    private fun updateUI() {
        recolorTiles()
        for (r in 0 until size) {
            for (c in 0 until size) {
                val v = values[r][c]
                val card = tiles[r][c] ?: continue
                val tv = card.getChildAt(0) as TextView
                if (v == 0) {
                    tv.text = ""
                    card.visibility = View.INVISIBLE
                } else {
                    tv.text = v.toString()
                    card.visibility = View.VISIBLE
                }
            }
        }
        tvMoves.text = "Hamle: $moves"
    }

    private fun recolorTiles() {
        if (!::tiles.isInitialized) return
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val tileColor = if (isDark) Color.parseColor("#FF6B35") else Color.parseColor("#6366F1")
        val textColor = Color.WHITE
        for (r in 0 until size) {
            for (c in 0 until size) {
                val card = tiles[r][c] ?: continue
                card.setCardBackgroundColor(tileColor)
                (card.getChildAt(0) as? TextView)?.setTextColor(textColor)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        recolorTiles()
    }

    private fun isPuzzleComplete(): Boolean {
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

    private fun animateTileMove(r: Int, c: Int) {
        val tile = tiles[r][c] ?: return
        ObjectAnimator.ofFloat(tile, "translationX", 0f).start()
    }

    private fun showWinDialog() {
        val timeElapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()

        val difficultyMultiplier = when (size) {
            3 -> 1.0
            4 -> 1.5
            5 -> 2.0
            else -> 1.0
        }

        val movePenalty = 10
        val timePenalty = 5
        val baseScore = 10000

        val finalScore = ( (baseScore * difficultyMultiplier) - (moves * movePenalty) - (timeElapsed * timePenalty) ).toInt().coerceAtLeast(0)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🎉 Tebrikler!")
            .setMessage("Puzzle\'ı $moves hamlede ve ${timeElapsed}s içinde tamamladın!\n\nPuan: $finalScore")
            .setPositiveButton("Yeni Oyun") { _, _ -> newGame() }
            .setNegativeButton("Menü") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun dp(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

    companion object {
        private const val padding = 16
    }
}