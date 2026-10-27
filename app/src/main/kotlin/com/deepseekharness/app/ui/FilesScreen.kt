package com.deepseekharness.app.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.deepseekharness.app.backup.BackupControl
import com.deepseekharness.app.data.WorkspaceFileExports
import com.deepseekharness.app.util.GuestPaths
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiText
import com.deepseekharness.app.util.WorkspaceDocumentIds
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Cut
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun t(zh: String, en: String) = UiText.choose(zh, en)

/** 一个文件或目录，即 DshaDocumentsProvider 返回的一行。 */
class FileEntry(
  val id: String,
  val name: String,
  val mime: String,
  val size: Long,
  val modified: Long,
  val flags: Int,
) {
  val directory get() = mime == Document.MIME_TYPE_DIR
  val writable get() = flags and Document.FLAG_SUPPORTS_WRITE != 0
}

/**
 * 文件操作全部经过 HYDSH 自己的 DocumentsProvider：路径校验、软链接不跟随、维护屏障都在那里，
 * 这里不直接碰宿主路径。
 */
object FileStore {
  /** 文本编辑上限；更大的文件请保存到手机后用其他应用打开。 */
  const val TEXT_LIMIT = 1L shl 20

  private val COLUMNS =
    arrayOf(
      Document.COLUMN_DOCUMENT_ID,
      Document.COLUMN_DISPLAY_NAME,
      Document.COLUMN_MIME_TYPE,
      Document.COLUMN_SIZE,
      Document.COLUMN_LAST_MODIFIED,
      Document.COLUMN_FLAGS,
    )

  private val TEXT_EXTENSIONS =
    ("txt md markdown json jsonc json5 js mjs cjs ts tsx jsx py sh bash zsh fish yaml yml toml ini conf cfg " +
        "env xml html htm css scss less java kt kts c h cc cpp hpp go rs rb php lua sql csv tsv log gradle " +
        "properties vue svelte diff patch lock service rc")
      .split(' ')
      .toSet()

  fun uri(context: Context, id: String): Uri =
    DocumentsContract.buildDocumentUri(WorkspaceFileExports.authority(context), id)

  fun list(context: Context, dir: String): List<FileEntry> =
    context.contentResolver
      .query(
        DocumentsContract.buildChildDocumentsUri(WorkspaceFileExports.authority(context), dir),
        COLUMNS,
        null,
        null,
        null,
      )
      ?.use { cursor -> buildList { while (cursor.moveToNext()) add(row(cursor)) } }
      ?: throw IOException(t("目录暂时无法读取", "The folder cannot be read right now"))

  fun stat(context: Context, id: String): FileEntry =
    context.contentResolver.query(uri(context, id), COLUMNS, null, null, null)?.use {
      if (it.moveToFirst()) row(it) else null
    } ?: throw IOException(t("文件不存在", "File not found"))

  private fun row(c: android.database.Cursor) =
    FileEntry(
      c.getString(0),
      c.getString(1) ?: "",
      c.getString(2) ?: "application/octet-stream",
      if (c.isNull(3)) -1 else c.getLong(3),
      if (c.isNull(4)) 0 else c.getLong(4),
      if (c.isNull(5)) 0 else c.getInt(5),
    )

  fun isText(e: FileEntry): Boolean {
    val ext = e.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return e.mime.startsWith("text/") ||
      ext in TEXT_EXTENSIONS ||
      e.mime in setOf("application/json", "application/xml", "application/javascript", "application/x-sh") ||
      // .bashrc、.gitignore 这类无扩展名的点文件
      (e.name.startsWith(".") && e.name.indexOf('.', 1) < 0) ||
      e.name in setOf("Dockerfile", "Makefile", "LICENSE", "README")
  }

  fun isImage(e: FileEntry) = e.mime.startsWith("image/") && e.mime != "image/svg+xml"

  fun create(context: Context, dir: String, name: String, folder: Boolean): String {
    val made =
      DocumentsContract.createDocument(
        context.contentResolver,
        uri(context, dir),
        if (folder) Document.MIME_TYPE_DIR else "application/octet-stream",
        name,
      ) ?: throw IOException(t("无法创建", "Could not create"))
    return DocumentsContract.getDocumentId(made)
  }

