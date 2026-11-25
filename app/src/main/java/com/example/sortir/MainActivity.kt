package com.example.sortir

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var gameView: GameView
    private var screenWidth = 0
    private var screenHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        gameView = GameView(this, screenWidth, screenHeight)
        setContentView(gameView)
    }

    override fun onPause() {
        super.onPause()
        gameView.saveHighScore()
    }
}

class GameView(context: Context, private val screenWidth: Int, private val screenHeight: Int) : View(context) {
    private val paint = Paint()
    private val pixelSize = 40
    private val margin = 5
    private val itemSize = pixelSize + margin

    private var shelves = mutableListOf<Shelf>()
    private var items = mutableListOf<Item>()
    private var selectedItem: Item? = null
    private var score = 0
    private var highScore = 0
    private var colors = listOf(
        Color.RED, Color.BLUE, Color.GREEN,
        Color.YELLOW, Color.MAGENTA, Color.CYAN
    )

    private val sharedPref = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

    init {
        highScore = sharedPref.getInt("high_score", 0)
        setupGame()
    }

    private fun setupGame() {
        // Создаем полки
        shelves.clear()
        val shelfCount = 5
        val shelfHeight = screenHeight / 2
        val shelfWidth = screenWidth - 200
        val startX = 100
        val shelfSpacing = (screenHeight - shelfHeight * shelfCount) / (shelfCount + 1)

        for (i in 0 until shelfCount) {
            val yPos = shelfSpacing * (i + 1) + shelfHeight * i
            shelves.add(Shelf(startX, yPos, shelfWidth, shelfHeight))
        }

        // Создаем предметы (по 3 каждого типа)
        items.clear()
        val itemsPerColor = 3
        val totalItems = colors.size * itemsPerColor

        colors.forEachIndexed { colorIndex, color ->
            repeat(itemsPerColor) {
                // Размещаем предметы на случайных полках
                val shelfIndex = Random.nextInt(shelfCount)
                val shelf = shelves[shelfIndex]
                val xPos = shelf.x + Random.nextInt(shelf.width - pixelSize)
                val yPos = shelf.y + shelf.height - pixelSize - 10 // Чтобы предметы стояли на полке

                items.add(Item(
                    x = xPos,
                    y = yPos,
                    color = color,
                    type = colorIndex,
                    shelfIndex = shelfIndex
                ))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Фон
        canvas.drawColor(Color.DKGRAY)

        // Отрисовка полок
        paint.color = Color.rgb(139, 69, 19) // Коричневый
        shelves.forEach { shelf ->
            canvas.drawRect(
                shelf.x.toFloat(),
                shelf.y.toFloat(),
                (shelf.x + shelf.width).toFloat(),
                (shelf.y + shelf.height).toFloat(),
                paint
            )
        }

        // Отрисовка предметов
        items.forEach { item ->
            paint.color = item.color
            canvas.drawRect(
                item.x.toFloat(),
                item.y.toFloat(),
                (item.x + pixelSize).toFloat(),
                (item.y + pixelSize).toFloat(),
                paint
            )

            // Добавляем детали для пиксельного стиля
            paint.color = Color.argb(100, 0, 0, 0)
            canvas.drawRect(
                (item.x + 5).toFloat(),
                (item.y + 5).toFloat(),
                (item.x + pixelSize - 5).toFloat(),
                (item.y + pixelSize - 5).toFloat(),
                paint
            )
        }

        // Отрисовка UI
        paint.color = Color.WHITE
        paint.textSize = 50f
        canvas.drawText("Счет: $score", 50f, 80f, paint)
        canvas.drawText("Рекорд: $highScore", screenWidth - 350f, 80f, paint)

        // Если предметов не осталось - генерируем новый уровень
        if (items.isEmpty()) {
            paint.color = Color.WHITE
            paint.textSize = 80f
            canvas.drawText("Уровень пройден!", (screenWidth / 2 - 250).toFloat(), (screenHeight / 2 - 100).toFloat(), paint)
            paint.textSize = 60f
            canvas.drawText("Нажмите для продолжения", (screenWidth / 2 - 300).toFloat(), (screenHeight / 2).toFloat(), paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (items.isEmpty()) {
                    setupGame()
                    invalidate()
                    return true
                }

                val touchX = event.x.toInt()
                val touchY = event.y.toInt()

                selectedItem = items.find { item ->
                    touchX >= item.x && touchX <= item.x + pixelSize &&
                            touchY >= item.y && touchY <= item.y + pixelSize
                }
            }
            MotionEvent.ACTION_MOVE -> {
                selectedItem?.let { item ->
                    item.x = event.x.toInt() - pixelSize / 2
                    item.y = event.y.toInt() - pixelSize / 2
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                selectedItem?.let { item ->
                    // Проверяем, на какую полку положили предмет
                    val newShelfIndex = shelves.indexOfFirst { shelf ->
                        event.x.toInt() >= shelf.x &&
                                event.x.toInt() <= shelf.x + shelf.width &&
                                event.y.toInt() >= shelf.y &&
                                event.y.toInt() <= shelf.y + shelf.height
                    }

                    if (newShelfIndex != -1) {
                        // Помещаем предмет на новую полку
                        item.shelfIndex = newShelfIndex
                        val shelf = shelves[newShelfIndex]
                        item.y = shelf.y + shelf.height - pixelSize - 10
                        item.x = event.x.toInt().coerceIn(
                            shelf.x,
                            shelf.x + shelf.width - pixelSize
                        )

                        // Проверяем, есть ли 3 одинаковых предмета на полке
                        checkForMatches(newShelfIndex)
                    } else {
                        // Возвращаем предмет на исходную полку
                        val shelf = shelves[item.shelfIndex]
                        item.y = shelf.y + shelf.height - pixelSize - 10
                        item.x = event.x.toInt().coerceIn(
                            shelf.x,
                            shelf.x + shelf.width - pixelSize
                        )
                    }

                    invalidate()
                }
                selectedItem = null
            }
        }
        return true
    }

    private fun checkForMatches(shelfIndex: Int) {
        val shelfItems = items.filter { it.shelfIndex == shelfIndex }
        val groupedItems = shelfItems.groupBy { it.type }

        groupedItems.forEach { (type, itemsOfType) ->
            if (itemsOfType.size >= 3) {
                // Удаляем 3 предмета этого типа
                val itemsToRemove = itemsOfType.take(3)
                items.removeAll(itemsToRemove)
                score += 100

                // Обновляем высокий счет
                if (score > highScore) {
                    highScore = score
                }
            }
        }
    }

    fun saveHighScore() {
        with(sharedPref.edit()) {
            putInt("high_score", highScore)
            apply()
        }
    }
}

data class Shelf(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class Item(
    var x: Int,
    var y: Int,
    val color: Int,
    val type: Int,
    var shelfIndex: Int
)