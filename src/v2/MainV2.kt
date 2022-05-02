package com.boris.rps.v2

import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextCharacter
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.BasicTextImage
import com.googlecode.lanterna.graphics.TextImage
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import java.util.LinkedList
import java.util.Queue
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

data class Position(val x: Int, val y: Int) {
  operator fun plus(other: Position): Position {
    return Position(x + other.x, y + other.y)
  }

  operator fun unaryMinus(): Position {
    return Position(-x, -y)
  }

  operator fun minus(other: Position): Position {
    return this + (-other)
  }
}

val Int.x: Position get() = Position(this, 0)
val Int.y: Position get() = Position(0, this)

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

infix fun Position.within(dimension: Dimension): Boolean {
  return x in dimension.xRange && y in dimension.yRange
}

fun Dimension.allPositions(): List<Position> {
  return yRange.flatMap { y -> xRange.map { it.x + y.y } }
}

class Grid<T>(val dimension: Dimension, private val default: T) {
  private val grid = MutableList(dimension.height) {
    MutableList(dimension.width) {
      default
    }
  }

  operator fun get(position: Position): T {
    require(position within dimension)
    return grid[position.y][position.x]
  }

  operator fun set(position: Position, value: T): T {
    require(position within dimension)
    return get(position).also {
      grid[position.y][position.x] = value
    }
  }
}

// ------------------------------------------------------------------------------------------------

interface Entity

data class PositionAndEntity(val position: Position, val entity: Entity)

interface EntityGrid {
  val dimension: Dimension

  fun entities(): List<PositionAndEntity>

  fun entityAt(position: Position): Entity?

  @kotlin.jvm.Throws(TileBusy::class, PointOutOfBounds::class, EntityAlreadyPresent::class)
  fun addEntity(entity: Entity, position: Position)

  @kotlin.jvm.Throws(EntityNotPresent::class)
  fun removeEntity(entity: Entity)

  @kotlin.jvm.Throws(EntityNotPresent::class, TileBusy::class, PointOutOfBounds::class)
  fun moveEntity(entity: Entity, position: Position)

  class TileBusy : RuntimeException()
  class EntityNotPresent : RuntimeException()
  class PointOutOfBounds : RuntimeException()
  class EntityAlreadyPresent : RuntimeException()
}

abstract class AbstractEntityGrid(final override val dimension: Dimension) : EntityGrid {
  private val grid = Grid<Entity?>(dimension, null)
  private val positions = mutableMapOf<Entity, Position>()

  override fun entities(): List<PositionAndEntity> {
    return dimension.allPositions().mapNotNull { pos -> grid[pos]?.let { PositionAndEntity(pos, it) } }
  }

  override fun entityAt(position: Position): Entity? {
    return grid[position]
  }

  override fun addEntity(entity: Entity, position: Position) {
    if (!(position within dimension)) {
      throw EntityGrid.PointOutOfBounds()
    }
    if (entityAt(position) != null) {
      throw EntityGrid.TileBusy()
    }
    if (this[entity] != null) {
      throw EntityGrid.EntityAlreadyPresent()
    }
    this[position] = entity
    this[entity] = position
  }

  override fun removeEntity(entity: Entity) {
    val position = this[entity] ?: throw EntityGrid.EntityNotPresent()
    this[position] = null
    this -= entity
  }

  override fun moveEntity(entity: Entity, position: Position) {
    // strong exception safety
    if (entityAt(position) != null) {
      throw EntityGrid.TileBusy()
    }
    removeEntity(entity)
    addEntity(entity, position)
  }

  protected operator fun get(position: Position): Entity? {
    return grid[position]
  }

  protected operator fun set(position: Position, entity: Entity?) {
    grid[position] = entity
  }

  protected operator fun get(entity: Entity): Position? {
    return positions[entity]
  }

  protected operator fun set(entity: Entity, position: Position) {
    positions[entity] = position
  }

  protected operator fun minusAssign(entity: Entity) {
    positions -= entity
  }
}

class LevelMap(dimension: Dimension) : AbstractEntityGrid(dimension)

class ArenaMap(val levelMap: LevelMap) : AbstractEntityGrid(levelMap.dimension) {
  override fun entities(): List<PositionAndEntity> {
    return super.entities() + levelMap.entities()
  }

