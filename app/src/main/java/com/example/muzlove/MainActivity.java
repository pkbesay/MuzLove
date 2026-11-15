package com.example.muzlove;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.net.Uri;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    // состояние (воспроизведение — в PlayerEngine, данные пользователя — в AppState)
    private PlayerEngine engine;
    private AppState appState;
    private PlayerSheets sheets;                             // шторки и диалоги плеера
    private ListSheets lists;                                // шторки и диалоги списков
    private AppDialogs dialogs;                              // все AlertDialog приложения
    private ProfileSheets profile;                           // профиль: правка, фото, спонсорство, «О приложении»
    private SettingsScreen settings;                         // строки экрана «Настройки»
    private boolean userSeeking = false;                     // пользователь тянет ползунок или волну
    private float miniRatio = 0f;                            // прогресс для полоски мини-плеера
    private int screen = 0;                                  // 0 home, 1 search, 2 library, 3 profile, 4 settings, 5 playlist
    private boolean npOpen = false;
    private boolean npBars = false;                          // плеер раскрыт: под системными панелями тёмный фон, значки светлые
    private boolean hasPhoto = false;                        // на экране профиля показано фото (а не буква)

    // экран плейлиста и медиатека
    static final int TAB_LIKED = 0, TAB_UPLOADS = 1, TAB_PLAYLISTS = 2;
    private static int libTab = TAB_LIKED;                   // выбранная вкладка «Моё» (пока жив процесс)
    private Playlist openPl;                                 // плейлист экрана 5
    private int plFrom = 0;                                  // экран, с которого открыт плейлист
    private final Random random = new Random();

    // тема и настройки
    private boolean light = false, waveform = false;
    private int cBase, cText, cMuted, cSurface;

    // хранилище
    private StorageManager storage;

    // вью
    private View root;
    private TextView eyebrow, h1;
    private LinearLayout searchBox, playlistsRow, headerBox;
    private View searchHistoryBox;
    private LinearLayout searchHistoryList;
    private boolean headerCollapsed = false;
    private int headerFullHeight = 0;
    private ValueAnimator headerAnim;
    private View playlistsScroll;
    private EditText searchInput;
    private View searchClear;
    private View libTabsScroll;
    private TextView tabLiked, tabUploads, tabPlaylists;
    private RecyclerView recycler;
    private TrackAdapter adapter;
    private ListTopAdapter top;                              // блок над списком треков (шапка, пустое состояние)
    private PlaylistScreen playlistScreen;
    private LinearLayout bottomNav;
    private View miniPlayer;
    private View miniCover, miniProgress;
    private TextView miniTitle, miniArtist;
    private ImageView miniLike, miniPlayIcon;
    private View miniPlayBtn;
    private View npOverlay, npCover, playBtn, playBuffering;
    private SeekBar slider;
    private ImageView btnShare, btnMore, btnQueue, btnEq;
    private TextView npAlbum, npTitle, npArtist, timeCur, timeDur;
    private ImageView npLike, btnShuffle, btnRepeat, playIcon, navHomeIc, navSearchIc, navLibIc, navProfileIc;
    private TextView navHomeTx, navSearchTx, navLibTx, navProfileTx;
    private LinearLayout profileContainer, settingsContainer, settingsList;
    private View btnSettingsBack;
    private TextView settingsTitleLabel;
    private WaveformView wave;

    private final ActivityResultLauncher<String> musicPicker = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> { if (uris != null && !uris.isEmpty()) Uploader.start(this, uris); }
    );

    private final ActivityResultLauncher<String> avatarPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) handleAvatarUpload(uri); }
    );

    // без разрешения музыка играет так же, просто без уведомления; строка в настройках покажет итог
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> refreshSettings()
    );

    // UI плеера следует за движком; слушатель активен между onStart и onStop
    private final PlayerEngine.Listener engineListener = new PlayerEngine.Listener() {
        @Override public void onTrackChanged() { renderPlayer(); }
        @Override public void onPlayStateChanged() {
            updatePlayIcons();
            if (engine.isPlaying()) askNotificationsOnce();
            refreshSettings();                 // таймер сна мог включиться или сработать
        }
        @Override public void onProgress(int positionMs, int durationMs) { updateProgress(positionMs, durationMs); }
        @Override public void onQueueChanged() { updateModeIcons(); }
        @Override public void onPlaybackIssue(String message) { toast(message); }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        storage = new StorageManager(this);
        // MainActivity можно открыть напрямую (из уведомления) — без регистрации отправляем на неё
        if (!storage.isRegistered()) {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
            return;
        }
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        appState = AppState.get(this);
        engine = PlayerEngine.get(this);
        dialogs = new AppDialogs(this);
        sheets = new PlayerSheets(this);   // до onStart: регистрирует ActivityResultLauncher
        lists = new ListSheets(this);
        profile = new ProfileSheets(this);
        settings = new SettingsScreen(this);
        Uploader.attach(this);             // загрузка, начатая прежней активностью, обновит эту
        light = storage.isLight();
        waveform = storage.isWaveform();
        computeTheme();

        bind();
        setupRecycler();
        setupNav();
        setupNowPlaying();
        setupBack();

        applyWaveformSetting();

        // Обработка системных отступов (чтобы не перекрывалось виджетами телефона)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // Сдвигаем мини-плеер вниз, пока он не активен
        miniPlayer.setTranslationY(dp(80));
        // ширина мини-плеера известна только после раскладки (а на паузе тикер полоску не обновит)
        miniPlayer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) miniPlayer.post(this::applyMiniProgress);
        });

        applyTheme();      // красит интерфейс + строит ряд плейлистов
        showScreen(0);

        PlaybackService.removeStaleNotifications(this);
        if (s == null) handleOpenPlayer(getIntent());
    }

    /** Нажатие на уведомление плеера (activity singleTop — сюда, если она уже открыта). */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (engine != null) handleOpenPlayer(intent);
    }

    /** Extra «open_player»: открыть экран плеера (post — чтобы работало и до первой раскладки). */
    private void handleOpenPlayer(Intent i) {
        if (i == null || !i.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false)) return;
        i.removeExtra(PlaybackService.EXTRA_OPEN_PLAYER);
        root.post(() -> { if (engine.isStarted() && !npOpen) openNowPlaying(true); });
    }

    /** Android 13+: при первом запуске музыки один раз спрашиваем разрешение на уведомления. */
    private void askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || storage.wasNotificationsAsked()) return;
        storage.setNotificationsAsked();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        root.post(() -> {
            if (isFinishing()) return;
            try { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS); }
            catch (RuntimeException ignored) {}
        });
    }

    private void bind() {
        root = findViewById(R.id.root);
        headerBox = findViewById(R.id.headerBox);
        eyebrow = findViewById(R.id.eyebrow);
        h1 = findViewById(R.id.h1);
        searchBox = findViewById(R.id.searchBox);
        searchInput = findViewById(R.id.searchInput);
        searchClear = findViewById(R.id.searchClear);
        libTabsScroll = findViewById(R.id.libTabsScroll);
        tabLiked = findViewById(R.id.tabLiked);
        tabUploads = findViewById(R.id.tabUploads);
        tabPlaylists = findViewById(R.id.tabPlaylists);
        playlistsScroll = findViewById(R.id.playlistsScroll);
        playlistsRow = findViewById(R.id.playlistsRow);
        recycler = findViewById(R.id.recycler);
        searchHistoryBox = findViewById(R.id.searchHistoryBox);
        searchHistoryList = findViewById(R.id.searchHistoryList);

        miniPlayer = findViewById(R.id.miniPlayer);
        miniCover = findViewById(R.id.miniCover);
        miniProgress = findViewById(R.id.miniProgress);
        miniTitle = findViewById(R.id.miniTitle);
        miniArtist = findViewById(R.id.miniArtist);
        miniLike = findViewById(R.id.miniLike);
        miniPlayIcon = findViewById(R.id.miniPlayIcon);
        miniPlayBtn = findViewById(R.id.miniPlayBtn);

        bottomNav = findViewById(R.id.bottomNav);
        navHomeIc = findViewById(R.id.navHomeIc);
        navSearchIc = findViewById(R.id.navSearchIc);
        navLibIc = findViewById(R.id.navLibIc);
        navHomeTx = findViewById(R.id.navHomeTx);
        navSearchTx = findViewById(R.id.navSearchTx);
        navLibTx = findViewById(R.id.navLibTx);
        navProfileIc = findViewById(R.id.navProfileIc);
        navProfileTx = findViewById(R.id.navProfileTx);

        profileContainer = findViewById(R.id.profileContainer);
        settingsContainer = findViewById(R.id.settingsContainer);
        settingsList = findViewById(R.id.settingsList);
        btnSettingsBack = findViewById(R.id.btnSettingsBack);
        settingsTitleLabel = findViewById(R.id.settingsTitleLabel);

        // Инфлейтим макет профиля в контейнер (один раз)
        LayoutInflater.from(this).inflate(R.layout.screen_profile, (ViewGroup) profileContainer, true);

        npOverlay = findViewById(R.id.npOverlay);
        npCover = findViewById(R.id.npCover);
        npAlbum = findViewById(R.id.npAlbum);
        npTitle = findViewById(R.id.npTitle);
        npArtist = findViewById(R.id.npArtist);
        npLike = findViewById(R.id.npLike);
        timeCur = findViewById(R.id.timeCur);
        timeDur = findViewById(R.id.timeDur);
        wave = findViewById(R.id.wave);
        playBtn = findViewById(R.id.btnPlay);
        playIcon = findViewById(R.id.playIcon);
        playBuffering = findViewById(R.id.playBuffering);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnShare = findViewById(R.id.btnShare);
        btnMore = findViewById(R.id.btnMore);
        btnQueue = findViewById(R.id.btnQueue);
        btnEq = findViewById(R.id.btnEq);
        slider = findViewById(R.id.slider);
        slider.setMax(1000);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || !engine.isStarted()) return;
                int target = (int) ((progress / 1000f) * engine.durationMs());
                if (userSeeking) timeCur.setText(fmt(target / 1000));   // пока тянут — только подпись
                else engine.seekTo(target);                              // клавиатура / спецвозможности
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                if (engine.isStarted())
                    engine.seekTo((int) ((seekBar.getProgress() / 1000f) * engine.durationMs()));
            }
        });
    }

    private void setupRecycler() {
        adapter = new TrackAdapter(appState.liked(), new TrackAdapter.Callbacks() {
            @Override public void onPlay(Track t) {
                // если играем трек из результатов поиска — запоминаем запрос в истории
                if (screen == 1) {
                    String q = searchInput.getText().toString().trim();
                    if (!q.isEmpty()) storage.addSearchHistory(q);
                }
                // очередь = список, который сейчас показан
                engine.playFrom(adapter.ids(), t.id);
            }
            @Override public void onMore(Track t) {
                hideKeyboard();
                lists.showTrackMenu(t, screen == 5 && isCustom(openPl) ? openPl : null);
            }
            @Override public void onShare(Track t) { hideKeyboard(); sheets.showShare(t, !light); }
            @Override public void onAddToPlaylist(Track t) { hideKeyboard(); sheets.showAddToPlaylist(t, !light); }
        });
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        top = new ListTopAdapter(box);
        playlistScreen = new PlaylistScreen(this, box);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new ConcatAdapter(top, adapter));

        recycler.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@androidx.annotation.NonNull RecyclerView rv, int state) {
                // результаты поиска листают — клавиатура не нужна
                if (screen == 1 && state == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard();
            }

            @Override
            public void onScrolled(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                // Прячем/показываем мини-плеер
                if (engine.isStarted() && miniPlayer.getVisibility() == View.VISIBLE) {
                    if (dy > 20) {
                        miniPlayer.animate().translationY(dp(100)).setDuration(300).start();
                    } else if (dy < -20) {
                        miniPlayer.animate().translationY(0).setDuration(300).start();
                    }
                }

                // Плавно сворачиваем/разворачиваем ТОЛЬКО заголовок (плейлисты оставляем,
                // иначе список становится коротким и шапку не вернуть прокруткой вверх).
                if (screen == 0) {
                    boolean atTop = recyclerView.computeVerticalScrollOffset() <= 6;
                    if (atTop || dy < -15) {
                        animateHeader(false);   // показать
                    } else if (dy > 15) {
                        animateHeader(true);    // спрятать
                    }
                }
            }
        });
    }

    /** Плавно сворачивает (collapse=true) или разворачивает заголовок по высоте + прозрачности. */
    private void animateHeader(boolean collapse) {
        if (collapse == headerCollapsed) return;
        headerCollapsed = collapse;

        if (headerFullHeight <= 0) headerFullHeight = headerBox.getHeight();
        if (headerFullHeight <= 0) {            // ещё не измерен — без анимации
            headerBox.setVisibility(collapse ? View.GONE : View.VISIBLE);
            return;
        }

        if (headerAnim != null) headerAnim.cancel();
        headerBox.setVisibility(View.VISIBLE);

        int from = headerBox.getHeight();
        int to = collapse ? 0 : headerFullHeight;

        headerAnim = ValueAnimator.ofInt(from, to);
        headerAnim.setDuration(230);
        headerAnim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        headerAnim.addUpdateListener(a -> {
            int h = (int) a.getAnimatedValue();
            ViewGroup.LayoutParams lp = headerBox.getLayoutParams();
            lp.height = h;
            headerBox.setLayoutParams(lp);
            headerBox.setAlpha((float) h / headerFullHeight);
        });
        headerAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (headerCollapsed) {
                    headerBox.setVisibility(View.GONE);
                } else {
                    ViewGroup.LayoutParams lp = headerBox.getLayoutParams();
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    headerBox.setLayoutParams(lp);
                    headerBox.setAlpha(1f);
                }
            }
        });
        headerAnim.start();
    }

    /** Сброс заголовка в видимое состояние (при переходе на любой экран). */
    private void resetHeaderState() {
        if (headerAnim != null) headerAnim.cancel();
        headerCollapsed = false;
        ViewGroup.LayoutParams lp = headerBox.getLayoutParams();
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        headerBox.setLayoutParams(lp);
        headerBox.setAlpha(1f);
        headerBox.setVisibility(View.VISIBLE);
    }

    private void setupNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> showScreen(0));
        findViewById(R.id.navSearch).setOnClickListener(v -> showScreen(1));
        findViewById(R.id.navLibrary).setOnClickListener(v -> showScreen(2));
        findViewById(R.id.navProfile).setOnClickListener(v -> showScreen(3));
        btnSettingsBack.setOnClickListener(v -> showScreen(3)); // Назад в профиль

        tabLiked.setOnClickListener(v -> selectLibTab(TAB_LIKED));
        tabUploads.setOnClickListener(v -> selectLibTab(TAB_UPLOADS));
        tabPlaylists.setOnClickListener(v -> selectLibTab(TAB_PLAYLISTS));

        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            public void onTextChanged(CharSequence c, int a, int b, int d) {
                searchClear.setVisibility(c.length() > 0 ? View.VISIBLE : View.GONE);
                if (screen == 1) applySearch(c.toString());
            }
            public void afterTextChanged(Editable e) {}
        });

        // «Найти» на клавиатуре: запрос — в историю, клавиатуру спрятать
        searchInput.setOnEditorActionListener((tv, actionId, e) -> {
            boolean enter = e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId != EditorInfo.IME_ACTION_SEARCH && !enter) return false;
            if (e == null || e.getAction() == KeyEvent.ACTION_DOWN) {
                String q = searchInput.getText().toString().trim();
                if (!q.isEmpty()) storage.addSearchHistory(q);
                hideKeyboard();
            }
            return true;
        });
        searchClear.setOnClickListener(v -> {
            searchInput.setText("");          // TextWatcher покажет историю
            searchInput.requestFocus();
        });

        miniPlayer.setOnClickListener(v -> { if (engine.isStarted()) openNowPlaying(true); });
        miniLike.setOnClickListener(v -> toggleLike());
        miniPlayBtn.setOnClickListener(v -> engine.toggle());
    }

    /** Системная кнопка «Назад» (onBackPressed() на Android 16 с targetSdk 36 не вызывается). */
    private void setupBack() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (npOpen) {
                    openNowPlaying(false);
                    showScreen(0);                 // как кнопка закрытия плеера
                } else if (screen == 5) {
                    closePlaylist();               // туда, откуда открыли плейлист
                } else if (screen == 4) {
                    showScreen(3);                 // из настроек — в профиль
                } else if (screen != 0) {
                    showScreen(0);
                } else {
                    moveTaskToBack(true);          // музыка и состояние экрана сохраняются
                }
            }
        });
    }

    private void setupNowPlaying() {
        npOverlay.setVisibility(View.VISIBLE);
        npOverlay.setTranslationY(getResources().getDisplayMetrics().heightPixels);

        // Делаем фон непрозрачным для кликов, чтобы они не пролетали на задний план
        npOverlay.setClickable(true);
        npOverlay.setFocusable(true);

        findViewById(R.id.npClose).setOnClickListener(v -> {
            openNowPlaying(false);
            showScreen(0); // При закрытии плеера перекидываем на главную
        });
        playBtn.setOnClickListener(v -> engine.toggle());
        findViewById(R.id.btnNext).setOnClickListener(v -> engine.next());
        findViewById(R.id.btnPrev).setOnClickListener(v -> engine.prev());
        btnShuffle.setOnClickListener(v -> engine.toggleShuffle());
        btnRepeat.setOnClickListener(v -> engine.cycleRepeat());
        // шторки с экрана плеера всегда тёмные, как и сам плеер
        btnShare.setOnClickListener(v -> sheets.showShare(engine.current(), true));
        btnMore.setOnClickListener(v -> sheets.showMore(engine.current(), true));
        btnQueue.setOnClickListener(v -> sheets.showQueue(true));
        btnEq.setOnClickListener(v -> sheets.showEqualizer(true));
        npLike.setOnClickListener(v -> toggleLike());
        // волна: как ползунок — пока тянут, двигается только подпись, перемотка при отпускании
        wave.setOnSeekListener(new WaveformView.OnSeekListener() {
            @Override public void onStartSeek() { userSeeking = true; }
            @Override public void onSeekMove(float ratio) {
                if (engine.isStarted()) timeCur.setText(fmt((int) (ratio * engine.durationMs()) / 1000));
            }
            @Override public void onStopSeek(float ratio) {
                userSeeking = false;
                if (engine.isStarted()) engine.seekTo((int) (ratio * engine.durationMs()));
            }
            @Override public void onCancelSeek() {
                userSeeking = false;
                updateProgress(engine.positionMs(), engine.durationMs());
            }
        });
    }

    /** «Звуковая волна»: вкл — волна вместо ползунка, выкл — ползунок. */
    private void applyWaveformSetting() {
        wave.setVisibility(waveform ? View.VISIBLE : View.GONE);
        slider.setVisibility(waveform ? View.GONE : View.VISIBLE);
    }

    private void rebuildPlaylistsRow() {
        playlistsRow.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (Playlist p : appState.allPlaylists()) {
            View card = inf.inflate(R.layout.item_playlist, playlistsRow, false);
            View art = card.findViewById(R.id.plArt);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{p.c1, p.c2});
            g.setCornerRadius(dp(16));
            art.setBackground(g);
            // отдельная иконка плейлистов (вместо обычной нотки)
            if (art instanceof ViewGroup) {
                View ico = ((ViewGroup) art).getChildAt(0);
                if (ico instanceof ImageView) ((ImageView) ico).setImageResource(R.drawable.ic_playlist);
            }
            TextView nm = card.findViewById(R.id.plName); nm.setText(Playlists.name(p.name)); nm.setTextColor(cText);
            String desc = playlistDesc(p);
            TextView ds = card.findViewById(R.id.plDesc); ds.setText(desc); ds.setTextColor(cMuted);
            card.setContentDescription(Playlists.name(p.name) + ", " + desc);
            pressFeedback(art, true);
            final Playlist fp = p;
            card.setOnClickListener(v -> openPlaylist(fp));
            if (isCustom(p)) card.setOnLongClickListener(v -> { lists.showPlaylistMenu(fp); return true; });
            playlistsRow.addView(card);
        }

        // последняя карточка — «Новый плейлист»
        View add = inf.inflate(R.layout.item_playlist, playlistsRow, false);
        View art = add.findViewById(R.id.plArt);
        art.setBackground(ListViews.outline(light, dp(16), dp(1.5f)));
        if (art instanceof ViewGroup) {
            View ico = ((ViewGroup) art).getChildAt(0);
            if (ico instanceof ImageView) {
                ((ImageView) ico).setImageResource(R.drawable.ic_plus);
                ((ImageView) ico).setColorFilter(cMuted);
            }
        }
        TextView nm = add.findViewById(R.id.plName); nm.setText("Новый плейлист"); nm.setTextColor(cText);
        TextView ds = add.findViewById(R.id.plDesc); ds.setText("Соберите свой"); ds.setTextColor(cMuted);
        add.setContentDescription("Новый плейлист");
        pressFeedback(art, !light);
        add.setOnClickListener(v -> createPlaylist(null, !light));
        playlistsRow.addView(add);
    }

    /** Рябь на обложке карточки при нажатии на всю карточку. */
    private void pressFeedback(View art, boolean onDark) {
        if (!(art instanceof FrameLayout)) return;
        art.setDuplicateParentStateEnabled(true);
        ((FrameLayout) art).setForeground(ListViews.rippleRounded(onDark, dp(16)));
    }

    /** Подпись плейлиста: у своих всегда посчитанное число треков (сохранённое desc может устареть). */
    private String playlistDesc(Playlist p) {
        if (isCustom(p) || p.desc == null) return Texts.tracks(playlistTracks(p).size());
        return p.desc;
    }

    boolean isCustom(Playlist p) { return p != null && appState.customPlaylists().contains(p); }

    /** Треки плейлиста, которые есть в приложении, по порядку и без повторов. */
    List<Track> playlistTracks(Playlist p) {
        List<Track> out = new ArrayList<>();
        if (p == null || p.ids == null) return out;
        Set<Integer> seen = new HashSet<>();
        for (int id : p.ids) {
            Track t = appState.find(id);
            if (t != null && seen.add(id)) out.add(t);
        }
        return out;
    }

    /** «Слушать» (shuffle=false — с первого трека) или «Перемешать» (со случайного). Если этот трек
     *  уже текущий, он не ставится на паузу: очередь становится плейлистом, а трек играет дальше. */
    void startPlaylist(Playlist p, boolean shuffle) {
        List<Integer> ids = new ArrayList<>();
        for (Track t : playlistTracks(p)) ids.add(t.id);
        if (ids.isEmpty()) { toast("В плейлисте нет треков"); return; }
        engine.setShuffle(shuffle);
        int start = shuffle ? ids.get(random.nextInt(ids.size())) : ids.get(0);
        engine.playContext(ids, start);
    }

    // ---------- экраны ----------
    private void showScreen(int s) {
        if (s == 5 && openPl == null) s = 0;
        if (s != 5) openPl = null;
        if (screen == 1 && s != 1) hideKeyboard();
        boolean changed = s != screen;
        screen = s;
        updateNavTints();
        applyScreenVisibility(s);
        if (changed) recycler.scrollToPosition(0);
    }

    /** Экран плейлиста; «Назад» вернёт на экран, с которого его открыли. */
    void openPlaylist(Playlist p) {
        if (p == null) return;
        if (npOpen) openNowPlaying(false);
        if (screen != 5) plFrom = screen == 4 ? 3 : screen;
        openPl = p;
        hideKeyboard();
        screen = 5;
        updateNavTints();
        applyScreenVisibility(5);
        recycler.scrollToPosition(0);
    }

    void closePlaylist() {
        openPl = null;
        showScreen(plFrom);
    }

    /** «Моё» с нужной вкладкой (карточки профиля, конец загрузки). */
    void showLibrary(int tab) {
        boolean changed = libTab != tab;
        libTab = tab;
        showScreen(2);
        if (changed) recycler.scrollToPosition(0);
    }

    private void selectLibTab(int tab) {
        if (libTab == tab) return;
        libTab = tab;
        fillContent();
        recycler.scrollToPosition(0);
    }

    private void setupProfileContent() {
        ImageView avatarIv = findViewById(R.id.profileAvatarImage);
        TextView avatarTxt = findViewById(R.id.profileAvatar);
        TextView nameTv = findViewById(R.id.profileName);
        TextView userTv = findViewById(R.id.profileBio);
        TextView likedCount = findViewById(R.id.profileLikedCount);
        TextView playlistCount = findViewById(R.id.profilePlaylistsCount);

        String nm = storage.getName();
        String avUri = storage.getAvatarUri();
        if (avUri != null && !Avatar.exists(this, avUri)) {     // файл пропал: забываем о нём, рисуем букву
            storage.setAvatarUri(null);
            avUri = null;
        }
        Bitmap photo = avUri != null ? Avatar.load(this, avUri, Math.round(dp(100))) : null;
        hasPhoto = photo != null;

        if (hasPhoto) {
            avatarIv.setVisibility(View.VISIBLE);
            avatarTxt.setVisibility(View.GONE);
            avatarIv.setImageBitmap(photo);

            // Скругление для ImageView
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(Color.LTGRAY);
            avatarIv.setBackground(shape);
            avatarIv.setClipToOutline(true);
            avatarIv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
        } else {
            avatarIv.setImageDrawable(null);
            avatarIv.setVisibility(View.GONE);
            avatarTxt.setVisibility(View.VISIBLE);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF8B5CF6, 0xFFEC4899});
            g.setShape(GradientDrawable.OVAL);
            avatarTxt.setBackground(g);
            avatarTxt.setText(Username.initial(nm));
        }

        if (nameTv != null) nameTv.setText(nm);
        if (userTv != null) userTv.setText("@" + storage.getUsername());
        if (likedCount != null) likedCount.setText(String.valueOf(appState.liked().size()));
        if (playlistCount != null)
            playlistCount.setText(String.valueOf(appState.allPlaylists().size()));

        View stat1 = findViewById(R.id.statCard1);
        if (stat1 != null) stat1.setOnClickListener(v -> showLibrary(TAB_LIKED));

        View stat2 = findViewById(R.id.statCard2);
        if (stat2 != null) stat2.setOnClickListener(v -> showLibrary(TAB_PLAYLISTS));

        findViewById(R.id.btnCreatePlaylist).setOnClickListener(v -> createPlaylist(null, !light));
        findViewById(R.id.btnUploadMusic).setOnClickListener(v -> pickMusicFile());
        findViewById(R.id.btnSettingsInProfile).setOnClickListener(v -> showScreen(4));
        findViewById(R.id.btnSponsorship).setOnClickListener(v -> profile.showSponsor());

        View avContainer = findViewById(R.id.profileAvatarContainer);
        if (avContainer != null) avContainer.setOnClickListener(v -> {
            if (hasPhoto) profile.showAvatarMenu();
            else pickAvatar();
        });

        View edit = findViewById(R.id.btnEditName);
        if (edit != null) edit.setOnClickListener(v -> profile.showEditProfile());

        applyProfileTheme();
    }

    /** Поиск по всем трекам (встроенным и загруженным): название, исполнитель, альбом. */
    private void applySearch(String q) {
        top.box().removeAllViews();
        String raw = q.trim();
        String qq = raw.toLowerCase(Locale.ROOT);
        if (qq.isEmpty()) {
            h1.setText("Поиск");
            adapter.setData(new ArrayList<>());
            showSearchHistory(true);     // показать недавние запросы
            return;
        }
        showSearchHistory(false);        // спрятать историю, показать результаты
        h1.setText("Результаты");
        List<Track> res = new ArrayList<>();
        for (Track t : appState.allTracks()) {
            String hay = (t.title + " " + t.artist + " " + t.album).toLowerCase(Locale.ROOT);
            if (hay.contains(qq)) res.add(t);
        }
        if (res.isEmpty()) {
            top.box().addView(views().emptyState("Ничего не найдено",
                    "По запросу «" + raw + "» нет ни треков, ни исполнителей, ни альбомов. Попробуйте изменить запрос.",
                    null, 0, null), Sheet.matchWrap());
        }
        adapter.setData(res);
        adapter.setCurrent(currentId());
    }

    /** Строители блоков над списком в цветах текущей темы. */
    private ListViews views() { return new ListViews(this, light, cText, cMuted); }

    private void showSearchHistory(boolean show) {
        if (searchHistoryBox == null) return;
        if (screen != 1) {               // история только на экране поиска
            searchHistoryBox.setVisibility(View.GONE);
            return;
        }
        if (show) {
            buildSearchHistory();
            searchHistoryBox.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            searchHistoryBox.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }
    }

    private void buildSearchHistory() {
        searchHistoryList.removeAllViews();
        List<String> hist = storage.getSearchHistory();

        // строка-заголовок: "Недавнее" + "Очистить"
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, (int) dp(6), 0, (int) dp(6));

        TextView lbl = new TextView(this);
        lbl.setText(hist.isEmpty() ? "Нет недавних запросов" : "Недавнее");
        lbl.setTextColor(cMuted);
        lbl.setTextSize(13);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(lbl);

        if (!hist.isEmpty()) {
            TextView clear = new TextView(this);
            clear.setText("Очистить");
            clear.setTextColor(cText);
            clear.setTextSize(13);
            clear.setPadding((int) dp(10), (int) dp(4), (int) dp(4), (int) dp(4));
            clear.setClickable(true);
            clear.setFocusable(true);
            clear.setOnClickListener(v -> { storage.clearSearchHistory(); buildSearchHistory(); });
            head.addView(clear);
        }
        searchHistoryList.addView(head);

        for (String term : hist) {
            final String t = term;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = (int) dp(12);
            row.setPadding(0, pad, 0, pad);
            row.setClickable(true);
            row.setFocusable(true);

            ImageView ic = new ImageView(this);
            ic.setImageResource(R.drawable.ic_history);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams((int) dp(18), (int) dp(18));
            ilp.rightMargin = (int) dp(14);
            ic.setLayoutParams(ilp);
            ic.setColorFilter(cMuted);

            TextView tv = new TextView(this);
            tv.setText(t);
            tv.setTextColor(cText);
            tv.setTextSize(15);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView go = new ImageView(this);
            go.setImageResource(R.drawable.ic_search);
            go.setLayoutParams(new LinearLayout.LayoutParams((int) dp(16), (int) dp(16)));
            go.setColorFilter(cMuted);

            row.addView(ic);
            row.addView(tv);
            row.addView(go);
            row.setOnClickListener(v -> {
                searchInput.setText(t);
                searchInput.setSelection(t.length());   // applySearch сработает через TextWatcher
            });
            // долгое нажатие убирает запрос из истории
            row.setOnLongClickListener(v -> {
                storage.removeSearchHistory(t);
                buildSearchHistory();
                toast("Запрос удалён из истории");
                return true;
            });
            row.setContentDescription(t);
            row.setBackground(ListViews.ripple(light, true));
            searchHistoryList.addView(row);
        }
    }

    // ---------- настройки ----------
    private void setupSettingsContent() { settings.fill(settingsList); }

    /** Строки настроек обновляются, только пока открыт экран «Настройки» (шторки, ответ на запрос разрешения). */
    void refreshSettings() {
        if (screen == 4 && settings != null && !isFinishing() && !isDestroyed()) setupSettingsContent();
    }

    void toggleTheme() {
        light = !light;
        storage.setLight(light);
        applyTheme();
        setupSettingsContent();
    }

    void toggleWaveform() {
        waveform = !waveform;
        storage.setWaveform(waveform);
        applyWaveformSetting();
        setupSettingsContent();
    }

    boolean isWaveform() { return waveform; }

    /** Имя или @username изменились. */
    void onProfileChanged() {
        if (screen == 3) setupProfileContent();
        if (screen == 4) setupSettingsContent();
    }

    /** Запрос разрешения на уведомления из «Настроек» (Android 13+). */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    void requestNotificationPermission() {
        try { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS); }
        catch (RuntimeException e) { toast("Не удалось открыть запрос разрешения"); }
    }

    /** Сброс приложения: то, что живёт в памяти процесса дольше активности. */
    void onAppReset() { libTab = TAB_LIKED; }

    View settingRow(String title, String value, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) dp(18), padV = (int) dp(16);
        row.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cSurface); bg.setCornerRadius(dp(14));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) dp(12);
        row.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title); t.setTextColor(cText); t.setTextSize(16);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(value); v.setTextColor(cMuted); v.setTextSize(14);

        row.addView(t); row.addView(v);
        row.setClickable(true); row.setFocusable(true);
        row.setOnClickListener(x -> onClick.run());
        return row;
    }

    // ---------- плейлисты и загрузка ----------
    /** Создание плейлиста (Профиль, карточка на Главной, «Моё», выбор плейлиста для трека forTrack). */
    void createPlaylist(Track forTrack, boolean dark) { lists.createPlaylist(forTrack, dark); }

    /** «Загрузить трек»: системный выбор одного или нескольких аудиофайлов. */
    private void pickMusicFile() {
        try { musicPicker.launch("audio/*"); }
        catch (ActivityNotFoundException e) { toast("Нет подходящего приложения"); }
    }

    /** Конец загрузки (Uploader): новые треки — открыть «Моё» / «Загруженные». */
    void onUploadsFinished(int added) {
        if (added > 0) showLibrary(TAB_UPLOADS);
        else refreshVisible();
    }

    /** Выбор фото профиля (системный выбор картинки). */
    void pickAvatar() {
        try { avatarPicker.launch("image/*"); }
        catch (ActivityNotFoundException e) { toast("Нет подходящего приложения"); }
    }

    /** «Удалить фото»: файл стирается, снова буква. */
    void removeAvatar() {
        Avatar.delete(this, storage.getAvatarUri());
        storage.setAvatarUri(null);
        setupProfileContent();
        toast("Фото удалено");
    }

    private void handleAvatarUpload(Uri uri) {
        try {
            String old = storage.getAvatarUri();
            storage.setAvatarUri(Avatar.save(this, uri));     // копия во внутренней памяти — постоянный доступ
            Avatar.delete(this, old);                         // прежний файл больше не нужен
            setupProfileContent();
        } catch (IOException | RuntimeException e) {
            toast("Ошибка при загрузке фото");
        }
    }

    private void updateNavTints() {
        int on = light ? Color.BLACK : cText;
        int off = light ? 0x60000000 : 0xFF7B7796;
        int tab = screen == 5 ? plFrom : screen;   // на экране плейлиста горит вкладка, откуда пришли
        navHomeIc.setColorFilter(tab == 0 ? on : off);
        navSearchIc.setColorFilter(tab == 1 ? on : off);
        navLibIc.setColorFilter(tab == 2 ? on : off);
        navProfileIc.setColorFilter(tab == 3 ? on : off);
        navHomeTx.setTextColor(tab == 0 ? on : off);
        navSearchTx.setTextColor(tab == 1 ? on : off);
        navLibTx.setTextColor(tab == 2 ? on : off);
        navProfileTx.setTextColor(tab == 3 ? on : off);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
    }

    // ---------- воспроизведение (команды — в PlayerEngine) ----------
    private int currentId() {
        Track t = engine.current();
        return t != null ? t.id : -1;
    }

    /** Текущий трек, а если ничего не загружено — первый (декоративный фон тёмной темы). */
    private Track currentOrFirst() {
        Track t = engine.current();
        if (t != null) return t;
        List<Track> all = appState.allTracks();
        return all.isEmpty() ? Data.TRACKS.get(0) : all.get(0);
    }

    /** Сердечко мини-плеера и экрана плеера: текущий трек. */
    private void toggleLike() {
        Track t = engine.current();
        if (t != null) toggleLike(t.id);
    }

    /** Лайк любого трека (меню трека, плеер): всё видимое обновляется. */
    void toggleLike(int id) {
        appState.toggleLike(id);
        refreshVisible();
    }

    /** Удаление загруженного трека: из очереди (до удаления файла), из данных, затем перерисовка. */
    void deleteUploadedTrack(int id) {
        engine.removeTrack(id);             // текущий — сменится следующим; единственный — плеер остановится
        if (appState.removeUploaded(id)) {
            for (Playlist p : appState.customPlaylists()) p.desc = Texts.tracks(p.ids != null ? p.ids.length : 0);
            appState.savePlaylists();
            toast("Трек удалён");
        }
        refreshVisible();
    }

    /** После любого изменения данных: ряд плейлистов, текущий список, профиль, сердечки. */
    void refreshVisible() {
        rebuildPlaylistsRow();
        if (screen == 5 && !appState.allPlaylists().contains(openPl)) {
            closePlaylist();                // плейлист удалён
        } else if (screen <= 2 || screen == 5) {
            fillContent();
        }
        if (screen == 3) setupProfileContent();
        updateLikeIcons();
    }

    // ---------- обновление UI ----------
    /** Полная отрисовка плеера по состоянию движка (onStart и смена трека). */
    private void renderPlayer() {
        Track t = engine.current();
        if (t == null) {
            if (npOpen) openNowPlaying(false);
            miniPlayer.animate().cancel();
            miniPlayer.setVisibility(View.GONE);
            miniPlayer.setTranslationY(dp(80));
            adapter.setCurrent(-1);
            updatePlayIcons();
            updateModeIcons();
            if (!light) updateLiveBackground(currentOrFirst());
            return;
        }
        updateNowPlayingUI(t);
        updateLiveBackground(t);
        adapter.setCurrent(t.id);
        updatePlayIcons();
        updateModeIcons();
        updateProgress(engine.positionMs(), engine.durationMs());
        openMini();
    }

    private void updateProgress(int posMs, int durMs) {
        float ratio = durMs > 0 ? Math.max(0f, Math.min(1f, (float) posMs / durMs)) : 0;
        timeDur.setText(fmt(durMs / 1000));
        if (!userSeeking) {                 // пока тянут ползунок, прогресс его не двигает
            wave.setProgress(ratio);
            slider.setProgress((int) (ratio * 1000));
            timeCur.setText(fmt(posMs / 1000));
        }
        miniRatio = ratio;
        applyMiniProgress();
    }

    private void applyMiniProgress() {
        ViewGroup.LayoutParams lp = miniProgress.getLayoutParams();
        int w = (int) (miniRatio * miniPlayer.getWidth());
        if (lp.width == w) return;
        lp.width = w;
        miniProgress.setLayoutParams(lp);
    }

    private void updatePlayIcons() {
        boolean playing = engine.isPlaying();
        int icon = playing ? R.drawable.ic_pause : R.drawable.ic_play;
        playIcon.setImageResource(icon);
        miniPlayIcon.setImageResource(icon);
        playIcon.setColorFilter(Color.parseColor("#0A0913"));
        miniPlayIcon.setColorFilter(Color.parseColor("#0A0913"));
        // пока трек готовится — крутилка в большой кнопке вместо значка (мини-плеер как был)
        boolean buffering = engine.isBuffering();
        playBuffering.setVisibility(buffering ? View.VISIBLE : View.GONE);
        playIcon.setVisibility(buffering ? View.INVISIBLE : View.VISIBLE);
        String cd = playing ? "Пауза" : "Воспроизвести";
        playBtn.setContentDescription(cd);
        miniPlayBtn.setContentDescription(cd);
    }

    /** Плеер всегда тёмный: активные кнопки жёлтые, неактивные — полупрозрачные белые. */
    private void updateModeIcons() {
        int accent = 0xFFF4FE52, off = 0xB3FFFFFF;
        btnShuffle.setColorFilter(engine.isShuffle() ? accent : off);
        int r = engine.repeatMode();
        btnRepeat.setImageResource(r == 2 ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        btnRepeat.setColorFilter(r != 0 ? accent : off);
    }

    private void updateLikeIcons() {
        Track t = engine.current();
        if (t == null) return;
        boolean isLiked = appState.isLiked(t.id);
        npLike.setColorFilter(isLiked ? 0xFFF4FE52 : Color.WHITE);
        miniLike.setColorFilter(isLiked ? t.c1 : (light ? 0xFF15131F : 0xFFC9C5DE));
        String cd = isLiked ? "Убрать из любимых" : "Добавить в любимые";
        npLike.setContentDescription(cd);
        miniLike.setContentDescription(cd);
    }

    private void updateNowPlayingUI(Track t) {
        GradientDrawable cover = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.c1, t.c2});
        cover.setCornerRadius(dp(26));
        npCover.setBackground(cover);

        GradientDrawable mini = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.c1, t.c2});
        mini.setCornerRadius(dp(10));
        miniCover.setBackground(mini);

        npTitle.setText(t.title); npArtist.setText(t.artist); npAlbum.setText(t.album);
        miniTitle.setText(t.title); miniArtist.setText(t.artist);
        miniProgress.setBackgroundColor(t.c1);

        wave.setSeed(t.id);
        wave.setColors(t.c1, 0x29FFFFFF);

        updateLikeIcons();

        npOverlay.setBackground(playerBackground());
    }

    /** Приятный тёмный градиент для фона плеера (и для области за системными панелями, пока плеер открыт). */
    private static GradientDrawable playerBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1A1826, 0xFF121019, 0xFF0D0C14});
    }

    private void updateLiveBackground(Track t) {
        if (npBars) {                       // плеер всегда тёмный: за панелями виден его фон
            root.setBackground(playerBackground());
            return;
        }
        if (light) {
            root.setBackgroundColor(Color.WHITE);
            return;
        }
        int a1 = 0x55, a2 = 0x22;
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{blend(t.c1, a1), blend(t.c2, a2), cBase});
        root.setBackground(bg);
    }

    private void openMini() {
        if (miniPlayer.getVisibility() != View.VISIBLE) {
            miniPlayer.setVisibility(View.VISIBLE);
            miniPlayer.animate().translationY(0).setDuration(300).start();
        }
    }

    private void openNowPlaying(boolean open) {
        npOpen = open;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        if (open) {
            // панели темнеют, когда плеер выехал: пока он едет, под ними ещё светлый экран
            npOverlay.animate().translationY(0).setDuration(340).withEndAction(() -> {
                if (!npOpen || isDestroyed()) return;
                npBars = true;
                applySystemBars();
            }).start();
        } else {
            npBars = false;                 // обратно — сразу, плеер уезжает и открывает светлый экран
            applySystemBars();
            npOverlay.animate().translationY(screenH).setDuration(340).start();
        }
    }

    private void applyScreenVisibility(int s) {
        recycler.setVisibility(View.GONE);
        profileContainer.setVisibility(View.GONE);
        settingsContainer.setVisibility(View.GONE);
        playlistsScroll.setVisibility(View.GONE);
        searchBox.setVisibility(View.GONE);
        eyebrow.setVisibility(View.GONE);
        searchHistoryBox.setVisibility(View.GONE);
        libTabsScroll.setVisibility(View.GONE);
        resetHeaderState(); // шапка всегда видна и развёрнута при открытии экрана

        if (s == 0) {
            eyebrow.setVisibility(View.VISIBLE);
            eyebrow.setText(greeting());
            h1.setText("Что послушаем?");
            playlistsScroll.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.VISIBLE);
        } else if (s == 1) {
            h1.setText("Поиск");
            searchBox.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.VISIBLE);
        } else if (s == 2) {
            h1.setText("Моя медиатека");
            libTabsScroll.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.VISIBLE);
        } else if (s == 3) {
            h1.setText("Профиль");
            profileContainer.setVisibility(View.VISIBLE);
            setupProfileContent();
        } else if (s == 4) {
            h1.setText("Настройки");
            settingsContainer.setVisibility(View.VISIBLE);
            setupSettingsContent();
        } else if (s == 5) {
            headerBox.setVisibility(View.GONE);   // у плейлиста своя шапка в списке
            recycler.setVisibility(View.VISIBLE);
        }
        fillContent();
    }

    /** Список и блок над ним для текущего экрана (без сброса шапки и прокрутки). */
    private void fillContent() {
        top.box().removeAllViews();
        if (screen == 0) adapter.setData(Data.TRACKS);
        else if (screen == 1) applySearch(searchInput.getText().toString());
        else if (screen == 2) fillLibrary();
        else if (screen == 5) fillPlaylist();
        else return;                        // профиль и настройки — без списка
        adapter.setCurrent(currentId());
    }

    private void fillLibrary() {
        styleLibTabs();
        ListViews v = views();
        LinearLayout box = top.box();
        List<Track> list = new ArrayList<>();
        if (libTab == TAB_LIKED) {
            for (Track t : appState.allTracks()) if (appState.isLiked(t.id)) list.add(t);
            if (list.isEmpty())
                box.addView(v.emptyState(null, "Здесь появятся треки, которые вы отметите сердечком",
                        null, 0, null), Sheet.matchWrap());
        } else if (libTab == TAB_UPLOADS) {
            list.addAll(appState.uploaded());
            if (list.isEmpty())
                box.addView(v.emptyState(null, "Загрузите свою музыку с телефона",
                        "Загрузить трек", R.drawable.ic_upload, this::pickMusicFile), Sheet.matchWrap());
            else box.addView(v.actionRow(R.drawable.ic_upload, "Загрузить трек", this::pickMusicFile));
        } else {
            box.addView(v.actionRow(R.drawable.ic_plus, "Новый плейлист", () -> createPlaylist(null, !light)));
            for (Playlist p : appState.allPlaylists()) {
                boolean custom = isCustom(p);
                box.addView(v.playlistRow(p, playlistDesc(p), () -> openPlaylist(p),
                        custom ? () -> lists.showPlaylistMenu(p) : null));
            }
        }
        adapter.setData(list);
    }

    private void fillPlaylist() {
        List<Track> tracks = playlistTracks(openPl);
        playlistScreen.render(openPl, tracks, isCustom(openPl), light, cText, cMuted);
        top.box().addView(playlistScreen.view());
        adapter.setData(tracks);
    }

    /** Вкладки «Моё»: выбранная — «таблетка» цвета текста, остальные — на лёгкой подложке. */
    private void styleLibTabs() {
        TextView[] tabs = {tabLiked, tabUploads, tabPlaylists};
        for (int i = 0; i < tabs.length; i++) {
            boolean on = i == libTab;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(18));
            g.setColor(on ? cText : (light ? 0x0F000000 : 0x17FFFFFF));
            tabs[i].setBackground(g);
            tabs[i].setForeground(ListViews.rippleRounded(on == light, dp(18)));   // светлая рябь на тёмной таблетке
            tabs[i].setTextColor(on ? cBase : cText);
            tabs[i].setSelected(on);
            tabs[i].setContentDescription(tabs[i].getText() + (on ? ", выбрано" : ""));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (engine == null) return;           // onCreate отправил на регистрацию
        engine.addListener(engineListener);
        renderPlayer();                       // движок мог играть, пока активности не было
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSettings();                    // из системных настроек уведомлений мог вернуться другой ответ
    }

    @Override
    protected void onStop() {
        if (engine != null) engine.removeListener(engineListener);
        super.onStop();                       // воспроизведение продолжается
    }

    @Override
    protected void onDestroy() {
        if (sheets != null) sheets.dismissAll();   // окна шторок и их слушатели движка не переживают активность
        if (lists != null) lists.dismissAll();
        if (profile != null) profile.dismissAll();
        if (dialogs != null) dialogs.dismissAll();
        super.onDestroy();
    }

    // ---------- для PlayerSheets, ListSheets и PlaylistScreen ----------
    PlayerEngine engine() { return engine; }
    AppState appState() { return appState; }
    PlayerSheets playerSheets() { return sheets; }
    ListSheets lists() { return lists; }
    AppDialogs dialogs() { return dialogs; }
    ProfileSheets profileSheets() { return profile; }
    /** true — светлая тема приложения (шторки вне плеера следуют ей: dark = !isLightTheme()). */
    boolean isLightTheme() { return light; }

    /** Свои плейлисты изменились: всё видимое (ряд на Главной, «Моё», экран плейлиста, профиль). */
    void onPlaylistsChanged() { refreshVisible(); }

    // ---------- тема ----------
    private void computeTheme() {
        if (light) { cBase = 0xFFFFFFFF; cText = 0xFF15131F; cMuted = 0xFF5D5A72; cSurface = 0xFFF2F1F6; }
        else { cBase = 0xFF0A0913; cText = 0xFFF3F1FA; cMuted = 0xFFA09CB8; cSurface = 0xFF161422; }
    }

    private void applyTheme() {
        computeTheme();
        applySystemBars();                 // панели, значки на них и фон под ними (светлая тема — белая)

        h1.setTextColor(cText);
        eyebrow.setTextColor(cMuted);
        int iconColor = light ? Color.BLACK : cText;

        GradientDrawable sb = new GradientDrawable();
        sb.setColor(light ? 0x14000000 : 0x17FFFFFF);
        sb.setCornerRadius(dp(14));
        searchBox.setBackground(sb);
        searchInput.setTextColor(cText);
        searchInput.setHintTextColor(cMuted);
        if (searchClear instanceof ImageView) ((ImageView) searchClear).setColorFilter(cMuted);
        searchClear.setBackground(ListViews.ripple(light, false));

        bottomNav.setBackgroundColor(cBase);
        updateNavTints();

        if (settingsTitleLabel != null) settingsTitleLabel.setTextColor(cText);
        if (btnSettingsBack instanceof ImageView) ((ImageView) btnSettingsBack).setColorFilter(iconColor);

        GradientDrawable mb = new GradientDrawable();
        mb.setColor(cSurface);
        mb.setCornerRadius(dp(18));
        miniPlayer.setBackground(mb);
        miniTitle.setTextColor(cText);
        miniArtist.setTextColor(cMuted);

        adapter.setLight(light);
        updateLikeIcons();                 // цвет сердечка мини-плеера зависит от темы
        rebuildPlaylistsRow();
        applyProfileTheme();
    }

    /** Системные панели: цвет (до Android 15; дальше они прозрачны и виден фон root), светлые или тёмные значки
     *  и фон под панелями. Тёмные значки — только в светлой теме и пока плеер закрыт (он всегда тёмный). */
    @SuppressWarnings("deprecation")
    private void applySystemBars() {
        boolean lightBars = light && !npBars;
        android.view.Window w = getWindow();
        w.setStatusBarColor(npBars ? 0xFF1A1826 : cBase);
        w.setNavigationBarColor(npBars ? 0xFF0D0C14 : cBase);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) w.setNavigationBarContrastEnforced(false);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
        c.setAppearanceLightStatusBars(lightBars);
        c.setAppearanceLightNavigationBars(lightBars);
        if (root == null) return;
        if (light && !npBars) root.setBackgroundColor(cBase);       // светлая тема полностью белая
        else updateLiveBackground(currentOrFirst());
    }

    private void applyProfileTheme() {
        setTextColorIf(R.id.profileName, cText);
        setTextColorIf(R.id.profileBio, cMuted);
        setTextColorIf(R.id.profileLikedCount, cText);
        setTextColorIf(R.id.likedLabel, cMuted);
        setTextColorIf(R.id.profilePlaylistsCount, cText);
        setTextColorIf(R.id.playlistsLabel, cMuted);
        setTextColorIf(R.id.createText, cText);
        setTextColorIf(R.id.uploadText, cText);
        setTextColorIf(R.id.settingsText, cText);
        setTextColorIf(R.id.sponsorText, cText);
        setCardBg(R.id.statCard1); setCardBg(R.id.statCard2);
        setCardBg(R.id.btnCreatePlaylist); setCardBg(R.id.btnUploadMusic); setCardBg(R.id.btnSettingsInProfile);
        setCardBg(R.id.btnSponsorship);
        int iconColor = light ? Color.BLACK : cText;
        tintIf(R.id.createIcon, iconColor); tintIf(R.id.uploadIcon, iconColor); tintIf(R.id.settingsIcon, iconColor);
        tintIf(R.id.sponsorIcon, iconColor);
        tintIf(R.id.btnEditName, iconColor);
    }

    private void setTextColorIf(int id, int color) {
        View v = findViewById(id);
        if (v instanceof TextView) ((TextView) v).setTextColor(color);
    }

    private void tintIf(int id, int color) {
        View v = findViewById(id);
        if (v instanceof ImageView) ((ImageView) v).setColorFilter(color);
    }

    private void setCardBg(int id) {
        View v = findViewById(id);
        if (v != null) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(cSurface);
            g.setCornerRadius(dp(16));
            v.setBackground(g);
        }
    }

    // ---------- утилиты ----------
    private String greeting() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (h < 6) return "Доброй ночи";
        if (h < 12) return "Доброе утро";
        if (h < 18) return "Добрый день";
        return "Добрый вечер";
    }

    static String tracksWord(int n) { return Texts.plural(n, "трек", "трека", "треков"); }

    void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private static String fmt(int s) {
        if (s < 0) s = 0;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private static int blend(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}