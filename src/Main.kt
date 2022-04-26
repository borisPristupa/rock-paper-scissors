package com.boris.rps

import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextCharacter
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.BasicTextImage
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import java.util.LinkedList
import java.util.Queue
import kotlinx.coroutines.runBlocking

data class Point(val x: Int, val y: Int) {
  operator fun plus(other: Point): Point {
    return Point(x + other.x, y + other.y)
  }

  operator fun unaryMinus(): Point {
    return Point(-x, -y)
  }

  operator fun minus(other: Point): Point {
    return this + (-other)
  }
}

val Int.x: Point get() = Point(this, 0)
val Int.y: Point get() = Point(0, this)

data class Dimension(val width: Int, val height: Int) {
  init {
    require(width >= 0)
    require(height >= 0)
  }

  val xRange: IntRange = 0 until width
  val yRange: IntRange = 0 until height
}

infix fun Int.by(height: Int): Dimension {
  require(this >= 0)
  require(height >= 0)
  return Dimension(this, height)
}

infix fun Point.within(dimension: Dimension): Boolean {
  return x in dimension.xRange && y in dimension.yRange
}

interface Shape {
  val dimension: Dimension
  fun points(): Sequence<Point>
}

interface Drawable : Shape {
  fun draw(): Sequence<Triple<Point, Char, TextColor>>

  companion object {
    val Red = TextColor.ANSI.RED
    val Green = TextColor.ANSI.GREEN
    val Black = TextColor.ANSI.BLACK
  }
}

fun textCharacter(
  char: Char,
  foreground: TextColor = TextColor.ANSI.BLACK_BRIGHT,
  background: TextColor = TextColor.ANSI.WHITE_BRIGHT
): TextCharacter {
  return TextCharacter(' ')
    .withCharacter(char)
    .withForegroundColor(foreground)
    .withBackgroundColor(background)
}

enum class Item(val symbol: Char) : Drawable {
  Rock('@'),
  Paper('#'),
  Scissors('%');

  override val dimension: Dimension = 1 by 1

  override fun points(): Sequence<Point> {
    return sequenceOf(0.x + 0.y)
  }

  override fun draw(): Sequence<Triple<Point, Char, TextColor>> {
    return points().map {
      Triple(it, symbol, Drawable.Black)
    }
  }

  fun stronger(): Item {
    return when (this) {
      Rock -> Paper
      Paper -> Scissors
      Scissors -> Rock
    }
  }

  fun weaker(): Item {
    return when (this) {
      Rock -> Scissors
      Paper -> Rock
      Scissors -> Paper
    }
  }

  companion object {
    fun random(): Item {
      return values().random()
    }

    fun randomItems(): Sequence<Item> {
      return generateSequence { random() }
    }
  }
}

class ItemEntity(item: Item) : GameEntity, Drawable by item {
  override val id: EntityId = GameEntity.nextId

  override fun tick(arena: MutableArena) = Unit
}

class Rect(override val dimension: Dimension) : Drawable {
  private val components: List<Pair<Point, Item>> =
    points().zip(Item.randomItems()).toList()

  private val width get() = dimension.width
  private val height get() = dimension.height

  override fun points(): Sequence<Point> {
    val top = dimension.xRange.asSequence().map { it.x + 0.y }
    val bot = dimension.xRange.asSequence().map { it.x + (height - 1).y }

    val left = dimension.yRange.asSequence().map { 0.x + it.y }
    val right = dimension.yRange.asSequence().map { (width - 1).x + it.y }

    return top + left + right + bot
  }

  override fun draw(): Sequence<Triple<Point, Char, TextColor>> {
    return components.asSequence().flatMap { (point, item) ->
      item.draw().map { Triple(point + it.first, it.second, it.third) }
    }
  }
}

fun getArena(dimension: Dimension): MutableArena {
  val arena = ArenaImpl(dimension)
  val (width, height) = dimension

  val top = dimension.xRange.asSequence().map { it.x + 0.y }
  val bot = dimension.xRange.asSequence().map { it.x + (height - 1).y }

  val left = dimension.yRange.asSequence().map { 0.x + it.y }
  val right = dimension.yRange.asSequence().map { (width - 1).x + it.y }

  val walls = (top + left + right + bot).distinct()

  for (point in walls) {
    arena.moveEntityTo(ItemEntity(Item.random()), point)
  }
  return arena
}

