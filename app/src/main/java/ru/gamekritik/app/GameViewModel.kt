package ru.gamekritik.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SupabaseRepository(SessionStore(application))
    var games by mutableStateOf<List<Game>>(emptyList()); private set
    var favorites by mutableStateOf<Set<Int>>(emptySet()); private set
    var history by mutableStateOf<List<Int>>(emptyList()); private set
    var myReviews by mutableStateOf<List<MyReview>>(emptyList()); private set
    var profile by mutableStateOf<Profile?>(null); private set
    var signedIn by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var authError by mutableStateOf<String?>(null); private set
    var reviews by mutableStateOf<List<Review>>(emptyList()); private set
    var steamReviews by mutableStateOf<List<SteamReview>>(emptyList()); private set
    var steamReviewsLoading by mutableStateOf(false); private set
    var guides by mutableStateOf<List<Article>>(emptyList()); private set
    var mods by mutableStateOf<List<Article>>(emptyList()); private set
    var screenshots by mutableStateOf<List<Media>>(emptyList()); private set
    var trailers by mutableStateOf<List<Media>>(emptyList()); private set
    var detailLoading by mutableStateOf(false); private set
    var detailError by mutableStateOf<String?>(null); private set
    private var detailJob: kotlinx.coroutines.Job? = null
    private val steamReviewCache = mutableMapOf<Int, List<SteamReview>>()
    init {
        refresh()
        if (repository.session != null) viewModelScope.launch {
            try { profile = repository.profile(); favorites = repository.favorites(); signedIn = true; history = runCatching { repository.history() }.getOrDefault(emptyList()); myReviews = runCatching { repository.myReviews() }.getOrDefault(emptyList()) }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }
    fun clearMessage() { error = null; message = null }
    private fun failure(e: Exception) { error = if (e is java.io.IOException) "Нет соединения с сервером. Проверьте интернет и повторите." else e.message ?: "Не удалось выполнить запрос." }
    fun refresh() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            try { games = repository.games() } catch (e: Exception) { if (e is CancellationException) throw e; failure(e) }
            finally { loading = false }
        }
    }
    private fun action(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true; error = null
            try { block() } catch (e: Exception) { if (e is CancellationException) throw e; failure(e) }
            finally { busy = false }
        }
    }
    fun authenticate(register: Boolean, name: String, email: String, password: String, onSuccess: () -> Unit) = action {
        authError = null
        try {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Введите корректный email." }
        require(password.length >= 6) { "Пароль должен содержать минимум 6 символов." }
        if (register) require(name.trim().length in 2..40) { "Никнейм должен содержать от 2 до 40 символов." }
        val loggedIn = if (register) repository.register(name, email, password) else { repository.login(email, password); true }
        if (!loggedIn) { message = "Проверьте почту: подтвердите регистрацию, затем войдите."; return@action }
        profile = repository.profile()
        favorites = repository.favorites()
        signedIn = true
        history = runCatching { repository.history() }.getOrDefault(emptyList())
        myReviews = runCatching { repository.myReviews() }.getOrDefault(emptyList())
        message = if (register) "Аккаунт создан. Добро пожаловать!" else "Вы вошли в аккаунт."
        onSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            authError = if (e is java.io.IOException) "Нет соединения. Проверьте интернет и повторите." else e.message ?: "Не удалось войти."
        }
    }
    fun clearAuthError() { authError = null }
    fun reloadProfile() = action { profile = repository.profile(); favorites = repository.favorites(); history = runCatching { repository.history() }.getOrDefault(emptyList()); myReviews = repository.myReviews() }
    fun recover(email: String) = action {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Сначала введите email." }
        repository.recover(email); message = "Если аккаунт существует, письмо для восстановления отправлено."
    }
    fun logout() { repository.logout(); signedIn = false; profile = null; favorites = emptySet(); history = emptyList(); myReviews = emptyList() }
    fun recordView(id: Int) {
        if (!signedIn) return
        history = (listOf(id) + history.filterNot { it == id }).take(30)
        viewModelScope.launch {
            try { repository.recordView(id); history = repository.history() }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }
    fun toggleFavorite(id: Int) = action {
        repository.favorite(id, id !in favorites)
        favorites = repository.favorites()
    }
    fun loadSection(id: Int, section: String) {
        detailJob?.cancel()
        reviews = emptyList(); steamReviews = emptyList(); guides = emptyList(); mods = emptyList(); screenshots = emptyList(); trailers = emptyList()
        detailJob = viewModelScope.launch {
            detailLoading = true; detailError = null
            try {
                when (section) {
                    "Отзывы" -> {
                        reviews = repository.reviews(id)
                        val game = games.find { it.id == id }
                        if (game != null && !game.upcoming()) {
                            steamReviewsLoading = true
                            steamReviews = steamReviewCache[id] ?: runCatching { repository.steamReviews(game) }.getOrDefault(emptyList()).also { steamReviewCache[id] = it }
                            steamReviewsLoading = false
                        }
                    }
                    "Гайды" -> guides = repository.guides(id)
                    "Моды" -> {
                        val cloud = repository.mods(id)
                        val game = games.find { it.id == id }
                        mods = if (cloud.isNotEmpty() || game == null) cloud else listOf(Article(
                            id = -id,
                            title = if (game.upcoming()) "Моды после релиза" else "Моды и инструкции сообщества",
                            content = if (game.upcoming()) "Игра ещё не вышла. Проверенных модификаций пока нет. Следите за новостями на официальной странице." else "Откройте руководства сообщества по модификациям. Перед установкой проверьте совместимость с вашей версией игры и сохраните резервную копию. Файлы доступны на странице автора.",
                            author = if (game.upcoming()) "Официальная страница Steam" else "Сообщество Steam",
                            url = if (game.upcoming()) game.sourceUrl else "https://steamcommunity.com/app/${game.sourceUrl.substringAfter("/app/").substringBefore("/")}/guides/?searchText=mod",
                            category = "Моды",
                            source = if (game.upcoming()) "Steam" else "Steam Community"
                        ))
                    }
                    "Скриншоты" -> screenshots = repository.screenshots(id)
                    "Трейлеры" -> trailers = repository.trailers(id)
                }
            } catch (e: Exception) { if (e is CancellationException) throw e; detailError = e.message ?: "Не удалось загрузить раздел." }
            finally { detailLoading = false; steamReviewsLoading = false }
        }
    }
    fun sendReview(id: Int, text: String, rating: Int, done: () -> Unit) = action {
        require(text.trim().length in 10..5000) { "Напишите отзыв от 10 до 5000 символов." }
        require(rating in 1..10) { "Выберите оценку от 1 до 10." }
        repository.review(id, text, rating)
        reviews = repository.reviews(id)
        myReviews = repository.myReviews()
        refresh(); done(); message = "Отзыв сохранён."
    }
}
