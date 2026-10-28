package ru.gamekritik.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class Game(val id: Int, val title: String, val description: String, val release: String, val developer: String, val platform: String, val rating: Double?,
    val cover: String = "", val hero: String = "", val genre: String = "", val publisher: String = "", val releaseStatus: String = "released",
    val releaseLabel: String = "", val firstReleaseYear: Int? = null, val sourceUrl: String = "", val popularity: Int = 0, val criticScore: Int? = null) {
    fun upcoming(today: LocalDate = LocalDate.now()) = releaseStatus == "upcoming" || runCatching { LocalDate.parse(release).isAfter(today) }.getOrDefault(false)
    fun releaseText(): String = releaseLabel.ifBlank { runCatching { LocalDate.parse(release).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrDefault("Дата уточняется") }
}
data class Review(val id: Int, val name: String, val text: String, val rating: Int, val date: String)
data class MyReview(val id: Int, val gameId: Int, val text: String, val rating: Int, val date: String)
data class SteamReview(val id: String, val text: String, val recommended: Boolean, val funnyVotes: Int, val date: String, val url: String)
data class Article(val id: Int, val title: String, val content: String, val author: String, val url: String = "", val cover: String = "", val category: String = "", val source: String = "")
data class Media(val title: String, val url: String, val thumbnail: String = "", val sourceUrl: String = "")
data class Profile(val id: Int, val name: String, val email: String)
data class Session(val access: String, val refresh: String, val expires: Long)

fun filterGames(games: List<Game>, query: String, category: Int, today: LocalDate = LocalDate.now(), genre: String = "Все жанры"): List<Game> {
    val found = games.filter { it.title.contains(query.trim(), ignoreCase = true) && (genre == "Все жанры" || genre in it.genre.split(" · ")) }
    return when (category) {
        1 -> found.filter { !it.upcoming(today) && (it.firstReleaseYear ?: it.release.take(4).toIntOrNull()) in 2025..2026 }.sortedByDescending { it.release }
        2 -> found.filter { it.upcoming(today) }.sortedWith(compareBy<Game> { it.release.ifBlank { "9999" } }.thenBy { it.title })
        else -> found.sortedWith(compareByDescending<Game> { it.popularity }.thenByDescending { it.rating ?: it.criticScore?.div(10.0) ?: -1.0 }.thenBy { it.title })
    }
}

/** Public key identifies the app. Private actions always require a Supabase Auth JWT. */
class SupabaseRepository(private val store: SessionStore? = null) {
    var session: Session? = store?.load()
        private set

    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = false): String = withContext(Dispatchers.IO) {
        if (authenticated) ensureSession()
        val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            if (authenticated) connection.setRequestProperty("Authorization", "Bearer ${session!!.access}")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(response).let { it.optString("msg", it.optString("message", it.optString("error_description"))) } }.getOrDefault("")
                throw IllegalStateException(when {
                    detail.contains("Invalid login credentials", true) -> "Неверная почта или пароль."
                    detail.contains("Email not confirmed", true) -> "Подтвердите почту по ссылке в письме."
                    detail.contains("already registered", true) || detail.contains("already been registered", true) -> "Эта почта уже зарегистрирована. Нажмите «Войти»."
                    detail.contains("email rate limit", true) -> "Превышен лимит писем. Попробуйте позже."
                    detail.contains("Database error saving new user", true) -> "Не удалось создать профиль. Попробуйте ещё раз или используйте другой никнейм."
                    detail.contains("gk_", true) || detail.contains("schema cache", true) -> "Для этой функции нужно применить SQL-настройку из папки supabase проекта."
                    status == 429 -> "Слишком много попыток. Попробуйте позже."
                    status == 401 || status == 403 -> "Нет доступа. Войдите в аккаунт и повторите."
                    else -> detail.ifBlank { "Ошибка сервера ($status). Повторите позже." }
                })
            }
            response
        } finally { connection.disconnect() }
    }

    private suspend fun ensureSession() {
        val current = session ?: error("Сначала войдите в аккаунт.")
        if (current.expires > System.currentTimeMillis() / 1000 + 60) return
        val result = JSONObject(request("/auth/v1/token?grant_type=refresh_token", "POST", JSONObject().put("refresh_token", current.refresh)))
        acceptSession(result)
    }

    private fun acceptSession(json: JSONObject) {
        if (!json.has("access_token")) return
        session = Session(json.getString("access_token"), json.getString("refresh_token"), System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600))
        store?.save(session!!)
    }

    suspend fun login(email: String, password: String) {
        acceptSession(JSONObject(request("/auth/v1/token?grant_type=password", "POST", JSONObject().put("email", email.trim()).put("password", password))))
    }
    suspend fun register(name: String, email: String, password: String): Boolean {
        logout()
        val response = JSONObject(request("/auth/v1/signup", "POST", JSONObject().put("email", email.trim()).put("password", password).put("data", JSONObject().put("name", name.trim()))))
        acceptSession(response)
        return session != null
    }
    suspend fun recover(email: String) { request("/auth/v1/recover", "POST", JSONObject().put("email", email.trim())) }
    fun logout() { session = null; store?.clear() }

    private suspend fun rows(table: String, query: String): List<JSONObject> = JSONArray(request("/rest/v1/$table?$query")).objects()
    private suspend fun rpc(name: String, data: JSONObject = JSONObject(), private: Boolean = true): String = request("/rest/v1/rpc/$name", "POST", data, private)
    suspend fun games(): List<Game> = rows("games", "select=*&order=game_id&limit=1000").filterNot {
        it.string("genre").split(" · ").any { genre -> genre.equals("Инди", ignoreCase = true) }
    }.map {
        Game(it.getInt("game_id"), it.getString("title"), it.string("description"), it.string("release_date"), it.string("developer"), it.string("platform"), if (it.isNull("rating")) null else it.getDouble("rating"),
            it.string("cover_image"), it.string("hero_image"), it.string("genre"), it.string("publisher"), it.string("release_status").ifBlank { "released" },
            it.string("release_label"), if (it.isNull("first_release_year")) null else it.optInt("first_release_year"), it.string("source_url"), it.optInt("popularity",0), if (it.isNull("critic_score")) null else it.optInt("critic_score"))
    }
    suspend fun profile(): Profile {
        val p = JSONObject(rpc("gk_profile"))
        return Profile(p.getInt("user_id"), p.getString("name"), p.getString("email"))
    }
    suspend fun favorites(): Set<Int> = JSONArray(rpc("gk_favorites")).objects().map { it.getInt("game_id") }.toSet()
    suspend fun favorite(gameId: Int, add: Boolean) { rpc("gk_set_favorite", JSONObject().put("p_game_id", gameId).put("p_add", add)) }
    suspend fun history(): List<Int> = JSONArray(rpc("gk_history")).objects().map { it.getInt("game_id") }
    suspend fun recordView(gameId: Int) { rpc("gk_record_view", JSONObject().put("p_game_id", gameId)) }
    suspend fun reviews(gameId: Int): List<Review> = JSONArray(rpc("gk_reviews", JSONObject().put("p_game_id", gameId), false)).objects().map {
        Review(it.getInt("review_id"), it.string("name").ifBlank { "Игрок" }, it.string("text"), it.getInt("rating"), it.string("date").take(10))
    }
    suspend fun myReviews(): List<MyReview> = JSONArray(rpc("gk_my_reviews")).objects().map {
        MyReview(it.getInt("review_id"), it.getInt("game_id"), it.string("text"), it.getInt("rating"), it.string("date").take(10))
    }
    suspend fun review(gameId: Int, text: String, rating: Int) { rpc("gk_write_review", JSONObject().put("p_game_id", gameId).put("p_text", text.trim()).put("p_rating", rating)) }
    suspend fun steamReviews(game: Game): List<SteamReview> = withContext(Dispatchers.IO) {
        if (game.upcoming()) return@withContext emptyList()
        val appId = Regex("/app/(\\d+)").find(game.sourceUrl)?.groupValues?.get(1) ?: return@withContext emptyList()
        val url = URL("https://store.steampowered.com/appreviews/$appId?json=1&filter=all&language=russian&review_type=all&purchase_type=all&num_per_page=100")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "GameKritik/1.5 Android")
            if (connection.responseCode != 200) return@withContext emptyList()
            val result = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (result.optInt("success") != 1) return@withContext emptyList()
            val all = result.optJSONArray("reviews") ?: return@withContext emptyList()
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            (0 until all.length()).mapNotNull { index ->
                val item = all.optJSONObject(index) ?: return@mapNotNull null
                val text = item.optString("review").trim()
                if (text.length < 12) return@mapNotNull null
                val id = item.optString("recommendationid")
                val steamId = item.optJSONObject("author")?.optString("steamid").orEmpty()
                if (id.isEmpty() || !id.all(Char::isDigit) || steamId.isEmpty() || !steamId.all(Char::isDigit)) return@mapNotNull null
                SteamReview(
                    id = id,
                    text = text.take(600),
                    recommended = item.optBoolean("voted_up"),
                    funnyVotes = item.optInt("votes_funny"),
                    date = runCatching { Instant.ofEpochSecond(item.getLong("timestamp_created")).atZone(ZoneId.systemDefault()).format(formatter) }.getOrDefault(""),
                    url = "https://steamcommunity.com/profiles/$steamId/recommended/$appId/"
                )
            }.sortedByDescending { it.funnyVotes }.take(5)
        } finally { connection.disconnect() }
    }
    suspend fun guides(gameId: Int): List<Article> = rows("guides", "select=*&game_id=eq.$gameId&order=guide_id").map {
        Article(it.getInt("guide_id"), it.string("title"), it.string("content"), it.string("author").ifBlank { "Сообщество" }, it.string("source_url"), it.string("cover_image"), it.string("category"), it.string("source_name"))
    }
    suspend fun mods(gameId: Int): List<Article> = rows("mods", "select=mod_id,title,description,author,download_url&game_id=eq.$gameId").map {
        Article(it.getInt("mod_id"), it.string("title"), it.string("description"), it.string("author"), it.string("download_url"), category = "Моды", source = it.string("author"))
    }
    suspend fun screenshots(gameId: Int): List<Media> = rows("screenshots", "select=*&game_id=eq.$gameId&order=screenshot_id").map { Media(it.string("description"), it.string("image_url")) }
    suspend fun trailers(gameId: Int): List<Media> = rows("trailers", "select=*&game_id=eq.$gameId&order=trailer_id").map { Media(it.string("title"), it.string("video_url"), it.string("thumbnail_url"), it.string("source_url")) }
}
private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.string(key: String) = if (isNull(key)) "" else optString(key, "")
