package com.deepseekharness.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.deepseekharness.app.R;
import com.deepseekharness.app.core.ConfigStore;
import com.deepseekharness.app.core.HarnessController;

/**
 * 主界面外壳：启动门禁 + 底部导航（启动 / 插件 / 设置 / 终端）+ 顶栏标题 + 关于入口。
 */
public class MainActivity extends AppCompatActivity {

  /** Read the lifecycle tracker; this class keeps no static reference to a page. */
  public static MainActivity active() {
    androidx.fragment.app.FragmentActivity activity =
        com.deepseekharness.app.ForegroundActivity.current();
    return activity instanceof MainActivity ? (MainActivity) activity : null;
  }

  private com.deepseekharness.app.core.UpdateEngine startupUpdates;
  private com.google.android.material.snackbar.Snackbar updateNotice;
  private boolean requestingLocalNetwork;
  private boolean restoreProbeStarted;
  private boolean discardedLauncherEntry;
  private boolean mainReady;
  private Bundle pendingMainState;
  private android.view.View entryOverlay;
  private final java.util.List<Fragment> gatedPages = new java.util.ArrayList<>();
  private final MainEntryGate entryGate = new MainEntryGate();
  private static final java.util.concurrent.Executor ENTRY_IO =
      java.util.concurrent.Executors.newSingleThreadExecutor();

  /**
   * 通知点击带来的「进入后自动打开 Web」意图，只在一次导航创建 LaunchFragment 期间有效。
   *
   * <p>必须在 {@code setSelectedItemId} 触发监听器之前置位、在创建时立刻消费掉，
   * 否则会残留下来，让用户之后手动点启动页时被意外带进 Web。
   */
  private boolean pendingOpenWeb;

  /** miuix 顶栏与底部标签；主界面就绪前为 null。 */
  private MainChrome chrome;
  /** 四个主标签页（ViewPager2）；子页面叠在 fragment_container 里。 */
  private MainPages pages;
  private androidx.viewpager2.widget.ViewPager2 pager;
  /** 外部要求打开“已安装插件”时，插件页还没建好就先记着。 */
  private boolean pendingInstalled;
  private int shownStack;

  private int selectedTab = R.id.nav_launch;

  private String openedRecovery = "";
  private final androidx.activity.result.ActivityResultLauncher<String> localNetworkPermission =
      registerForActivityResult(
          new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
          granted -> {
            requestingLocalNetwork = false;
            if (granted)
              com.deepseekharness.app.bridge.LocalNetworkAccess.applyConfiguredFeatures(this);
            else
              android.widget.Toast.makeText(
                      this,
                      com.deepseekharness.app.util.UiText.text(
                          "未允许局域网访问；本机对话仍可使用，LAN / 无线 ADB 需在系统权限设置中开启"),
                      android.widget.Toast.LENGTH_LONG)
                  .show();
          });
  private final androidx.activity.result.ActivityResultLauncher<String[]> legacyBackupPicker =
      registerForActivityResult(
          new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
          uri -> {
            if (uri != null) openBackupForRestore(uri);
          });

  public void requestLocalNetwork() {
    if (com.deepseekharness.app.bridge.LocalNetworkAccess.granted(this)) {
      com.deepseekharness.app.bridge.LocalNetworkAccess.applyConfiguredFeatures(this);
    } else if (!requestingLocalNetwork) {
      requestingLocalNetwork = true;
      getSharedPreferences(com.deepseekharness.app.util.Constants.PREFS, MODE_PRIVATE)
          .edit()
          .putBoolean("local_network_permission_asked", true)
          .apply();
      localNetworkPermission.launch(com.deepseekharness.app.bridge.LocalNetworkAccess.PERMISSION);
    }
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    BrowserTasks.entry("CREATE", getIntent());
    if (resumeBackground(getIntent())) {
      discardedLauncherEntry = true;
      finish(); // Only this newly created native wrapper; the existing Web task is retained.
      return;
    }

    if (BrowserTasks.duplicateLauncher(this, savedInstanceState)) {
      discardedLauncherEntry = true;
      finish();
      BrowserTasks.openMain(this);
      return;
    }

    if (savedInstanceState != null)
      openedRecovery = savedInstanceState.getString("opened_startup_recovery", "");
    ConfigStore config = new ConfigStore(this);

    // 启动门禁：未欢迎 → Welcome；环境未解压 → Extract
    if (!config.isWelcomed()) {
      startActivity(new Intent(this, WelcomeActivity.class));
      finish();
      return;
    }
    showEntryCheck();
    if (savedInstanceState != null) {
      var hold = getSupportFragmentManager().beginTransaction();
      for (Fragment page : getSupportFragmentManager().getFragments()) {
        if (!page.isAdded()) continue;
        gatedPages.add(page);
        hold.setMaxLifecycle(page, androidx.lifecycle.Lifecycle.State.CREATED);
      }
      if (!gatedPages.isEmpty()) hold.commitNow();
    }
    pendingMainState = savedInstanceState;
    beginEntryCheck();
  }

