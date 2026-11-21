package com.quran.labs.androidquran.view;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranSettings;

/**
 * Generic layout class for a single page of the Quran.
 * <p>
 * The actual contents of the page are determined by the subclass, which specifies the view to
 * display with {@link #generateContentView(Context, boolean)}. If the device is landscape and
 * {@link #shouldWrapWithScrollView()} returns true, then the view is wrapped with an
 * {@link ObservableScrollView}. Margins get displayed around the page, along with an appropriate
 * background.
 */
public abstract class QuranPageLayout extends QuranPageWrapperLayout
    implements ObservableScrollView.OnScrollListener {

  @IntDef( { BorderMode.HIDDEN, BorderMode.LIGHT, BorderMode.DARK, BorderMode.LINE } )
  @interface BorderMode {
    int HIDDEN = 0;
    int LIGHT = 1;
    int DARK = 2;
    int LINE = 3;
  }

  private static PaintDrawable leftGradient;
  private static PaintDrawable rightGradient;
  // Tasnim's page theme: its paper, shaded from the spine like the light page
  private static PaintDrawable tasnimLeftGradient;
  private static PaintDrawable tasnimRightGradient;
  private static int gradientForNumberOfPages;
  private static boolean areGradientsLandscape;
  private static BitmapDrawable leftPageBorder;
  private static BitmapDrawable rightPageBorder;
  private static BitmapDrawable leftPageBorderNight;
  private static BitmapDrawable rightPageBorderNight;

  private static int lineColor;
  private static ShapeDrawable lineDrawable;

  protected Context context;
  protected PageController pageController;
  protected int pageNumber;
  protected boolean shouldHideLine;
  protected boolean isFullWidth;
  private int skippedPages;

  private ObservableScrollView scrollView;
  private @BorderMode int leftBorder;
  private @BorderMode int rightBorder;
  private View innerView;
  private int viewPaddingSmall;
  private int viewPaddingLarge;

  // Tasnim's ornamental frame around a single portrait page (null where there is no room for it)
  @Nullable private TasnimPageFrame frame;
  private final RectF frameContent = new RectF();
  private ImageButton frameMenuButton;
  private ImageButton frameSearchButton;

  public QuranPageLayout(Context context) {
    super(context);
    this.context = context;
  }

  public void initialize() {
    ViewCompat.setLayoutDirection(this, ViewCompat.LAYOUT_DIRECTION_LTR);
    Resources resources = context.getResources();
    final boolean isLandscape =
        resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    innerView = generateContentView(context, isLandscape);
    viewPaddingSmall = resources.getDimensionPixelSize(R.dimen.page_margin_small);
    viewPaddingLarge = resources.getDimensionPixelSize(R.dimen.page_margin_large);

    LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    if (isLandscape && shouldWrapWithScrollView()) {
      scrollView = new ObservableScrollView(context);
      scrollView.setFillViewport(true);
      addView(scrollView, lp);
      scrollView.addView(innerView, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
      scrollView.setOnScrollListener(this);
    } else {
      addView(innerView, lp);
    }

    if (areGradientsLandscape != isLandscape) {
      leftGradient = null;
      rightGradient = null;
      tasnimLeftGradient = null;
      tasnimRightGradient = null;
      areGradientsLandscape = isLandscape;
    }

    if (lineDrawable == null) {
      lineDrawable = new ShapeDrawable(new RectShape());
      lineDrawable.setIntrinsicWidth(1);
      lineDrawable.setIntrinsicHeight(1);

      // these bitmaps are 11x1, so fairly small to keep both day and night versions around
      leftPageBorder = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.border_left));
      leftPageBorderNight = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.night_left_border));
      rightPageBorder = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.border_right));
      rightPageBorderNight = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.night_right_border));
    }

    if (!isLandscape && shouldShowFrame()) {
      frame = new TasnimPageFrame(context);
      frameMenuButton = frameButton(R.drawable.ic_tasnim_menu, R.string.menu_settings, true);
      frameSearchButton = frameButton(R.drawable.ic_tasnim_search, R.string.menu_search, false);
      // keep the frame's header and footer clear of the screen's cutouts (a camera hole)
      ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
        androidx.core.view.DisplayCutoutCompat cutout = insets.getDisplayCutout();
        int top = cutout == null ? 0 : cutout.getSafeInsetTop();
        int bottom = cutout == null ? 0 : cutout.getSafeInsetBottom();
        if (frame != null && frame.setInsets(top, bottom)) {
          requestLayout();
          invalidate();
        }
        return insets;
      });
    }

    updateGradients();
    setWillNotDraw(false);
  }

  /** Whether this kind of page takes the ornamental frame in portrait (the mushaf's own pages). */
  protected boolean shouldShowFrame() {
    return false;
  }

  /** Whether the ornamental frame is drawn around this page (it then shows the page's labels). */
  public boolean isFramed() {
    return frame != null;
  }

  /** The sura, juz and page number in the frame's header and footer. */
  public void setFrameLabels(String sura, String juz, String page) {
    if (frame == null) return;
    frame.setSura(sura);
    frame.setJuz(juz);
    frame.setPage(page);
    invalidate();
  }

  private ImageButton frameButton(int icon, int description, boolean isMenu) {
    ImageButton button = new ImageButton(context);
    button.setImageResource(icon);
    button.setContentDescription(context.getString(description));
    button.setScaleType(ImageButton.ScaleType.CENTER);
    button.setOnClickListener(v -> {
      if (context instanceof PageFrameHost) {
        if (isMenu) {
          ((PageFrameHost) context).onPageFrameMenu();
        } else {
          ((PageFrameHost) context).onPageFrameSearch();
        }
      }
    });
    addView(button, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    return button;
  }

  private void styleFrameButtons(boolean nightMode) {
    if (frame == null) return;
    int ring = ContextCompat.getColor(context,
        nightMode ? R.color.page_frame_band_night : R.color.page_frame_band);
    int face = ContextCompat.getColor(context,
        nightMode ? R.color.page_frame_button_night : R.color.page_frame_button);
    int gold = ContextCompat.getColor(context,
        nightMode ? R.color.mushaf_gilt_night : R.color.page_frame_gold);
    int icon = ContextCompat.getColor(context,
        nightMode ? R.color.mushaf_gilt_night : R.color.page_frame_band);
    int inset = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f,
        getResources().getDisplayMetrics()));
    for (ImageButton button : new ImageButton[] { frameMenuButton, frameSearchButton }) {
      GradientDrawable outer = new GradientDrawable();
      outer.setShape(GradientDrawable.OVAL);
      outer.setColor(ring);
      GradientDrawable inner = new GradientDrawable();
      inner.setShape(GradientDrawable.OVAL);
      inner.setColor(face);
      inner.setStroke(Math.max(1, inset / 2), gold);
      LayerDrawable background = new LayerDrawable(new Drawable[] { outer, inner });
      background.setLayerInset(1, inset, inset, inset, inset);
      button.setBackground(background);
      button.setColorFilter(icon);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    View view = resolveView();
    if (view != null && frame != null) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int height = MeasureSpec.getSize(heightMeasureSpec);
      frame.content(width, height, frameContent);
      view.measure(
          MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(frameContent.width())), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(frameContent.height())), MeasureSpec.EXACTLY));
      int size = MeasureSpec.makeMeasureSpec(frame.getButtonSize(), MeasureSpec.EXACTLY);
      frameMenuButton.measure(size, size);
      frameSearchButton.measure(size, size);
    } else if (view != null) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int height = MeasureSpec.getSize(heightMeasureSpec);
      int leftLineWidth = leftBorder == BorderMode.LINE ? 1 : getBorderWidth(leftPageBorder);
      int rightLineWidth = rightBorder == BorderMode.HIDDEN ?
          0 : getBorderWidth(rightPageBorder);
      width = width - (leftLineWidth + rightLineWidth);
      if (!isFullWidth) {
        int headerFooterHeight = 0;
        width = width - (viewPaddingSmall + viewPaddingLarge);
        height = height - 2 * headerFooterHeight;
      }
      view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
  }

  private int getBorderWidth(Drawable drawable) {
    if (drawable == lineDrawable)
      return lineDrawable.getIntrinsicWidth();

    // enlarge the book side indicator to a sensible device-independent width
    return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
        13f,
        getResources().getDisplayMetrics());
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    View view = resolveView();
    if (view != null && frame != null) {
      frame.content(getMeasuredWidth(), getMeasuredHeight(), frameContent);
      view.layout(Math.round(frameContent.left), Math.round(frameContent.top),
          Math.round(frameContent.right), Math.round(frameContent.bottom));
      int top = frame.buttonTop();
      int inset = frame.buttonInset();
      int size = frame.getButtonSize();
      int width = getMeasuredWidth();
      frameSearchButton.layout(inset, top, inset + size, top + size);
      frameMenuButton.layout(width - inset - size, top, width - inset, top + size);
      super.onLayout(changed, l, t, r, b);
    } else if (view != null) {
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      @Px int leftLineWidth = leftBorder == BorderMode.LINE ?
          1 : getBorderWidth(leftPageBorder);
      @Px int rightLineWidth = rightBorder == BorderMode.HIDDEN ?
          0 : getBorderWidth(rightPageBorder);
      int headerFooterHeight = 0;
      view.layout(leftLineWidth, headerFooterHeight,
          width - rightLineWidth, height - headerFooterHeight);
      super.onLayout(changed, l, t, r, b);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int width = getWidth();
    if (width > 0 && frame != null) {
      frame.draw(canvas, width, getHeight());
    } else if (width > 0) {
      int height = getHeight();
      if (leftBorder != BorderMode.LINE || !shouldHideLine) {
        Drawable left = leftBorder == BorderMode.LINE ? lineDrawable :
            leftBorder == BorderMode.LIGHT ? leftPageBorder : leftPageBorderNight;
        left.setBounds(0, 0, getBorderWidth(left), height);
        left.draw(canvas);
      }

      if (rightBorder != BorderMode.HIDDEN) {
        Drawable right = rightBorder == BorderMode.LIGHT ? rightPageBorder : rightPageBorderNight;
        right.setBounds(width - getBorderWidth(right), 0, width, height);
        right.draw(canvas);
      }
    }
  }

  protected abstract View generateContentView(@NonNull Context context, boolean isLandscape);

  protected boolean shouldWrapWithScrollView() {
    return true;
  }

  private View resolveView() {
    return scrollView != null ? scrollView : innerView;
  }

  public void setPageController(PageController controller, int pageNumber, int skippedPages) {
    this.pageNumber = pageNumber;
    this.pageController = controller;
    this.skippedPages = skippedPages;
  }

  protected int getPagesVisible() {
    return 1;
  }

  private void updateGradients() {
    int pagesVisible = getPagesVisible();
    if (rightGradient == null || gradientForNumberOfPages != pagesVisible) {
      final WindowManager mgr =
          (WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
      Display display = mgr.getDefaultDisplay();
      int width = QuranDisplayHelper.getWidthKitKat(display);
      width = width / pagesVisible;
      final int[] colors = new int[] {
          ContextCompat.getColor(context, R.color.page_gradient_spine),
          ContextCompat.getColor(context, R.color.page_gradient_inner),
          ContextCompat.getColor(context, R.color.page_gradient_middle),
          ContextCompat.getColor(context, R.color.page_gradient_edge) };
      leftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
      rightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
      tasnimLeftGradient = QuranDisplayHelper.getPaintDrawable(width, 0, colors);
      tasnimRightGradient = QuranDisplayHelper.getPaintDrawable(0, width, colors);
      gradientForNumberOfPages = pagesVisible;
    }
  }

  @Override
  public void updateView(@NonNull QuranSettings quranSettings) {
    super.updateView(quranSettings);
    boolean nightMode = quranSettings.isNightMode();
    int lineColor = Color.BLACK;
    final int nightModeTextBrightness = nightMode ?
        quranSettings.getPageTextBrightness() : Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
    if (nightMode) {
      lineColor = Color.argb(nightModeTextBrightness, 255, 255, 255);
    }

    if ((pageNumber + skippedPages) % 2 == 0) {
      leftBorder = nightMode ? BorderMode.DARK : BorderMode.LIGHT;
      rightBorder = BorderMode.HIDDEN;
    } else {
      rightBorder = nightMode ? BorderMode.DARK : BorderMode.LIGHT;
      if (QuranPageLayout.lineColor != lineColor) {
        QuranPageLayout.lineColor = lineColor;
        lineDrawable.getPaint().setColor(lineColor);
      }
      leftBorder = BorderMode.LINE;
    }

    updateBackground(nightMode, quranSettings);
    if (frame != null) {
      frame.setNightMode(nightMode);
      styleFrameButtons(nightMode);
      invalidate();
    }
  }

  protected void updateBackground(boolean nightMode, QuranSettings quranSettings) {
    if (quranSettings.isTasnimPage()) {
      setBackgroundDrawable((pageNumber % 2 == 0 ? tasnimLeftGradient : tasnimRightGradient));
    } else if (!quranSettings.isLightPage()) {
      setBackgroundColor(quranSettings.getPageThemeBackgroundColor());
    } else if (quranSettings.useOldBackground()) {
      setBackgroundColor(ContextCompat.getColor(context, com.quran.mobile.feature.pagetheme.R.color.page_background));
    } else {
      setBackgroundDrawable((pageNumber % 2 == 0 ? leftGradient : rightGradient));
    }
  }

  @Override
  void handleRetryClicked() {
    if (pageController != null) {
      pageController.handleRetryClicked();
    }
  }

  public int getCurrentScrollY() {
    return scrollView == null ? 0 : scrollView.getScrollY();
  }

  public boolean canScroll() {
    return scrollView != null;
  }

  public void smoothScrollLayoutTo(int y) {
    scrollView.smoothScrollTo(scrollView.getScrollX(), y);
  }

  @Override
  public void onScrollChanged(ObservableScrollView scrollView,
      int x, int y, int oldx, int oldy) {
    if (pageController != null) {
      pageController.onScrollChanged(y);
    }
  }
}
