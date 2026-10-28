package ru.gamekritik.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.platform.testTag
import coil.request.ImageRequest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable fun GameArtwork(url: String, description: String, modifier: Modifier = Modifier, fit: Boolean = true) {
    var attempt by remember(url) { mutableIntStateOf(0) }
    if(url.isBlank()) { Placeholder(modifier, "Нет изображения"); return }
    key(url, attempt) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
            contentDescription = description, modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Panel),
            contentScale = if(fit) ContentScale.Fit else ContentScale.Crop,
            success = { SubcomposeAsyncImageContent(Modifier.testTag("artwork-loaded")) },
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = Muted, strokeWidth = 2.dp) } },
            error = { Box(Modifier.fillMaxSize().clickable { attempt++ }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Refresh,"Повторить загрузку изображения", tint = Muted) } }
        )
    }
}

@Composable fun ArticleScreen(article: Article, back: () -> Unit, openUrl: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppHeader(if (article.category == "Моды") "Моды" else "Гайды и прохождения", back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if(article.cover.isNotBlank()) GameArtwork(article.cover, article.title, Modifier.fillMaxWidth().height(190.dp))
            Text(article.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Автор: ${article.author}", color = Muted, fontSize = 13.sp)
            if(article.category.isNotBlank()) Text(article.category, color = Accent, fontSize = 13.sp)
            Text(article.content, color = Ink, fontSize = 15.sp, lineHeight = 24.sp)
            if(article.url.isNotBlank()) {
                Text("Источник: ${article.source.ifBlank { "Страница автора" }}", color = Muted, fontSize = 12.sp)
                RedButton(if(article.source == "Steam Community") "Открыть руководства в Steam" else "Открыть источник") { openUrl(article.url) }
            }
        }
    }
}

private tailrec fun Context.mediaActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.mediaActivity()
    else -> null
}

@Composable private fun FullscreenMedia(close: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val activity = LocalContext.current.mediaActivity()
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if(previous != null) activity.requestedOrientation = previous }
    }
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if(window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowCompat.getInsetsController(window, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("fullscreen-media"), content = content)
    }
}

@Composable private fun MediaClose(close: () -> Unit, modifier: Modifier) {
    IconButton(close, modifier.size(48.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(24.dp))) {
        Icon(Icons.Outlined.Close, "Закрыть полный экран", tint = Color.White)
    }
}

@Composable fun ScreenshotViewer(items: List<Media>, initial: Int, close: () -> Unit) {
    if(items.isEmpty()) return
    var selected by remember { mutableIntStateOf(initial) }
    var controls by remember { mutableStateOf(true) }
    FullscreenMedia(close) {
        SubcomposeAsyncImage(model = items[selected].url, contentDescription = items[selected].title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().testTag("fullscreen-image").clickable { controls = !controls },
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) } },
            error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Не удалось загрузить скриншот", color = Color.White) } })
        if(controls) {
            MediaClose(close, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp))
            Text("Скриншот ${selected + 1} / ${items.size}", Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(16.dp).background(Color.Black.copy(alpha = .6f)).padding(8.dp), color = Color.White)
            Row(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(12.dp))) {
                TextButton({ selected-- }, enabled = selected > 0) { Text("Предыдущий") }
                TextButton({ selected++ }, enabled = selected < items.lastIndex) { Text("Следующий") }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun TrailerPlayer(media: Media, close: () -> Unit, openUrl: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failure by remember(media.url) { mutableStateOf(false) }
    var controls by remember(media.url) { mutableStateOf(true) }
    val player = remember(media.url) { ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener { override fun onPlayerError(error: PlaybackException) { failure = true } })
        setMediaItem(MediaItem.fromUri(media.url)); prepare(); playWhenReady = true
    } }
    DisposableEffect(player,lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }
    FullscreenMedia(close) {
        AndroidView(factory = { PlayerView(it).apply {
            this.player = player; useController = true
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            controllerShowTimeoutMs = 3000
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { controls = it == android.view.View.VISIBLE })
        } }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize().testTag("fullscreen-video"))
        if(controls || failure) MediaClose(close, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp))
        if(failure) Column(Modifier.align(Alignment.Center).padding(56.dp).background(Color.Black.copy(alpha = .85f)).padding(16.dp)) {
            Text("Не удалось загрузить видео", color = Color.White)
            if(media.sourceUrl.isNotBlank()) TextButton({ openUrl(media.sourceUrl) }) { Text("Открыть официальный источник") }
        }
    }
}
