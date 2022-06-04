package com.boris.rps

import java.util.LinkedList
import java.util.Queue
import kotlin.math.abs
import kotlin.math.hypot

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

  val log: Queue<CharSequence> = LinkedList()

  init {
    require(playField.arena in world.arenas)
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
