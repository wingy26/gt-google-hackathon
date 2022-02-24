package hello

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono

@SpringBootApplication
class KotlinApplication {

    private class Directions(
        val direction: String,
        val beingHitDirection: String,
        val left: String,
        val right: String,
        val findingAlgorithm: (myState: PlayerState, distance: Int) -> (itState: PlayerState) -> Boolean
    )

    companion object {
        private const val HIT = "H"
        private const val BLOCK = "B"
        private const val EMPTY = "E"

        private const val NORTH = "N"
        private const val EAST = "E"
        private const val SOUTH = "S"
        private const val WEST = "W"

//        private val directions = listOf(
//            NORTH to SOUTH,
//            EAST to WEST,
//            SOUTH to NORTH,
//            WEST to EAST
//        )

        private val directions = mapOf(
            NORTH to Directions(
                NORTH,
                SOUTH,
                WEST,
                EAST
            ) { myState: PlayerState, distance: Int -> { it.x == myState.x && it.y == myState.y - distance } },
            EAST to Directions(
                EAST,
                WEST,
                NORTH,
                SOUTH
            ) { myState: PlayerState, distance: Int -> { it.y == myState.y && it.x == myState.x + distance } },
            SOUTH to Directions(
                SOUTH,
                NORTH,
                EAST,
                WEST
            ) { myState: PlayerState, distance: Int -> { it.x == myState.x && it.y == myState.y + distance } },
            WEST to Directions(
                WEST,
                EAST,
                SOUTH,
                NORTH
            ) { myState: PlayerState, distance: Int -> { it.y == myState.y && it.x == myState.x - distance } },
        )
    }

    @Bean
    fun routes() = router {
        GET {
            ServerResponse.ok().body(Mono.just("Let the battle begin!"))
        }

        POST("/**", accept(APPLICATION_JSON)) { request ->
            request.bodyToMono(ArenaUpdate::class.java).flatMap { arenaUpdate ->
                val myState = arenaUpdate.arena.state[arenaUpdate._links.self.href]!!
                val currentHitState = findHitCountAtState(myState, arenaUpdate.arena)
                val willBeHitCount = currentHitState.values.count { it.first == HIT }

                val response = when (willBeHitCount) {
                    0, 1 -> {
                        when {
                            currentHitState[myState.direction]?.first == BLOCK -> "T"
                            currentHitState[directions[myState.direction]!!.left]?.first == BLOCK -> "L"
                            currentHitState[directions[myState.direction]!!.right]?.first == BLOCK -> "R"
                            // Check boundary
                            myState.direction == NORTH && myState.y == 0 -> if (myState.x != 0) "L" else "R"
                            myState.direction == SOUTH && myState.y == arenaUpdate.arena.dims[1] -> if (myState.x != 0) "R" else "L"
                            myState.direction == WEST && myState.x == 0 -> if (myState.y == 0) "L" else "R"
                            myState.direction == EAST && myState.x == arenaUpdate.arena.dims[0] -> if (myState.y == 0) "R" else "L"

                            else -> "F"
                        }
                    }
                    else -> {
                        val forwardState = currentHitState[myState.direction]!!
                        if ((forwardState.first == HIT || forwardState.first == BLOCK) && forwardState.second == 1) {
                            // Blocked, turn left or right
                            when(myState.direction) {
                                NORTH-> if (myState.x != 0) "L" else "R"
                                SOUTH -> if (myState.x != 0) "R" else "L"
                                WEST -> if (myState.y == 0) "L" else "R"
                                EAST -> if (myState.y == 0) "R" else "L"
                                else -> "F"
                            }
                        } else {
                            "F"
                        }
                    }
                }

                ServerResponse.ok().body(Mono.just(response))
            }
        }
    }

    private fun findHitCountAtState(myState: PlayerState, arena: Arena): Map<String, Pair<String, Int>> {
        val targetedPlayers = arena.state.values
            .filter {
                (it.x == myState.x && it.y in (myState.y - 3)..(myState.y + 3))
                        || (it.y == myState.y && it.x in (myState.x - 3)..(myState.x + 3))
            }

        val currentHitState = mutableMapOf<String, Pair<String, Int>>()
        (1..3).forEach { distance ->
            directions.values.forEach { direction ->
                val hit = targetedPlayers.find(direction.findingAlgorithm(myState, distance))?.direction

                if (currentHitState[direction.direction]?.first != HIT && currentHitState[direction.direction]?.first != BLOCK) {
                    when (hit) {
                        direction.beingHitDirection -> currentHitState[direction.direction] = HIT to distance
                        null -> currentHitState[direction.direction] = EMPTY to distance
                        else -> currentHitState[direction.direction] = BLOCK to distance
                    }
                }
            }
        }
        return currentHitState
    }
}

fun main(args: Array<String>) {
    runApplication<KotlinApplication>(*args)
}

data class ArenaUpdate(val _links: Links, val arena: Arena)
data class PlayerState(val x: Int, val y: Int, val direction: String, val score: Int, val wasHit: Boolean)
data class Links(val self: Self)
data class Self(val href: String)
data class Arena(val dims: List<Int>, val state: Map<String, PlayerState>)
