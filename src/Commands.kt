package com.boris.rps

import com.googlecode.lanterna.screen.TerminalScreen
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Returns `true` iff execution is successful
 */
fun executeCommand(command: String, screen: TerminalScreen, game: Game): Boolean {
  val parts = command.split(' ', '\t')
    .map { it.trim() }
    .filter { it.isNotEmpty() }

  if (parts.isEmpty()) {
    return true // an empty command is like pressing Escape
  }

  val commandName = parts[0]
  val args = parts.drop(1)

  return Command.map[commandName]
    ?.execute(args, screen, game)
    ?: run {
      game.log += "Unknown command '$commandName'"
      false
    }
}

private sealed interface Command {
  companion object {
    val map = mutableMapOf(
      "save" to SaveTerrainCommand,
      "load" to LoadTerrainCommand,
    )
  }

  fun execute(args: List<String>, screen: TerminalScreen, game: Game): Boolean
}

private val TerrainJson = Json {
  serializersModule = SerializersModule {
    contextual(
      IntRange::class,
      object : KSerializer<IntRange> {
        override val descriptor: SerialDescriptor
          get() = PrimitiveSerialDescriptor("IntRange", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): IntRange {
          val (first, last) = decoder.decodeString().split(';').map { it.toInt() }
          return first..last
        }

        override fun serialize(encoder: Encoder, value: IntRange) {
          encoder.encodeString("${value.first};${value.last}")
        }
      }
    )
  }
}

private object SaveTerrainCommand : Command {
  override fun execute(args: List<String>, screen: TerminalScreen, game: Game): Boolean {
    if (args.size != 1) {
      game.log += "'save' command expects a path argument"
      return false
    }

    val filePath = args[0]
    val terrain = Terrain(
      game.world.arenas
        .map { Level(it.arenaMap.levelMap, it.rooms) }
    )
    return try {
      Path.of(filePath).toFile()
        .writeText(TerrainJson.encodeToString(terrain))
      game.log += "Terrain saved"
      true
    } catch (e: InvalidPathException) {
      game.log += "Failed to save: path '$filePath' is invalid"
      false
    } catch (e: IOException) {
      val msg = e.message ?: "IOException"
      game.log += "Failed to save: $msg"
      false
    } catch (e: Exception) {
      val msg = e.message ?: e.toString()
      game.log += "Failed to save: $msg"
      false
    }
  }
}

private object LoadTerrainCommand : Command {
  override fun execute(args: List<String>, screen: TerminalScreen, game: Game): Boolean {
    if (args.size != 1) {
      game.log += "'load' command expects a path argument"
      return false
    }

    val filePath = args[0]
    return try {
      val terrainText = Path.of(filePath).toFile().readText()
      val terrain = TerrainJson.decodeFromString<Terrain>(terrainText)
      game.reset(terrain)
      game.log += "Terrain loaded, game restarted"
      true
    } catch (e: InvalidPathException) {
      game.log += "Failed to load: path '$filePath' is invalid"
      false
    } catch (e: IOException) {
      val msg = e.message ?: "IOException"
      game.log += "Failed to load: $msg"
      false
    } catch (e: SerializationException) {
      game.log += "Invalid save file format (at '$filePath')"
      false
    } catch (e: Exception) {
      val msg = e.message ?: e.toString()
      game.log += "Failed to load: $msg"
      false
    }
  }
}
