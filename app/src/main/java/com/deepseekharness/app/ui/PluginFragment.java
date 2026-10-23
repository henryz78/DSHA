package com.deepseekharness.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;

import com.deepseekharness.app.R;
import com.deepseekharness.app.core.PluginRepository;
import com.deepseekharness.app.util.PluginSort;
import com.deepseekharness.app.util.PluginSource;
import com.deepseekharness.app.util.UiStateText;
import com.deepseekharness.app.util.UiText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 插件安装与已装插件管理；耗时任务由 Activity 范围的 Repository 承接。
 *
 * <p>界面由 PluginScreen（miuix）绘制；这里只维护 {@link PluginUiState} 和处理操作。
 */
public class PluginFragment extends Fragment implements OwnTopBar {
  private PluginRepository repository;
  private PluginRepository.State current;
  private View root;
  private final PluginUiState ui = new PluginUiState();
  private boolean market = true;
  private PluginSort.Mode sortOrder = PluginSort.Mode.NAME_ASC;
  private final List<PluginRepository.Item> visibleItems = new ArrayList<>();
  private ArrayList<String> pendingExports = new ArrayList<>();
  private android.net.Uri pendingImport;

  /** 应用级 Repository 拥有失效修订号；页面只记住自己已渲染的修订。 */
  private long refreshedRevision = -1;

  private final android.os.Handler refreshHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable refreshInvalidated = this::refreshInstalledIfNeeded;
  private String environmentNotice;

  /** 只在首次读取或安全启动失效后同步一次；等待中的轮询只读内存状态。 */
  private void refreshInstalledIfNeeded() {
    refreshHandler.removeCallbacks(refreshInvalidated);
    if (root == null || !isResumed() || market) return;
    if (pluginRefreshBlocked()) {
      refreshHandler.postDelayed(refreshInvalidated, 500);
      return;
    }
    long installedRevision = repository.installedRevision();
    if (refreshedRevision == installedRevision) return;
    // 失败由操作结果明确显示，用户可手动重试；不在失败后无限全量刷新。
    refreshedRevision = installedRevision;
    syncInstalledState();
  }

  // 调试自测替换这两个边界，验证刷新时机，不触发真实插件注册或容器任务。
  boolean pluginRefreshBlocked() {
    String blocked = repository.environmentBlockMessage();
    if (!blocked.isEmpty()) {
      PluginRepository.State shown = repository.state().getValue();
      if (!repository.isBusy()) {
        environmentNotice = blocked;
        if (shown == null || !blocked.equals(shown.message)) repository.selectionMessage(blocked);
      }
      return true;
    }
    if (environmentNotice != null) {
      PluginRepository.State shown = repository.state().getValue();
      if (!repository.isBusy() && shown != null && environmentNotice.equals(shown.message))
        repository.selectionMessage(UiText.text("环境任务已结束；当前显示缓存列表，可点「刷新」同步插件状态"));
      environmentNotice = null;
    }
    com.deepseekharness.app.core.HarnessController controller =
        com.deepseekharness.app.core.HarnessController.get(requireContext());
    return repository.isBusy() || controller.isStarting() || controller.isStopping();
  }

  void syncInstalledState() {
    repository.refresh();
  }

