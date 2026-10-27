package com.deepseekharness.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.R
import com.deepseekharness.app.util.UiText
import com.deepseekharness.app.util.WorkspaceDocumentIds
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun t(zh: String, en: String) = UiText.choose(zh, en)

/**
 * 文件页打开的查看/编辑页：UTF-8 文本可编辑保存（1 MB 以内），图片只看。
 * 保存前核对修改时间，文件在打开后被 AI 或终端改过时先提醒，不静默覆盖。
 */
class FileEditorActivity : AppCompatActivity() {
  companion object {
    fun open(context: Context, e: FileEntry, asText: Boolean) {
      context.startActivity(
        Intent(context, FileEditorActivity::class.java)
          .putExtra("id", e.id)
          .putExtra("name", e.name)
          .putExtra("image", !asText && FileStore.isImage(e))
      )
    }
  }

  private lateinit var id: String
  private var name = ""

  /** 0 读取中 · 1 文本 · 2 图片 · 3 出错 */
  private var mode by mutableIntStateOf(0)
  private var error by mutableStateOf("")
  private var text by mutableStateOf(TextFieldValue(""))
  private var original by mutableStateOf("")
  private var picture by mutableStateOf<ImageBitmap?>(null)
  private var saving by mutableStateOf(false)
  private var writable = false
  private var crlf = false
  private var modified = 0L

  private val dirty get() = mode == 1 && text.text != original

