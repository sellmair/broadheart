@file:OptIn(ExperimentalCoroutinesApi::class)

package io.sellmair.broadheart

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.sellmair.broadheart.model.HeartRate
import io.sellmair.broadheart.service.GroupService
import io.sellmair.broadheart.service.MainService
import io.sellmair.broadheart.service.UserService
import io.sellmair.broadheart.ui.Route
import io.sellmair.broadheart.ui.mainPage.MainPage
import io.sellmair.broadheart.ui.settingsPage.SettingsPage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import shared.ui.*


class MainActivity : ComponentActivity(), CoroutineScope {

    override var coroutineContext: CoroutineContext = Dispatchers.Main + Job()

    private val mainServiceConnection = MainServiceConnection()

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coroutineContext = Dispatchers.Main + Job()

        requestPermissions(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ), 0
        )
        startForegroundService()

        val groupStates = mainServiceConnection.groupService
            .flatMapLatest { servicesOrNull -> servicesOrNull?.groupState ?: flowOf(null) }

        setContent {
            var route by remember { mutableStateOf(Route.MainPage) }
            val groupState by groupStates.collectAsState(null)

            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = route == Route.MainPage,
                    enter = slideInHorizontally(tween(250, 250)) + fadeIn(tween(500, 250)),
                    exit = slideOutHorizontally(tween(500)) + fadeOut(tween(250))
                ) {
                    MainPage(
                        groupState = groupState,
                        onRoute = { route = it },
                        onMyHeartRateLimitChanged = { newHeartRateLimit ->
                            mainServiceConnection.updateHeartRateLimitChannel.trySend(newHeartRateLimit)
                        }
                    )
                }

                AnimatedVisibility(
                    visible = route == Route.SettingsPage,
                    enter = slideInHorizontally(tween(250, 250), initialOffsetX = { it }) + fadeIn(tween(500, 250)),
                    exit = slideOutHorizontally(tween(500), targetOffsetX = { it }) + fadeOut(tween(250))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    )
                    SettingsPage(
                        userService = mainServiceConnection.userService.value ?: return@AnimatedVisibility,
                        groupService = mainServiceConnection.groupService.value ?: return@AnimatedVisibility,
                        onBack = { route = Route.MainPage }
                    )
                }
                Box(Modifier.background(Color.White)) {
                    SharedAndroidUI()
                }
            }
        }
    }

    private inner class MainServiceConnection : ServiceConnection {
        private val _userService = MutableStateFlow<UserService?>(null)
        private val _groupService = MutableStateFlow<GroupService?>(null)

        val userService = _userService.asStateFlow()
        val groupService = _groupService.asStateFlow()

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MainService.MainServiceBinder) {
                _userService.tryEmit(service.services.userService)
                _groupService.tryEmit(service.services.groupService)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _userService.tryEmit(null)
            _groupService.tryEmit(null)
        }

        val updateHeartRateLimitChannel = Channel<HeartRate>(Channel.CONFLATED)

        init {
            launch {
                updateHeartRateLimitChannel.consumeEach { newHeartRateLimit ->
                    val userService = userService.filterNotNull().first()
                    val groupService = groupService.filterNotNull().first()
                    val me = userService.currentUser()
                    userService.saveUpperHeartRateLimit(me, newHeartRateLimit)
                    groupService.updateState()
                }
            }
        }
    }

    private fun startForegroundService() {
        bindService(Intent(this, MainService::class.java), mainServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mainServiceConnection)
        cancel()
    }
}
