package ru.gamekritik.app
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class CatalogTest {
    private val games = listOf(Game(1,"The Witcher 3","","2015-05-19","","",9.8), Game(2,"Future Game","","2027-01-01","","",null), Game(3,"Elden Ring","","2022-02-25","","",9.6))
    @Test fun searchTrimsAndIgnoresCase() { assertEquals(listOf(1), filterGames(games," WITCHER ",0).map { it.id }) }
    @Test fun newGamesExcludeFutureReleases() { assertTrue(filterGames(games,"",1,LocalDate.of(2026,10,7)).isEmpty()) }
    @Test fun upcomingGamesExcludeReleased() { assertEquals(listOf(2),filterGames(games,"",2,LocalDate.of(2026,10,7)).map { it.id }) }
    @Test fun unratedGamesSortLast() { assertEquals(listOf(1,3,2),filterGames(games,"",0).map { it.id }) }
    @Test fun newCategoryUsesOriginalYearAndExcludesUnknownUpcoming() {
        val sample = listOf(
            Game(1,"New","","2025-04-24","","",null),
            Game(2,"Port","","2025-01-30","","",null,firstReleaseYear=2023),
            Game(3,"Unannounced date","","","","",null,releaseStatus="upcoming"),
            Game(4,"2026","","2026-05-26","","",null))
        assertEquals(listOf(4,1),filterGames(sample,"",1,LocalDate.of(2026,10,20)).map { it.id })
        assertEquals(listOf(3),filterGames(sample,"",2,LocalDate.of(2026,10,20)).map { it.id })
    }
    @Test fun genreAndSearchApplyTogether() {
        val sample = listOf(games[0].copy(genre="RPG · Экшен"),games[2].copy(genre="Экшен"))
        assertEquals(listOf(1),filterGames(sample,"witcher",0,genre="RPG").map { it.id })
        assertTrue(filterGames(sample,"elden",0,genre="RPG").isEmpty())
    }
    @Test fun knownUpcomingDatesSortBeforeUnknownDates() {
        val sample = listOf(games[1].copy(id=4,release=""),games[1]).map { it.copy(releaseStatus="upcoming") }
        assertEquals(listOf(2,4),filterGames(sample,"",2).map { it.id })
    }
}
