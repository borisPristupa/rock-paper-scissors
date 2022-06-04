package com.boris.rps

import kotlinx.serialization.Serializable

@Serializable
sealed interface Entity

@Serializable
sealed interface RpsEntity : Entity {
  val kind: RPS
}

@Serializable
class Block(override val kind: RPS) : RpsEntity {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Block) return false

    if (kind != other.kind) return false

    return true
  }

  override fun hashCode(): Int {
    return kind.hashCode()
  }
}

@Serializable
sealed interface Controllable : Entity {
  var direction: Direction
}

interface EnemyEntity : Controllable

class RpsEnemyEntity(override val kind: RPS, override var direction: Direction) : RpsEntity, EnemyEntity

interface PlayerEntity : Controllable

class RpsPlayerEntity(override var kind: RPS, override var direction: Direction) : RpsEntity, PlayerEntity
