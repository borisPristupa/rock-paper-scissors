package com.boris.rps

import kotlin.random.Random

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
