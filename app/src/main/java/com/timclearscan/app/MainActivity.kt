package com.timclearscan.app

import android.content.ComponentName
import android.os.Bundle
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val playbackState = MutableStateFlow(PlaybackUi())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )

        controllerFuture =
            MediaController.Builder(this, token).buildAsync()

        controllerFuture.addListener(
            {
                val controller = controllerFuture.get()

                controller.addListener(
                    object : Player.Listener {

                        override fun onIsPlayingChanged(
                            isPlaying: Boolean
                        ) {
                            syncPlayback(controller)
                        }

                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            syncPlayback(controller)
                        }

                        override fun onPlaybackStateChanged(
                            playbackState: Int
                        ) {
                            syncPlayback(controller)
                        }
                    }
                )

                syncPlayback(controller)
            },
            mainExecutor
        )

        setContent {
            ClearScanApp(
                playbackState =
                    playbackState.collectAsStateWithLifecycle().value,
                onPlay = ::playFeed,
                onTogglePlayback = ::togglePlayback
            )
        }
    }

    private fun syncPlayback(
        controller: MediaController
    ) {
        val item = controller.currentMediaItem

        playbackState.value = PlaybackUi(
            id = item?.mediaId,
            title =
                item?.mediaMetadata?.title?.toString().orEmpty(),
            location =
                item?.mediaMetadata?.artist?.toString().orEmpty(),
            isPlaying = controller.isPlaying,
            isLoading =
                controller.playbackState == Player.STATE_BUFFERING
        )
    }

    private fun playFeed(feed: Feed) {
        if (!controllerFuture.isDone) return

        val controller = controllerFuture.get()

        val item = MediaItem.Builder()
            .setMediaId(feed.id)
            .setUri(feed.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(feed.name)
                    .setArtist(feed.location)
                    .build()
            )
            .build()

        controller.setMediaItem(item)
        controller.prepare()
        controller.play()
    }

    private fun togglePlayback() {
        if (!controllerFuture.isDone) return

        val controller = controllerFuture.get()

        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    override fun onDestroy() {
        MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }
}

