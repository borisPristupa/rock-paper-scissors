package com.boris.rps

import com.googlecode.lanterna.TextCharacter
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.TextImage

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
    for ((i, msg) in log.reversed().withIndex()) {
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
