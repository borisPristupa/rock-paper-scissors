package com.boris.rps

import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.BasicTextImage
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import java.util.LinkedList
import java.util.Queue

fun loop(screen: TerminalScreen, game: Game, actors: List<Actor>, playerActionQueue: Queue<PlayerAction>) {
  var pause = false
  val command = StringBuilder()

  while (true) {
    val input: KeyStroke? = screen.pollInput()

    if (!pause) {
      when {
        input?.keyType == KeyType.ArrowUp -> playerActionQueue.add(PlayerAction.Go(Direction.Up))
        input?.keyType == KeyType.ArrowDown -> playerActionQueue.add(PlayerAction.Go(Direction.Down))
        input?.keyType == KeyType.ArrowLeft -> playerActionQueue.add(PlayerAction.Go(Direction.Left))
        input?.keyType == KeyType.ArrowRight -> playerActionQueue.add(PlayerAction.Go(Direction.Right))
        input?.character == 'z' -> playerActionQueue.add(PlayerAction.Hit)
        input?.keyType == KeyType.Escape -> break
        input?.character == '/' -> {
          command.clear()
          pause = true
          game.log.add("Game paused")
        }
      }
    } else {
      when {
        input?.keyType == KeyType.Escape -> {
          pause = false
        }
        input?.keyType == KeyType.Backspace -> {
          if (command.isEmpty()) {
            pause = false
          } else {
            command.deleteCharAt(command.lastIndex)
          }
        }
        input?.keyType == KeyType.Enter -> {
          if (executeCommand(command.toString(), screen, game)) {
            pause = false
          }
        }
        input?.character != null -> {
          command.append(input.character)
        }
      }
      if (!pause) {
        game.log.add("Game continues")
      }
    }

    if (!pause) {
      for (actor in actors) {
        actor.makeTurn(game)
      }
    }

    val textImage = BasicTextImage(
      TerminalSize(screen.terminalSize.columns, screen.terminalSize.rows),
      textCharacter(' ', background = TextColor.ANSI.BLACK_BRIGHT)
    )
    game.draw(textImage)
    if (pause) {
      val commandBackground = TextColor.ANSI.BLACK_BRIGHT
      val commandRow = screen.terminalSize.rows - 1
      for (column in 0 until screen.terminalSize.columns) {
        textImage.setCharacterAt(column, commandRow, textCharacter(' ', background = commandBackground))
      }
      textImage.drawText(0.x + commandRow.y, "/$command")
    }

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
