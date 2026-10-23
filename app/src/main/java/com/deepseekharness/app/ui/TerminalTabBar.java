package com.deepseekharness.app.ui;

import com.deepseekharness.app.util.TerminalTabs;
import com.deepseekharness.app.util.UiText;
import java.util.ArrayList;
import java.util.List;

/** PTY 和简易终端共用：把会话列表转换成顶部标签（由 TerminalChrome 绘制）。 */
final class TerminalTabBar {
  private TerminalTabBar() {}

  static <T> void render(TerminalHeader header, TerminalTabs.ReadOnly<T> tabs) {
    var current = tabs.current();
    List<TerminalTabItem> items = new ArrayList<>();
    for (var tab : tabs.snapshot())
      items.add(
          new TerminalTabItem(
              tab.id,
              UiText.format(tab.isClosing() ? "终端 %s · 关闭中…" : "终端 %s", tab.number),
              current != null && current.id == tab.id,
              tab.isClosing()));
    header.setTabs(items);
  }
}