  override fun entityAt(position: Position): Entity? {
    return super.entityAt(position) ?: levelMap.entityAt(position)
  }

  override fun removeEntity(entity: Entity) {
    val entityPos = this[entity]
    if (entityPos != null) {
      this[entityPos] = null
      this -= entity
    } else {
      levelMap.removeEntity(entity)
    }
  }

  override fun moveEntity(entity: Entity, position: Position) {
    if (this[entity] != null) {
      removeEntity(entity)
      addEntity(entity, position)
    } else {
      // if the entity is within the level, it remains there
      levelMap.moveEntity(entity, position)
    }
  }
}

sealed class RPS : Comparable<RPS> {
  object Rock : RPS()
  object Paper : RPS()
  object Scissors : RPS()

  private fun stronger(): RPS {
    return when (this) {
      Rock -> Paper
      Paper -> Scissors
      Scissors -> Rock
    }
  }

  override fun compareTo(other: RPS): Int {
    return when (other) {
      this -> 0
      stronger() -> -1
      else -> 1
    }
  }

  companion object {
    fun random(): RPS {
      return listOf(Rock, Paper, Scissors).random()
    }
  }
}

class Room(
  val from: Position,
  val toExclusive: Position
) {
  val size: Dimension get() = (toExclusive.x - from.x) by (toExclusive.y - from.y)

  operator fun contains(position: Position): Boolean {
    return (position - from) within size
  }
}

class Rooms(
  initialPos: Position,
  val roomGrid: Grid<Room?>
) {
  val initialRoom: Room = roomGrid[initialPos]!!

  fun allRooms(): List<Room> {
    return roomGrid.dimension.allPositions().mapNotNull { roomGrid[it] }
  }
}

data class Level(val levelMap: LevelMap, val rooms: Rooms)
data class Arena(val arenaMap: ArenaMap, val rooms: Rooms)

@JvmInline
value class Terrain(val levels: List<Level>) {
  init {
    require(levels.isNotEmpty())
  }
}

@JvmInline
value class World(val arenas: List<Arena>) {
  init {
    require(arenas.isNotEmpty())
  }
}

fun randomTerrain(
  roomSize: Dimension,
  numberOfRooms: Int = 8,
  numberOfLevels: Int = 3
): Terrain { // fixme magic constant
  require(numberOfRooms > 0)
  require(numberOfLevels > 0)

  return (0 until numberOfLevels)
    .map { randomLevel(roomSize, numberOfRooms) }
    .let { Terrain(it) }
}

fun randomLevel(roomSize: Dimension, numberOfRooms: Int): Level {
  require(numberOfRooms > 0)

  val grid = Grid((numberOfRooms * 2) by (numberOfRooms * 2), false)

  val initial = numberOfRooms.x + numberOfRooms.y
  grid[initial] = true

  var minX = initial.x
  var minY = initial.y
  var maxX = initial.x
  var maxY = initial.y
  run {
    val positions = mutableListOf(initial)
    repeat(numberOfRooms - 1) {
      var pos = positions.random()
      while (
        listOf(pos + 1.x, pos + 1.y, pos - 1.x, pos - 1.y).all { grid[it] }
      ) {
        pos -= if (Random.nextBoolean()) {
          1.x
        } else {
          1.y
        }
      }

      val variants = listOf(pos + 1.x, pos + 1.y, pos - 1.x, pos - 1.y).filterNot { grid[it] }
      pos = variants.random()
      positions += pos
      grid[pos] = true
      minX = minOf(minX, pos.x)
      minY = minOf(minY, pos.y)
      maxX = maxOf(maxX, pos.x)
      maxY = maxOf(maxY, pos.y)
    }
  }

  val levelDimensionInRooms = (maxX - minX + 1) by (maxY - minY + 1)
  val roomGrid = Grid<Room?>(levelDimensionInRooms, null)
  for (position in grid.dimension.allPositions()) {
    if (grid[position]) {
      val posInRooms = position - minX.x - minY.y
      roomGrid[posInRooms] = Room(
        (roomSize.xRange.first + posInRooms.x * (roomSize.width - 1)).x +
          (roomSize.yRange.first + posInRooms.y * (roomSize.height - 1)).y,

        (roomSize.xRange.last + 1 + posInRooms.x * (roomSize.width - 1)).x +
          (roomSize.yRange.last + 1 + posInRooms.y * (roomSize.height - 1)).y
      )
    }
  }

  val rooms = Rooms(
    initial - (minX.x + minY.y),
    roomGrid
  )

  val levelDimension = (levelDimensionInRooms.width * roomSize.width - levelDimensionInRooms.width + 1) by
    (levelDimensionInRooms.height * roomSize.height - levelDimensionInRooms.height + 1)
  val level = LevelMap(levelDimension)

  val walls = run {
    val (width, height) = roomSize

    val top = roomSize.xRange.asSequence().map { it.x + 0.y }
    val bot = roomSize.xRange.asSequence().map { it.x + (height - 1).y }

    val left = roomSize.yRange.asSequence().map { 0.x + it.y }
    val right = roomSize.yRange.asSequence().map { (width - 1).x + it.y }

    (top + left + right + bot).toList()
  }

  roomGrid.dimension.allPositions().mapNotNull { roomGrid[it] }.forEach { room ->
    walls.map { it + room.from }.forEach {
      if (level.entityAt(it) == null) {
        level.addEntity(Block(RPS.random()), it)
      }
    }
  }

  return Level(level, rooms)
}

