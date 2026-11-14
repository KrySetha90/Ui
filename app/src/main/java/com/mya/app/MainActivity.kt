package com.myassistant.app

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { App() } }
    }
}

private val Teal = Color(0xFF5BA399)   // card colour from your mock-up
private val Mint = Color(0xFF4ADBC8)   // model screen background
private val Gray = Color(0xFFBDBDBD)   // text box

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cmds = remember { Commands(ctx.applicationContext) }
    val prefs = remember { ctx.getSharedPreferences("assistant", 0) }
    val dir = remember { File(ctx.filesDir, "models").apply { mkdirs() } }
    fun listSaved() = dir.list()?.filter { it.endsWith(".glb", true) }?.sorted() ?: emptyList()

    var screen by remember { mutableIntStateOf(0) }
    var model by remember { mutableStateOf(prefs.getString("model", "") ?: "") }
    var saved by remember { mutableStateOf(listSaved()) }
    var folder by remember { mutableStateOf(listOf<DocumentFile>()) }
    var text by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("hello...!") }

    val modelUrl = if (model.isEmpty() || !File(dir, model).exists()) "/assets/models/chisa.glb" else "/models/$model"

    fun importModel(uri: Uri, original: String?) {
        scope.launch {
            var name = (original ?: "model.glb").replace(Regex("[^A-Za-z0-9._-]"), "_")
            if (!name.endsWith(".glb", true)) name += ".glb"
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use { i -> File(dir, name).outputStream().use { o -> i.copyTo(o) } }
            }
            saved = listSaved(); model = name; prefs.edit().putString("model", name).apply()
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importModel(it, DocumentFile.fromSingleUri(ctx, it)?.name) }
    }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            folder = DocumentFile.fromTreeUri(ctx, it)?.listFiles()?.filter { f -> f.name?.endsWith(".glb", true) == true } ?: emptyList()
        }
    }

    fun send() {
        val t = text.trim(); if (t.isEmpty()) return
        text = ""; reply = "..."
        scope.launch { reply = try { cmds.run(t) } catch (e: Exception) { "Something went wrong: ${e.message}" } }
    }

    if (screen == 0) {
        Column(Modifier.fillMaxSize().background(Color.White).systemBarsPadding().imePadding().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black).clickable { screen = 1 }, contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White, fontSize = 34.sp)
                }
                Box(Modifier.height(52.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                    .clickable { reply = cmds.torch(null) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("light", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1.52f).clip(RoundedCornerShape(20.dp)).background(Teal)) {
                    Avatar(modelUrl, Modifier.fillMaxSize())
                    Text(reply, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 6,
                        modifier = Modifier.padding(18.dp).fillMaxWidth(0.45f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp)).background(Gray).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("text...", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color(0x99000000))
                    BasicTextField(text, { text = it }, singleLine = true,
                        textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.fillMaxWidth())
                }
                Text("▶", fontSize = 44.sp, modifier = Modifier.padding(start = 10.dp).clickable { send() })
            }
        }
    } else {
        Column(Modifier.fillMaxSize().background(Mint).systemBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black).clickable { screen = 0 }, contentAlignment = Alignment.Center) {
                Text("+", color = Color.White, fontSize = 34.sp, modifier = Modifier.rotate(45f))
            }
            Spacer(Modifier.height(16.dp))
            BigButton("upload 3d model here") { pick.launch(arrayOf("*/*")) }
            BigButton("open folder") { tree.launch(null) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ModelRow("Default (Chisa)", model.isEmpty()) { model = ""; prefs.edit().putString("model", "").apply() } }
                items(saved) { f -> ModelRow(f, f == model) { model = f; prefs.edit().putString("model", f).apply() } }
                if (folder.isNotEmpty()) item { Text("In the folder you opened:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
                items(folder) { d -> ModelRow("↓ " + (d.name ?: "model.glb"), false) { importModel(d.uri, d.name) } }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black).clickable { onClick() },
        contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontSize = 22.sp) }
}

@Composable
private fun ModelRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text((if (selected) "✓ " else "") + label, fontSize = 18.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x66FFFFFF)).clickable { onClick() }.padding(14.dp))
}

/** Shows the 3D model (GLB) using Google's <model-viewer> inside a transparent WebView. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Avatar(modelUrl: String, modifier: Modifier) {
    AndroidView(modifier = modifier, factory = { c ->
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(c))
            .addPathHandler("/models/", WebViewAssetLoader.InternalStoragePathHandler(c, File(c.filesDir, "models")))
            .build()
        WebView(c).apply {
            setBackgroundColor(0)
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)
            }
        }
    }, update = { wv ->
        if (wv.tag != modelUrl) {
            wv.tag = modelUrl
            wv.loadUrl("https://appassets.androidplatform.net/assets/avatar.html?m=" + Uri.encode(modelUrl))
        }
    })
}
