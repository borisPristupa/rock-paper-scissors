package com.boris.rps

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
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

@Serializable
data class Dimension(val width: Int, val height: Int) {
  init {
    require(width >= 0)
    require(height >= 0)
  }

  @Contextual
  val xRange: IntRange = 0 until width
  @Contextual
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Grid<*>) return false

    if (dimension != other.dimension) return false
    if (default != other.default) return false
    if (grid != other.grid) return false

    return true
  }

  override fun hashCode(): Int {
    var result = dimension.hashCode()
    result = 31 * result + (default?.hashCode() ?: 0)
    result = 31 * result + grid.hashCode()
    return result
  }
}

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
