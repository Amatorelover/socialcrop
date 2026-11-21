package com.lihoo.socialcrop

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lihoo.socialcrop.ui.AboutScreen
import com.lihoo.socialcrop.ui.EditorScreen
import com.lihoo.socialcrop.ui.FrameSettingsScreen
import com.lihoo.socialcrop.ui.HomeScreen
import com.lihoo.socialcrop.ui.ResultScreen
import com.lihoo.socialcrop.ui.WatermarkScreen
import com.lihoo.socialcrop.ui.theme.SocialCropTheme
import com.lihoo.socialcrop.data.FrameStyle
import com.lihoo.socialcrop.util.LocaleManager
import com.lihoo.socialcrop.viewmodel.EditorViewModel

/**
 * 切换语言会 recreate Activity。记住当前页面，重建后从 home 出发再 navigate 回去，
 * 保证回退栈里始终有 home，返回键不会失效（直接把子页面设为 startDestination 会卡死）。
 * 进程重启时自然重置为 home。
 */
object NavState {
    var lastRoute: String = "home"
    var restoreRoute: String? = null
}

class MainActivity : ComponentActivity() {
    /** 在 setContentView 之前套上选定语言的 Configuration，Compose 的 stringResource 会读到它 */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase, LocaleManager.get(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SocialCropTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val vm: EditorViewModel = viewModel()
    val context = LocalContext.current

    DisposableEffect(nav) {
        val listener = NavController.OnDestinationChangedListener { _, destination, arguments ->
            val route = destination.route
            if (route != null) {
                // destination.route 是路由「模板」（watermark?target={target}），不是当前页的实际 URL。
                // 旧版直接存模板，语言切换重建后 target 变成字面量 {target}，解析失败就错跳全局水印页。
                NavState.lastRoute = if (route.startsWith("watermark")) {
                    "watermark?target=${arguments?.getString("target") ?: "global"}"
                } else route
            }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }

    // 恢复上次保存的边框参数（只保存 applicationContext，不泄漏 Activity）
    LaunchedEffect(Unit) { vm.initSettings(context) }

    // 语言切换重建后恢复到原页面（home 保持在栈底）。
    // 边框模式已在编辑页直接平铺，参数页从 editor 进入，只有两级。
    LaunchedEffect(Unit) {
        val route = NavState.restoreRoute
        NavState.restoreRoute = null
        if (route.isNullOrBlank() || route == "home") return@LaunchedEffect

        // 进程被杀后重建时图片/结果已丢失，这些页面不该在空数据下打开
        val needsImage = route.startsWith("editor") || route.startsWith("frame_") ||
            route.startsWith("watermark") || route == "result"
        if (needsImage && vm.bitmap == null) {
            nav.popBackStack("home", inclusive = false)
            return@LaunchedEffect
        }
        // navigation-compose 2.8.x 在 recreate 时会自动恢复回退栈；
        // 已经恢复过就不要再压一遍 editor / frame_settings
        val current = nav.currentBackStackEntry?.destination?.route
        if (current != null && current != "home") return@LaunchedEffect

        val chain = when {
            route.startsWith("frame_settings") -> listOf("editor", "frame_settings")
            route.startsWith("watermark") -> listOf("editor", route)
            else -> listOf(route)
        }
        chain.forEach { nav.navigate(it) }
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(nav, vm) }
        composable("editor") { EditorScreen(nav, vm) }
        composable("frame_settings") { FrameSettingsScreen(nav, vm) }
        composable("watermark?target={target}") { backStackEntry ->
            val t = backStackEntry.arguments?.getString("target") ?: "global"
            val target = if (t == "global") EditorViewModel.WatermarkTarget.Global
            else runCatching {
                EditorViewModel.WatermarkTarget.Frame(enumValueOf<FrameStyle>(t))
            }.getOrDefault(EditorViewModel.WatermarkTarget.Global)
            WatermarkScreen(nav, vm, target)
        }
        composable("result") { ResultScreen(nav, vm) }
        composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}