  override fun onCreate(saved: Bundle?) {
    super.onCreate(saved)
    id = runCatching { WorkspaceDocumentIds.checked(intent.getStringExtra("id")) }.getOrElse {
      finish()
      return
    }
    name = intent.getStringExtra("name") ?: id.substringAfterLast('/')
    setContentView(composePage(this) { Screen() })
    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = leave()
      },
    )
    load(intent.getBooleanExtra("image", false))
  }

  private fun load(image: Boolean) {
    Thread(
        {
          val result = runCatching {
            val e = FileStore.stat(this, id)
            if (image) decodeImage() else readText(e)
          }
          runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            result.onFailure {
              error = FileStore.errorText(it)
              mode = 3
            }
          }
        },
        "file-editor-load",
      )
      .start()
  }

  private fun decodeImage() {
    val uri = FileStore.uri(this, id)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val bitmap =
      contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
      } ?: throw IOException(t("无法显示这张图片，可在文件页用其他应用打开。", "Cannot show this image. Open it with another app from Files."))
    runOnUiThread {
      picture = bitmap.asImageBitmap()
      mode = 2
    }
  }

  private fun readText(e: FileEntry) {
    val tooBig = t("文件超过 1 MB，请保存到手机后用其他应用打开。", "The file is over 1 MB. Save it to your phone and open it with another app.")
    if (e.size > FileStore.TEXT_LIMIT) throw IOException(tooBig)
    val bytes =
      (contentResolver.openInputStream(FileStore.uri(this, id)) ?: throw IOException(name)).use { input ->
        // 最多多读 1 字节，用来判断是否超限（大小在打开后可能变了）。
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() <= FileStore.TEXT_LIMIT) {
          val n = input.read(buffer)
          if (n < 0) break
          out.write(buffer, 0, n)
        }
        out.toByteArray()
      }
    if (bytes.size > FileStore.TEXT_LIMIT) throw IOException(tooBig)
    if (bytes.take(8000).any { it == 0.toByte() })
      throw IOException(t("这不是文本文件，不能在这里编辑。", "This is not a text file and cannot be edited here."))
    val content =
      try {
        StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString()
      } catch (notUtf8: CharacterCodingException) {
        throw IOException(t("文件不是 UTF-8 编码，为避免乱码不在这里编辑。", "The file is not UTF-8, so it is not edited here to avoid corrupting it."))
      }
    val windows = content.contains("\r\n")
    val normalized = if (windows) content.replace("\r\n", "\n") else content
    runOnUiThread {
      crlf = windows
      writable = e.writable
      modified = e.modified
      original = normalized
      text = TextFieldValue(normalized)
      mode = 1
    }
  }

  /** force：用户确认覆盖他人修改；then：保存成功后要做的事（如离开）。 */
  private fun save(force: Boolean = false, then: () -> Unit = {}) {
    if (saving || !writable) return
    saving = true
    val content = text.text
    val expected = modified
    Thread(
        {
          val result = runCatching {
            if (!force && FileStore.stat(this, id).modified != expected) return@runCatching null
            val bytes = (if (crlf) content.replace("\n", "\r\n") else content).toByteArray(StandardCharsets.UTF_8)
            (contentResolver.openOutputStream(FileStore.uri(this, id), "wt") ?: throw IOException(name)).use {
              it.write(bytes)
            }
            FileStore.stat(this, id).modified
          }
          runOnUiThread {
            saving = false
            if (isFinishing || isDestroyed) return@runOnUiThread
            result
              .onSuccess { stamp ->
                if (stamp == null) conflict(then)
                else {
                  modified = stamp
                  original = content
                  Toast.makeText(this, t("已保存", "Saved"), Toast.LENGTH_SHORT).show()
                  then()
                }
              }
              .onFailure {
                Toast.makeText(this, t("保存失败：", "Save failed: ") + FileStore.errorText(it), Toast.LENGTH_LONG).show()
              }
          }
        },
        "file-editor-save",
      )
      .start()
  }

  private fun conflict(then: () -> Unit) {
    DshaDialogBuilder(this)
      .setTitle(t("文件已被修改", "File changed"))
      .setMessage(
        t(
          "打开后，这个文件被其他程序（如 AI 或终端）改过。覆盖保存会丢掉对方的修改。",
          "Another program (such as the AI or a terminal) changed this file after you opened it. Overwriting discards those changes.",
        )
      )
      .setPositiveButton(t("覆盖保存", "Overwrite")) { _, _ -> save(true, then) }
      .setNegativeButton(t("取消", "Cancel"), null)
      .show()
  }

  private fun leave() {
    if (!dirty) {
      finish()
      return
    }
    DshaDialogBuilder(this)
      .setTitle(t("保存修改？", "Save changes?"))
      .setPositiveButton(t("保存", "Save")) { _, _ -> save { finish() } }
      .setNeutralButton(t("不保存", "Discard")) { _, _ -> finish() }
      .setNegativeButton(t("取消", "Cancel"), null)
      .show()
  }

  @androidx.compose.runtime.Composable
  private fun Screen() {
    val colors = MiuixTheme.colorScheme
    Scaffold(
      topBar = {
        SmallTopAppBar(
          title = (if (dirty) "● " else "") + name + if (mode == 1 && !writable) t("（只读）", " (read-only)") else "",
          modifier = Modifier.consumeWindowInsets(WindowInsets.systemBars),
          defaultWindowInsetsPadding = false,
          navigationIcon = {
            IconButton(onClick = ::leave, modifier = Modifier.padding(start = 12.dp)) {
              Icon(ImageVector.vectorResource(R.drawable.ic_ui_back), contentDescription = t("返回", "Back"))
            }
          },
          actions = {
            if (mode == 1 && writable)
              IconButton(onClick = { save() }, enabled = dirty && !saving, modifier = Modifier.padding(end = 12.dp)) {
                Icon(MiuixIcons.Ok, contentDescription = t("保存", "Save"))
              }
          },
        )
      },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
      Box(Modifier.padding(padding).fillMaxSize()) {
        when (mode) {
          0 -> InfiniteProgressIndicator(Modifier.align(Alignment.Center), color = colors.primary)
          1 ->
            BasicTextField(
              value = text,
              onValueChange = { text = it },
              readOnly = !writable,
              textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = colors.onSurface),
              cursorBrush = SolidColor(colors.primary),
              modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            )
          2 ->
            picture?.let {
              Image(it, contentDescription = name, modifier = Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
            }
          else ->
            Text(
              error,
              Modifier.align(Alignment.Center).padding(32.dp),
              color = colors.onSurfaceVariantSummary,
              textAlign = TextAlign.Center,
            )
        }
      }
    }
  }
}
