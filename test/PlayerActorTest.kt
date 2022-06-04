import com.boris.rps.Block
import com.boris.rps.Dimension
import com.boris.rps.Direction
import com.boris.rps.Game
import com.boris.rps.Grid
import com.boris.rps.Level
import com.boris.rps.LevelMap
import com.boris.rps.PlayField
import com.boris.rps.Player
import com.boris.rps.PlayerAction
import com.boris.rps.PlayerEntity
import com.boris.rps.Position
import com.boris.rps.RPS
import com.boris.rps.Room
import com.boris.rps.Rooms
import com.boris.rps.RpsPlayerEntity
import com.boris.rps.World
import com.boris.rps.allPositions
import com.boris.rps.by
import com.boris.rps.inhabit
import com.boris.rps.injectPlayer
import com.boris.rps.putWallsOn
import com.boris.rps.x
import com.boris.rps.y
import java.util.LinkedList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerActorTest {
  @Test
  fun test_move_within_room() {
    val (game, player) = planGame(
      7 by 7,
      PlayerAction.Go(Direction.Left),
      PlayerAction.Go(Direction.Right),
      PlayerAction.Go(Direction.Up),
      PlayerAction.Go(Direction.Up),
      PlayerAction.Go(Direction.Up),
    )

    val initialPos: Position = notNull(game.playerPosition())
    assertEquals(game.world.arenas.first(), game.playField.arena)
    assertEquals(game.world.arenas.first().rooms.initialRoom, game.playField.currentRoom)

    player.makeTurn(game)
    assertEquals(initialPos - 1.x, game.playerPosition())
    assertEquals(Direction.Left, game.playerEntity()?.direction)

    player.makeTurn(game)
    assertEquals(initialPos, game.playerPosition())
    assertEquals(Direction.Right, game.playerEntity()?.direction)

    player.makeTurn(game)
    assertEquals(initialPos - 1.y, game.playerPosition())
    assertEquals(Direction.Up, game.playerEntity()?.direction)

    player.makeTurn(game)
    assertEquals(initialPos - 2.y, game.playerPosition())
    assertEquals(Direction.Up, game.playerEntity()?.direction)

    player.makeTurn(game)
    assertEquals(initialPos - 2.y, game.playerPosition()) // wall
    assertEquals(Direction.Up, game.playerEntity()?.direction)
  }

  @Test
  fun test_enter_room() {
    val (game, player) = planGame(
      5 by 5,
      PlayerAction.Go(Direction.Left),
      PlayerAction.Go(Direction.Left),
      PlayerAction.Go(Direction.Left),
    )

    val initialPlayerPos: Position = notNull(game.playerPosition())
    val arenaMap = game.playField.arena.arenaMap
    val brick = notNull(arenaMap.entityAt(initialPlayerPos - 2.x))
    arenaMap.removeEntity(brick)

    val initialRoomPos = notNull(game.roomPosition())
    assertEquals(
      game.world.arenas[0].rooms.initialRoom,
      game.playField.arena.rooms.roomGrid[initialRoomPos]
    )

    player.makeTurn(game)
    assertEquals(initialRoomPos, game.roomPosition())

    player.makeTurn(game)
    assertEquals(initialRoomPos, game.roomPosition())

    player.makeTurn(game)
    assertEquals(initialRoomPos - 1.x, game.roomPosition())
  }

  @Test
  fun test_hit_wall() {
    val (game, player) = planGame(
      5 by 5,
      PlayerAction.Go(Direction.Left),
      PlayerAction.Go(Direction.Left),
      PlayerAction.Hit,
    )

    val initialPlayerPos: Position = notNull(game.playerPosition())
    val arenaMap = game.playField.arena.arenaMap
    val brick = notNull(arenaMap.entityAt(initialPlayerPos - 2.x) as? Block)
    val playerEntity = notNull(game.playerEntity() as? RpsPlayerEntity)
    playerEntity.kind = brick.kind.stronger()

    player.makeTurn(game)
    player.makeTurn(game)
    player.makeTurn(game)
    assertNull(arenaMap.entityAt(initialPlayerPos - 2.x))
    assertEquals(brick.kind, playerEntity.kind)
  }

  private fun <T> notNull(value: T?): T {
    assertNotNull(value)
    return value!!
  }

  private fun Game.playerPosition(): Position? {
    return playField.arena.arenaMap.entities()
      .find { it.entity is PlayerEntity }
      ?.position
  }

  private fun Game.playerEntity(): PlayerEntity? {
    return playField.arena.arenaMap.entities()
      .find { it.entity is PlayerEntity }
      ?.entity as? PlayerEntity
  }

  private fun Game.roomPosition(): Position? {
    val grid = playField.arena.rooms.roomGrid
    return grid.dimension.allPositions()
      .find { grid[it] == playField.currentRoom }
  }

  private fun planGame(roomSize: Dimension, vararg actions: PlayerAction): Pair<Game, Player> {
    val roomsGridSize = 3 by 3
    val roomGrid = Grid<Room?>(roomsGridSize, null)
    for (y in roomsGridSize.yRange) {
      for (x in roomsGridSize.xRange) {
        val fromPos = (x * (roomSize.width - 1)).x + (y * (roomSize.height - 1)).y
        val toPos = fromPos + roomSize.width.x + roomSize.height.y
        roomGrid[x.x + y.y] = Room(fromPos, toPos)
      }
    }
    val levelSize = (roomsGridSize.width * (roomSize.width - 1) + 1) by (roomsGridSize.height * (roomSize.height - 1) + 1)
    val rooms = Rooms(1.x + 1.y, roomGrid)
    val level = Level(LevelMap(levelSize), rooms).also { putWallsOn(it) }
    val arena = inhabit(level)
    val game = Game(World(listOf(arena)), PlayField(arena))

    val playerEntity = RpsPlayerEntity(RPS.random(), Direction.random())
    game.playField.injectPlayer(playerEntity)
    val player = Player(LinkedList(actions.toList()), playerEntity)
    return game to player
  }
}