data class PlaybackUi(
    val id: String? = null,
    val title: String = "",
    val location: String = "",
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearScanApp(
    playbackState: PlaybackUi,
    onPlay: (Feed) -> Unit,
    onTogglePlayback: () -> Unit
) {
    val context =
        androidx.compose.ui.platform.LocalContext.current

    val store = remember {
        FeedStore(context)
    }

    var feeds by remember {
        mutableStateOf(store.load())
    }

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    var search by remember {
        mutableStateOf("")
    }

    var showAdd by remember {
        mutableStateOf(false)
    }

    fun saveFeeds(updated: List<Feed>) {
        feeds = updated
        store.save(updated)
    }

    val visibleFeeds =
        remember(feeds, selectedTab, search) {

            feeds
                .filter {
                    selectedTab == 1 || it.favorite
                }
                .filter {
                    search.isBlank() ||
                        it.name.contains(
                            search,
                            ignoreCase = true
                        ) ||
                        it.location.contains(
                            search,
                            ignoreCase = true
                        )
                }
                .sortedWith(
                    compareByDescending<Feed> {
                        it.favorite
                    }
                        .thenByDescending {
                            it.lastPlayedAt
                        }
                        .thenBy {
                            it.name.lowercase()
                        }
                )
        }

    ClearScanTheme {

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = Amber
                                )

                                Spacer(
                                    Modifier.width(10.dp)
                                )

                                Text(
                                    "ClearScan",
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }
                        }
                    )

                    TabRow(
                        selectedTabIndex =
                            selectedTab
                    ) {
                        Tab(
                            selected =
                                selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                            },
                            text = {
                                Text("Favorites")
                            }
                        )

                        Tab(
                            selected =
                                selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                            },
                            text = {
                                Text("All Feeds")
                            }
                        )
                    }
                }
            },

            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        showAdd = true
                    },
                    containerColor = Amber,
                    contentColor = Color.Black
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription =
                            "Add feed"
                    )
                }
            },

            bottomBar = {
                if (playbackState.id != null) {
                    NowPlayingBar(
                        playbackState,
                        onTogglePlayback
                    )
                }
            },

            containerColor = BackgroundDark

        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(
                        horizontal = 14.dp
                    )
            ) {

                Spacer(
                    Modifier.height(12.dp)
                )

                OutlinedTextField(
                    value = search,
                    onValueChange = {
                        search = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Search saved feeds"
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null
                        )
                    }
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                if (visibleFeeds.isEmpty()) {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(30.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.Center
                    ) {

                        Icon(
                            Icons.Default.Radio,
                            contentDescription = null,
                            tint = Amber,
                            modifier =
                                Modifier.size(60.dp)
                        )

                        Spacer(
                            Modifier.height(16.dp)
                        )

                        Text(
                            if (selectedTab == 0)
                                "No favorites"
                            else
                                "No feeds found",
                            style =
                                MaterialTheme.typography.titleLarge
                        )
                    }

                } else {

                    LazyColumn(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            ),
                        contentPadding =
                            PaddingValues(
                                bottom = 100.dp
                            )
                    ) {

                        items(
                            visibleFeeds,
                            key = {
                                it.id
                            }
                        ) { feed ->

                            FeedCard(
                                feed = feed,
                                isActive =
                                    playbackState.id ==
                                        feed.id,
                                isPlaying =
                                    playbackState.id ==
                                        feed.id &&
                                        playbackState.isPlaying,

                                onPlay = {

                                    val now =
                                        System.currentTimeMillis()

                                    saveFeeds(
                                        feeds.map {
                                            if (
                                                it.id ==
                                                feed.id
                                            ) {
                                                it.copy(
                                                    lastPlayedAt =
                                                        now
                                                )
                                            } else {
                                                it
                                            }
                                        }
                                    )

                                    onPlay(feed)
                                },

                                onFavorite = {
                                    saveFeeds(
                                        feeds.map {
                                            if (
                                                it.id ==
                                                feed.id
                                            ) {
                                                it.copy(
                                                    favorite =
                                                        !it.favorite
                                                )
                                            } else {
                                                it
                                            }
                                        }
                                    )
                                },

                                onDelete = {
                                    saveFeeds(
                                        feeds.filterNot {
                                            it.id ==
                                                feed.id
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showAdd) {

            AddFeedDialog(
                onDismiss = {
                    showAdd = false
                },

                onSave = {
                        name,
                        location,
                        url,
                        favorite ->

                    val newFeed = Feed(
                        id =
                            UUID.randomUUID()
                                .toString(),
                        name = name.trim(),
                        location =
                            location.trim(),
                        url = url.trim(),
                        favorite = favorite
                    )

                    saveFeeds(
                        feeds + newFeed
                    )

                    showAdd = false
                }
            )
        }
    }
}

@Composable
private fun FeedCard(
    feed: Feed,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isActive)
                        ActiveSurface
                    else
                        SurfaceDark
            ),
        shape =
            RoundedCornerShape(16.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onPlay()
                }
                .padding(14.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            FilledIconButton(
                onClick = onPlay,
                colors =
                    IconButtonDefaults
                        .filledIconButtonColors(
                            containerColor = Amber,
                            contentColor =
                                Color.Black
                        )
            ) {
                Icon(
                    if (isPlaying)
                        Icons.Default.Radio
                    else
                        Icons.Default.PlayArrow,
                    contentDescription = "Play"
                )
            }

            Spacer(
                Modifier.width(12.dp)
            )

            Column(
                Modifier.weight(1f)
            ) {

                Text(
                    feed.name,
                    fontWeight =
                        FontWeight.SemiBold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                if (
                    feed.location.isNotBlank()
                ) {
                    Text(
                        feed.location,
                        color =
                            Color(0xFFB8B8B8),
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )
                }

                Text(
                    if (isActive)
                        "LIVE"
                    else
                        "Ready",
                    color =
                        if (isActive)
                            Amber
                        else
                            Color(
                                0xFF777777
                            ),
                    style =
                        MaterialTheme.typography
                            .labelSmall
                )
            }

            IconButton(
                onClick = onFavorite
            ) {
                Icon(
                    if (feed.favorite)
                        Icons.Default.Favorite
                    else
                        Icons.Default.FavoriteBorder,
                    contentDescription =
                        "Favorite",
                    tint =
                        if (feed.favorite)
                            Amber
                        else
                            Color(
                                0xFFBBBBBB
                            )
                )
            }

            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription =
                        "Delete",
                    tint =
                        Color(0xFF888888)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingBar(
    state: PlaybackUi,
    onTogglePlayback: () -> Unit
) {

    Surface(
        color = Color(0xFF171717)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 10.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.Radio,
                contentDescription = null,
                tint = Amber
            )

            Spacer(
                Modifier.width(10.dp)
            )

            Column(
                Modifier.weight(1f)
            ) {

                Text(
                    state.title.ifBlank {
                        "Scanner feed"
                    },
                    fontWeight =
                        FontWeight.SemiBold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Text(
                    when {
                        state.isLoading ->
                            "Buffering..."
                        state.isPlaying ->
                            "Live audio"
                        else ->
                            "Paused"
                    },
                    color =
                        if (state.isPlaying)
                            Amber
                        else
                            Color(
                                0xFFAAAAAA
                            )
                )
            }

            FilledIconButton(
                onClick =
                    onTogglePlayback,
                colors =
                    IconButtonDefaults
                        .filledIconButtonColors(
                            containerColor = Amber,
                            contentColor =
                                Color.Black
                        )
            ) {
                Icon(
                    if (state.isPlaying)
                        Icons.Default.Pause
                    else
                        Icons.Default.PlayArrow,
                    contentDescription =
                        "Play or pause"
                )
            }
        }
    }
}

@Composable
private fun AddFeedDialog(
    onDismiss: () -> Unit,
    onSave: (
        String,
        String,
        String,
        Boolean
    ) -> Unit
) {

    var name by remember {
        mutableStateOf("")
    }

    var location by remember {
        mutableStateOf("")
    }

    var url by remember {
        mutableStateOf("")
    }

    var favorite by remember {
        mutableStateOf(true)
    }

    val valid =
        name.isNotBlank() &&
            URLUtil.isNetworkUrl(
                url.trim()
            )

    AlertDialog(
        onDismissRequest =
            onDismiss,

        title = {
            Text(
                "Add scanner feed"
            )
        },

        text = {

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = {
                        Text("Feed name")
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = {
                        location = it
                    },
                    label = {
                        Text(
                            "Location or agency"
                        )
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                    },
                    label = {
                        Text(
                            "Direct stream URL"
                        )
                    },
                    placeholder = {
                        Text(
                            "https://..."
                        )
                    }
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Checkbox(
                        checked = favorite,
                        onCheckedChange = {
                            favorite = it
                        }
                    )

                    Text(
                        "Add to favorites"
                    )
                }
            }
        },

        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        name,
                        location,
                        url,
                        favorite
                    )
                }
            ) {
                Text("Save")
            }
        },

        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

private val BackgroundDark =
    Color(0xFF090909)

private val SurfaceDark =
    Color(0xFF131313)

private val ActiveSurface =
    Color(0xFF211B0E)

private val Amber =
    Color(0xFFFFB300)

@Composable
private fun ClearScanTheme(
    content: @Composable () -> Unit
) {

    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Amber,
                background =
                    BackgroundDark,
                surface =
                    SurfaceDark,
                onPrimary =
                    Color.Black,
                onBackground =
                    Color(0xFFF0F0F0),
                onSurface =
                    Color(0xFFF0F0F0)
            ),
        content = content
    )
}