  fun rename(context: Context, id: String, name: String) {
    DocumentsContract.renameDocument(context.contentResolver, uri(context, id), name)
  }

  fun delete(context: Context, id: String) {
    DocumentsContract.deleteDocument(context.contentResolver, uri(context, id))
  }

  /** 同一存储内改名式移动；目标目录已有同名项时报错，不覆盖。 */
  fun move(context: Context, e: FileEntry, target: String) {
    val parent = WorkspaceDocumentIds.parent(e.id)
    if (parent == target) return
    if (Build.VERSION.SDK_INT >= 24)
      DocumentsContract.moveDocument(context.contentResolver, uri(context, e.id), uri(context, parent), uri(context, target))
        ?: throw IOException(t("移动失败", "Move failed"))
    else {
      copy(context, e, target)
      delete(context, e.id)
    }
  }

  /** 复制到目标目录；同名时由 provider 自动加 (1)、(2) 后缀。软链接按目标内容复制。 */
  fun copy(context: Context, e: FileEntry, target: String, depth: Int = 0) {
    if (target == e.id || target.startsWith(e.id + "/"))
      throw IOException(t("不能复制到自身内部", "Cannot copy a folder into itself"))
    // ponytail: 层级上限防软链接成环；需要更深时改成按真实路径去重。
    if (depth > 32) throw IOException(t("目录层级过深", "Folder nesting is too deep"))
    if (e.directory) {
      val made = create(context, target, e.name, true)
      for (child in list(context, e.id)) copy(context, child, made, depth + 1)
      return
    }
    val made = create(context, target, e.name, false)
    val resolver = context.contentResolver
    (resolver.openInputStream(uri(context, e.id)) ?: throw IOException(e.name)).use { input ->
      (resolver.openOutputStream(uri(context, made), "w") ?: throw IOException(e.name)).use { input.copyTo(it) }
    }
  }

  /** 从手机（系统文件选择器）导入到当前目录。 */
  fun import(context: Context, source: Uri, dir: String) {
    val resolver = context.contentResolver
    val name =
      resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
      }?.replace('/', '_')?.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "imported"
    val made = create(context, dir, name, false)
    (resolver.openInputStream(source) ?: throw IOException(name)).use { input ->
      (resolver.openOutputStream(uri(context, made), "w") ?: throw IOException(name)).use { input.copyTo(it) }
    }
  }

  fun errorText(error: Throwable): String {
    val message = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    return SensitiveData.redact(message.take(300))
  }
}

/** 文件页状态；跨旋转保留。所有文件操作排在同一个后台线程上，依次执行。 */
class FilesModel : ViewModel() {
  var dir by mutableStateOf("")
  var entries by mutableStateOf(emptyList<FileEntry>())
  var loading by mutableStateOf(false)
  var busy by mutableStateOf(false)
  var message by mutableStateOf("")
  var sort by mutableIntStateOf(0)
  var hidden by mutableStateOf(false)
  var selected by mutableStateOf(emptySet<String>())
  var clip by mutableStateOf(emptyList<FileEntry>())
  var cut by mutableStateOf(false)
  val history = mutableStateListOf<String>()
  var saving: FileEntry? = null
  private val worker = Executors.newSingleThreadExecutor()
  private val main = Handler(Looper.getMainLooper())
  private var generation = 0

  fun open(context: Context, id: String, remember: Boolean = true) {
    if (remember && dir.isNotEmpty() && dir != id) history.add(dir)
    if (dir != id) selected = emptySet()
    dir = id
    reload(context)
  }