interface RpsEntity : Entity {
  val type: RPS
}

class Block(override val type: RPS) : RpsEntity

enum class Direction {
  Up, Down, Left, Right;

  companion object {
    fun random(): Direction {
      return values().random()
    }
  }
}

fun Direction.asVector(): Position {
  return when (this) {
    Direction.Up -> (-1).y
    Direction.Down -> 1.y
    Direction.Left -> (-1).x
    Direction.Right -> 1.x
  }
}

interface Controllable : Entity {
  var direction: Direction
}

interface EnemyEntity : Controllable

class RpsEnemyEntity(override val type: RPS, override var direction: Direction) : RpsEntity, EnemyEntity

interface PlayerEntity : Controllable

class RpsPlayerEntity(override var type: RPS, override var direction: Direction) : RpsEntity, PlayerEntity

fun inhabit(level: Level): Arena {
  val arenaMap = ArenaMap(level.levelMap)

  for (room in level.rooms.allRooms()) {
//    val maxFactor = maxOf(room.size.width, room.size.height)

    val numberOfInhabitants = 0 // Random.nextInt(maxFactor / 2, maxFactor)
    val tiles = room.size.xRange.flatMap { x -> room.size.yRange.map { x.x + it.y + room.from } }
    val freeTiles = tiles.filter { arenaMap.entityAt(it) == null }.toMutableSet()

    repeat(numberOfInhabitants) {
      val injectionPosition = freeTiles.random()
      arenaMap.addEntity(RpsEnemyEntity(RPS.random(), Direction.random()), injectionPosition)
      freeTiles -= injectionPosition
    }
  }

  return Arena(arenaMap, level.rooms)
}

fun PlayField.injectPlayer(playerEntity: PlayerEntity) {
  val roomSize = currentRoom.size
  roomSize.allPositions()
    .sortedBy { hypot(abs(it.x - roomSize.width / 2).toDouble(), abs(it.y - roomSize.height / 2).toDouble()) }
    .map { currentRoom.from + it }
    .first { arena.arenaMap.entityAt(it) == null } // fixme no such?
    .let { arena.arenaMap.addEntity(playerEntity, it) }
}

class PlayField(val arena: Arena) {
  var currentRoom = arena.rooms.initialRoom
    set(value) {
      require(value in arena.rooms.allRooms())
      field = value
    }
}

class Game(world: World, playField: PlayField) {
  var world = world
    private set

  var playField = playField
    private set

  val log: Queue<String> = LinkedList()

  init {
    require(playField.arena in world.arenas)
  }

  fun changePlayField(playField: PlayField) {
    require(playField.arena in world.arenas)
    this.playField = playField
  }

  fun reset(world: World, playField: PlayField) {
    require(playField.arena in world.arenas)
    val players = this.playField.arena.arenaMap.entities().map { it.entity }.filterIsInstance<PlayerEntity>()
    this.world = world
    this.playField = playField
    players.forEach { playField.injectPlayer(it) }
  }
}

fun randomGame(roomSize: Dimension): Game {
  val terrain = randomTerrain(roomSize)
  val arenas = terrain.levels.map { inhabit(it) }
  return Game(World(arenas), PlayField(arenas.first()))
}

