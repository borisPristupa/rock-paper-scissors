package com.boris.rps

import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.BasicTextImage
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import java.util.LinkedList
import java.util.Queue

fun loop(screen: TerminalScreen, game: Game, actors: List<Actor>, playerActionQueue: Queue<PlayerAction>) {
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

    loop(screen, game, actors, playerActionQueue)
  }
}
