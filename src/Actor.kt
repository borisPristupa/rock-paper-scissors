package com.boris.rps

import java.util.Queue

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
              if (targetEntity.kind < playerEntity.kind) {
                map.removeEntity(targetEntity)
                playerEntity.kind = targetEntity.kind
                val destroyedName = when (targetEntity.kind) {
                  RPS.Paper -> "Paper"
                  RPS.Rock -> "Rock"
                  RPS.Scissors -> "Scissors"
                }
                game.log.add("$destroyedName destroyed")
              } else if (targetEntity.kind > playerEntity.kind) {
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
