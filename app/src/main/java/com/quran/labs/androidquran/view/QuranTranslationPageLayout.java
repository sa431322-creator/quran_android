package com.quran.labs.androidquran.view;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.quran.labs.androidquran.ui.translation.TranslationView;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranSettings;

public class QuranTranslationPageLayout extends QuranPageLayout {
  private TranslationView translationView;
  // in Tasnim's ornamental page frame (a single page in portrait); a tablet's two pages have none
  private final boolean withFrame;

  public QuranTranslationPageLayout(Context context) {
    this(context, false);
  }

  public QuranTranslationPageLayout(Context context, boolean withFrame) {
    super(context);
    this.withFrame = withFrame;
    isFullWidth = true;
    initialize();
  }

  @Override
  protected boolean shouldShowFrame() {
    return withFrame;
  }

  @Override
  protected boolean shouldShowFrameTranslationsButton() {
    return true;
  }

  @Override
  protected View generateContentView(@NonNull Context context, boolean isLandscape) {
    translationView = new TranslationView(context);
    return translationView;
  }

  @Override
  public void setPageController(PageController controller, int pageNumber, int skips) {
    super.setPageController(controller, pageNumber, skips);
    translationView.setPageController(controller);
  }

  @Override
  public void updateView(@NonNull QuranSettings quranSettings) {
    super.updateView(quranSettings);
    translationView.refresh(quranSettings);
  }

  @Override
  protected void updateBackground(boolean nightMode, QuranSettings quranSettings) {
    setBackgroundColor(quranSettings.getPageThemeBackgroundColor());
  }

  @Override
  protected boolean shouldWrapWithScrollView() {
    return false;
  }

  public TranslationView getTranslationView() {
    return translationView;
  }
}