  /** 依次尝试，打开第一个能读的目录（工作区还没建好时退到 Documents、主目录）。 */
  fun openFirst(context: Context, candidates: List<String>) {
    val app = context.applicationContext
    val ticket = ++generation
    loading = true
    worker.execute {
      var opened: Pair<String, List<FileEntry>>? = null
      var failure: Throwable? = null
      for (id in candidates) {
        val result = runCatching { FileStore.list(app, id) }
        result.onSuccess { opened = id to it }.onFailure { failure = it }
        if (opened != null) break
      }
      main.post {
        if (ticket != generation) return@post
        loading = false
        val (id, list) = opened ?: return@post run { message = failure?.let(FileStore::errorText) ?: "" }
        if (dir.isNotEmpty() && dir != id) history.add(dir)
        if (dir != id) selected = emptySet()
        dir = id
        entries = list
        if (!busy) message = ""
      }
    }
  }

  fun reload(context: Context) {
    val app = context.applicationContext
    val target = dir
    val ticket = ++generation
    loading = true
    worker.execute {
      val result = runCatching { FileStore.list(app, target) }
      main.post {
        if (ticket != generation) return@post
        loading = false
        result
          .onSuccess {
            entries = it
            if (!busy) message = ""
          }
          .onFailure {
            entries = emptyList()
            message = FileStore.errorText(it)
          }
      }
    }
  }

  /** 后台执行一项操作，结束后刷新目录；失败原因显示在列表上方。 */
  fun run(context: Context, label: String, work: (Context) -> Unit) {
    val app = context.applicationContext
    busy = true
    message = label
    worker.execute {
      val result = runCatching { work(app) }
      main.post {
        busy = false
        message = result.exceptionOrNull()?.let(FileStore::errorText) ?: ""
        reload(app)
      }
    }
  }

  override fun onCleared() {
    worker.shutdown()
  }
}

/** 底部第五个标签「文件」：浏览、新建、重命名、删除、复制移动、导入、保存到手机、分享，文本直接编辑。 */
class FilesFragment : Fragment(), OwnTopBar {
  private lateinit var model: FilesModel

  private val back =
    object : OnBackPressedCallback(false) {
      override fun handleOnBackPressed() {
        if (model.selected.isNotEmpty()) model.selected = emptySet()
        else if (model.history.isNotEmpty()) model.open(requireContext(), model.history.removeAt(model.history.lastIndex), false)
      }
    }