interface Arena { // todo: mb don't need immutable?
  val dimension: Dimension

  fun entityAt(point: Point): GameEntity?
  fun positionOf(entity: GameEntity): Point?
  fun entities(): Sequence<Pair<Point, GameEntity>>
}

interface MutableArena : Arena {
  fun clearAt(point: Point): GameEntity?
  fun moveEntityTo(entity: GameEntity, point: Point)
}

class ArenaImpl(override val dimension: Dimension) : MutableArena {
  private val grid: MutableList<MutableList<GameEntity?>> = MutableList(dimension.height) {
    MutableList(dimension.width) {
      null
    }
  }
  private val positions = mutableMapOf<EntityId, Point>()

  override fun entityAt(point: Point): GameEntity? {
    require(point.x in dimension.xRange && point.y in dimension.yRange) // todo message | logging
    return grid[point.y][point.x]
  }

  override fun positionOf(entity: GameEntity): Point? {
    return positions[entity.id]
  }

  override fun clearAt(point: Point): GameEntity? {
    return entityAt(point)?.also { entity ->
      positions -= entity.id
      grid[point.y][point.x] = null
    }
  }

  override fun moveEntityTo(entity: GameEntity, point: Point) {
    require(entityAt(point) == null) // todo message
    positionOf(entity)?.let { clearAt(it) }
    positions[entity.id] = point
    grid[point.y][point.x] = entity
  }

  override fun entities(): Sequence<Pair<Point, GameEntity>> {
    return grid.asSequence().flatMapIndexed { y: Int, row: MutableList<GameEntity?> ->
      row.mapIndexedNotNull { x, entity -> entity?.let { (x.x + y.y) to it } }
    } // ten().mapNotNull { it }
  }
}

typealias EntityId = ULong

interface GameEntity : Drawable {
  companion object { // fixme
    var nextId: EntityId = EntityId.MIN_VALUE
      get() = field++
      private set
  }

  val id: EntityId

  fun tick(arena: MutableArena)
}

enum class PlayerAction {
  Up, Down, Left, Right, NoAction
}

class Player(
  initialItem: Item,
  private val inputSource: () -> PlayerAction
) : Drawable, GameEntity {
  var item: Item = initialItem

  override val dimension: Dimension
    get() = 1 by 1

  override val id: EntityId = GameEntity.nextId

  override fun tick(arena: MutableArena) {
    val position = arena.positionOf(this) ?: return // todo: nop
    val moved = when (inputSource()) {
      PlayerAction.Up -> tryMoveTo(position - 1.y, arena)
      PlayerAction.Down -> tryMoveTo(position + 1.y, arena)
      PlayerAction.Left -> tryMoveTo(position - 1.x, arena)
      PlayerAction.Right -> tryMoveTo(position + 1.x, arena)
      PlayerAction.NoAction -> false
    }
    if (!moved) {
      strengthen()
    }
  }

  private fun tryMoveTo(newPosition: Point, arena: MutableArena): Boolean {
    if (newPosition within arena.dimension && arena.entityAt(newPosition) == null) {
      arena.moveEntityTo(this, newPosition)
      return true
    }
    return false
  }

  override fun points(): Sequence<Point> {
    return sequenceOf(0.x + 0.y)
  }

  override fun draw(): Sequence<Triple<Point, Char, TextColor>> {
    return points().flatMap { item.draw().map { Triple(it.first, it.second, Drawable.Green) } }
  }

  fun strengthen() {
    item = item.stronger()
  }
}

// class PlayFrame(dimension: Dimension) {
//  private val rect: Rect = Rect(dimension)
//
//  fun draw(screen: TerminalScreen, drawables: List<Pair<Point, Drawable>>) {
//    screen.clear()
//
//    for ((point, char, color) in rect.draw()) {
//      val (x, y) = point
//      screen.setCharacter(x, y, textCharacter(char, color)) // fixme
//    }
//
//    for ((position, drawable) in drawables) {
//      for ((point, char, color) in drawable.draw()) {
//        val (x, y) = point + position
//        screen.setCharacter(x, y, textCharacter(char, color)) // fixme
//      }
//    }
//    screen.refresh()
//  }
// }

