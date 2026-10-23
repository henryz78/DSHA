package com.deepseekharness.app.ui;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.deepseekharness.app.R;
import com.deepseekharness.app.backup.BackupControl;
import com.deepseekharness.app.backup.DocumentStreams;
import com.deepseekharness.app.core.ConfigStore;
import com.deepseekharness.app.core.RuntimeTasks;
import com.deepseekharness.app.data.WorkspaceFileExports;
import com.deepseekharness.app.util.SensitiveData;
import com.deepseekharness.app.util.UiText;
import com.deepseekharness.app.util.WorkspaceDocumentIds;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only work-file browser; selected files can be saved with SAF or shared without root. */
public final class WorkspaceFilesActivity extends AppCompatActivity {
  private static final String SAVED_DIRECTORY = "workspace_document_id";
  private static final String PERSONAL_DIRECTORY = "linux/ubuntu/root/Documents";
  private static final int PAGE_SIZE = 200;
  private static final String[] COLUMNS = {
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    OpenableColumns.DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    OpenableColumns.SIZE,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED
  };

  private static final class Entry {
    final String id, name, mime;
    final long size, modified;
    final boolean directory;

    Entry(Cursor cursor) throws IOException {
      id = WorkspaceDocumentIds.checked(cursor.getString(0));
      name = cursor.getString(1);
      mime = cursor.getString(2);
      directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
      size = cursor.isNull(3) ? -1 : cursor.getLong(3);
      modified = cursor.isNull(4) ? 0 : cursor.getLong(4);
      if (name == null || mime == null || !directory && size < 0)
        throw new IOException("WORKSPACE_SOURCE_METADATA");
    }

    WorkspaceFileExports.Source source() throws IOException {
      return new WorkspaceFileExports.Source(id, name, mime, size, modified);
    }
  }

  private static final class Page {
    final String id;
    final List<Entry> entries;
    final int offset;
    final boolean more;

    Page(String id, List<Entry> entries, int offset, boolean more) {
      this.id = id;
      this.entries = entries;
      this.offset = offset;
      this.more = more;
    }
  }

  private enum Phase {
    IDLE,
    PERSONAL_FILES,
    LOADING,
    EMPTY,
    SAVING,
    SAVED,
    CANCELLED,
    ERROR,
    RESTARTED,
    SHARE_PICKER
  }

  private static final class Notice {
    final Phase phase;
    final String detail;

    Notice(Phase phase, String detail) {
      this.phase = phase;
      this.detail = detail;
    }
  }

  public static final class Model extends ViewModel {
    final MutableLiveData<Page> page = new MutableLiveData<>();
    final MutableLiveData<Notice> notice = new MutableLiveData<>(new Notice(Phase.IDLE, ""));
    final AtomicBoolean working = new AtomicBoolean();
    volatile BackupControl control;
    String directory;
    int offset;
    WorkspaceFileExports.Source pending;
    boolean choosingDestination;

    @Override
    protected void onCleared() {
      if (control != null) control.cancel();
    }
  }

  private Model model;
  private HyGroup files;
  private HyLine location, status;
  private HyLine workspace, root, up, refresh, cancel;
  private HyLine previousPage, nextPage;
  private final java.util.List<HyLine> fileLines = new ArrayList<>();
  private String workspaceId;

  /** Shared native entry; no extras or elevated permissions are required. */
  public static Intent intent(Context context) {
    return new Intent(context, WorkspaceFilesActivity.class);
  }