  private val importer =
    registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isEmpty()) return@registerForActivityResult
      val dir = model.dir
      model.run(requireContext(), t("正在导入 %s 项…", "Importing %s items…").format(uris.size)) { app ->
        for (uri in uris) FileStore.import(app, uri, dir)
      }
    }

  private val saver =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val entry = model.saving
      model.saving = null
      val destination = result.data?.data
      if (entry == null || destination == null || result.resultCode != android.app.Activity.RESULT_OK)
        return@registerForActivityResult
      model.run(requireContext(), t("正在保存 %s…", "Saving %s…").format(entry.name)) { app ->
        WorkspaceFileExports.save(
          app,
          WorkspaceFileExports.Source(entry.id, entry.name, entry.mime, entry.size, entry.modified),
          destination,
          BackupControl(null),
        )
      }
    }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
    model = ViewModelProvider(this)[FilesModel::class.java]
    if (model.dir.isEmpty()) model.openFirst(requireContext(), WORKSPACE_CANDIDATES)
    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, back)
    return composePage(requireContext()) {
      SideEffect { updateBack() }
      FilesScreen(model, this)
    }
  }

  override fun onResume() {
    super.onResume()
    updateBack()
    // 回到这个标签时刷新：AI 或终端可能在别处改过文件。
    if (model.dir.isNotEmpty() && !model.busy) model.reload(requireContext())
  }

  override fun onPause() {
    super.onPause()
    back.isEnabled = false
  }

  private fun updateBack() {
    back.isEnabled = isResumed && (model.selected.isNotEmpty() || model.history.isNotEmpty())
  }

  companion object {
    /** DSH 实际的默认工作区（启动时 DSHA_WORKSPACE_DOCUMENTS=/root/Documents），没有时依次退回。 */
    val WORKSPACE = GuestPaths.ROOT_RELATIVE + "/root/Documents/deepseek-harness/default-workspace"
    val WORKSPACE_CANDIDATES =
      listOf(WORKSPACE, GuestPaths.ROOT_RELATIVE + "/root/Documents", GuestPaths.ROOT_RELATIVE + "/root")
  }

  fun openEntry(e: FileEntry) {
    val context = requireContext()
    when {
      e.directory -> model.open(context, e.id)
      FileStore.isImage(e) || FileStore.isText(e) -> FileEditorActivity.open(context, e, false)
      else ->
        choose(
          e.name,
          listOf(
            t("以文本打开", "Open as text") to { FileEditorActivity.open(context, e, true) },
            t("用其他应用打开", "Open with…") to { openWith(e) },
            t("保存到手机", "Save to phone") to { saveToPhone(e) },
            t("分享", "Share") to { share(listOf(e)) },
          ),
        )
    }
  }

  fun choose(title: String, items: List<Pair<String, () -> Unit>>) {
    DshaDialogBuilder(requireContext())
      .setTitle(title)
      .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
      .show()
  }

  fun create(folder: Boolean) {
    askName(
      requireContext(),
      if (folder) t("新建文件夹", "New folder") else t("新建文件", "New file"),
      if (folder) t("新建文件夹", "New folder") else "untitled.txt",
    ) { name ->
      val dir = model.dir
      model.run(requireContext(), t("正在创建…", "Creating…")) { app -> FileStore.create(app, dir, name, folder) }
    }
  }

  fun importFiles() {
    runCatching { importer.launch(arrayOf("*/*")) }
      .onFailure { model.message = FileStore.errorText(it) }
  }

  fun rename(e: FileEntry) {
    askName(requireContext(), t("重命名", "Rename"), e.name) { name ->
      if (name == e.name) return@askName
      model.selected = emptySet()
      model.run(requireContext(), t("正在重命名…", "Renaming…")) { app -> FileStore.rename(app, e.id, name) }
    }
  }

  fun delete(items: List<FileEntry>) {
    DshaDialogBuilder(requireContext())
      .setTitle(if (items.size == 1) t("删除 %s？", "Delete %s?").format(items[0].name) else t("删除 %s 项？", "Delete %s items?").format(items.size))
      .setMessage(t("删除后无法恢复。文件夹会连同里面的内容一起删除。", "This cannot be undone. Folders are deleted with their contents."))
      .setPositiveButton(t("删除", "Delete")) { _, _ ->
        model.selected = emptySet()
        model.run(requireContext(), t("正在删除…", "Deleting…")) { app -> items.forEach { FileStore.delete(app, it.id) } }
      }
      .setNegativeButton(t("取消", "Cancel"), null)
      .show()
  }

  fun paste() {
    val items = model.clip
    val cut = model.cut
    val dir = model.dir
    model.clip = emptyList()
    model.run(requireContext(), if (cut) t("正在移动…", "Moving…") else t("正在复制…", "Copying…")) { app ->
      for (e in items) if (cut) FileStore.move(app, e, dir) else FileStore.copy(app, e, dir)
    }
  }

  fun saveToPhone(e: FileEntry) {
    model.saving = e
    runCatching {
      saver.launch(
        Intent(Intent.ACTION_CREATE_DOCUMENT)
          .addCategory(Intent.CATEGORY_OPENABLE)
          .setType(e.mime)
          .putExtra(Intent.EXTRA_TITLE, e.name)
      )
    }.onFailure {
      model.saving = null
      model.message = FileStore.errorText(it)
    }
  }

  fun share(items: List<FileEntry>) {
    val context = requireContext()
    val uris = items.map { FileStore.uri(context, it.id) }
    val send =
      if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
      else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    send.type = if (items.size == 1) items[0].mime else "*/*"
    send.clipData = ClipData(items[0].name, arrayOf(send.type), ClipData.Item(uris[0])).apply {
      uris.drop(1).forEach { addItem(ClipData.Item(it)) }
    }
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
      startActivity(Intent.createChooser(send, t("分享", "Share")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.onFailure { model.message = FileStore.errorText(it) }
  }

  fun openWith(e: FileEntry) {
    val view =
      Intent(Intent.ACTION_VIEW)
        .setDataAndType(FileStore.uri(requireContext(), e.id), e.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { startActivity(Intent.createChooser(view, t("打开方式", "Open with"))) }
      .onFailure { model.message = FileStore.errorText(it) }
  }
}

/** 弹窗里输入一个名字（新建 / 重命名）；默认选中扩展名之前的部分。 */
fun askName(context: Context, title: String, initial: String, done: (String) -> Unit) {
  val value = arrayOf(initial)
  val view =
    composePage(context) {
      val dot = initial.lastIndexOf('.')
      var text by remember {
        mutableStateOf(TextFieldValue(initial, TextRange(0, if (dot > 0) dot else initial.length)))
      }
      val focus = remember { FocusRequester() }
      TextField(
        value = text,
        onValueChange = {
          text = it
          value[0] = it.text
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).focusRequester(focus),
        singleLine = true,
      )
      LaunchedEffect(Unit) { focus.requestFocus() }
    }
  view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
  val dialog =
    DshaDialogBuilder(context)
      .setTitle(title)
      .setView(view)
      .setPositiveButton(t("确定", "OK")) { _, _ ->
        val name = value[0].trim()
        if (name.isEmpty() || name == "." || name == ".." || '/' in name)
          Toast.makeText(context, t("名称无效", "Invalid name"), Toast.LENGTH_SHORT).show()
        else done(name)
      }
      .setNegativeButton(t("取消", "Cancel"), null)
      .show()
  dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}

/** 路径栏：从 “/”（Linux 根）或 “HYDSH”（应用数据根）到当前目录的每一级。 */
private fun crumbs(id: String): List<Pair<String, String>> {
  val out = ArrayList<Pair<String, String>>()
  var current = id
  while (true) {
    val label =
      when (current) {
        "root" -> "HYDSH"
        GuestPaths.ROOT_RELATIVE -> "/"
        else -> current.substringAfterLast('/')
      }
    out.add(0, label to current)
    if (current == "root" || current == GuestPaths.ROOT_RELATIVE) return out
    current = runCatching { WorkspaceDocumentIds.parent(current) }.getOrDefault("root")
  }
}

@Composable
private fun FilesScreen(model: FilesModel, page: FilesFragment) {
  val context = LocalContext.current
  val colors = MiuixTheme.colorScheme
  val selecting = model.selected.isNotEmpty()
  val visible =
    remember(model.entries, model.sort, model.hidden) {
      val shown = if (model.hidden) model.entries else model.entries.filter { !it.name.startsWith(".") }
      val order =
        when (model.sort) {
          1 -> compareByDescending<FileEntry> { it.modified }
          2 -> compareByDescending<FileEntry> { it.size }
          else -> compareBy<FileEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
      shown.sortedWith(compareBy<FileEntry> { !it.directory }.then(order))
    }
  val barInset = with(LocalDensity.current) { GlassBar.contentBottomPx.toDp() }

  Box(Modifier.fillMaxSize()) {
    HyPage(
      title = if (selecting) t("已选择 %s 项", "%s selected").format(model.selected.size) else t("文件", "Files"),
      actions = {
        if (selecting) {
          TopIcon(MiuixIcons.SelectAll, t("全选", "Select all")) {
            model.selected = if (model.selected.size == visible.size) emptySet() else visible.map { it.id }.toSet()
          }
          TopIcon(MiuixIcons.Close, t("取消选择", "Clear selection"), last = true) { model.selected = emptySet() }
        } else {
          TopIcon(MiuixIcons.Add, t("新建", "New")) {
            page.choose(
              t("新建", "New"),
              listOf(
                t("新建文件", "New file") to { page.create(false) },
                t("新建文件夹", "New folder") to { page.create(true) },
                t("从手机导入", "Import from phone") to { page.importFiles() },
              ),
            )
          }
          TopIcon(MiuixIcons.Sort, t("排序与显示", "Sort and view"), last = true) {
            val mark = { on: Boolean -> if (on) "✓ " else "" }
            page.choose(
              t("排序与显示", "Sort and view"),
              listOf(
                mark(model.sort == 0) + t("按名称", "By name") to { model.sort = 0 },
                mark(model.sort == 1) + t("按修改时间", "By date modified") to { model.sort = 1 },
                mark(model.sort == 2) + t("按大小", "By size") to { model.sort = 2 },
                mark(model.hidden) + t("显示隐藏文件", "Show hidden files") to { model.hidden = !model.hidden },
                t("刷新", "Refresh") to { model.reload(context) },
              ),
            )
          }
        }
      },
    ) {
      item { PathBar(model.dir) { model.open(context, it) } }
      item {
        Row(
          Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          for ((label, id) in
            listOf(
              t("工作目录", "Workspace") to FilesFragment.WORKSPACE,
              t("主目录", "Home") to GuestPaths.ROOT_RELATIVE + "/root",
              t("根目录", "Root") to GuestPaths.ROOT_RELATIVE,
              t("应用数据", "App data") to "root",
            )) Chip(label, model.dir == id) {
              if (id == FilesFragment.WORKSPACE) model.openFirst(context, FilesFragment.WORKSPACE_CANDIDATES) else model.open(context, id)
            }
        }
      }
      if (model.message.isNotEmpty() || model.busy)
        item {
          Row(Modifier.padding(horizontal = 28.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (model.busy) {
              InfiniteProgressIndicator(color = colors.primary)
              Spacer(Modifier.width(12.dp))
            }
            Text(model.message, color = if (model.busy) colors.onSurfaceVariantSummary else colors.error, fontSize = 14.sp)
          }
        }
      if (model.loading && model.entries.isEmpty())
        item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { InfiniteProgressIndicator(color = colors.primary) } }
      else if (visible.isEmpty() && model.message.isEmpty()) item { HyNote(t("这个文件夹是空的。", "This folder is empty.")) }
      itemsIndexed(visible, key = { _, e -> e.id }) { index, e ->
        FileRow(
          e,
          first = index == 0,
          last = index == visible.lastIndex,
          selecting = selecting,
          checked = e.id in model.selected,
          onClick = {
            if (selecting) model.selected = model.selected.toggle(e.id)
            else if (!model.busy) page.openEntry(e)
          },
          onLongClick = { model.selected = model.selected.toggle(e.id) },
        )
      }
      // 给底部操作条留位置。
      item { Spacer(Modifier.height(96.dp)) }
    }

    val bar = Modifier.align(Alignment.BottomCenter).padding(bottom = barInset + 12.dp).padding(horizontal = 12.dp)
    if (selecting) {
      val chosen = visible.filter { it.id in model.selected }
      val single = chosen.singleOrNull()
      Card(bar) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
          BarAction(MiuixIcons.Copy, t("复制", "Copy")) {
            model.clip = chosen
            model.cut = false
            model.selected = emptySet()
          }
          BarAction(MiuixIcons.Cut, t("移动", "Move")) {
            model.clip = chosen
            model.cut = true
            model.selected = emptySet()
          }
          BarAction(MiuixIcons.Rename, t("重命名", "Rename"), enabled = single != null) { single?.let(page::rename) }
          BarAction(MiuixIcons.Delete, t("删除", "Delete")) { page.delete(chosen) }
          BarAction(MiuixIcons.More, t("更多", "More")) {
            val files = chosen.filter { !it.directory }
            val more = ArrayList<Pair<String, () -> Unit>>()
            if (files.isNotEmpty()) more += t("分享", "Share") to { page.share(files) }
            if (single != null && !single.directory) {
              more += t("保存到手机", "Save to phone") to { page.saveToPhone(single) }
              more += t("用其他应用打开", "Open with…") to { page.openWith(single) }
              more += t("以文本打开", "Open as text") to { FileEditorActivity.open(context, single, true) }
            }
            if (more.isEmpty()) model.message = t("文件夹不能分享或单独保存。", "Folders cannot be shared or saved individually.")
            else page.choose(single?.name ?: t("%s 项", "%s items").format(chosen.size), more)
          }
        }
      }
    } else if (model.clip.isNotEmpty()) {
      Card(bar) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(
            if (model.cut) t("待移动 %s 项", "%s to move").format(model.clip.size) else t("已复制 %s 项", "%s copied").format(model.clip.size),
            Modifier.weight(1f),
            fontSize = 15.sp,
          )
          TextButton(t("取消", "Cancel"), onClick = { model.clip = emptyList() })
          Spacer(Modifier.width(8.dp))
          TextButton(
            t("粘贴到这里", "Paste here"),
            onClick = page::paste,
            enabled = !model.busy,
            colors = ButtonDefaults.textButtonColorsPrimary(),
          )
        }
      }
    }
  }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

