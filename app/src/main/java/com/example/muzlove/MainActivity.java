package com.example.muzlove;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    // состояние
    private final List<Integer> order = new ArrayList<>();   // порядок воспроизведения (id)
    private int pos = 0;                                     // позиция в order
    private boolean started = false, playing = false, shuffle = false;
    private int repeat = 0;                                  // 0 off, 1 all, 2 one
    private final Set<Integer> liked = new HashSet<>();
    private int screen = 0;                                  // 0 home, 1 search, 2 library, 3 profile, 4 settings
    private final List<Integer> screenHistory = new ArrayList<>();
    private boolean npOpen = false;

    // тема и настройки
    private boolean light = false, waveform = true;
    private int cBase, cText, cMuted, cSurface;

    // плеер
    private MediaPlayer mp;
    private boolean prepared = false, fallback = false;
    private int durationMs = 1000;
    private int timeMs = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // хранилище
    private StorageManager storage;
    private final List<Playlist> customPlaylists = new ArrayList<>();
    private final List<Track> uploadedTracks = new ArrayList<>();
    private final List<Track> allTracks = new ArrayList<>(); // встроенные + загруженные

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

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (playing) {
                if (prepared && !fallback && mp != null) {
                    timeMs = mp.getCurrentPosition();
                    durationMs = mp.getDuration() > 0 ? mp.getDuration() : durationMs;
                } else if (fallback) {
                    timeMs += 250;
                    if (timeMs >= durationMs) { onTrackEnded(); return; }
                }
                updateProgress();
                handler.postDelayed(this, 250);
            }
        }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        storage = new StorageManager(this);
        light = storage.isLight();
        waveform = storage.isWaveform();
        computeTheme();

        customPlaylists.addAll(storage.getPlaylists());
        uploadedTracks.addAll(storage.getTracks());

        bind();
        setupPlayer();
        setupRecycler();
        setupNav();
        setupNowPlaying();

        // лайки: загружаем сохранённые, либо ставим значения по умолчанию
        Set<Integer> savedLikes = storage.getLikes();
        if (savedLikes == null) { liked.add(1); liked.add(4); storage.saveLikes(liked); }
        else liked.addAll(savedLikes);

        rebuildAllTracks();
        rebuildOrder();
        wave.setSimpleMode(!waveform);

        // Обработка системных отступов (чтобы не перекрывалось виджетами телефона)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // Сдвигаем мини-плеер вниз, пока он не активен
        miniPlayer.setTranslationY(dp(80));

        applyTheme();      // красит интерфейс + строит ряд плейлистов
        showScreen(0);
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
                if (fromUser && durationMs > 0) {
                    timeMs = (int) ((progress / 1000f) * durationMs);
                    updateTimeLabels();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (durationMs > 0) {
                    mp.seekTo(timeMs);
                }
            }
        });
    }

    private void setupPlayer() {
        mp = new MediaPlayer();
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setOnPreparedListener(m -> {
            prepared = true; fallback = false;
            durationMs = m.getDuration() > 0 ? m.getDuration() : durationMs;
            if (playing) m.start();
            updateTimeLabels();
            startTicker();
        });
        mp.setOnCompletionListener(m -> onTrackEnded());
        mp.setOnErrorListener((m, what, extra) -> { fallback = true; startTicker(); return true; });
    }

    private void setupRecycler() {
        adapter = new TrackAdapter(liked, t -> {
            // если играем трек из результатов поиска — запоминаем запрос в истории
            if (screen == 1) {
                String q = searchInput.getText().toString().trim();
                if (!q.isEmpty()) storage.addSearchHistory(q);
            }
            playById(t);
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        recycler.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                // Прячем/показываем мини-плеер
                if (started && miniPlayer.getVisibility() == View.VISIBLE) {
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

        miniPlayer.setOnClickListener(v -> openNowPlaying(true));
        miniLike.setOnClickListener(v -> toggleLike(current().id));
        miniPlayIcon.setOnClickListener(v -> togglePlay());
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
        playBtn.setOnClickListener(v -> togglePlay());
        findViewById(R.id.btnNext).setOnClickListener(v -> next());
        findViewById(R.id.btnPrev).setOnClickListener(v -> prev());
        btnShuffle.setOnClickListener(v -> toggleShuffle());
        btnRepeat.setOnClickListener(v -> cycleRepeat());
        btnShare.setOnClickListener(v -> showShareSheet());
        btnMore.setOnClickListener(v -> showMoreSheet());
        btnQueue.setOnClickListener(v -> { openNowPlaying(false); showScreen(2); });
        npLike.setOnClickListener(v -> toggleLike(current().id));
        wave.setOnSeek(ratio -> seekTo((int) (ratio * durationMs)));
    }

    private void rebuildPlaylistsRow() {
        playlistsRow.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        List<Playlist> all = new ArrayList<>();
        all.addAll(Data.PLAYLISTS);
        all.addAll(customPlaylists);
        for (Playlist p : all) {
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
            card.setOnClickListener(v -> {
                if (fp.ids != null && fp.ids.length > 0) playById(findTrack(fp.ids[0]));
            });
            playlistsRow.addView(card);
        }
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
        if (likedCount != null) likedCount.setText(String.valueOf(liked.size()));
        if (playlistCount != null)
            playlistCount.setText(String.valueOf(Data.PLAYLISTS.size() + customPlaylists.size()));

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
        for (Track t : allTracks) {
            String hay = (t.title + " " + t.artist + " " + t.album).toLowerCase(Locale.ROOT);
            if (hay.contains(qq)) res.add(t);
        }
        adapter.setData(res);
        if (started) adapter.setCurrent(current().id);
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
                    List<Integer> ids = new ArrayList<>(liked);
                    if (ids.isEmpty())
                        for (int i = 0; i < Math.min(3, Data.TRACKS.size()); i++) ids.add(Data.TRACKS.get(i).id);
                    int[] arr = new int[ids.size()];
                    for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
                    Playlist pl = new Playlist(name, ids.size() + " " + tracksWord(ids.size()),
                            "#8B5CF6", "#EC4899", arr);
                    customPlaylists.add(pl);
                    storage.savePlaylists(customPlaylists);
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
        int id = 1000 + uploadedTracks.size();
        Track t = new Track(id, name, "Моя музыка", "Загрузки", 200,
                "#10B981", "#06B6D4", dest.getAbsolutePath());
        uploadedTracks.add(t);
        storage.saveTracks(uploadedTracks);
        rebuildAllTracks();
        rebuildOrder();
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

    // ---------- воспроизведение ----------
    private void rebuildAllTracks() {
        allTracks.clear();
        allTracks.addAll(Data.TRACKS);
        allTracks.addAll(uploadedTracks);
    }

    private void rebuildOrder() {
        int curId = (!order.isEmpty() && pos >= 0 && pos < order.size()) ? order.get(pos) : -1;
        order.clear();
        for (Track t : allTracks) order.add(t.id);
        pos = curId != -1 ? Math.max(0, order.indexOf(curId)) : 0;
    }

    private Track findTrack(int id) {
        for (Track t : allTracks) if (t.id == id) return t;
        return allTracks.isEmpty() ? Data.TRACKS.get(0) : allTracks.get(0);
    }

    private Track current() {
        if (order.isEmpty()) return allTracks.isEmpty() ? Data.TRACKS.get(0) : allTracks.get(0);
        if (pos < 0 || pos >= order.size()) pos = 0;
        return findTrack(order.get(pos));
    }

    private Track currentOrFirst() {
        return started ? current() : (allTracks.isEmpty() ? Data.TRACKS.get(0) : allTracks.get(0));
    }

    private void playById(Track t) {
        if (started && current().id == t.id) {
            togglePlay();
            return;
        }
        rebuildAllTracks();
        order.clear();
        for (Track x : allTracks) order.add(x.id);
        if (shuffle) {
            order.remove((Integer) t.id);
            Collections.shuffle(order);
            order.add(0, t.id);
        }
        pos = Math.max(0, order.indexOf(t.id));
        started = true; playing = true;
        loadCurrent(true);
        openMini();
    }

    private void loadCurrent(boolean autoplay) {
        Track t = current();
        prepared = false; fallback = false;
        timeMs = 0; durationMs = Math.max(1000, t.dur * 1000);

        // Если была пауза, то при переключении трекаautoplay (автозапуск) не должен срабатывать,
        // кроме случая явного вызова playById (там autoplay всегда true)
        if (!autoplay) playing = false;

        try {
            mp.reset();
            mp.setDataSource(t.url);
            mp.prepareAsync();
        } catch (Exception e) {
            fallback = true;
            if (playing) startTicker();
        }
        updateNowPlayingUI();
        updateLiveBackground(t);
        adapter.setCurrent(t.id);
        updatePlayIcons();
    }

    private void togglePlay() {
        if (!started) return;
        playing = !playing;
        if (playing) {
            if (prepared && !fallback) mp.start();
            startTicker();
        } else {
            if (prepared && !fallback) mp.pause();
            handler.removeCallbacks(ticker);
        }
        updatePlayIcons();
    }

    private void next() {
        pos++;
        if (pos >= order.size()) {
            if (repeat == 0) { playing = false; updatePlayIcons(); pos = order.size() - 1; return; }
            pos = 0;
        }
        // Сохраняем текущее состояние воспроизведения: если стояла пауза, новый трек не заиграет сам
        loadCurrent(playing);
    }

    private void prev() {
        if (timeMs > 3000) { seekTo(0); return; }
        pos--;
        if (pos < 0) pos = order.size() - 1;
        // Сохраняем состояние паузы
        loadCurrent(playing);
    }

    private void onTrackEnded() {
        handler.removeCallbacks(ticker);
        if (repeat == 2) { timeMs = 0; seekTo(0); playing = true; if (prepared && !fallback) mp.start(); startTicker(); return; }
        next();
    }

    private void seekTo(int ms) {
        timeMs = Math.max(0, Math.min(durationMs, ms));
        if (prepared && !fallback) mp.seekTo(timeMs);
        updateProgress();
    }

    private void toggleShuffle() {
        shuffle = !shuffle;
        int cur = current().id;
        rebuildAllTracks();
        order.clear();
        for (Track x : allTracks) order.add(x.id);
        if (shuffle) {
            order.remove((Integer) cur);
            Collections.shuffle(order);
            order.add(0, cur);
            pos = 0;
        } else {
            pos = Math.max(0, order.indexOf(cur));
        }
        btnShuffle.setColorFilter(shuffle ? current().c1 : Color.parseColor("#C9C5DE"));
    }

    private void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        updateRepeatIcon();
    }

    private void updateRepeatIcon() {
        int icon = (repeat == 2) ? R.drawable.ic_repeat_one : R.drawable.ic_repeat;
        btnRepeat.setImageResource(icon);
        btnRepeat.setColorFilter(repeat != 0 ? current().c1 : (light ? Color.WHITE : Color.parseColor("#C9C5DE")));
    }

    private void toggleLike(int id) {
        if (liked.contains(id)) liked.remove(id); else liked.add(id);
        storage.saveLikes(liked);
        boolean isLiked = liked.contains(id);
        npLike.setColorFilter(isLiked ? current().c1 : Color.WHITE);
        miniLike.setColorFilter(isLiked ? current().c1 : Color.parseColor("#C9C5DE"));
        adapter.refreshLikes();
        if (screen == 2) showScreen(2);
        if (screen == 3) setupProfileContent();
    }

    private void startTicker() {
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    // ---------- обновление UI ----------
    private void updateNotification() {
        if (!started) return;
        Track t = current();
        String channelId = "music_island";
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Music Island", android.app.NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }

        android.app.PendingIntent contentIntent = android.app.PendingIntent.getActivity(this, 0,
                new android.content.Intent(this, MainActivity.class),
                android.app.PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder b = new androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notes)
                .setContentTitle(t.title)
                .setContentText(t.artist)
                .setOngoing(playing)
                .setContentIntent(contentIntent)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW);

        nm.notify(1, b.build());
    }

    private void updateProgress() {
        float ratio = durationMs > 0 ? (float) timeMs / durationMs : 0;
        wave.setProgress(ratio);
        if (slider != null) slider.setProgress((int) (ratio * 1000));
        timeCur.setText(fmt(timeMs / 1000));
        ViewGroup.LayoutParams lp = miniProgress.getLayoutParams();
        int full = miniPlayer.getWidth();
        lp.width = (int) (ratio * full);
        miniProgress.setLayoutParams(lp);
    }

    private void updateTimeLabels() {
        timeDur.setText(fmt(durationMs / 1000));
        timeCur.setText(fmt(timeMs / 1000));
    }

    private void updatePlayIcons() {
        int icon = playing ? R.drawable.ic_pause : R.drawable.ic_play;
        playIcon.setImageResource(icon);
        miniPlayIcon.setImageResource(icon);
        playIcon.setColorFilter(Color.parseColor("#0A0913"));
        miniPlayIcon.setColorFilter(Color.parseColor("#0A0913"));
    }

    private void updateNowPlayingUI() {
        Track t = current();
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

        boolean isLiked = liked.contains(t.id);
        int accent = Color.parseColor("#F4FE52");
        npLike.setColorFilter(isLiked ? accent : Color.WHITE);
        miniLike.setColorFilter(isLiked ? accent : (light ? Color.BLACK : Color.parseColor("#C9C5DE")));
        btnShuffle.setColorFilter(shuffle ? accent : Color.parseColor("#B3FFFFFF"));
        btnRepeat.setColorFilter(repeat != 0 ? accent : Color.parseColor("#B3FFFFFF"));

        updateTimeLabels();

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
        com.google.android.material.bottomsheet.BottomSheetDialog d = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#161422"));
        root.setPadding(0, (int)dp(10), 0, (int)dp(20));

        Track t = current();
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

    @Override
    public void onBackPressed() {
        if (npOpen) {
            openNowPlaying(false);
            showScreen(0);
            return;
        }
        if (screen != 0) {
            showScreen(0);
            return;
        }
        super.onBackPressed();
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
            for (Track t : Data.TRACKS) if (liked.contains(t.id)) lib.add(t);
            lib.addAll(uploadedTracks);
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
        if (started && (s == 0 || s == 1 || s == 2)) adapter.setCurrent(current().id);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (started) updateNotification();
    }

    @Override
    protected void onStart() {
        super.onStart();
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        nm.cancel(1);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (mp != null) { mp.release(); mp = null; }
        super.onDestroy();
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