  private final ActivityResultLauncher<Intent> savePicker =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            WorkspaceFileExports.Source selected = model.pending;
            model.pending = null;
            model.choosingDestination = false;
            Uri destination =
                result.getResultCode() == RESULT_OK && result.getData() != null
                    ? result.getData().getData()
                    : null;
            if (destination == null) {
              model.notice.setValue(new Notice(Phase.CANCELLED, ""));
            } else if (selected == null) {
              model.notice.setValue(new Notice(Phase.RESTARTED, ""));
            } else {
              save(selected, destination);
            }
          });

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    model = new ViewModelProvider(this).get(Model.class);
    try {
      workspaceId = WorkspaceDocumentIds.forGuestDirectory(new ConfigStore(this).getWorkdir());
    } catch (IOException invalid) {
      workspaceId = "linux/ubuntu/root";
    }
    if (model.directory == null) {
      String restored = saved == null ? null : saved.getString(SAVED_DIRECTORY);
      try {
        model.directory = restored == null ? workspaceId : WorkspaceDocumentIds.checked(restored);
      } catch (IOException invalid) {
        model.directory = workspaceId;
      }
    }

    var screen = new HyScreenModel(UiText.choose("作品文件导出", "Export work files"));
    screen.note(
        UiText.choose(
            "选择工作目录里的作品文件，保存到手机或分享给其他应用。无需 Root；这是普通文件导出，完整数据备份另有入口。",
            "Choose a file from your workspace to save on the device or share with another app. No root is needed. Full data backups have a separate entry."));
    var where = screen.section("");
    location = where.selectable("");
    status = where.selectable("");
    cancel =
        where.button(
            UiText.choose("取消导出", "Cancel export"),
            false,
            () -> {
              BackupControl control = model.control;
              if (control != null) control.cancel();
            });
    var navigation = screen.section(UiText.choose("跳转", "Go to"));
    workspace =
        navigation.arrow(
            UiText.choose("工作目录", "Workspace"), "", R.drawable.ic_ui2_folder, () -> load(workspaceId, 0, true));
    root = navigation.arrow(UiText.choose("应用根目录", "App root"), "", R.drawable.ic_ui2_box, () -> load("root"));
    up = navigation.arrow(UiText.choose("上一级", "Up"), "", R.drawable.ic_ui_back, this::parent);
    refresh =
        navigation.arrow(
            UiText.choose("刷新", "Refresh"), "", R.drawable.ic_recovery_refresh, () -> load(model.directory));
    files = screen.section(UiText.choose("文件", "Files"));
    var pagination = screen.section("");
    previousPage =
        pagination.button(
            UiText.choose("上一页", "Previous page"),
            false,
            () -> load(model.directory, Math.max(0, model.offset - PAGE_SIZE)));
    nextPage =
        pagination.button(
            UiText.choose("下一页", "Next page"),
            false,
            () -> load(model.directory, model.offset + PAGE_SIZE));
    setContentView(HyScreenKt.hyScreen(this, screen, true));
    model.page.observe(
        this,
        page -> {
          if (page != null) showEntries(page.entries);
          render();
        });
    model.notice.observe(this, notice -> render());
    if (model.page.getValue() == null && !model.working.get())
      load(model.directory, 0, saved == null && workspaceId.equals(model.directory));
    else render();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putString(SAVED_DIRECTORY, model.directory);
    super.onSaveInstanceState(state);
  }

  private void parent() {
    try {
      load(WorkspaceDocumentIds.parent(model.directory));
    } catch (IOException invalid) {
      load("root");
    }
  }

  private void load(String id) {
    load(id, 0);
  }

  private void load(String id, int offset) {
    load(id, offset, false);
  }

  private void load(String id, int offset, boolean allowWorkspaceFallback) {
    if (model.choosingDestination || !model.working.compareAndSet(false, true)) return;
    final String directory;
    try {
      directory = WorkspaceDocumentIds.checked(id);
    } catch (IOException invalid) {
      model.working.set(false);
      model.notice.setValue(new Notice(Phase.ERROR, invalid.getMessage()));
      return;
    }
    model.directory = directory;
    model.offset = offset;
    model.page.setValue(new Page(directory, List.of(), offset, false));
    model.notice.setValue(new Notice(Phase.LOADING, ""));
    Model active = model;
    Context app = getApplicationContext();
    BackupControl control = new BackupControl(null);
    active.control = control;
    new Thread(
            () -> {
              List<Entry> entries = new ArrayList<>();
              Notice outcome;
              try (RuntimeTasks ignored = RuntimeTasks.begin("作品文件浏览")) {
                String openedDirectory = directory;
                Cursor listing;
                try {
                  listing =
                      DocumentStreams.query(
                          app.getContentResolver(),
                          DocumentsContract.buildChildDocumentsUri(
                              WorkspaceFileExports.authority(app), directory),
                          COLUMNS,
                          control);
                } catch (IOException error) {
                  // Only the initial/configured-workspace action may try this one existing
                  // directory. A cancelled query or manual navigation keeps its own result.
                  if (!allowWorkspaceFallback
                      || offset != 0
                      || control.isCancelled()
                      || error instanceof InterruptedIOException
                      || PERSONAL_DIRECTORY.equals(directory)) throw error;
                  listing =
                      DocumentStreams.query(
                          app.getContentResolver(),
                          DocumentsContract.buildChildDocumentsUri(
                              WorkspaceFileExports.authority(app), PERSONAL_DIRECTORY),
                          COLUMNS,
                          control);
                  openedDirectory = PERSONAL_DIRECTORY;
                }
                try (Cursor cursor = listing) {
                  control.check();
                  cursor.moveToPosition(offset - 1);
                  int visited = 0;
                  while (visited < PAGE_SIZE && cursor.moveToNext()) {
                    control.check();
                    visited++;
                    // The provider can also describe special nodes. Only directories and
                    // regular-file rows with a known byte length belong in this export browser.
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))
                        || !cursor.isNull(3)) entries.add(new Entry(cursor));
                  }
                  boolean more = cursor.moveToNext();
                  entries.sort(
                      Comparator.comparing((Entry entry) -> !entry.directory)
                          .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
                  control.check();
                  active.directory = openedDirectory;
                  active.page.postValue(
                      new Page(openedDirectory, List.copyOf(entries), offset, more));
                  outcome =
                      new Notice(
                          !directory.equals(openedDirectory)
                              ? Phase.PERSONAL_FILES
                              : entries.isEmpty() ? Phase.EMPTY : Phase.IDLE,
                          "");
                }
              } catch (Exception error) {
                outcome =
                    new Notice(
                        control.isCancelled() ? Phase.CANCELLED : Phase.ERROR, detail(error));
              } finally {
                active.control = null;
                active.working.set(false);
              }
              active.notice.postValue(outcome);
            },
            "dsha-work-files-list")
        .start();
  }

  private void actions(Entry entry) {
    final WorkspaceFileExports.Source source;
    try {
      source = entry.source();
    } catch (IOException error) {
      model.notice.setValue(new Notice(Phase.ERROR, detail(error)));
      return;
    }
    new DshaDialogBuilder(this)
        .setTitle(source.name)
        .setMessage(
            UiText.choose(
                "保存原始文件，或选择接收文件的应用。", "Save the original file or choose an app to receive it."))
        .setPositiveButton(
            UiText.choose("保存到…", "Save to…"), (dialog, which) -> chooseDestination(source))
        .setNeutralButton(UiText.choose("分享…", "Share…"), (dialog, which) -> share(source))
        .setNegativeButton(UiText.choose("取消", "Cancel"), null)
        .show();
  }

  private void chooseDestination(WorkspaceFileExports.Source source) {
    if (model.working.get() || model.choosingDestination) return;
    model.pending = source;
    model.choosingDestination = true;
    Intent picker =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(source.mime)
            .putExtra(Intent.EXTRA_TITLE, source.name);
    try {
      savePicker.launch(picker);
      render();
    } catch (RuntimeException unavailable) {
      model.pending = null;
      model.choosingDestination = false;
      model.notice.setValue(new Notice(Phase.ERROR, detail(unavailable)));
    }
  }

  private void save(WorkspaceFileExports.Source source, Uri destination) {
    if (!model.working.compareAndSet(false, true)) return;
    Model active = model;
    Context app = getApplicationContext();
    BackupControl control = new BackupControl(null);
    active.control = control;
    active.notice.setValue(new Notice(Phase.SAVING, source.name));
    new Thread(
            () -> {
              Notice outcome;
              try {
                WorkspaceFileExports.Result result =
                    WorkspaceFileExports.save(app, source, destination, control);
                outcome = new Notice(Phase.SAVED, result.name);
              } catch (Exception error) {
                outcome =
                    new Notice(
                        control.isCancelled() ? Phase.CANCELLED : Phase.ERROR, detail(error));
              } finally {
                active.control = null;
                active.working.set(false);
              }
              active.notice.postValue(outcome);
            },
            "dsha-work-file-export")
        .start();
  }

  private void share(WorkspaceFileExports.Source source) {
    try {
      Uri uri = WorkspaceFileExports.document(this, source.id);
      Intent send =
          new Intent(Intent.ACTION_SEND)
              .setType(source.mime)
              .putExtra(Intent.EXTRA_STREAM, uri)
              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      send.setClipData(
          new ClipData(source.name, new String[] {source.mime}, new ClipData.Item(uri)));
      Intent chooser = Intent.createChooser(send, UiText.choose("分享作品文件", "Share work file"));
      chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      chooser.setClipData(send.getClipData());
      startActivity(chooser);
      model.notice.setValue(new Notice(Phase.SHARE_PICKER, ""));
    } catch (Exception error) {
      model.notice.setValue(new Notice(Phase.ERROR, detail(error)));
    }
  }

  private void render() {
    if (files == null) return;
    boolean working = model.working.get();
    boolean enabled = !working && !model.choosingDestination;
    for (HyLine button : new HyLine[] {workspace, root, up, refresh}) button.setEnabled(enabled);
    up.setEnabled(enabled && !"root".equals(model.directory));
    for (HyLine line : fileLines) line.setEnabled(enabled);
    Page page = model.page.getValue();
    previousPage.setVisible(page != null && page.offset > 0);
    nextPage.setVisible(page != null && page.more);
    previousPage.setEnabled(enabled);
    nextPage.setEnabled(enabled);
    cancel.setVisible(working);
    Notice notice = model.notice.getValue();
    if (notice == null) return;
    String message;
    switch (notice.phase) {
      case PERSONAL_FILES:
        message =
            UiText.choose(
                "配置的工作目录暂不可读取，已打开个人文件目录。",
                "The configured workspace is temporarily unreadable. The personal files folder is open.");
        break;
      case LOADING:
        message = UiText.choose("正在读取目录…", "Reading folder…");
        break;
      case EMPTY:
        message = UiText.choose("这个目录没有可读取的文件。", "This folder has no readable files.");
        break;
      case SAVING:
        message = UiText.format("正在核对并导出：%s", notice.detail);
        break;
      case SAVED:
        message = UiText.format("已保存文件：%s", notice.detail);
        break;
      case CANCELLED:
        message = UiText.choose("已取消文件操作。", "File operation cancelled.");
        break;
      case ERROR:
        message = UiText.format("文件操作未完成：%s\n可刷新目录，或返回上一级查找作品文件。", errorText(notice.detail));
        break;
      case RESTARTED:
        message =
            UiText.choose(
                "应用在选择保存位置期间重新启动，请重新选择文件导出。",
                "The app restarted while choosing a destination. Select the file again.");
        break;
      case SHARE_PICKER:
        message =
            UiText.choose(
                "已打开分享选择器，请在接收应用中完成分享。",
                "The share picker is open. Complete sharing in the receiving app.");
        break;
      default:
        message =
            UiText.choose(
                "点目录继续浏览，点文件选择保存或分享。", "Tap a folder to browse, or a file to save or share.");
    }
    status.setText(message);
    try {
      location.setText(UiText.format("位置：%s", WorkspaceDocumentIds.location(model.directory)));
    } catch (IOException invalid) {
      location.setText("HYDSH");
    }
  }

  private static String detail(Exception error) {
    String message = error.getMessage();
    if (message == null || message.isEmpty()) message = error.getClass().getSimpleName();
    return SensitiveData.redact(message.length() > 500 ? message.substring(0, 500) : message);
  }

  private static String errorText(String detail) {
    switch (detail) {
      case "WORKSPACE_SOURCE_CHANGED":
      case "SOURCE_CHANGED":
        return UiText.choose(
            "文件发生了变化，请等待写入完成后刷新并重试。",
            "The file changed. Wait for writing to finish, then refresh and retry.");
      case "WORKSPACE_EXPORT_VERIFY":
        return UiText.choose(
            "保存后的文件未通过字节核验，请重新选择保存位置。",
            "The saved file failed verification. Choose another destination and retry.");
      case "WORKSPACE_EXPORT_DESTINATION":
        return UiText.choose(
            "请选择手机或其他文件服务中的新文件保存位置。",
            "Choose a new file destination on the device or another file service.");
      case "WORKSPACE_SOURCE_METADATA":
        return UiText.choose(
            "所选项目不是可导出的普通文件。", "The selected item is not an exportable regular file.");
      default:
        return detail;
    }
  }

  /** 文件清单：目录点进去继续浏览，文件弹出保存或分享。 */
  private void showEntries(List<Entry> entries) {
    files.clear();
    fileLines.clear();
    for (Entry entry : entries) {
      String description =
          entry.directory
              ? UiText.choose("目录", "Folder")
              : Formatter.formatFileSize(WorkspaceFilesActivity.this, entry.size);
      fileLines.add(
          files.arrow(
              entry.name,
              description,
              entry.directory ? R.drawable.ic_ui2_folder : R.drawable.ic_recovery_document,
              () -> {
                if (model.working.get() || model.choosingDestination) return;
                if (entry.directory) load(entry.id);
                else actions(entry);
              }));
    }
  }

}
