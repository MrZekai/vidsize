package com.vidsize.compressor.ui.screens

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidsize.compressor.R
import com.vidsize.compressor.ads.suppressAppOpenOnReturn
import com.vidsize.compressor.ui.components.IconAction
import com.vidsize.compressor.ui.theme.Space
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeType

@Composable
fun LegalDocumentSheet(
    title: String,
    assetFileName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val webView = remember(context, assetFileName) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.setSupportZoom(false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val uri = request?.url ?: return false
                    val isBundledLegal =
                        uri.scheme == "file" &&
                            uri.path?.startsWith("/android_asset/legal/") == true
                    if (isBundledLegal) return false

                    val action = when (uri.scheme?.lowercase()) {
                        "http", "https" -> Intent.ACTION_VIEW
                        "mailto" -> Intent.ACTION_SENDTO
                        else -> return true
                    }
                    context.suppressAppOpenOnReturn()
                    runCatching { context.startActivity(Intent(action, uri)) }
                    return true
                }
            }
            loadUrl("file:///android_asset/legal/$assetFileName")
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .widthIn(max = 720.dp),
            shape = VidsizeShape.sheet,
            color = VidsizeColor.Surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VidsizeColor.Surface)
                        .statusBarsPadding()
                        .padding(horizontal = Space.gutter, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAction(
                        icon = R.drawable.ic_arrow_back,
                        contentDescription = title,
                        onClick = onDismiss,
                    )
                    Text(
                        text = title,
                        modifier = Modifier.padding(start = Space.sm),
                        style = VidsizeType.screenTitle,
                        color = VidsizeColor.Ink,
                    )
                }

                AndroidView(
                    factory = { webView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}
