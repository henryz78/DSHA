package com.deepseekharness.app.ui;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.deepseekharness.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 所有应用内弹窗共用 Material 容器、排版、按钮和窗口动画，不接管业务监听器。 */
public final class DshaDialogBuilder extends MaterialAlertDialogBuilder {
  private final boolean safetyConfirmation;
  private final long consentRevision;
  private android.content.DialogInterface.OnClickListener positive;
  private static final java.util.Map<AlertDialog, Long> safetyDialogs =
      new java.util.WeakHashMap<>();

  public DshaDialogBuilder(Context context) {
    this(context, false);
  }

  /** Only explicit yes/no safety approvals opt in. Parameter, scope and /ask dialogs never do. */
  public DshaDialogBuilder(Context context, boolean safetyConfirmation) {
    super(context, R.style.Dialog_DSHA_Material);
    // miuix 风格：大圆角卡片，距屏幕边缘 12dp，在 create() 里贴底显示。
    int inset = dp(context, 12);
    android.graphics.drawable.GradientDrawable card = new android.graphics.drawable.GradientDrawable();
    card.setColor(context.getColor(R.color.card));
    card.setCornerRadius(dp(context, 32));
    setBackground(card);
    setBackgroundInsetStart(inset);
    setBackgroundInsetEnd(inset);
    setBackgroundInsetTop(inset);
    setBackgroundInsetBottom(inset);
    this.safetyConfirmation = safetyConfirmation;
    consentRevision =
        safetyConfirmation
            ? new com.deepseekharness.app.core.DeviceGrants(context).consentPolicy().revision()
            : -1;
  }

  @Override
  public DshaDialogBuilder setPositiveButton(
      CharSequence text, android.content.DialogInterface.OnClickListener listener) {
    positive = listener;
    super.setPositiveButton(
        text,
        listener == null
            ? null
            : (dialog, which) -> {
              if (!safetyConfirmation
                  || consentRevision
                      == new com.deepseekharness.app.core.DeviceGrants(getContext())
                          .consentPolicy()
                          .revision()) listener.onClick(dialog, which);
            });
    return this;
  }

  @Override
  public DshaDialogBuilder setPositiveButton(
      int text, android.content.DialogInterface.OnClickListener listener) {
    return setPositiveButton(getContext().getText(text), listener);
  }

  @Override
  public AlertDialog show() {
    var policy =
        safetyConfirmation
            ? new com.deepseekharness.app.core.DeviceGrants(getContext()).consentPolicy()
            : null;
    if (policy != null && policy.revision() != consentRevision) return create();
    if (positive != null && policy != null && policy.currentGlobal(consentRevision)) {
      AlertDialog dialog = create();
      // This builder exists only because the user already requested its concrete action.
      if (new com.deepseekharness.app.core.DeviceGrants(getContext())
          .consentPolicy()
          .currentGlobal(consentRevision))
        positive.onClick(dialog, android.content.DialogInterface.BUTTON_POSITIVE);
      return dialog;
    }
    AlertDialog dialog = super.show();
    applyMiuixLook(dialog);
    return dialog;
  }