sealed class GameAction {
  object Exit : GameAction()
  class PlayerGameAction(val playerAction: PlayerAction) : GameAction()
}

class Game(var dimension: Dimension) {
  //  private val playFrame = PlayFrame(dimension)
  private val arena: MutableArena = getArena((dimension.width / 2) by (dimension.height / 2))

  private val playerActions: Queue<PlayerAction> = LinkedList()
  private val player = Player(Item.Rock) {
    playerActions.poll() ?: PlayerAction.NoAction
  }

  init {
    arena.moveEntityTo(player, (arena.dimension.width / 2).x + (arena.dimension.height / 2).y)
  }

  fun playerDo(playerAction: PlayerAction) {
    playerActions.add(playerAction)
  }

  fun tick() {
    for ((_, entity) in arena.entities()) {
      entity.tick(arena)
    }
  }

  fun draw(screen: TerminalScreen) {
    val textImage =
      BasicTextImage(TerminalSize(dimension.width, dimension.height), textCharacter(' ', TextColor.ANSI.DEFAULT))

    textImage.setCharacterAt(1, 1, textCharacter(HEART_FULL, Drawable.Black))
    textImage.setCharacterAt(2, 1, textCharacter(HEART_EMPTY, Drawable.Black))
    textImage.setCharacterAt(3, 1, textCharacter(HEART_EMPTY, Drawable.Black))

    for ((position, entity) in arena.entities()) {
      for ((point, char, color) in entity.draw()) {
        val arenaX = (dimension.width - arena.dimension.width) / 2
        val arenaY = (dimension.height - arena.dimension.height) / 2
        val (x, y) = point + position + arenaX.x + arenaY.y
        textImage.setCharacterAt(x, y, textCharacter(char, color)) // fixme
      }
    }

    screen.newTextGraphics().drawImage(TerminalPosition.TOP_LEFT_CORNER, textImage)
    screen.refresh()
  }
}

const val HEART_EMPTY = '\u2661'
const val HEART_FULL = '\u2665'

sealed class Key {
  object Escape : Key()
  object ArrowUp : Key()
  object ArrowDown : Key()
  object ArrowLeft : Key()
  object ArrowRight : Key()
  class Symbol(val symbol: Char) : Key()
  object Unsupported : Key()
}

val keyMap = mapOf(
  Key.Escape to GameAction.Exit, // todo: it may also mean exit from command mode
  Key.ArrowUp to GameAction.PlayerGameAction(PlayerAction.Up),
  Key.ArrowDown to GameAction.PlayerGameAction(PlayerAction.Down),
  Key.ArrowLeft to GameAction.PlayerGameAction(PlayerAction.Left),
  Key.ArrowRight to GameAction.PlayerGameAction(PlayerAction.Right),
)

fun KeyStroke.toKey(): Key {
  return if (character == null) {
    when (keyType) {
      KeyType.ArrowUp -> Key.ArrowUp
      KeyType.ArrowDown -> Key.ArrowDown
      KeyType.ArrowLeft -> Key.ArrowLeft
      KeyType.ArrowRight -> Key.ArrowRight
      KeyType.Escape -> Key.Escape
      else -> Key.Unsupported
    }
  } else {
    Key.Symbol(character)
  }
}

fun loop(screen: TerminalScreen, game: Game) = runBlocking {
  while (true) {
    val key = screen.pollInput()?.toKey()
    when (val action = key?.let { keyMap[it] }) {
      GameAction.Exit -> break
      null -> game.tick()
      is GameAction.PlayerGameAction -> game.playerDo(action.playerAction)
    }
    game.draw(screen)
  }
}

fun TerminalSize.toDimension(): Dimension {
  return columns by rows
}

fun main() {
  DefaultTerminalFactory().createScreen().use { screen ->
    screen.startScreen()
    val game = Game(screen.terminalSize.toDimension())
    screen.terminal.addResizeListener { _, newSize ->
      screen.clear()
      game.dimension = newSize.toDimension()
    }
    loop(screen, game)
  }
}
