package com.deepseekharness.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.deepseekharness.app.R
import com.deepseekharness.app.ui.compose.HydshTheme

/** 设置首页：miuix 实现；子页面暂时沿用旧界面。 */
class SettingsFragment : Fragment() {
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    val nav =
      SettingsNavigation(
        openFragment = { page ->
          UiMotion.page(requireContext(), parentFragmentManager.beginTransaction())
            .replace(R.id.fragment_container, page)
            .addToBackStack("settings")
            .commit()
        },
        openActivity = { activity -> startActivity(Intent(requireContext(), activity)) },
      )
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent { HydshTheme { SettingsScreen(nav) } }
    }
  }
}
