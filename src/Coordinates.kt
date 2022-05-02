package com.boris.rps

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