  private final ActivityResultLauncher<android.content.Intent> importPicker =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (repository == null) repository = PluginRepository.get(requireContext());
            android.content.Intent data = result.getData();
            android.net.Uri uri = data == null ? null : data.getData();
            if (uri == null
                && data != null
                && data.getClipData() != null
                && data.getClipData().getItemCount() > 0)
              uri = data.getClipData().getItemAt(0).getUri();
            if (result.getResultCode() != android.app.Activity.RESULT_OK || uri == null) {
              repository.selectionMessage(UiText.text("未选择文件。可再次点击导入插件包。"));
              return;
            }
            if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme())) {
              repository.selectionMessage(UiText.text("文件管理器返回的地址无法读取，请改用系统文件选择器。"));
              return;
            }
            if ("file".equals(uri.getScheme())
                && android.os.Build.VERSION.SDK_INT < 30
                && requireContext()
                        .checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
              pendingImport = uri;
              PluginFragment.this.readPermission.launch(
                  android.Manifest.permission.READ_EXTERNAL_STORAGE);
              return;
            }
            repository.importArchive(uri);
          });
  private final ActivityResultLauncher<String> readPermission =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          allowed -> {
            android.net.Uri selected = pendingImport;
            pendingImport = null;
            if (allowed && selected != null) repository.importArchive(selected);
            else repository.selectionMessage(UiText.text("未获得文件读取权限，请改用系统文件选择器导入。"));
          });
  private final ActivityResultLauncher<String> exportPicker =
      registerForActivityResult(
          new ActivityResultContracts.CreateDocument("application/gzip"),
          uri -> {
            if (uri != null && !pendingExports.isEmpty())
              repository.exportArchives(new ArrayList<>(pendingExports), uri);
            pendingExports.clear();
          });

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
    ComposeView view = new ComposeView(requireContext());
    PluginScreenKt.bindPluginScreen(
        view,
        ui,
        new PluginActions() {
          @Override
          public void selectTab(boolean showMarket) {
            PluginFragment.this.selectTab(showMarket);
          }

          @Override
          public void openWebsite() {
            openPluginWebsite();
          }

          @Override
          public void paste() {
            pasteLink();
          }

          @Override
          public void install() {
            installLink();
          }

          @Override
          public void importArchive() {
            chooseImport(false);
          }

          @Override
          public void setDownloadSource(int index) {
            if (repository.isBusy()) return;
            new com.deepseekharness.app.core.ConfigStore(requireContext())
                .setPluginDownloadSource(
                    com.deepseekharness.app.util.PluginDownloadSource.values()[index]);
            ui.setDownloadSource(index);
          }

          @Override
          public void refresh() {
            repository.refresh();
          }

          @Override
          public void checkUpdates() {
            repository.checkUpdates(null);
          }

          @Override
          public void exportPlugins() {
            chooseExport();
          }

          @Override
          public void restore() {
            new DshaDialogBuilder(requireContext())
                .setTitle(UiText.text("恢复第三方插件？"))
                .setMessage(UiText.text("恢复安全启动前已启用的插件；之后手动禁用的插件保持禁用。恢复后重启 Web 生效。"))
                .setNegativeButton(UiText.text("取消"), null)
                .setPositiveButton(UiText.text("恢复"), (d, which) -> repository.safeMode(false, null))
                .show();
          }

          @Override
          public void sort(int index) {
            sortOrder = PluginSort.Mode.values()[index];
            new com.deepseekharness.app.core.ConfigStore(requireContext()).setPluginSort(sortOrder);
            render();
          }

          @Override
          public void cancelTask() {
            repository.cancelTask();
          }

          @Override
          public void showStatus() {
            showPluginStatus();
          }

          @Override
          public void toggle(@NonNull String name, boolean enabled) {
            PluginRepository.Item item = visible(name);
            if (item != null && enabled != item.enabled) PluginFragment.this.toggle(item, enabled);
          }

          @Override
          public void itemActions(@NonNull String name) {
            PluginRepository.Item item = visible(name);
            if (item != null) PluginFragment.this.itemActions(item);
          }

          @Override
          public void filtersChanged() {
            render();
          }

          @Override
          public void linkChanged() {
            recognizeLink();
          }
        });
    return view;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
    root = view;
    sortOrder = new com.deepseekharness.app.core.ConfigStore(requireContext()).getPluginSort();
    repository = installedRepository();
    if (getArguments() != null && getArguments().getBoolean("show_installed", false))
      market = false;
    if (saved != null) {
      market = saved.getBoolean("market", true);
      if (saved.containsKey("sortOrder"))
        sortOrder = PluginSort.Mode.parse(saved.getString("sortOrder"));
      else if (saved.getBoolean("enabledFirst")) sortOrder = PluginSort.Mode.ENABLED_FIRST;
      ArrayList<String> names = saved.getStringArrayList("pendingExports");
      if (names != null) pendingExports = names;
      String imported = saved.getString("pendingImport");
      if (imported != null) pendingImport = android.net.Uri.parse(imported);
    }
    ui.setDownloadSources(
        Arrays.asList(
            UiText.text("自动选择（推荐）"), UiText.text("npm 官方源"), UiText.text("npmmirror 国内镜像")));
    ui.setDownloadSource(
        new com.deepseekharness.app.core.ConfigStore(requireContext())
            .getPluginDownloadSource()
            .ordinal());
    ui.setSortLabels(
        Arrays.asList(
            getString(R.string.plugin_sort_az),
            getString(R.string.plugin_sort_za),
            getString(R.string.plugin_sort_enabled),
            getString(R.string.plugin_sort_updates)));
    repository
        .state()
        .observe(
            getViewLifecycleOwner(),
            state -> {
              current = state;
              render();
            });
    recognizeLink();
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshInstalledIfNeeded();
  }

  PluginRepository installedRepository() {
    return PluginRepository.get(requireContext());
  }

  @Override
  public void onPause() {
    refreshHandler.removeCallbacks(refreshInvalidated);
    super.onPause();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle state) {
    super.onSaveInstanceState(state);
    state.putBoolean("market", market);
    state.putString("sortOrder", sortOrder.name());
    state.putStringArrayList("pendingExports", pendingExports);
    if (pendingImport != null) state.putString("pendingImport", pendingImport.toString());
  }

  @Override
  public void onDestroyView() {
    refreshHandler.removeCallbacks(refreshInvalidated);
    root = null;
    super.onDestroyView();
  }

  private PluginRepository.Item visible(String name) {
    for (PluginRepository.Item item : visibleItems) if (item.name.equals(name)) return item;
    return null;
  }

  private void hideKeyboard() {
    if (root == null) return;
    root.clearFocus();
    android.view.inputmethod.InputMethodManager keyboard =
        (android.view.inputmethod.InputMethodManager)
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (keyboard != null) keyboard.hideSoftInputFromWindow(root.getWindowToken(), 0);
  }

  private void showPluginStatus() {
    if (current == null || current.message.isEmpty()) return;
    var dialog =
        new DshaDialogBuilder(requireContext())
            .setTitle(UiText.text("插件操作结果"))
            .setMessage(UiStateText.render(current.message));
    if (!repository.isBusy() && repository.preview().getValue() != null) {
      dialog
          .setPositiveButton(
              UiText.choose("重试安装", "Retry installation"), (d, w) -> repository.confirmPreview())
          .setNeutralButton(
              UiText.choose("清理临时包", "Remove temporary package"),
              (d, w) -> repository.discardPreview())
          .setNegativeButton(UiText.text("关闭"), null);
    } else dialog.setPositiveButton(UiText.text("关闭"), null);
    dialog.show();
  }

  private void recognizeLink() {
    if (root == null) return;
    String input = ui.link();
    boolean valid = false;
    try {
      PluginSource source = PluginSource.parse(input);
      ui.setLinkHint(UiText.format("已识别：%s", source.description()));
      valid = true;
    } catch (IllegalArgumentException error) {
      ui.setLinkHint(
          input.trim().isEmpty() ? UiText.text("支持仓库、分支/子目录、Release 下载和压缩包直链") : error.getMessage());
    }
    ui.setInstallEnabled(valid && !repository.isBusy());
  }

  /** 外部要求打开“已安装”时调用（页面已在主界面翻页器里建好）。 */
  void showInstalled() {
    if (getView() == null) market = false;
    else selectTab(false);
  }

  private void selectTab(boolean showMarket) {
    market = showMarket;
    if (!market) refreshInstalledIfNeeded();
    hideKeyboard();
    render();
  }

  private void openPluginWebsite() {
    try {
      startActivity(new android.content.Intent(requireContext(), CommunityActivity.class));
    } catch (RuntimeException error) {
      toast(
          UiText.format(
              "无法打开浏览器，请在浏览器中访问 %s", com.deepseekharness.app.util.Constants.SITE_URL));
    }
  }

  private void installLink() {
    if (repository.isBusy()) return;
    try {
      PluginSource source = PluginSource.parse(ui.link());
      hideKeyboard();
      repository.install(source);
    } catch (IllegalArgumentException error) {
      toast(error.getMessage());
    }
  }

  private void chooseImport(boolean alternative) {
    if (repository.isBusy()) {
      toast(UiText.text("请等待当前插件操作完成后再导入"));
      return;
    }
    hideKeyboard();
    repository.selectionMessage(UiText.text("请选择 ZIP / TAR.GZ 插件包。"));
    try {
      importPicker.launch(PluginFilePicker.intent(requireContext(), alternative));
    } catch (android.content.ActivityNotFoundException error) {
      if (!alternative) {
        chooseImport(true);
        return;
      }
      repository.selectionMessage(UiText.text("未找到可用的文件选择器，请启用系统「文件」应用后重试。"));
      toast(UiText.text("没有可用的文件选择器"));
    } catch (RuntimeException error) {
      repository.selectionMessage(
          UiText.format("无法打开文件选择器，请检查系统文件应用：%s", error.getClass().getSimpleName()));
    }
  }

  private void pasteLink() {
    ClipboardManager clipboard =
        (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0) {
      toast(UiText.text("剪贴板没有链接"));
      return;
    }
    CharSequence text = clip.getItemAt(0).coerceToText(requireContext());
    if (text != null) ui.setLink(text.toString());
  }

  private void render() {
    if (root == null || current == null) return;
    ui.setMarket(market);
    ui.setSafeMode(repository.isSafeMode());
    ui.setBusy(current.busy);
    ui.setPercent(current.percent);
    ui.setCancellable(current.cancellable);
    ui.setMessage(UiStateText.render(current.message));
    ui.setSortIndex(sortOrder.ordinal());
    String query = ui.search().trim().toLowerCase(Locale.ROOT);
    boolean hideBuiltin = ui.getHideBuiltin();
    visibleItems.clear();
    for (PluginRepository.Item item : current.items) {
      if (hideBuiltin && (item.builtin || item.official || item.runtimeProvided)) continue;
      if (!(item.name + " " + item.description).toLowerCase(Locale.ROOT).contains(query)) continue;
      visibleItems.add(item);
    }
    visibleItems.sort(
        PluginSort.comparator(sortOrder, it -> it.name, it -> it.enabled, it -> it.updateAvailable));
    List<PluginRow> rows = new ArrayList<>();
    for (PluginRepository.Item item : visibleItems) rows.add(row(item));
    ui.setRows(rows);
    ui.setCountText(
        !repository.hasKnownItems()
            ? UiText.choose(
                current.busy ? "正在检测插件…" : "插件列表尚未确认",
                current.busy ? "Checking plugins…" : "Plugin list unconfirmed")
            : getResources()
                .getQuantityString(R.plurals.plugin_count, visibleItems.size(), visibleItems.size()));
    ui.setEmptyText(
        current.busy
            ? UiText.text("正在读取插件…")
            : !repository.hasKnownItems()
                ? UiText.choose(
                    "插件列表尚未确认，请查看状态并刷新重试",
                    "The plugin list is unconfirmed. Check the status and refresh to retry.")
                : UiText.text("没有符合条件的插件"));
    recognizeLink();
  }

  private PluginRow row(PluginRepository.Item item) {
    String state =
        item.dynamic
            ? (item.enabled ? UiText.text("临时插件 · 已运行") : UiText.text("临时插件 · 未运行"))
            : !item.available
                ? UiText.text("实体缺失，请重新导入")
                : !item.loadState.isEmpty()
                    ? switch (item.loadState) {
                      case "review-required" -> UiText.text("升级后待自动启用");
                      case "queued" -> UiText.text("等待加载");
                      case "attempted" -> UiText.text("正在确认加载");
                      case "loaded" -> UiText.text(item.enabled ? "已确认加载" : "已禁用");
                      case "failed", "unconfirmed" -> UiText.text("加载未确认，保持停用");
                      case "changed" ->
                          UiText.choose(
                              "加载期间内容变化；检查后重新启用",
                              "Content changed during loading; check and enable again");
                      default -> UiText.text(item.enabled ? "已启用" : "已禁用");
                    }
                    : item.enabled
                        ? UiText.text("已启用")
                        : item.detected ? UiText.text("已检测，可开启以加入 Web") : UiText.text("已禁用");
    String status =
        state
            + (item.version.isEmpty() ? "" : " · " + item.version)
            + (item.updateAvailable ? UiText.format(" · 可更新：%s", item.latestVersion) : "");
    String description =
        item.description.isEmpty()
            ? (item.official
                ? UiText.text("官方核心")
                : item.runtimeProvided
                    ? UiText.choose("随 DSH 安装提供", "Provided by DSH")
                    : item.builtin
                        ? UiText.text("DSHA 内置插件")
                        : item.runtimeLocated
                            ? UiText.choose(
                                "DSH 安装目录中的用户/未验证插件",
                                "User or unverified plugin in the DSH installation")
                            : UiText.text("第三方插件"))
            : item.builtin ? UiStateText.render(item.description) : UiText.raw(item.description);
    return new PluginRow(
        item.name,
        status,
        !item.available ? 2 : item.enabled ? 1 : 0,
        description,
        !item.dynamic,
        item.enabled,
        !repository.isBusy() && (item.available || item.enabled));
  }

  private void chooseExport() {
    if (current == null || repository.isBusy()) return;
    List<String> names = new ArrayList<>();
    for (PluginRepository.Item item : current.items) if (item.exportable) names.add(item.name);
    if (names.isEmpty()) {
      toast(UiText.text("没有可导出的插件"));
      return;
    }
    boolean[] checked = new boolean[names.size()];
    AlertDialog dialog =
        new DshaDialogBuilder(requireContext())
            .setTitle(UiText.text("选择要导出的插件"))
            .setMultiChoiceItems(
                names.toArray(new String[0]),
                checked,
                (d, which, value) -> {
                  checked[which] = value;
                  boolean any = false;
                  for (boolean selected : checked) any |= selected;
                  ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(any);
                })
            .setNegativeButton(UiText.text("取消"), null)
            .setPositiveButton(
                UiText.text("选择保存位置"),
                (d, which) -> {
                  ArrayList<String> selected = new ArrayList<>();
                  for (int i = 0; i < checked.length; i++) if (checked[i]) selected.add(names.get(i));
                  beginExport(selected);
                })
            .create();
    dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false));
    dialog.show();
  }

  private void beginExport(ArrayList<String> names) {
    if (names.isEmpty() || repository.isBusy()) return;
    pendingExports = names;
    String name =
        names.size() == 1 ? names.get(0).replaceAll("[^A-Za-z0-9._-]", "_") : "HYDSH-plugins";
    String stamp =
        new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new java.util.Date());
    try {
      exportPicker.launch(name + "-" + stamp + ".tar.gz");
    } catch (Exception error) {
      pendingExports.clear();
      toast(UiText.text("无法打开保存位置选择器"));
    }
  }

  private void toggle(PluginRepository.Item item, boolean enabled) {
    if (repository.isBusy()) return;
    if (item.official && !enabled) {
      new DshaDialogBuilder(requireContext(), true)
          .setTitle(UiText.text("禁用官方核心？"))
          .setMessage(item.name + UiText.text(" 是 Web 运行所需的核心，禁用后页面可能无法启动。"))
          .setPositiveButton(UiText.text("禁用"), (d, which) -> repository.setEnabled(item, false))
          .setNegativeButton(UiText.text("取消"), null)
          .show();
    } else repository.setEnabled(item, enabled);
  }

  private void itemActions(PluginRepository.Item item) {
    List<PluginAction> actions = new ArrayList<>();
    actions.add(PluginAction.DETAIL);
    actions.add(PluginAction.COPY_NAME);
    if (!item.source.isEmpty()) actions.add(PluginAction.COPY_SOURCE);
    if (item.exportable) actions.add(PluginAction.EXPORT);
    if (item.updatable) actions.add(PluginAction.CHECK);
    if (item.updateAvailable) actions.add(PluginAction.UPDATE);
    if (!item.rollbackVersion.isEmpty()) actions.add(PluginAction.ROLLBACK);
    if (item.deletable) actions.add(PluginAction.DELETE);
    new DshaDialogBuilder(requireContext())
        .setTitle(item.name)
        .setItems(
            actions.stream().map(action -> actionLabel(action, item)).toArray(String[]::new),
            (d, which) -> {
              PluginAction action = actions.get(which);
              if (action == PluginAction.DETAIL) showDetails(item);
              else if (action == PluginAction.CHECK) repository.checkUpdates(item);
              else if (action == PluginAction.UPDATE) repository.prepareUpdate(item);
              else if (action == PluginAction.ROLLBACK) confirmRollback(item);
              else if (action == PluginAction.EXPORT) {
                ArrayList<String> names = new ArrayList<>();
                names.add(item.name);
                beginExport(names);
              } else if (action == PluginAction.DELETE) confirmDelete(item);
              else {
                ClipboardManager clipboard =
                    (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null)
                  clipboard.setPrimaryClip(
                      ClipData.newPlainText(
                          UiText.text("插件"),
                          action == PluginAction.COPY_NAME ? item.name : item.source));
                toast(UiText.text("已复制"));
              }
            })
        .show();
  }

  private void showDetails(PluginRepository.Item item) {
    String location =
        item.runtimeProvided
            ? UiText.choose("随 DSH 安装提供", "Provided by DSH")
            : item.location.isEmpty() ? UiText.choose("未提供路径", "Path not provided") : item.location;
    String source =
        item.source.isEmpty()
            ? (item.builtin
                ? UiText.choose("随包内置", "Bundled")
                : item.runtimeProvided
                    ? UiText.choose("已核对当前 APK 受管内容", "Verified against the current APK")
                    : item.official
                        ? UiText.choose("DSH 官方组件", "Official DSH component")
                        : UiText.choose("来源未记录", "Source not recorded"))
            : item.source;
    new DshaDialogBuilder(requireContext())
        .setTitle(item.name)
        .setMessage(
            UiText.format(
                "%s\n\n版本：%s\n位置：%s\n来源：%s%s%s%s",
                item.builtin ? UiStateText.render(item.description) : item.description,
                item.version,
                location,
                source,
                item.latestVersion.isEmpty()
                    ? ""
                    : UiText.format("\n上次检查版本：%s", item.latestVersion),
                item.updateMessage.isEmpty() ? "" : "\n" + UiStateText.render(item.updateMessage),
                item.rollbackVersion.isEmpty()
                    ? ""
                    : UiText.format("\n可回退：%s", item.rollbackVersion)))
        .setPositiveButton(UiText.text("关闭"), null)
        .setNeutralButton(
            UiText.choose("能力声明", "Capabilities"),
            (dialog, button) ->
                repository.inspectCapabilities(
                    item,
                    details -> {
                      if (!isAdded()) return;
                      new DshaDialogBuilder(requireContext())
                          .setTitle(item.name)
                          .setMessage(details)
                          .setPositiveButton(UiText.text("关闭"), null)
                          .show();
                    }))
        .show();
  }

  private void confirmRollback(PluginRepository.Item item) {
    new DshaDialogBuilder(requireContext(), true)
        .setTitle(UiText.text("回退插件？"))
        .setMessage(
            item.name
                + UiText.text("：")
                + item.version
                + " → "
                + item.rollbackVersion
                + UiText.text("\n只恢复插件文件，当前启用状态和对话数据保留；重启 Web 生效。"))
        .setNegativeButton(UiText.text("取消"), null)
        .setPositiveButton(UiText.text("回退"), (confirm, button) -> repository.rollback(item))
        .show();
  }

  private enum PluginAction {
    DETAIL,
    COPY_NAME,
    COPY_SOURCE,
    EXPORT,
    CHECK,
    UPDATE,
    ROLLBACK,
    DELETE
  }

  private String actionLabel(PluginAction action, PluginRepository.Item item) {
    return switch (action) {
      case DETAIL -> UiText.text("查看详情");
      case COPY_NAME -> UiText.text("复制插件名称");
      case COPY_SOURCE -> UiText.text("复制来源链接");
      case EXPORT -> UiText.text("导出插件包");
      case CHECK -> UiText.text("检查插件更新");
      case UPDATE -> UiText.format("更新至 %s", item.latestVersion);
      case ROLLBACK -> UiText.format("回退至 %s", item.rollbackVersion);
      case DELETE -> UiText.text("删除插件");
    };
  }

  private void confirmDelete(PluginRepository.Item item) {
    if (!item.deletable || repository.isBusy()) {
      toast(UiText.text("请等待当前插件操作完成"));
      return;
    }
    new DshaDialogBuilder(requireContext(), true)
        .setTitle(UiText.choose("删除插件？", "Delete plugin?"))
        .setMessage(
            UiText.format("%s 将从加载路径移除；原件保留在数据页的保留区。对话、其他插件和外部源码目录保留。", item.name))
        .setNegativeButton(UiText.choose("取消", "Cancel"), null)
        .setPositiveButton(
            UiText.choose("删除", "Delete"),
            (dialog, which) -> {
              if (!repository.isBusy()) repository.delete(item);
            })
        .show();
  }

  private void toast(String message) {
    if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
  }
}