  private void beginEntryCheck() {
    HarnessController controller = HarnessController.get(this);
    controller.restoreRunningWebSession(
        () -> {
          if (!mainReady
              && !isFinishing()
              && !isDestroyed()
              && getLifecycle()
                  .getCurrentState()
                  .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            checkEntry(
                pendingMainState,
                new ConfigStore(this),
                controller,
                com.deepseekharness.app.BuildConfig.DEBUG
                    && getIntent().getBooleanExtra("skip_extract", false));
        });
  }

  private void showEntryCheck() {
    // FragmentManager可能先于后台磁盘检查恢复页面。首次就保留真实容器，
    // 后面只移除覆盖层，避免替换整个布局后恢复视图无法重新附着。
    setContentView(R.layout.activity_main);
    entryOverlay =
        EntryCheckScreenKt.entryCheckPage(
            this, () -> startActivity(DiagnosticActivity.downloadLogs(this)));
    addContentView(entryOverlay, new android.view.ViewGroup.LayoutParams(-1, -1));
  }

  private void checkEntry(
      Bundle saved, ConfigStore config, HarnessController controller, boolean skipExtract) {
    long generation = controller.getWebGeneration();
    Intent admittedIntent = getIntent();
    entryGate.submit(
        ENTRY_IO,
        this::runOnUiThread,
        () -> {
          boolean limited =
              admittedIntent.getBooleanExtra("limited_entry", false)
                  || config.allowsLimitedEntry(controller.proot().environmentIdentity());
          return !limited
              && (com.deepseekharness.app.core.MaintenanceCoordinator.pending(controller)
                  || !skipExtract
                      && (!controller.isEnvironmentReady()
                          || com.deepseekharness.app.core.EnvironmentAccess
                              .requiresMaintenanceBeforeStart(controller)));
        },
        () ->
            !isFinishing()
                && !isDestroyed()
                && admittedIntent == getIntent()
                && getLifecycle()
                    .getCurrentState()
                    .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                && !getSupportFragmentManager().isStateSaved(),
        (maintenance, failed) -> {
          if (failed)
            com.deepseekharness.app.core.DiagnosticLog.record(
                this, "MAIN_ENVIRONMENT_CHECK", "UNAVAILABLE");
          if (generation != controller.getWebGeneration()) {
            checkEntry(saved, config, controller, skipExtract);
            return;
          }
          if (!failed
              && maintenance
              && !controller.hasUnresolvedWebSession()
              && !controller.isRestoringWebSession()) {
            startActivity(new Intent(this, ExtractActivity.class));
            finish();
          } else {
            // 原实例证据未知时留在Main提供诊断，不触发解压或静默更新原环境。
            showMain(saved, config, controller);
          }
        });
  }

  /**
   * 检查页等主界面当前页真正排好版后再淡出：两页标题在同一位置，交接时看起来是同一页，
   * 不会先闪一下空白（最多等约 1 秒）。
   */
  private void dismissEntryOverlay(int frames) {
    android.view.View overlay = entryOverlay;
    if (overlay == null || !(overlay.getParent() instanceof android.view.ViewGroup parent)) {
      entryOverlay = null;
      return;
    }
    Fragment page = shownFragment();
    boolean drawn = page != null && page.getView() != null && page.getView().isLaidOut();
    if (!drawn && frames < 60 && !isDestroyed()) {
      android.view.Choreographer.getInstance()
          .postFrameCallback(t -> dismissEntryOverlay(frames + 1));
      return;
    }
    entryOverlay = null;
    if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
      parent.removeView(overlay);
      return;
    }
    // 再隔一帧，确保主界面这一帧已经画出来。
    overlay.postOnAnimation(
        () ->
            overlay
                .animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> parent.removeView(overlay))
                .start());
  }

  private void showMain(
      Bundle savedInstanceState, ConfigStore config, HarnessController controller) {
    if (mainReady || isFinishing() || isDestroyed()) return;
    mainReady = true;
    dismissEntryOverlay(0);
    TextView recovery = findViewById(R.id.environment_recovery_banner);
    recovery.setOnClickListener(
        v -> {
          if (com.deepseekharness.app.core.BackupTask.get(this).maintenanceBusy()
              || BackgroundTasksActivity.busy(this)) BackgroundTasksActivity.open(this);
          else if (controller.config().isStartupRecoveryRequested()
              || EnvironmentUiStatus.get(this).repairs)
            startActivity(new Intent(this, StartupRecoveryActivity.class));
          else startActivity(new Intent(this, ExtractActivity.class).putExtra("review_only", true));
        });
    String pendingLink =
        getSharedPreferences("dsha-install-link", MODE_PRIVATE).getString("pending", "");
    if (!pendingLink.isEmpty()
        && !controller.hasUnresolvedWebSession()
        && !com.deepseekharness.app.core.EnvironmentAccess.needsRecovery(controller)) {
      getSharedPreferences("dsha-install-link", MODE_PRIVATE).edit().remove("pending").apply();
      try {
        com.deepseekharness.app.util.PluginInstallLink.parse(pendingLink);
        startActivity(
            new Intent(this, PluginInstallActivity.class)
                .setData(android.net.Uri.parse(pendingLink)));
      } catch (IllegalArgumentException ignored) {
      }
    }

    chrome = new MainChrome();
    chrome.attachTopBar(
        findViewById(R.id.app_bar),
        () -> getOnBackPressedDispatcher().onBackPressed(),
        () -> BackgroundTasksActivity.open(this));
    pager = findViewById(R.id.main_pager);
    pages = new MainPages(this, this::tabKind, this::createTab);
    pages.attach(pager);
    GlassBar.load(this);
    GlassBar.setSource(findViewById(R.id.content_frame));
    chrome.attachBottomBar(findViewById(R.id.bottom_nav), this::selectTab, this::applyBarInset);
    findViewById(R.id.bottom_nav)
        .addOnLayoutChangeListener(
            (v, l, t, r, b, ol, ot, or, ob) -> {
              if (b - t != ob - ot) v.post(this::applyBarInset);
            });
    getSupportFragmentManager().addOnBackStackChangedListener(this::updateToolbar);
    getSupportFragmentManager().addOnBackStackChangedListener(this::movePagerForSubpage);
    getSupportFragmentManager()
        .registerFragmentLifecycleCallbacks(
            new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
              @Override
              public void onFragmentResumed(
                  androidx.fragment.app.FragmentManager manager, Fragment fragment) {
                updateToolbar();
              }
            },
            false);
    // 通知点击进入：必须在 selectTab 之前登记 —— selectTab 会同步创建 LaunchFragment，
    // 那时再置标记已经晚了（参数带不进去）。
    consumeOpenWeb(getIntent());
    if (savedInstanceState == null || controller.hasUnresolvedWebSession()) {
      selectTab(
          !controller.hasUnresolvedWebSession()
                  && getIntent().getBooleanExtra("open_plugins", false)
              ? R.id.nav_plugins
              : R.id.nav_launch);
    } else {
      selectedTab = savedInstanceState.getInt("selected_tab", R.id.nav_launch);
      chrome.setTab(selectedTab);
    }
    releaseGatedPages(!controller.hasUnresolvedWebSession());
    consumeTaskTarget(getIntent());
    // 只订阅后台检查；提示不切换导航，也不自动打开更新页或 Web。
    startupUpdates = com.deepseekharness.app.core.UpdateEngine.get(this);
    startupUpdates.state().observe(this, state -> showStartupUpdate());
    startupUpdates.checkOnStartup(config.isCheckUpdate());
    scheduleFirstRunWeb();
    recoveryHandler.removeCallbacks(refreshRecovery);
    recoveryHandler.post(refreshRecovery);
    maybePromptExternalRestore();
  }

  /** 切换底部标签。原 BottomNavigationView 监听器的逻辑，原样搬过来。 */
  private void selectTab(int id) {
    if (chrome == null || pages == null) return;
    selectedTab = id;
    chrome.setTab(id);
    if (!getSupportFragmentManager().isStateSaved())
      getSupportFragmentManager()
          .popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
    // 环境需要修复、或终端类型改了时，对应页面换成新的。
    pages.refresh();
    if (id == R.id.nav_plugins && getIntent().getBooleanExtra("open_plugins", false)) {
      getIntent().removeExtra("open_plugins");
      Fragment plugins = tabFragment(1);
      if (plugins instanceof PluginFragment) ((PluginFragment) plugins).showInstalled();
      else pendingInstalled = true;
    }
    pages.slide(pager, MainPages.index(id));
    applyBarInset();
  }

  /** 某个标签页的种类：插件页 0 正常 / 1 修复；终端页 0 PTY / 1 简易 / 2 修复。 */
  private int tabKind(int position) {
    boolean recovery =
        com.deepseekharness.app.core.EnvironmentAccess.needsRecovery(HarnessController.get(this));
    if (position == 1) return recovery ? 1 : 0;
    if (position == 3) return recovery ? 2 : PtyTerminalFragment.preferred(this) ? 0 : 1;
    return 0;
  }

  /** 翻页器需要某一页时才创建（原 selectTab 里的建页逻辑）。 */
  private Fragment createTab(int position) {
    boolean recovery =
        com.deepseekharness.app.core.EnvironmentAccess.needsRecovery(HarnessController.get(this));
    if (position == 0) {
      Fragment f = new LaunchFragment();
      if (pendingOpenWeb) {
        pendingOpenWeb = false;
        Bundle args = new Bundle();
        args.putBoolean(LaunchFragment.ARG_OPEN_WEB, true);
        f.setArguments(args);
      }
      return f;
    }
    if (position == 1) {
      if (recovery) return new EnvironmentRecoveryFragment();
      Fragment f = new PluginFragment();
      if (pendingInstalled) {
        pendingInstalled = false;
        Bundle args = new Bundle();
        args.putBoolean("show_installed", true);
        f.setArguments(args);
      }
      return f;
    }
    if (position == 2) return new SettingsFragment();
    // 终端：默认挂真 PTY 页（vim/htop/tmux 能跑），可在 PTY 页切回简易版
    if (recovery) return new EnvironmentRecoveryFragment();
    return PtyTerminalFragment.preferred(this) ? new PtyTerminalFragment() : new TerminalFragment();
  }

  /** 终端页在 PTY 与简易版之间切换后调用。 */
  void refreshTabs() {
    if (pages != null) pages.refresh();
  }

  @androidx.annotation.Nullable
  private Fragment tabFragment(int position) {
    return pages == null
        ? null
        : getSupportFragmentManager().findFragmentByTag(pages.tag(position));
  }

  /** 当前看到的页面：有子页面时是最上层的子页面，否则是选中的标签页。 */
  @androidx.annotation.Nullable
  private Fragment shownFragment() {
    Fragment top = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    return top != null ? top : tabFragment(MainPages.index(selectedTab));
  }

  /** 打开子页面时标签页向左让出一小段并变暗，返回时复原（和子页面之间的推入动画一致）。 */
  private void movePagerForSubpage() {
    if (pager == null) return;
    int count = getSupportFragmentManager().getBackStackEntryCount();
    boolean covered = count > 0;
    boolean was = shownStack > 0;
    shownStack = count;
    if (covered == was) return;
    float x = covered ? -0.25f * pager.getWidth() : 0f;
    float alpha = covered ? 0.6f : 1f;
    if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
      pager.setTranslationX(x);
      pager.setAlpha(alpha);
      return;
    }
    pager
        .animate()
        .translationX(x)
        .alpha(alpha)
        .setDuration(380)
        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
        .start();
  }

  private void releaseGatedPages(boolean allowed) {
    if (allowed && !gatedPages.isEmpty()) {
      var resume = getSupportFragmentManager().beginTransaction();
      String current = pages == null ? null : pages.tag(MainPages.index(selectedTab));
      for (Fragment page : gatedPages)
        if (page.isAdded())
          resume.setMaxLifecycle(
              page,
              page.getTag() != null && page.getTag().startsWith("f") && !page.getTag().equals(current)
                  ? androidx.lifecycle.Lifecycle.State.STARTED
                  : androidx.lifecycle.Lifecycle.State.RESUMED);
      resume.commitNow();
    }
    gatedPages.clear();
  }

  private void scheduleFirstRunWeb() {
    findViewById(android.R.id.content)
        .post(
            () -> {
              if (!isFinishing() && !isDestroyed()) FirstRunWebStart.schedule(this, getIntent());
            });
  }

  private void showStartupUpdate() {
    if (startupUpdates == null
        || updateNotice != null
        || isFinishing()
        || !hasWindowFocus()
        || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        || !new ConfigStore(this).isCheckUpdate()) return;
    com.deepseekharness.app.util.UpdatePolicy.Release release = startupUpdates.startupNotice();
    if (release == null) return;
    updateNotice =
        com.google.android.material.snackbar.Snackbar.make(
                findViewById(android.R.id.content),
                com.deepseekharness.app.util.UiText.format(
                    "发现新版本 %s · %s",
                    release.version,
                    com.deepseekharness.app.core.UpdateEngine.channelName(
                        startupUpdates.channel())),
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .setAnchorView(R.id.bottom_nav)
            .setAction(
                com.deepseekharness.app.util.UiText.text("查看更新"),
                view -> startActivity(new Intent(this, UpdateActivity.class)));
    updateNotice.addCallback(
        new com.google.android.material.snackbar.Snackbar.Callback() {
          @Override
          public void onShown(com.google.android.material.snackbar.Snackbar bar) {
            if (hasWindowFocus()
                && getLifecycle()
                    .getCurrentState()
                    .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
              startupUpdates.markStartupNoticeShown(release);
          }

          @Override
          public void onDismissed(com.google.android.material.snackbar.Snackbar bar, int event) {
            if (updateNotice == bar) updateNotice = null;
          }
        });
    updateNotice.show();
  }

  @Override
  protected void onPostResume() {
    super.onPostResume();
    if (mainReady && getIntent().getBooleanExtra(FirstRunWebStart.EXTRA, false))
      scheduleFirstRunWeb();
    showStartupUpdate();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) showStartupUpdate();
  }

  @Override
  protected void onPause() {
    if (!mainReady) entryGate.cancel();
    recoveryHandler.removeCallbacks(refreshRecovery);
    if (updateNotice != null) {
      updateNotice.dismiss();
      updateNotice = null;
    }
    super.onPause();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    BrowserTasks.entry("NEW_INTENT", intent);
    if (resumeBackground(intent)) return;
    if (!mainReady) {
      entryGate.cancel();
      beginEntryCheck();
      return;
    }
    if (intent.getBooleanExtra("open_plugins", false)) selectTab(R.id.nav_plugins);
    if (intent.getBooleanExtra("open_launch", false)) {
      intent.removeExtra("open_launch");
      selectTab(R.id.nav_launch);
    }
    consumeOpenWeb(intent);
    consumeTaskTarget(intent);
    if (findViewById(android.R.id.content) != null) scheduleFirstRunWeb();
  }

  private boolean resumeBackground(Intent intent) {
    if (BrowserTasks.nativeDestination(intent)) {
      BrowserTasks.nativeNavigation();
      return false;
    }
    return BrowserTasks.resumeBackground(this, true);
  }

  private void consumeTaskTarget(Intent intent) {
    if (chrome == null) return;
    if (intent.getBooleanExtra("open_terminal", false)) {
      intent.removeExtra("open_terminal");
      selectTab(R.id.nav_terminal);
    }
    if (intent.getBooleanExtra("open_install", false)) {
      intent.removeExtra("open_install");
      selectTab(R.id.nav_settings);
      UiMotion.page(this, getSupportFragmentManager().beginTransaction())
          .replace(R.id.fragment_container, new InstallFragment())
          .addToBackStack("settings")
          .commit();
    }
  }

  /**
   * 消费通知/外部的 {@code open_web} 意图：切到启动页，并把「进来后自动进 Web」的意图
   * 交给 {@link LaunchFragment}（只有它知道鉴权是否就绪）。
   *
   * <p>时序很关键：{@code setSelectedItemId} 会<b>同步</b>触发
   * {@code OnItemSelectedListener} 去创建 fragment，所以标记必须在那之前设好；
   * 否则 fragment 已经建完，参数永远带不进去（表现为"点了通知没反应"）。
   *
   * <p>Web 未就绪时（例如服务刚被系统回收）只切页不报错：停在启动页让用户看到真实状态，
   * 比弹一个必然失败的错误更合适。
   */
  private void consumeOpenWeb(Intent intent) {
    if (intent == null || !intent.getBooleanExtra("open_web", false)) return;
    intent.removeExtra("open_web");
    if (chrome == null) return;
    pendingOpenWeb = true;
    if (selectedTab != R.id.nav_launch) selectTab(R.id.nav_launch);
    // 启动页已经建好时直接把意图交给它；还没建就等创建时通过参数带进去。
    getSupportFragmentManager().executePendingTransactions();
    Fragment shown = tabFragment(0);
    if (shown instanceof LaunchFragment) {
      pendingOpenWeb = false;
      ((LaunchFragment) shown).requestAutoEnterWeb();
    }
  }

  /**
   * 经典底栏：页面整体停在底栏上方。玻璃底栏：页面铺到底栏后面，列表末尾留出底栏高度，
   * 终端页自己在底部让出底栏高度。留白不随页面变化，切标签时布局不会跳。
   */
  private void applyBarInset() {
    android.view.View bar = findViewById(R.id.bottom_nav);
    BackdropSourceLayout frame = findViewById(R.id.content_frame);
    if (bar == null || frame == null) return;
    findViewById(R.id.app_bar)
        .setVisibility(
            // 四个标签页都自带标题；页面还没建好（null）时也不显示，否则切页时会闪出旧标题栏。
            shownFragment() == null || shownFragment() instanceof OwnTopBar
                ? android.view.View.GONE
                : android.view.View.VISIBLE);
    boolean glass = GlassBar.INSTANCE.getEnabled();
    int height = bar.getVisibility() == android.view.View.VISIBLE ? bar.getHeight() : 0;
    frame.setCapture(glass);
    int pad = glass ? 0 : height;
    if (frame.getPaddingBottom() != pad) frame.setPadding(0, 0, 0, pad);
    GlassBar.setContentBottomPx(glass ? height : 0);
    for (Fragment page : getSupportFragmentManager().getFragments())
      if ((page instanceof PtyTerminalFragment || page instanceof TerminalFragment)
          && page.getView() != null) GlassBar.padTerminal(page.getView());
  }

  private void updateToolbar() {
    if (chrome == null) return;
    Fragment shown = shownFragment();
    applyBarInset();
    chrome.setBack(getSupportFragmentManager().getBackStackEntryCount() > 0);
    String title;
    if (shown instanceof AboutFragment) title = com.deepseekharness.app.util.UiText.choose("关于", "About");
    else if (shown instanceof OverlayFragment)
      title = com.deepseekharness.app.util.UiText.choose("悬浮条", "Floating bar");
    else if (shown instanceof ConfigFragment)
      title = com.deepseekharness.app.util.UiText.choose("高级运行选项", "Advanced options");
    else if (shown instanceof DeviceGrantsFragment)
      title = com.deepseekharness.app.util.UiText.choose("设备控制", "Device control");
    else if (shown instanceof WorkspaceFragment)
      title = com.deepseekharness.app.util.UiText.choose("备份与恢复", "Backup & restore");
    else if (shown instanceof InstallFragment)
      title = com.deepseekharness.app.util.UiText.choose("环境状态与修复", "Status & repair");
    else if (shown instanceof SettingsFragment) title = getString(R.string.ui_m0023);
    else if (shown instanceof PluginFragment) title = getString(R.string.ui_m0016);
    else if (shown instanceof TerminalFragment || shown instanceof PtyTerminalFragment)
      title = getString(R.string.ui_m0021);
    else title = getString(R.string.app_name);
    chrome.setTitle(title);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (discardedLauncherEntry) return;
    BrowserTasks.entry("RESUME", getIntent());
    // 已核验的同代网页先恢复，避免入口检查完成后遗漏唯一一次 onResume。
    if (BrowserTasks.resumeBackground(this)) return;
    if (!mainReady && !isFinishing()) {
      beginEntryCheck();
      return;
    }
    BrowserTasks.rememberMain(this);
    // 从修复页等处回来时，环境状态可能变了：插件/终端页换成对应的页面。
    refreshTabs();
    if (!new ConfigStore(this)
        .getUiLanguage()
        .equals(com.deepseekharness.app.util.UiText.language())) LanguageController.apply(this);
    recoveryHandler.removeCallbacks(refreshRecovery);
    recoveryHandler.post(refreshRecovery);
    updateToolbar();
    if (!isFinishing()
        && findViewById(R.id.bottom_nav) != null
        && (new ConfigStore(this).isLanMode()
            || com.deepseekharness.app.DeviceBridgeService.isAdbEnabled(this))
        && !com.deepseekharness.app.bridge.LocalNetworkAccess.granted(this)
        && !getSharedPreferences(com.deepseekharness.app.util.Constants.PREFS, MODE_PRIVATE)
            .getBoolean("local_network_permission_asked", false)) requestLocalNetwork();
    // Android 12+ 可能拒绝后台唤起前台服务，回到可见界面后补一次恢复。
    if (!isFinishing()
        && findViewById(R.id.bottom_nav) != null
        && com.deepseekharness.app.DeviceBridgeService.isAdbEnabled(this)
        && !com.deepseekharness.app.DeviceBridgeService.isRunning()) {
      com.deepseekharness.app.DeviceBridgeService.apply(this);
    }
    maybePromptExternalRestore();
  }

  /**
   * 卸载重装后的空环境仍给旧 Download/DSHA 备份一个明确入口。
   * 扫描本身在后台执行；无法枚举旧 MediaStore 归属时也不把「0 个」
   * 当成「没有备份」，而是提供 SAF 手动选择（issue #22）。
   */
  private void maybePromptExternalRestore() {
    if (restoreProbeStarted || isFinishing() || isDestroyed()) return;
    HarnessController controller = HarnessController.get(this);
    restoreProbeStarted = true;
    new Thread(
            () -> {
              if (!controller.isEnvironmentReady()
                  || com.deepseekharness.app.core.BackupTask.get(this).busy()
                  || com.deepseekharness.app.backup.ExternalBackupScanner.hasUserData(
                      controller.proot().getRootfsDir())) return;
              com.deepseekharness.app.backup.ExternalBackupScanner.Scan scan =
                  com.deepseekharness.app.backup.ExternalBackupScanner.scan(this);
              boolean invisible =
                  scan.total() == 0
                      && !com.deepseekharness.app.backup.ExternalBackupScanner.canSeeAllFiles();
              if (scan.total() == 0 && !invisible) return;
              String declineKey =
                  scan.best != null
                      ? scan.best.name
                      : scan.total() == 0 ? "__invisible__" : "__unreadable__" + scan.unreadable;
              android.content.SharedPreferences prefs =
                  getSharedPreferences(com.deepseekharness.app.util.Constants.PREFS, MODE_PRIVATE);
              if (declineKey.equals(prefs.getString("restore_prompt_declined", ""))) return;
              runOnUiThread(
                  () -> {
                    if (isFinishing() || isDestroyed()) return;
                    try {
                      com.deepseekharness.app.ui.DshaDialogBuilder dialog =
                          new com.deepseekharness.app.ui.DshaDialogBuilder(this);
                      if (scan.best != null) {
                        String message =
                            com.deepseekharness.app.util.UiText.format(
                                "发现已有备份：\n%s\n\n先核验内容和实际范围，确认后才会恢复。", scan.best.describe());
                        if (scan.unreadable > 0)
                          message +=
                              com.deepseekharness.app.util.UiText.format(
                                  "\n另有 %s 个备份无法自动读取，可手动选择。", scan.unreadable);
                        dialog
                            .setTitle(
                                com.deepseekharness.app.util.UiText.choose(
                                    "检测到已有备份", "Backup found"))
                            .setMessage(message)
                            .setPositiveButton(
                                com.deepseekharness.app.util.UiText.choose(
                                    "选择恢复", "Inspect and restore"),
                                (d, w) -> openBackupForRestore(scan.best))
                            .setNegativeButton(
                                com.deepseekharness.app.util.UiText.choose("忽略", "Ignore"),
                                (d, w) ->
                                    prefs
                                        .edit()
                                        .putString("restore_prompt_declined", declineKey)
                                        .apply());
                        if (scan.unreadable > 0)
                          dialog.setNeutralButton(
                              com.deepseekharness.app.util.UiText.choose("手动选择", "Choose manually"),
                              (d, w) -> pickBackupForRestore());
                      } else if (invisible) {
                        dialog
                            .setTitle(
                                com.deepseekharness.app.util.UiText.choose(
                                    "是否需要恢复以前的备份？", "Restore an older backup?"))
                            .setMessage(
                                com.deepseekharness.app.util.UiText.choose(
                                    "当前环境没有用户数据。系统暂时无法枚举卸载前的 Download/DSHA 文件；可用文件选择器直接选择备份，不需要开启所有文件访问。",
                                    "This environment has no user data. Android cannot enumerate backups from the previous installation, but the file picker can open one without all-files access."))
                            .setPositiveButton(
                                com.deepseekharness.app.util.UiText.choose("手动选择", "Choose backup"),
                                (d, w) -> pickBackupForRestore())
                            .setNegativeButton(
                                com.deepseekharness.app.util.UiText.choose("不用了", "Not now"),
                                (d, w) ->
                                    prefs
                                        .edit()
                                        .putString("restore_prompt_declined", declineKey)
                                        .apply());
                      } else {
                        dialog
                            .setTitle(
                                com.deepseekharness.app.util.UiText.choose(
                                    "备份无法自动读取", "Backup cannot be read automatically"))
                            .setMessage(
                                com.deepseekharness.app.util.UiText.choose(
                                    "请用文件选择器指定备份包。",
                                    "Choose the backup package with the file picker."))
                            .setPositiveButton(
                                com.deepseekharness.app.util.UiText.choose("手动选择", "Choose backup"),
                                (d, w) -> pickBackupForRestore())
                            .setNegativeButton(
                                com.deepseekharness.app.util.UiText.choose("忽略", "Ignore"),
                                (d, w) ->
                                    prefs
                                        .edit()
                                        .putString("restore_prompt_declined", declineKey)
                                        .apply());
                      }
                      dialog.show();
                    } catch (Throwable ignored) {
                    }
                  });
            },
            "dsha-restore-probe")
        .start();
  }

  public void pickBackupForRestore() {
    legacyBackupPicker.launch(
        new String[] {
          com.deepseekharness.app.util.BackupFileNames.MIME_TYPE,
          "application/octet-stream",
          "application/gzip",
          "application/x-gzip",
          "*/*"
        });
  }

  private void openBackupForRestore(
      com.deepseekharness.app.backup.ExternalBackupScanner.Candidate candidate) {
    if (candidate == null) return;
    android.net.Uri uri = candidate.uri;
    if (uri == null && candidate.file != null) {
      try {
        uri =
            androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".updates", candidate.file);
      } catch (IllegalArgumentException ignored) {
      }
    }
    if (uri != null) openBackupForRestore(uri);
    else pickBackupForRestore();
  }

  private void openBackupForRestore(android.net.Uri uri) {
    startActivity(
        new Intent(this, NativeDataActivity.class)
            .putExtra("restore_uri", uri.toString())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
  }

  private final android.os.Handler recoveryHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());

  /** Native plugin/settings work stays in front of a persistent startup failure. */
  static boolean startupRecoveryDestination(int selectedItem, Fragment page) {
    return selectedItem == R.id.nav_launch && (page == null || page instanceof LaunchFragment);
  }

  private final Runnable refreshRecovery =
      new Runnable() {
        @Override
        public void run() {
          TextView banner = findViewById(R.id.environment_recovery_banner);
          if (banner == null || isFinishing()) return;
          HarnessController controller = HarnessController.get(MainActivity.this);
          boolean startupRecovery =
              controller.config().isStartupRecoveryRequested()
                  || EnvironmentUiStatus.get(MainActivity.this).repairs;
          boolean limited = EnvironmentUiStatus.get(MainActivity.this).recovery;
          boolean busy =
              com.deepseekharness.app.core.BackupTask.get(MainActivity.this).maintenanceBusy();
          banner.setVisibility(
              limited || busy || startupRecovery
                  ? android.view.View.VISIBLE
                  : android.view.View.GONE);
          banner.setText(
              busy
                  ? com.deepseekharness.app.util.UiText.text("环境维护进行中 · 点击查看进度")
                  : limited
                      ? com.deepseekharness.app.util.UiText.text("受限模式：环境需要恢复 · 点击处理")
                      : com.deepseekharness.app.util.UiText.choose(
                          "启动未完成 · 点击查看恢复选项", "Startup did not complete · View recovery options"));
          com.deepseekharness.app.util.BackupTaskState.Snapshot task =
              com.deepseekharness.app.core.BackupTask.get(MainActivity.this).snapshot();
          if (busy && task.busy())
            banner.setText(
                com.deepseekharness.app.util.UiText.format(
                    "%s · 点击查看进度",
                    (task.status == com.deepseekharness.app.util.BackupTaskState.Status.PREVIEW
                        ? com.deepseekharness.app.util.UiText.choose(
                            "等待确认恢复备份", "Backup restore awaiting confirmation")
                        : com.deepseekharness.app.util.StartupText.render(task.kind))));
          String key =
              controller.startupDiagnostics().recordId()
                  + ":"
                  + controller.config().getWebFailureReason()
                  + ":"
                  + EnvironmentUiStatus.get(MainActivity.this).repairs;
          if (startupRecovery
              && !limited
              && !busy
              && !com.deepseekharness.app.core.MaintenanceCoordinator.isEnvironmentTaskBusy()
              && !controller.isStarting()
              && !controller.isStopping()
              && startupRecoveryDestination(
                  selectedTab,
                  shownFragment())
              && !key.equals(openedRecovery)
              && getLifecycle()
                  .getCurrentState()
                  .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            openedRecovery = key;
            startActivity(new Intent(MainActivity.this, StartupRecoveryActivity.class));
          }
          recoveryHandler.postDelayed(this, 1000);
        }
      };

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putString("opened_startup_recovery", openedRecovery);
    state.putInt("selected_tab", selectedTab);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onDestroy() {
    entryGate.cancel();
    if (GlassBar.getSource() == findViewById(R.id.content_frame)) GlassBar.setSource(null);
    // 主题切换/旋转只重建界面，不能把正在运行的终端一起关闭。
    if (!discardedLauncherEntry && !isChangingConfigurations()) {
      try {
        PtyTerminalFragment.shutdown();
      } catch (Throwable ignored) {
      }
      try {
        TerminalFragment.shutdownShell();
      } catch (Throwable ignored) {
      }
    }
    super.onDestroy();
  }

  public static void start(Context ctx) {
    BrowserTasks.openMain(ctx);
  }
}