  /** 标题居中，按钮改成并排等宽的圆角按钮：确认为主色，其余为灰色。 */
  private static void applyMiuixLook(AlertDialog dialog) {
    Context context = dialog.getContext();
    android.widget.TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
    if (title != null) {
      // 标题控件默认按内容宽度排在左侧，先撑满整行再居中。
      android.view.ViewGroup.LayoutParams params = title.getLayoutParams();
      if (params != null) {
        params.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        if (params instanceof android.widget.LinearLayout.LayoutParams linear) linear.weight = 1f;
        title.setLayoutParams(params);
      }
      title.setGravity(android.view.Gravity.CENTER);
      title.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
    }
    android.widget.TextView message = dialog.findViewById(android.R.id.message);
    // 短提示居中；长段落保持左对齐，居中的长文不好读。
    if (message != null && message.getText() != null && message.getText().length() <= 60)
      message.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
    android.view.View spacer = dialog.findViewById(androidx.appcompat.R.id.spacer);
    if (spacer != null) spacer.setVisibility(android.view.View.GONE);
    int[] order = {
      android.content.DialogInterface.BUTTON_NEUTRAL,
      android.content.DialogInterface.BUTTON_NEGATIVE,
      android.content.DialogInterface.BUTTON_POSITIVE
    };
    for (int which : order) {
      android.widget.Button button = dialog.getButton(which);
      if (button == null || button.getVisibility() != android.view.View.VISIBLE) continue;
      boolean primary = which == android.content.DialogInterface.BUTTON_POSITIVE;
      int fill = context.getColor(primary ? R.color.primary : R.color.secondary_button);
      int text = primary ? context.getColor(R.color.accent_on) : context.getColor(R.color.text);
      int[][] states = {{-android.R.attr.state_enabled}, {}};
      android.graphics.drawable.GradientDrawable shape =
          new android.graphics.drawable.GradientDrawable();
      shape.setCornerRadius(dp(context, 16));
      shape.setColor(
          new android.content.res.ColorStateList(
              states, new int[] {(fill & 0x00FFFFFF) | 0x66000000, fill}));
      button.setBackground(
          new android.graphics.drawable.RippleDrawable(
              android.content.res.ColorStateList.valueOf(context.getColor(R.color.ripple)),
              shape,
              null));
      button.setTextColor(
          new android.content.res.ColorStateList(
              states, new int[] {(text & 0x00FFFFFF) | 0x80000000, text}));
      button.setMinHeight(dp(context, 52));
      button.setTextSize(17);
      button.setAllCaps(false);
      button.setStateListAnimator(null);
      android.widget.LinearLayout.LayoutParams params =
          new android.widget.LinearLayout.LayoutParams(
              0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      params.setMarginStart(dp(context, 6));
      params.setMarginEnd(dp(context, 6));
      button.setLayoutParams(params);
    }
  }

  private static int dp(Context context, int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  public static void invalidateSafetyConfirmations() {
    new android.os.Handler(android.os.Looper.getMainLooper())
        .post(
            () -> {
              for (var entry : new java.util.ArrayList<>(safetyDialogs.entrySet())) {
                AlertDialog dialog = entry.getKey();
                if (dialog != null
                    && entry.getValue()
                        != new com.deepseekharness.app.core.DeviceGrants(dialog.getContext())
                            .consentPolicy()
                            .revision()) {
                  if (dialog.isShowing()) dialog.cancel();
                  safetyDialogs.remove(dialog);
                }
              }
            });
  }

  @Override
  public AlertDialog create() {
    AlertDialog dialog = super.create();
    if (safetyConfirmation) safetyDialogs.put(dialog, consentRevision);
    if (dialog.getWindow() != null) {
      android.view.Window window = dialog.getWindow();
      window.setWindowAnimations(R.style.Animation_DSHA_Dialog);
      // 手机竖屏贴底、铺满宽度（两侧留白由背景内边距提供），与 miuix 对话框一致。
      if (getContext().getResources().getConfiguration().screenWidthDp < 600) {
        window.setGravity(android.view.Gravity.BOTTOM);
        window.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
      }
      // 只在窗口第一次真正显示后设置尺寸。持续监听布局会在动画、输入法和
      // CardSheet 的底部测量期间反复调用 setLayout，导致 Language 首帧停顿。
      window
          .getDecorView()
          .post(
              () -> {
                if (dialog.isShowing() && window.getDecorView().getWindowToken() != null)
                  sizeOnce(window, window.getDecorView());
              });
    }
    return dialog;
  }

  private void sizeOnce(android.view.Window window, android.view.View view) {
    android.view.WindowManager.LayoutParams attributes = window.getAttributes();
    int gravity = attributes.gravity;
    // CardSheet.show() 在 show() 后同步设置底部和 MATCH_PARENT；不能把它改回中心窄窗。
    if ((gravity & android.view.Gravity.VERTICAL_GRAVITY_MASK) == android.view.Gravity.BOTTOM
        || attributes.width == android.view.ViewGroup.LayoutParams.MATCH_PARENT) return;
    {
      android.content.res.Resources resources = getContext().getResources();
      float density = resources.getDisplayMetrics().density;
      int width =
          Math.min(
              Math.round(560 * density),
              Math.min(
                  resources.getDisplayMetrics().widthPixels,
                  Math.round(resources.getConfiguration().screenWidthDp * density)));
      int maxHeight = Math.round((resources.getConfiguration().screenHeightDp - 24) * density);
      int height = view.getHeight() > maxHeight ? maxHeight : attributes.height;
      if (width != attributes.width || height != attributes.height) window.setLayout(width, height);
    }
  }
}
