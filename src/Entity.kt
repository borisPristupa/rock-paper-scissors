package com.boris.rps

interface Entity

interface RpsEntity : Entity {
  val type: RPS
}

class Block(override val type: RPS) : RpsEntity

interface Controllable : Entity {
  var direction: Direction
}

interface EnemyEntity : Controllable

class RpsEnemyEntity(override val type: RPS, override var direction: Direction) : RpsEntity, EnemyEntity

interface PlayerEntity : Controllable

class RpsPlayerEntity(override var type: RPS, override var direction: Direction) : RpsEntity, PlayerEntity
