package com.deepseekharness.app.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 欢迎引导（3 页）：第 3 页点「开始」或「跳过」进入环境准备。
 */
public class WelcomeActivity extends AppCompatActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(
        WelcomeScreenKt.welcomePage(
            this, () -> startActivity(new Intent(this, OnboardingPrepareActivity.class))));
  }
}
