package com.example.muzlove;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // состояние (воспроизведение — в PlayerEngine, данные пользователя — в AppState)
    private PlayerEngine engine;
    private AppState appState;
    private boolean userSeeking = false;                     // пользователь тянет ползунок
    private float miniRatio = 0f;                            // прогресс для полоски мини-плеера
    private int screen = 0;                                  // 0 home, 1 search, 2 library, 3 profile, 4 settings
    private final List<Integer> screenHistory = new ArrayList<>();
    private boolean npOpen = false;

    // тема и настройки
    private boolean light = false, waveform = true;
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
    private RecyclerView recycler;
    private TrackAdapter adapter;
    private LinearLayout bottomNav;
    private View miniPlayer;
    private View miniCover, miniProgress;
    private TextView miniTitle, miniArtist;
    private ImageView miniLike, miniPlayIcon;
    private View npOverlay, npCover, playBtn;
    private SeekBar slider;
    private ImageView btnShare, btnMore, btnQueue;
    private TextView npAlbum, npTitle, npArtist, timeCur, timeDur;
    private ImageView npLike, btnShuffle, btnRepeat, playIcon, navHomeIc, navSearchIc, navLibIc, navProfileIc;
    private TextView navHomeTx, navSearchTx, navLibTx, navProfileTx;
    private LinearLayout profileContainer, settingsContainer, settingsList;
    private View btnSettingsBack;
    private TextView settingsTitleLabel;
    private WaveformView wave;

    private final ActivityResultLauncher<String> musicPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) handleMusicUpload(uri); }
    );

    private final ActivityResultLauncher<String> avatarPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) handleAvatarUpload(uri); }
    );

    // ответ не важен: без разрешения музыка играет так же, просто без уведомления
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {}
    );

    // UI плеера следует за движком; слушатель активен между onStart и onStop
    private final PlayerEngine.Listener engineListener = new PlayerEngine.Listener() {
        @Override public void onTrackChanged() { renderPlayer(); }
        @Override public void onPlayStateChanged() {
            updatePlayIcons();
            if (engine.isPlaying()) askNotificationsOnce();
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
        light = storage.isLight();
        waveform = storage.isWaveform();
        computeTheme();

        bind();
        setupRecycler();
        setupNav();
        setupNowPlaying();
        setupBack();

        wave.setSimpleMode(!waveform);

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
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnShare = findViewById(R.id.btnShare);
        btnMore = findViewById(R.id.btnMore);
        btnQueue = findViewById(R.id.btnQueue);
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
        adapter = new TrackAdapter(appState.liked(), t -> {
            // если играем трек из результатов поиска — запоминаем запрос в истории
            if (screen == 1) {
                String q = searchInput.getText().toString().trim();
                if (!q.isEmpty()) storage.addSearchHistory(q);
            }
            // очередь = список, который сейчас показан
            engine.playFrom(adapter.ids(), t.id);
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        recycler.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
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

        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            public void onTextChanged(CharSequence c, int a, int b, int d) { applySearch(c.toString()); }
            public void afterTextChanged(Editable e) {}
        });

        searchInput.setOnEditorActionListener((tv, actionId, e) -> {
            String q = searchInput.getText().toString().trim();
            if (!q.isEmpty()) storage.addSearchHistory(q);
            return false;
        });

        miniPlayer.setOnClickListener(v -> { if (engine.isStarted()) openNowPlaying(true); });
        miniLike.setOnClickListener(v -> toggleLike());
        miniPlayIcon.setOnClickListener(v -> engine.toggle());
    }

    /** Системная кнопка «Назад» (onBackPressed() на Android 16 с targetSdk 36 не вызывается). */
    private void setupBack() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (npOpen) {
                    openNowPlaying(false);
                    showScreen(0);                 // как кнопка закрытия плеера
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
        btnShare.setOnClickListener(v -> showShareSheet());
        btnMore.setOnClickListener(v -> showMoreSheet());
        btnQueue.setOnClickListener(v -> { openNowPlaying(false); showScreen(2); });
        npLike.setOnClickListener(v -> toggleLike());
        wave.setOnSeek(ratio -> engine.seekTo((int) (ratio * engine.durationMs())));
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
            TextView nm = card.findViewById(R.id.plName); nm.setText(p.name); nm.setTextColor(cText);
            TextView ds = card.findViewById(R.id.plDesc); ds.setText(p.desc); ds.setTextColor(cMuted);
            final Playlist fp = p;
            card.setOnClickListener(v -> playPlaylist(fp));
            playlistsRow.addView(card);
        }
    }

    /** Карточка плейлиста: очередь = треки плейлиста, которые есть в приложении. */
    private void playPlaylist(Playlist p) {
        List<Integer> ids = new ArrayList<>();
        if (p.ids != null) for (int id : p.ids) if (appState.find(id) != null) ids.add(id);
        if (ids.isEmpty()) { toast("В плейлисте нет треков"); return; }
        engine.playFrom(ids, ids.get(0));
    }

    // ---------- экраны ----------
    private void showScreen(int s) {
        if (screen != s) {
            screenHistory.add(screen);
            if (screenHistory.size() > 10) screenHistory.remove(0);
        }
        screen = s;
        updateNavTints();
        applyScreenVisibility(s);
    }

    private void setupProfileContent() {
        ImageView avatarIv = findViewById(R.id.profileAvatarImage);
        TextView avatarTxt = findViewById(R.id.profileAvatar);
        TextView nameTv = findViewById(R.id.profileName);
        TextView likedCount = findViewById(R.id.profileLikedCount);
        TextView playlistCount = findViewById(R.id.profilePlaylistsCount);

        String nm = storage.getName();
        String avUri = storage.getAvatarUri();

        if (avUri != null) {
            avatarIv.setVisibility(View.VISIBLE);
            avatarTxt.setVisibility(View.GONE);
            avatarIv.setImageURI(Uri.parse(avUri));

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
            avatarIv.setVisibility(View.GONE);
            avatarTxt.setVisibility(View.VISIBLE);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF8B5CF6, 0xFFEC4899});
            g.setShape(GradientDrawable.OVAL);
            avatarTxt.setBackground(g);
            avatarTxt.setText(nm.isEmpty() ? "M" : nm.substring(0, 1).toUpperCase(Locale.ROOT));
        }

        if (nameTv != null) nameTv.setText(nm);
        if (likedCount != null) likedCount.setText(String.valueOf(appState.liked().size()));
        if (playlistCount != null)
            playlistCount.setText(String.valueOf(appState.allPlaylists().size()));

        View stat1 = findViewById(R.id.statCard1);
        if (stat1 != null) stat1.setOnClickListener(v -> showScreen(2)); // К Любимым (Медиатека)

        View stat2 = findViewById(R.id.statCard2);
        if (stat2 != null) stat2.setOnClickListener(v -> showScreen(0)); // К плейлистам (Главная)

        findViewById(R.id.btnCreatePlaylist).setOnClickListener(v -> createPlaylistDialog());
        findViewById(R.id.btnUploadMusic).setOnClickListener(v -> pickMusicFile());
        findViewById(R.id.btnSettingsInProfile).setOnClickListener(v -> showScreen(4));
        findViewById(R.id.btnSponsorship).setOnClickListener(v -> toast("Временно недоступно"));

        View avContainer = findViewById(R.id.profileAvatarContainer);
        if (avContainer != null) avContainer.setOnClickListener(v -> avatarPicker.launch("image/*"));

        View edit = findViewById(R.id.btnEditName);
        if (edit != null) edit.setOnClickListener(v -> editNameDialog());

        applyProfileTheme();
    }

    private void applySearch(String q) {
        String qq = q.trim().toLowerCase(Locale.ROOT);
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
        adapter.setData(res);
        adapter.setCurrent(currentId());
    }

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
            searchHistoryList.addView(row);
        }
    }

    // ---------- настройки ----------
    private void setupSettingsContent() {
        settingsList.removeAllViews();
        settingsList.addView(settingRow("Тёмная тема", !light ? "Вкл" : "Выкл", () -> {
            light = !light; storage.setLight(light); applyTheme(); setupSettingsContent();
        }));
        settingsList.addView(settingRow("Звуковая волна", waveform ? "Вкл" : "Выкл", () -> {
            waveform = !waveform; storage.setWaveform(waveform); wave.setSimpleMode(!waveform); setupSettingsContent();
        }));
        settingsList.addView(settingRow("Изменить имя", storage.getName(), this::editNameDialog));
        settingsList.addView(settingRow("О приложении", "Resonance 1.7", () ->
                new AlertDialog.Builder(this).setTitle("Resonance")
                        .setMessage("Музыкальный плеер.\nВерсия 1.7\n(Автоскрытие шапки и настроек)").setPositiveButton("OK", null).show()));
    }

    private View settingRow(String title, String value, Runnable onClick) {
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

    private void editNameDialog() {
        final EditText in = new EditText(this);
        in.setText(storage.getName());
        in.setTextColor(cText);
        in.setHintTextColor(cMuted);
        FrameLayout wrap = new FrameLayout(this);
        int p = (int) dp(20);
        wrap.setPadding(p, (int) dp(8), p, 0);
        wrap.addView(in);
        new AlertDialog.Builder(this)
                .setTitle("Имя профиля")
                .setView(wrap)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (n.isEmpty()) n = "Меломан";
                    storage.setName(n);
                    if (screen == 3) setupProfileContent();
                    if (screen == 4) setupSettingsContent();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ---------- плейлисты и загрузка ----------
    private void createPlaylistDialog() {
        final EditText in = new EditText(this);
        in.setHint("Название плейлиста");
        in.setTextColor(cText);
        in.setHintTextColor(cMuted);
        FrameLayout wrap = new FrameLayout(this);
        int p = (int) dp(20);
        wrap.setPadding(p, (int) dp(8), p, 0);
        wrap.addView(in);
        new AlertDialog.Builder(this)
                .setTitle("Новый плейлист")
                .setView(wrap)
                .setPositiveButton("Создать", (d, w) -> {
                    String name = in.getText().toString().trim();
                    if (name.isEmpty()) name = "Мой плейлист";
                    List<Integer> ids = new ArrayList<>(appState.liked());
                    if (ids.isEmpty())
                        for (int i = 0; i < Math.min(3, Data.TRACKS.size()); i++) ids.add(Data.TRACKS.get(i).id);
                    int[] arr = new int[ids.size()];
                    for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
                    Playlist pl = new Playlist(name, ids.size() + " " + tracksWord(ids.size()),
                            "#8B5CF6", "#EC4899", arr);
                    appState.customPlaylists().add(pl);
                    appState.savePlaylists();
                    rebuildPlaylistsRow();
                    if (screen == 3) setupProfileContent();
                    toast("Плейлист создан");
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void pickMusicFile() { musicPicker.launch("audio/*"); }

    private void handleMusicUpload(Uri uri) {
        String name = queryName(uri);
        File dest = new File(getFilesDir(), "track_" + System.currentTimeMillis() + ".audio");
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) { toast("Не удалось открыть файл"); return; }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            toast("Не удалось загрузить файл");
            return;
        }
        Track t = new Track(appState.nextUploadId(), name, "Моя музыка", "Загрузки", 200,
                "#10B981", "#06B6D4", dest.getAbsolutePath());
        appState.addUploaded(t);
        toast("Трек добавлен");
        showScreen(2);
    }

    private void handleAvatarUpload(Uri uri) {
        try {
            // Копируем во внутреннюю память, чтобы иметь постоянный доступ
            File dest = new File(getFilesDir(), "avatar_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            storage.setAvatarUri(Uri.fromFile(dest).toString());
            setupProfileContent();
        } catch (Exception e) {
            toast("Ошибка при загрузке фото");
        }
    }

    private String queryName(Uri uri) {
        String name = "Загруженный трек";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i != -1) { String sName = c.getString(i); if (sName != null) name = sName; }
            }
        } catch (Exception ignored) {}
        return name;
    }

    private void updateNavTints() {
        int on = light ? Color.BLACK : cText;
        int off = light ? 0x60000000 : 0xFF7B7796;
        navHomeIc.setColorFilter(screen == 0 ? on : off);
        navSearchIc.setColorFilter(screen == 1 ? on : off);
        navLibIc.setColorFilter(screen == 2 ? on : off);
        navProfileIc.setColorFilter(screen == 3 ? on : off);
        navHomeTx.setTextColor(screen == 0 ? on : off);
        navSearchTx.setTextColor(screen == 1 ? on : off);
        navLibTx.setTextColor(screen == 2 ? on : off);
        navProfileTx.setTextColor(screen == 3 ? on : off);
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

    private void toggleLike() {
        Track t = engine.current();
        if (t == null) return;
        appState.toggleLike(t.id);
        updateLikeIcons();
        adapter.refreshLikes();
        if (screen == 2) showScreen(2);
        if (screen == 3) setupProfileContent();
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
        int icon = engine.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play;
        playIcon.setImageResource(icon);
        miniPlayIcon.setImageResource(icon);
        playIcon.setColorFilter(Color.parseColor("#0A0913"));
        miniPlayIcon.setColorFilter(Color.parseColor("#0A0913"));
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

        // Приятный темный градиент для фона плеера
        GradientDrawable npBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1A1826, 0xFF121019, 0xFF0D0C14});
        npOverlay.setBackground(npBg);
    }

    private void updateLiveBackground(Track t) {
        if (light) {
            root.setBackgroundColor(Color.WHITE);
            return;
        }
        int a1 = 0x55, a2 = 0x22;
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{blend(t.c1, a1), blend(t.c2, a2), cBase});
        root.setBackground(bg);
    }

    private void showShareSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog d = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int)dp(20), (int)dp(20), (int)dp(20), (int)dp(30));
        root.setBackgroundColor(Color.parseColor("#161422"));

        TextView t = new TextView(this);
        t.setText("Поделиться треком"); t.setTextColor(Color.WHITE);
        t.setTextSize(20); t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, (int)dp(20));
        root.addView(t);

        String[] opts = {"Facebook", "Ссылка", "Еще"};
        int[] ics = {R.drawable.ic_profile, R.drawable.ic_search, R.drawable.ic_more};
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        for(int i=0; i<opts.length; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setPadding((int)dp(15), 0, (int)dp(15), 0);
            ImageView iv = new ImageView(this);
            iv.setImageResource(ics[i]); iv.setColorFilter(Color.WHITE);
            iv.setBackgroundResource(R.drawable.circle_overlay);
            iv.getBackground().setTint(Color.parseColor("#33FFFFFF"));
            iv.setPadding((int)dp(12), (int)dp(12), (int)dp(12), (int)dp(12));
            col.addView(iv, new LinearLayout.LayoutParams((int)dp(50), (int)dp(50)));
            TextView tv = new TextView(this); tv.setText(opts[i]); tv.setTextColor(Color.WHITE);
            tv.setTextSize(12); tv.setPadding(0, (int)dp(8), 0, 0);
            col.addView(tv);
            row.addView(col);
        }
        root.addView(row);
        d.setContentView(root); d.show();
    }

    private void showMoreSheet() {
        Track t = engine.current();
        if (t == null) return;
        com.google.android.material.bottomsheet.BottomSheetDialog d = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#161422"));
        root.setPadding(0, (int)dp(10), 0, (int)dp(20));

        LinearLayout header = new LinearLayout(this);
        header.setPadding((int)dp(20), (int)dp(10), (int)dp(20), (int)dp(20));
        ImageView iv = new ImageView(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.c1, t.c2});
        g.setCornerRadius(dp(8)); iv.setBackground(g);
        header.addView(iv, new LinearLayout.LayoutParams((int)dp(50), (int)dp(50)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL); info.setPadding((int)dp(15), 0, 0, 0);
        TextView title = new TextView(this); title.setText(t.title); title.setTextColor(Color.WHITE); title.setTextSize(16);
        TextView art = new TextView(this); art.setText(t.artist); art.setTextColor(Color.LTGRAY); art.setTextSize(14);
        info.addView(title); info.addView(art);
        header.addView(info);
        root.addView(header);

        String[] items = {"Поделиться", "Добавить в плейлист", "Слушать на устройстве", "Удалить из очереди", "О треке"};
        int[] ics = {R.drawable.ic_upload, R.drawable.ic_plus, R.drawable.ic_notes, R.drawable.ic_trash, R.drawable.ic_notes};
        for(int i=0; i<items.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int)dp(20), (int)dp(12), (int)dp(20), (int)dp(12));
            ImageView icon = new ImageView(this); icon.setImageResource(ics[i]); icon.setColorFilter(Color.WHITE);
            row.addView(icon, new LinearLayout.LayoutParams((int)dp(24), (int)dp(24)));
            TextView txt = new TextView(this); txt.setText(items[i]); txt.setTextColor(Color.WHITE);
            txt.setTextSize(15); txt.setPadding((int)dp(15), 0, 0, 0);
            row.addView(txt);
            final int idx = i;
            row.setOnClickListener(v -> { d.dismiss(); if(idx==0) showShareSheet(); });
            root.addView(row);
        }
        d.setContentView(root); d.show();
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
        npOverlay.animate().translationY(open ? 0 : screenH).setDuration(340).start();
    }

    private void applyScreenVisibility(int s) {
        recycler.setVisibility(View.GONE);
        profileContainer.setVisibility(View.GONE);
        settingsContainer.setVisibility(View.GONE);
        playlistsScroll.setVisibility(View.GONE);
        searchBox.setVisibility(View.GONE);
        eyebrow.setVisibility(View.GONE);
        searchHistoryBox.setVisibility(View.GONE);
        resetHeaderState(); // шапка всегда видна и развёрнута при открытии экрана

        if (s == 0) {
            eyebrow.setVisibility(View.VISIBLE);
            eyebrow.setText(greeting());
            h1.setText("Что послушаем?");
            playlistsScroll.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.VISIBLE);
            adapter.setData(Data.TRACKS);
        } else if (s == 1) {
            h1.setText("Поиск");
            searchBox.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.VISIBLE);
            applySearch(searchInput.getText().toString());
        } else if (s == 2) {
            h1.setText("Моя медиатека");
            recycler.setVisibility(View.VISIBLE);
            List<Track> lib = new ArrayList<>();
            for (Track t : Data.TRACKS) if (appState.isLiked(t.id)) lib.add(t);
            lib.addAll(appState.uploaded());
            adapter.setData(lib);
        } else if (s == 3) {
            h1.setText("Профиль");
            profileContainer.setVisibility(View.VISIBLE);
            setupProfileContent();
        } else if (s == 4) {
            h1.setText("Настройки");
            settingsContainer.setVisibility(View.VISIBLE);
            setupSettingsContent();
        }
        if (s == 0 || s == 1 || s == 2) adapter.setCurrent(currentId());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (engine == null) return;           // onCreate отправил на регистрацию
        engine.addListener(engineListener);
        renderPlayer();                       // движок мог играть, пока активности не было
    }

    @Override
    protected void onStop() {
        if (engine != null) engine.removeListener(engineListener);
        super.onStop();                       // воспроизведение продолжается
    }

    // ---------- тема ----------
    private void computeTheme() {
        if (light) { cBase = 0xFFFFFFFF; cText = 0xFF15131F; cMuted = 0xFF5D5A72; cSurface = 0xFFF2F1F6; }
        else { cBase = 0xFF0A0913; cText = 0xFFF3F1FA; cMuted = 0xFFA09CB8; cSurface = 0xFF161422; }
    }

    private void applyTheme() {
        computeTheme();
        getWindow().setStatusBarColor(cBase);
        getWindow().setNavigationBarColor(cBase);
        View dec = getWindow().getDecorView();
        int f = dec.getSystemUiVisibility();
        if (light) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; else f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        dec.setSystemUiVisibility(f);

        root.setBackgroundColor(cBase); // Светлая тема теперь полностью белая

        h1.setTextColor(cText);
        eyebrow.setTextColor(cMuted);
        int iconColor = light ? Color.BLACK : cText;

        GradientDrawable sb = new GradientDrawable();
        sb.setColor(light ? 0x14000000 : 0x17FFFFFF);
        sb.setCornerRadius(dp(14));
        searchBox.setBackground(sb);
        searchInput.setTextColor(cText);
        searchInput.setHintTextColor(cMuted);

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
        if (!light) updateLiveBackground(currentOrFirst());
        rebuildPlaylistsRow();
        applyProfileTheme();
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

    private String tracksWord(int n) {
        int a = n % 10, b = n % 100;
        if (a == 1 && b != 11) return "трек";
        if (a >= 2 && a <= 4 && (b < 10 || b >= 20)) return "трека";
        return "треков";
    }

    private void toast(String s) {
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