fun Game.reset() {
  val tmpGame = randomGame(playField.currentRoom.size)
  reset(tmpGame.world, tmpGame.playField)
}

// ------------------------------------------------------------------------------------------------

interface Actor {
  fun makeTurn(game: Game)
}

sealed class PlayerAction {
  class Go(val direction: Direction) : PlayerAction()
  object Hit : PlayerAction()
}

class Physics : Actor {
  override fun makeTurn(game: Game) {
    // todo
  }
}

class Player(private val actionSource: Queue<PlayerAction>, private val playerEntity: PlayerEntity) : Actor {
  override fun makeTurn(game: Game) {
    val playField = game.playField
    val map = playField.arena.arenaMap
    val room = playField.currentRoom

    val position = map.entities()
      .filter { it.position in room }
      .find { it.entity == playerEntity }
      ?.position
      ?: return // not in the current room

    val action = actionSource.poll()

    val oldDirection = playerEntity.direction

    playerEntity.direction = when (action) {
      is PlayerAction.Go -> action.direction
      else -> oldDirection
    }

    when (action) {
      is PlayerAction.Go -> {
        if (/*oldDirection == playerEntity.direction*/true) {
          val newPos = position + playerEntity.direction.asVector()
          if (newPos within map.dimension && map.entityAt(newPos) == null) {
            if (newPos !in room) {
              val newRoom = playField.arena.rooms.roomGrid[
                playField.arena.rooms.roomGrid.dimension.allPositions().first {
                  playField.arena.rooms.roomGrid[it] == room
                } + playerEntity.direction.asVector()
              ]
              if (newRoom != null) {
                map.moveEntity(playerEntity, newPos)
                game.playField.currentRoom = newRoom
              }
            } else {
              map.moveEntity(playerEntity, newPos)
            }
          }
        }
      }

      is PlayerAction.Hit -> {
        if (playerEntity is RpsPlayerEntity) {
          val targetPos = position + playerEntity.direction.asVector()
          if (targetPos within map.dimension) {
            val targetEntity = map.entityAt(targetPos)
            if (targetEntity is RpsEntity) {
              if (targetEntity.type < playerEntity.type) {
                map.removeEntity(targetEntity)
                playerEntity.type = targetEntity.type
                val destroyedName = when (targetEntity.type) {
                  RPS.Paper -> "Paper"
                  RPS.Rock -> "Rock"
                  RPS.Scissors -> "Scissors"
                }
                game.log.add("$destroyedName destroyed")
              } else if (targetEntity.type > playerEntity.type) {
                game.reset()
                game.log.add("WASTED")
              }
            }
          }
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------

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

fun RPS.symbol(): Char {
  return when (this) {
    RPS.Rock -> '@'
    RPS.Paper -> '#'
    RPS.Scissors -> '%'
  }
}

fun PlayField.draw(textImage: TextImage, playFieldPos: Position) {
  arena.arenaMap.dimension.allPositions().filter { it in currentRoom }.forEach {
    val (x, y) = it - currentRoom.from + playFieldPos
    textImage.setCharacterAt(x, y, textCharacter(' ', background = TextColor.ANSI.WHITE_BRIGHT))
  }
  for ((entityPos, entity) in arena.arenaMap.entities().filter { it.position in currentRoom }) {
    val (x, y) = entityPos - currentRoom.from + playFieldPos

    val color = when (entity) {
      is PlayerEntity -> TextColor.ANSI.GREEN
      is EnemyEntity -> TextColor.ANSI.RED
      else -> TextColor.ANSI.BLACK
    }

    val symbol = when (entity) {
      is RpsEntity -> entity.type.symbol()
      else -> error("Don't know how to render entity $entity")
    }

    textImage.setCharacterAt(x, y, textCharacter(symbol, foreground = color, background = TextColor.ANSI.WHITE_BRIGHT))
  }
}

fun Game.draw(textImage: TextImage) {
  fun drawText(position: Position, text: String) {
    for ((i, char) in text.withIndex()) {
      val (x, y) = position + i.x
      textImage.setCharacterAt(
        x,
        y,
        textCharacter(char, TextColor.ANSI.WHITE_BRIGHT, background = TextColor.ANSI.BLACK_BRIGHT)
      )
    }
  }

  val imageSize = textImage.size.columns by textImage.size.rows
  val playFieldSize = playField.currentRoom.size

  require(imageSize.width >= playFieldSize.width)
  require(imageSize.height >= playFieldSize.height)

  val playFieldPos = run {
    val x = (imageSize.width - playFieldSize.width) / 2
    val y = (imageSize.height - playFieldSize.height) / 2
    x.x + y.y
  }
  playField.draw(textImage, playFieldPos)

  // draw log
  // todo: wrap text in log, measure it size, mb crop or restrict log message size
  run {
    while (log.size > 5) { // fixme: magic constant
      log.poll()
    }
    for ((i, msg) in log.withIndex()) {
      val msgStartPos = 1.x + (i * 2).y
      drawText(msgStartPos, msg)
    }
  }

  // draw minimap & direction
  run {
    val roomGrid = playField.arena.rooms.roomGrid
    val minimapPos = 1.x + (imageSize.height - roomGrid.dimension.height - 1).y
    val directionSymbol = when (
      playField.arena.arenaMap.entities()
        .map { it.entity }
        .filterIsInstance<PlayerEntity>()
        .first()
        .direction
    ) { // fixme
      Direction.Up -> '∧'
      Direction.Down -> '∨'
      Direction.Left -> '<'
      Direction.Right -> '>'
    }
    drawText(minimapPos - 2.y, "Minimap")
    for (roomPosInGrid in roomGrid.dimension.allPositions()) {
      val room = roomGrid[roomPosInGrid] ?: continue
      val containsPlayer = playField.arena.arenaMap.entities().filter { it.position in room }.any { it.entity is PlayerEntity }
      val color = if (containsPlayer) TextColor.ANSI.GREEN else TextColor.ANSI.WHITE
      val symbol = if (containsPlayer) directionSymbol else ' '
      val tilePos = minimapPos + roomPosInGrid
      textImage.setCharacterAt(
        tilePos.x,
        tilePos.y,
        textCharacter(symbol, foreground = TextColor.ANSI.WHITE_BRIGHT, background = color)
      )
    }
  }
}

// ------------------------------------------------------------------------------------------------

// fun loop(screen: TerminalScreen, game: Game) = runBlocking {
//  while (true) {
//    val key = screen.pollInput()?.toKey()
//    when (val action = key?.let { keyMap[it] }) {
//      GameAction.Exit -> break
//      null -> game.tick()
//      is GameAction.PlayerGameAction -> game.playerDo(action.playerAction)
//    }
//    game.draw(screen)
//  }
// }

fun main() {
  DefaultTerminalFactory().createScreen().use { screen ->
    val roomSize = run {
      val width = screen.terminalSize.columns / 2
      val height = screen.terminalSize.rows / 2
      width by height
    }
    val game = randomGame(roomSize)
    game.log.add("Game started")

    val playerEntity = RpsPlayerEntity(RPS.Rock, Direction.random())
    game.playField.injectPlayer(playerEntity)

    val playerActionQueue = LinkedList<PlayerAction>()
    val actors = listOf(Physics(), Player(playerActionQueue, playerEntity))

    screen.startScreen()
    screen.terminal.addResizeListener { _, _ ->
      screen.clear()
    }

    while (true) {
      val input = screen.pollInput()
      when {
        input?.keyType == KeyType.ArrowUp -> playerActionQueue.add(PlayerAction.Go(Direction.Up))
        input?.keyType == KeyType.ArrowDown -> playerActionQueue.add(PlayerAction.Go(Direction.Down))
        input?.keyType == KeyType.ArrowLeft -> playerActionQueue.add(PlayerAction.Go(Direction.Left))
        input?.keyType == KeyType.ArrowRight -> playerActionQueue.add(PlayerAction.Go(Direction.Right))
        input?.keyType == KeyType.Escape -> break
        input?.character == 'z' -> playerActionQueue.add(PlayerAction.Hit)
      }

      for (actor in actors) {
        actor.makeTurn(game)
      }

      val textImage = BasicTextImage(
        TerminalSize(screen.terminalSize.columns, screen.terminalSize.rows),
        textCharacter(' ', background = TextColor.ANSI.BLACK_BRIGHT)
      )
      game.draw(textImage)
      screen.newTextGraphics().drawImage(TerminalPosition.TOP_LEFT_CORNER, textImage)
      screen.refresh()
      Thread.sleep(1000L / 60)
    }
  }
}
