package ru.gamekritik.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

val Background: Color @Composable get() = MaterialTheme.colorScheme.background
val Panel: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Gold: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFC107) else Color(0xFF795500)
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground

private val DarkColors = darkColorScheme(primary = Color(0xFFE53935), onPrimary = Color.White,
    background = Color(0xFF121212), onBackground = Color.White,
    surface = Color(0xFF121212), onSurface = Color.White,
    surfaceVariant = Color(0xFF202020), onSurfaceVariant = Color(0xFFACACAC))
private val LightColors = lightColorScheme(primary = Color(0xFFB42322), onPrimary = Color.White,
    background = Color(0xFFF8F7F5), onBackground = Color(0xFF171717),
    surface = Color.White, onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFECEAE7), onSurfaceVariant = Color(0xFF555555))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
                GameKritikApp()
            }
        }
    }
}

@Composable fun GameKritikApp(vm: GameViewModel = viewModel()) {
    var page by rememberSaveable { mutableStateOf("welcome") }
    var gameId by rememberSaveable { mutableStateOf<Int?>(null) }
    var section by rememberSaveable { mutableStateOf("Описание") }
    var reviewDialog by remember { mutableStateOf(false) }
    var article by remember { mutableStateOf<Article?>(null) }
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    val game = vm.games.find { it.id == gameId }
    val auth = page == "login" || page == "register"
    fun back() { when { article != null -> article = null; auth -> page = "catalog"; section != "Описание" && gameId != null -> section = "Описание"; gameId != null -> gameId = null; page != "welcome" -> page = "welcome" } }
    BackHandler(page != "welcome" || gameId != null) { back() }
    LaunchedEffect(vm.error, vm.message) {
        (vm.error ?: vm.message)?.let { snack.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(page) { if (page == "profile" && vm.signedIn) vm.reloadProfile() }
    LaunchedEffect(gameId, section) { gameId?.let { if (section != "Описание") vm.loadSection(it, section) else vm.recordView(it) } }
    fun openUrl(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme in listOf("https", "http")) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
    Scaffold(containerColor = Background, snackbarHost = { SnackbarHost(snack) }, bottomBar = {
        if (page != "welcome" && !auth) NavigationBar(containerColor = Panel, tonalElevation = 0.dp, modifier = Modifier.heightIn(min = 68.dp)) {
            listOf(Triple("home", "Главная", Icons.Outlined.Home), Triple("catalog", "Каталог", Icons.Outlined.SportsEsports), Triple("favorites", "Избранное", Icons.Outlined.FavoriteBorder), Triple("profile", "Профиль", Icons.Outlined.PersonOutline)).forEach { (key, label, icon) ->
                NavigationBarItem(selected = page == key, onClick = { article = null; page = key; gameId = null; section = "Описание" }, icon = { Icon(icon, label, Modifier.size(23.dp)) }, label = { Text(label, fontSize = 10.sp, maxLines = 1) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Panel, unselectedIconColor = Muted, unselectedTextColor = Muted))
            }
        }
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when {
                article != null -> ArticleScreen(article!!, { article = null }, ::openUrl)
                page == "welcome" -> Welcome { page = "catalog" }
                auth -> AuthScreen(page == "register", vm, { back() }, { page = if (page == "register") "login" else "register" }, { page = "catalog" })
                game != null -> Column {
                    AppHeader(if (section == "Описание") game.title else when(section) { "Гайды" -> "Гайды и прохождения"; "Моды" -> "Модификации (Моды)"; else -> section }, { back() }) {
                        IconButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${game.title} — ${game.rating?.let { "${rating(it)}/10" } ?: "без оценки"} в GameKritik") }, "Поделиться")) }) { Icon(Icons.Outlined.Share, "Поделиться") }
                    }
                    when (section) {
                        "Описание" -> GameDetail(game, game.id in vm.favorites, vm.busy, { if(vm.signedIn) vm.toggleFavorite(game.id) else page = "login" }, { section = it })
                        else -> DetailSection(vm, game, section, { if(vm.signedIn) reviewDialog = true else page = "login" }, { article = it }, ::openUrl)
                    }
                }
                page == "profile" -> ProfileScreen(vm, { page = "login" }, { page = "register" }, { gameId = it; section = "Описание" }, { gameId = it; section = "Отзывы" })
                page == "favorites" && !vm.signedIn -> Column { AppHeader("Избранное"); EmptyState("Сохраните любимые игры", "Войдите, чтобы избранное сохранялось в вашем аккаунте.", "Войти") { page = "login" } }
                else -> Catalog(vm, page == "favorites", page == "home", { page = "login" }, { gameId = it; section = "Описание" })
            }
        }
    }
    if (reviewDialog && game != null) ReviewDialog(vm.busy, { reviewDialog = false }) { text, value -> vm.sendReview(game.id, text, value) { reviewDialog = false } }
}

@Composable fun Brand(size: Int = 24) {
    Text(buildAnnotatedString { append("Game"); withStyle(SpanStyle(color = Accent)) { append("Kritik") } }, fontSize = size.sp, fontWeight = FontWeight.Bold, color = Ink)
}
@Composable fun Welcome(start: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(Accent), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SportsEsports, null, Modifier.size(44.dp), Color.White) }
        Spacer(Modifier.height(14.dp)); Brand(34)
        Spacer(Modifier.height(32.dp)); Text("Игры. Отзывы. Гайды. Всё в одном месте.", color = Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(34.dp)); RedButton("Начать", onClick = start)
    }
}
@Composable fun AppHeader(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", Modifier.size(24.dp)) }
        Text(title, Modifier.weight(1f).padding(horizontal = 4.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}
@Composable fun RedButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = enabled, shape = RoundedCornerShape(9.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)) { Text(text, fontWeight = FontWeight.Bold) }
}
@Composable fun Placeholder(modifier: Modifier = Modifier, label: String? = null) {
    Column(modifier.clip(RoundedCornerShape(5.dp)).background(Panel), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Image, null, Modifier.size(28.dp), Muted)
        if (label != null) Text(label, Modifier.padding(12.dp), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
@Composable fun Tabs(labels: List<String>, selected: Int, choose: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            Box(Modifier.clip(RoundedCornerShape(7.dp)).background(if(index == selected) Accent else Panel).clickable { choose(index) }.padding(horizontal = 14.dp, vertical = 10.dp)) { Text(label, color = if(index == selected) Color.White else Muted, fontSize = 13.sp, fontWeight = if(index == selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}
fun rating(value: Double) = String.format(Locale.US, "%.1f", value)
@Composable fun Rating(value: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { Icon(Icons.Outlined.StarBorder, null, Modifier.size(15.dp), Gold) }
        Text(value?.let(::rating) ?: "—", color = Gold, fontSize = 13.sp)
    }
}
@Composable fun Catalog(vm: GameViewModel, favoritesOnly: Boolean, home: Boolean, login: () -> Unit, open: (Int) -> Unit) {
    var category by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var genre by rememberSaveable { mutableStateOf("Все жанры") }
    var genreMenu by remember { mutableStateOf(false) }
    val genres = listOf("Все жанры") + vm.games.flatMap { it.genre.split(" · ") }.filter { it.isNotBlank() }.distinct().sorted()
    val games = filterGames(vm.games.filter { !favoritesOnly || it.id in vm.favorites }, query, category, genre = genre)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            if(favoritesOnly) Text("Избранное", fontSize = 23.sp, fontWeight = FontWeight.Bold) else Brand()
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { search = !search }) { Icon(Icons.Outlined.Search, "Поиск игр") }
            if (!vm.signedIn) Button(login, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp)) { Text("Войти", fontSize = 12.sp) }
        }
        if (search) { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Найти игру") }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }); Spacer(Modifier.height(12.dp)) }
        if(home) { Text("Откройте свою следующую игру", color = Muted, fontSize = 14.sp); Spacer(Modifier.height(14.dp)) }
        Tabs(listOf("Популярные", "Новые", "Скоро выйдут"), category) { category = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton({ genreMenu = true }) { Icon(Icons.Outlined.Tune, null, Modifier.size(17.dp)); Text("  $genre", fontSize = 12.sp) }
                DropdownMenu(genreMenu, { genreMenu = false }) { genres.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { genre = item; genreMenu = false }) } }
            }
            Spacer(Modifier.weight(1f)); Text("${games.size} игр", color = Muted, fontSize = 12.sp)
        }
        if(category == 1) Text("Релизы 2025–2026", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        if(vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        if(games.isEmpty() && !vm.loading) EmptyState(if(favoritesOnly) "Пока нет избранных игр" else "Игры не найдены", if(query.isNotBlank() || genre != "Все жанры") "Попробуйте другое название или жанр." else if(favoritesOnly) "Добавляйте игры в избранное на странице игры." else "В этой категории пока нет игр.", "Обновить", vm::refresh)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(games, key = { it.id }) { game ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).clickable { open(game.id) }) {
                    GameArtwork(game.cover, game.title, Modifier.fillMaxWidth().aspectRatio(460f / 215f), fit = true)
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(game.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(game.genre.ifBlank { game.platform }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if(game.upcoming()) Text(game.releaseText(), color = Accent, fontSize = 12.sp)
                        else if(game.rating != null) Rating(game.rating)
                        else if(game.criticScore != null) Text("${game.criticScore}/100 · Metacritic", color = Gold, fontSize = 12.sp)
                        else Text("${game.firstReleaseYear ?: game.release.take(4)} · Нет оценок", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            item { TextButton(onClick = vm::refresh, Modifier.fillMaxWidth()) { Text("Обновить каталог") } }
        }
    }
}
@Composable fun EmptyState(title: String, subtitle: String, button: String? = null, action: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.SportsEsports, null, Modifier.size(42.dp), Muted)
        Text(title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(subtitle, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        if(button != null) TextButton(action) { Text(button) }
    }
}
@Composable fun GameDetail(game: Game, favorite: Boolean, busy: Boolean, toggleFavorite: () -> Unit, section: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        GameArtwork(game.cover.ifBlank { game.hero }, game.title, Modifier.fillMaxWidth().aspectRatio(460f / 215f), fit = true)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column { Text(game.title, fontSize = 23.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); if(game.upcoming()) Text("Скоро выйдет · ${game.releaseText()}", color = Accent) else { Rating(game.rating); if(game.criticScore != null) Text("Metacritic: ${game.criticScore}/100", color = Muted, fontSize = 12.sp) } }
            if(game.platform.isNotBlank()) Text(game.platform, Modifier.clip(RoundedCornerShape(6.dp)).background(Panel).padding(9.dp), fontSize = 12.sp, color = Muted)
            RedButton(if(favorite) "♥  В избранном" else "♡  В избранное", !busy, toggleFavorite)
            Tabs(listOf("Описание", "Скриншоты", "Трейлеры"), 0) { if(it > 0) section(listOf("Описание", "Скриншоты", "Трейлеры")[it]) }
            Text(game.description.ifBlank { "Описание пока не добавлено." }, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                InfoRow("Жанр", game.genre); InfoRow("Разработчик", game.developer); InfoRow("Издатель", game.publisher); InfoRow("Платформы", game.platform); InfoRow("Дата выхода", game.releaseText())
            }
            listOf("Отзывы", "Гайды", "Моды").forEach { name -> OutlinedButton({ section(name) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text(name, color = Ink) } }
        }
    }
}
@Composable fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(label, Modifier.weight(1f), color = Muted, fontSize = 12.sp); Text(value.ifBlank { "—" }, Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End) }
}
@Composable fun DetailSection(vm: GameViewModel, game: Game, section: String, writeReview: () -> Unit, article: (Article) -> Unit, openUrl: (String) -> Unit) {
    var filter by rememberSaveable(game.id) { mutableIntStateOf(0) }
    var guideCategory by rememberSaveable(game.id) { mutableStateOf("Все") }
    var selectedShot by remember { mutableStateOf<Int?>(null) }
    var selectedTrailer by remember { mutableStateOf<Media?>(null) }
    selectedShot?.let { if(it in vm.screenshots.indices) ScreenshotViewer(vm.screenshots,it) { selectedShot = null } }
    selectedTrailer?.let { TrailerPlayer(it, { selectedTrailer = null }, openUrl) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        if(vm.detailLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent) }
        if(vm.detailError != null) item { EmptyState("Не удалось загрузить", vm.detailError.orEmpty(), "Повторить") { vm.loadSection(game.id, section) } }
        when(section) {
            "Отзывы" -> {
                item { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(game.rating?.let(::rating) ?: "—", fontSize = 38.sp, fontWeight = FontWeight.Bold); Text("${vm.reviews.size} отзывов", color = Muted, fontSize = 12.sp) }; Rating(game.rating) } }
                item { RedButton("✎  Написать отзыв", !vm.busy, writeReview) }
                item { Tabs(listOf("Все", "Положительные", "Отрицательные"), filter) { filter = it } }
                val filtered = vm.reviews.filter { filter == 0 || (filter == 1 && it.rating >= 6) || (filter == 2 && it.rating < 6) }
                items(filtered, key = { it.id }) { review -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.AccountCircle, null, Modifier.size(34.dp), Muted); Column(Modifier.weight(1f).padding(start = 8.dp)) { Text(review.name, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(review.date, color = Muted, fontSize = 11.sp) }; Text("${review.rating}", color = Gold, fontWeight = FontWeight.Bold) }
                    Text(review.text, color = Muted, fontSize = 14.sp, lineHeight = 19.sp)
                } }
                if(filtered.isEmpty() && !vm.detailLoading && vm.detailError == null) item { EmptyState("Отзывов GameKritik пока нет", "Поделитесь впечатлениями об игре.") }
                if(!game.upcoming()) {
                    item { Text("Отзывы Steam", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
                    item { Text("Реальные отзывы игроков Steam с высоким числом отметок «забавно». Текст может содержать грубые выражения.", color = Muted, fontSize = 12.sp) }
                    if(vm.steamReviewsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent) }
                    items(vm.steamReviews, key = { "steam-${it.id}" }) { review ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if(review.recommended) "👍 Рекомендует" else "👎 Не рекомендует", Modifier.weight(1f), color = if(review.recommended) Color(0xFF3A9B62) else Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("😄 ${review.funnyVotes}", color = Muted, fontSize = 12.sp)
                            }
                            Text(review.text, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Steam · ${review.date}", Modifier.weight(1f), color = Muted, fontSize = 11.sp)
                                TextButton({ openUrl(review.url) }) { Text("Оригинал ↗", fontSize = 12.sp) }
                            }
                        }
                    }
                    if(vm.steamReviews.isEmpty() && !vm.steamReviewsLoading && !vm.detailLoading) item {
                        Text("Отзывы Steam сейчас недоступны. Откройте их на странице игры.", color = Muted, fontSize = 12.sp)
                        TextButton({ openUrl(game.sourceUrl) }) { Text("Открыть Steam") }
                    }
                }
            }
            "Гайды", "Моды" -> {
                if(section == "Гайды") item {
                    val categories = listOf("Все") + vm.guides.map { it.category }.filter { it.isNotBlank() }.distinct()
                    Tabs(categories, categories.indexOf(guideCategory).coerceAtLeast(0)) { guideCategory = categories[it] }
                }
                val list = if(section == "Гайды") vm.guides.filter { guideCategory == "Все" || it.category == guideCategory } else vm.mods
                items(list, key = { it.id }) { item -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).clickable { article(item) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameArtwork(item.cover.ifBlank { game.cover }, item.title, Modifier.width(120.dp).aspectRatio(460f / 215f), fit = true)
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Автор: ${item.author.ifBlank { "не указан" }}", color = Muted, fontSize = 12.sp)
                        if(item.category.isNotBlank()) Text(item.category, color = Accent, fontSize = 11.sp)
                        if(section == "Моды" && item.url.isNotBlank()) TextButton({ openUrl(item.url) }, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp)); Text(" Открыть источник", fontSize = 12.sp) }
                    }
                } }
                if(list.isEmpty() && !vm.detailLoading && vm.detailError == null) item { EmptyState("Раздел пока пуст", "Материалы появятся здесь после добавления в базу.") }
            }
            "Скриншоты" -> {
                if(vm.screenshots.isEmpty() && !vm.detailLoading && vm.detailError == null) item { EmptyState("Скриншотов пока нет", "Издатель ещё не добавил изображения.") }
                items(vm.screenshots.size) { index ->
                    val media = vm.screenshots[index]
                    GameArtwork(media.url, media.title, Modifier.fillMaxWidth().height(200.dp).clickable { selectedShot = index })
                }
            }
            "Трейлеры" -> {
                items(vm.trailers) { item -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().height(190.dp).clickable { if(item.url.contains(".m3u8") || item.url.contains(".mp4")) selectedTrailer = item else openUrl(item.url) }, contentAlignment = Alignment.Center) {
                        GameArtwork(item.thumbnail.ifBlank { game.hero }, item.title, Modifier.fillMaxSize())
                        Icon(Icons.Outlined.PlayCircle, "Смотреть трейлер", Modifier.size(54.dp).background(Color.Black.copy(alpha = .35f), RoundedCornerShape(50)), Color.White)
                    }
                    RedButton("Смотреть трейлер") { if(item.url.contains(".m3u8") || item.url.contains(".mp4")) selectedTrailer = item else openUrl(item.url) }
                } }
                if(vm.trailers.isEmpty() && !vm.detailLoading && vm.detailError == null) item { EmptyState("Трейлеров пока нет", "Ссылки появятся после добавления в базу.") }
            }
        }
    }
}
@Composable fun Input(label: String, value: String, placeholder: String, password: Boolean = false, email: Boolean = false, change: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Muted, fontSize = 14.sp)
        OutlinedTextField(value, change, Modifier.fillMaxWidth(), placeholder = { Text(placeholder, color = Muted, fontSize = 14.sp) }, singleLine = true, shape = RoundedCornerShape(8.dp), visualTransformation = if(password && !visible) PasswordVisualTransformation() else VisualTransformation.None, keyboardOptions = KeyboardOptions(keyboardType = if(password) KeyboardType.Password else if(email) KeyboardType.Email else KeyboardType.Text), trailingIcon = if(password) {{ IconButton({ visible = !visible }) { Icon(if(visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Показать или скрыть пароль") } }} else null, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel, unfocusedBorderColor = Color(0xFF303030), focusedBorderColor = Accent))
    }
}
@Composable fun AuthScreen(register: Boolean, vm: GameViewModel, back: () -> Unit, switch: () -> Unit, done: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }; var email by rememberSaveable { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(register) { vm.clearAuthError(); localError = null }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        AppHeader(if(register) "Регистрация" else "Вход в аккаунт", back)
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if(register) "Регистрация" else "Вход", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            if(register) Input("Никнейм", name, "Придумайте никнейм", change = { name = it })
            Input("Почта (Email)", email, "Введите почту", email = true, change = { email = it })
            Input("Пароль", password, if(register) "Придумайте пароль" else "Введите пароль", password = true, change = { password = it })
            if(register) {
                Text("Минимум 6 символов. Аккаунт создаётся сразу, без письма.", color = Muted, fontSize = 12.sp)
                Input("Повторите пароль", confirmation, "Введите пароль ещё раз", password = true, change = { confirmation = it })
            }
            (localError ?: vm.authError)?.let { Text(it, color = Accent, fontSize = 14.sp) }
            if(!register) TextButton({ vm.recover(email) }, enabled = !vm.busy, contentPadding = PaddingValues(0.dp)) { Text("Забыли пароль?") }
            RedButton(if(vm.busy) "Подождите…" else if(register) "Зарегистрироваться" else "Войти", !vm.busy) {
                localError = null
                if (register && password != confirmation) localError = "Пароли не совпадают."
                else vm.authenticate(register, name, email, password, done)
            }
            TextButton(switch, Modifier.align(Alignment.CenterHorizontally), enabled = !vm.busy) { Text(if(register) "Уже есть аккаунт? Войти" else "Нет аккаунта? Регистрация") }
        }
    }
}
@Composable fun ProfileScreen(vm: GameViewModel, login: () -> Unit, register: () -> Unit, openGame: (Int) -> Unit, openReview: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppHeader("Профиль")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.AccountCircle, null, Modifier.size(80.dp), Muted)
            if(vm.signedIn) {
                Text(vm.profile?.name ?: "Ваш аккаунт", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(vm.profile?.email.orEmpty(), color = Muted)
                Text("Игр в избранном: ${vm.favorites.size}", color = Muted)
                if(vm.profile == null) RedButton("Загрузить профиль", !vm.busy, vm::reloadProfile)
            } else {
                Text("Ваш игровой профиль", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Войдите, чтобы сохранять игры и писать отзывы.", color = Muted, textAlign = TextAlign.Center)
                RedButton("Войти", onClick = login)
                TextButton(register) { Text("Создать аккаунт") }
            }
            }
            }
            if (vm.signedIn) {
                item { Text("Мои отзывы", Modifier.fillMaxWidth().padding(top = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                if (vm.myReviews.isEmpty()) item { Text("Вы пока не написали отзывов. Откройте игру и нажмите «Написать отзыв».", color = Muted) }
                items(vm.myReviews, key = { "review-${it.id}" }) { review ->
                    vm.games.find { it.id == review.gameId }?.let { game ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable { openReview(game.id) }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(game.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2)
                                Text("${review.rating}/10", color = Gold, fontWeight = FontWeight.Bold)
                            }
                            Text(review.text, color = Ink, maxLines = 4, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            Text(review.date, color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                item { Text("История просмотров", Modifier.fillMaxWidth().padding(top = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                if (vm.history.isEmpty()) item { Text("Вы ещё не открывали игры. Просмотренные игры появятся здесь и сохранятся в профиле.", color = Muted) }
                items(vm.history, key = { it }) { id ->
                    vm.games.find { it.id == id }?.let { game ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable { openGame(id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GameArtwork(game.cover, game.title, Modifier.size(width = 96.dp, height = 52.dp))
                            Text(game.title, Modifier.weight(1f).padding(start = 12.dp), fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)); RedButton("Выйти", !vm.busy, vm::logout) }
            }
        }
    }
}
@Composable fun ReviewDialog(busy: Boolean, close: () -> Unit, send: (String, Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }; var value by rememberSaveable { mutableFloatStateOf(8f) }
    AlertDialog(onDismissRequest = { if(!busy) close() }, title = { Text("Написать отзыв") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Ваша оценка: ${value.toInt()} / 10", color = Gold); Slider(value, { value = it }, valueRange = 1f..10f, steps = 8, enabled = !busy); OutlinedTextField(text, { if(it.length <= 5000) text = it }, Modifier.fillMaxWidth(), placeholder = { Text("Что вам понравилось в игре?") }, minLines = 4, maxLines = 8); Text("От 10 до 5000 символов", color = Muted, fontSize = 12.sp) } }, confirmButton = { TextButton({ send(text, value.toInt()) }, enabled = !busy && text.trim().length >= 10) { Text(if(busy) "Отправка…" else "Опубликовать") } }, dismissButton = { TextButton(close, enabled = !busy) { Text("Отмена") } }, containerColor = Panel)
}

