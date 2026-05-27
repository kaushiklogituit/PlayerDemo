package com.example.exoplayerdummy

import android.os.Bundle
import com.example.exoplayerdummy.AppLogger as Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.exoplayerdummy.domain.usecase.FindVideoByIdUseCase
import com.example.exoplayerdummy.player.config.MediaCacheManager
import com.example.exoplayerdummy.presentation.catalog.CatalogRoot
import com.example.exoplayerdummy.presentation.player.PlaybackRoot
import com.example.exoplayerdummy.ui.theme.AppTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val cacheManager: MediaCacheManager by inject()
    private val findVideo: FindVideoByIdUseCase by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate(savedInstanceState=${savedInstanceState != null})")
        enableEdgeToEdge()
        Log.d(TAG, "Edge-to-edge enabled; composing app content")
        setContent {
            AppTheme {
                StreamPlayerApp(findVideo = findVideo)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy — releasing media cache")
        cacheManager.release()
    }
}

@Composable
private fun StreamPlayerApp(findVideo: FindVideoByIdUseCase) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "catalog") {

        composable("catalog") {
            CatalogRoot(
                onNavigateToPlayer = { videoId ->
                    Log.i("StreamPlayerApp", "Navigating to player: videoId=$videoId")
                    navController.navigate("player/$videoId")
                }
            )
        }

        composable(
            route = "player/{videoId}",
            arguments = listOf(navArgument("videoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId")
            if (videoId == null) {
                Log.e("StreamPlayerApp", "Player destination opened without a videoId")
                return@composable
            }
            val video = remember(videoId) { findVideo(videoId) }
            if (video == null) {
                Log.e("StreamPlayerApp", "No catalog video found for route videoId=$videoId")
                return@composable
            }
            Log.d("StreamPlayerApp", "Resolved route videoId=$videoId to title=${video.title}, protocol=${video.protocol}, live=${video.isLiveStream}")

            PlaybackRoot(
                video = video,
                onNavigateBack = {
                    Log.i("StreamPlayerApp", "Navigating back to catalog")
                    navController.popBackStack()
                }
            )
        }
    }
}