@Composable
private fun TopIcon(icon: ImageVector, label: String, last: Boolean = false, onClick: () -> Unit) {
  IconButton(onClick = onClick, modifier = if (last) Modifier.padding(end = 12.dp) else Modifier) {
    Icon(icon, contentDescription = label)
  }
}

@Composable
private fun PathBar(dir: String, open: (String) -> Unit) {
  val scroll = rememberScrollState()
  LaunchedEffect(dir) { scroll.scrollTo(scroll.maxValue) }
  val colors = MiuixTheme.colorScheme
  val parts = crumbs(dir)
  Row(
    Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 20.dp).padding(bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    parts.forEachIndexed { i, (label, id) ->
      val current = i == parts.lastIndex
      Text(
        label,
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !current) { open(id) }.padding(horizontal = 6.dp, vertical = 4.dp),
        color = if (current) colors.onSurface else colors.primary,
        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
        fontSize = 14.sp,
      )
      if (!current) Text("›", color = colors.onSurfaceVariantSummary, fontSize = 14.sp)
    }
  }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
  val colors = MiuixTheme.colorScheme
  Text(
    label,
    Modifier.clip(RoundedCornerShape(50))
      .background(if (active) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer)
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    color = if (active) colors.primary else colors.onSurface,
    fontSize = 14.sp,
  )
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
  e: FileEntry,
  first: Boolean,
  last: Boolean,
  selecting: Boolean,
  checked: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val context = LocalContext.current
  val colors = MiuixTheme.colorScheme
  val r = 16.dp
  val shape = RoundedCornerShape(if (first) r else 0.dp, if (first) r else 0.dp, if (last) r else 0.dp, if (last) r else 0.dp)
  val meta =
    (if (e.modified > 0) dateFormat.format(Date(e.modified)) else "") +
      (if (!e.directory && e.size >= 0) "  ·  " + Formatter.formatFileSize(context, e.size) else "")
  Row(
    Modifier.padding(horizontal = 12.dp)
      .fillMaxWidth()
      .clip(shape)
      .background(if (checked) colors.primary.copy(alpha = 0.12f) else colors.surfaceContainer)
      .combinedClickable(onClick = onClick, onLongClick = onLongClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      when {
        e.directory -> MiuixIcons.Folder
        FileStore.isImage(e) -> MiuixIcons.Image
        FileStore.isText(e) -> MiuixIcons.Notes
        else -> MiuixIcons.File
      },
      contentDescription = null,
      modifier = Modifier.size(26.dp),
      tint = if (e.directory) colors.primary else colors.onSurfaceVariantSummary,
    )
    Column(Modifier.weight(1f).padding(start = 16.dp)) {
      Text(e.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
      if (meta.isNotEmpty()) Text(meta, color = colors.onSurfaceVariantSummary, fontSize = 13.sp, maxLines = 1)
    }
    if (selecting)
      Checkbox(
        state = if (checked) ToggleableState.On else ToggleableState.Off,
        onClick = onClick,
        modifier = Modifier.padding(start = 12.dp),
      )
  }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
  val colors = MiuixTheme.colorScheme
  val tint = if (enabled) colors.onSurface else colors.onSurfaceVariantSummary.copy(alpha = 0.5f)
  Column(
    Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
    Text(label, color = tint, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
  }
}
