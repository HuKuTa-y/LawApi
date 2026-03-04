package com.example.lawapp;
import com.example.lawapp.R;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.lawapp.adapters.*;
import com.example.lawapp.api.ApiClient;
import com.example.lawapp.api.LawApiService;
import com.example.lawapp.cache.CacheManager;
import com.example.lawapp.models.*;
import com.example.lawapp.utils.OfflineException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // UI Elements
    private EditText articleNumberEditText, searchEditText;
    private Button findArticlesButton, cancelSearchButton, searchButton;
    private RecyclerView codeksRecyclerView, lawsRecyclerView, articlesRecyclerView, contentRecyclerView;

    // Adapters
    private CodekAdapter codekAdapter;
    private LawAdapter lawAdapter;
    private ArticleAdapter articleAdapter;
    private ArticleAdapter contentAdapter;

    // Data
    private List<Codek> codeksList = new ArrayList<>();
    private List<Law> lawsList = new ArrayList<>();
    private List<ArticleFull> articlesFull = new ArrayList<>();
    private List<ArticleFull> currentArticles = new ArrayList<>();
    private List<ArticleFull> contentList = new ArrayList<>();

    // API & Threading
    private LawApiService apiService;
    private ExecutorService executor;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация
        CacheManager.initialize(this);
        apiService = ApiClient.getService();
        executor = Executors.newFixedThreadPool(4);
        mainHandler = new Handler(Looper.getMainLooper());

        // UI инициализация
        initViews();
        setupRecyclerViews();
        setupClickListeners();

        // 🔥 Гибридная загрузка данных
        loadHybridData();
    }

    private void initViews() {
        articleNumberEditText = findViewById(R.id.articleNumberEditText);
        searchEditText = findViewById(R.id.searchEditText);
        findArticlesButton = findViewById(R.id.findArticlesButton);
        cancelSearchButton = findViewById(R.id.cancelSearchButton);
        searchButton = findViewById(R.id.searchButton);

        codeksRecyclerView = findViewById(R.id.codeksRecyclerView);
        lawsRecyclerView = findViewById(R.id.lawsRecyclerView);
        articlesRecyclerView = findViewById(R.id.articlesRecyclerView);
        contentRecyclerView = findViewById(R.id.contentRecyclerView);
    }

    private void setupRecyclerViews() {
        codekAdapter = new CodekAdapter(codeksList, this::onCodekClick);
        lawAdapter = new LawAdapter(lawsList, this::onLawClick);
        articleAdapter = new ArticleAdapter(currentArticles, this::onArticleClick);
        contentAdapter = new ArticleAdapter(contentList, this::onContentClick);

        codeksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        lawsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        articlesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contentRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        codeksRecyclerView.setAdapter(codekAdapter);
        lawsRecyclerView.setAdapter(lawAdapter);
        articlesRecyclerView.setAdapter(articleAdapter);
        contentRecyclerView.setAdapter(contentAdapter);
    }

    private void setupClickListeners() {
        findArticlesButton.setOnClickListener(v -> searchByNumber());
        cancelSearchButton.setOnClickListener(v -> cancelSearch());
        searchButton.setOnClickListener(v -> searchByText());
    }

    // 🔥 Гибридная загрузка (как в WPF)
    private void loadHybridData() {
        executor.execute(() -> {
            try {
                // ЭТАП 1: Обязательные данные
                codeksList = CacheManager.getData("/api/codeks", "codeks.cache.json",
                        apiService.getCodeks(), true);

                lawsList = CacheManager.getData("/api/laws", "laws.cache.json",
                        apiService.getLaws(), true);

                // ЭТАП 2: Articles (не критично)
                articlesFull = CacheManager.getData("/api/articles_full", "articles_full.cache.json",
                        apiService.getArticlesFull(), false);

                mainHandler.post(() -> {
                    codekAdapter.notifyDataSetChanged();
                    lawAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Данные загружены", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private void onCodekClick(Codek codek) {
        loadArticlesBySource(codek.номер);
    }

    private void onLawClick(Law law) {
        loadArticlesBySource(law.номер);
    }

    private void loadArticlesBySource(String sourceNumber) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(sourceNumber, StandardCharsets.UTF_8.toString());
                List<ArticleFull> articles = CacheManager.getData(
                        "/api/articles/by-source?source_number=" + encoded,
                        "articles_" + encoded + ".cache.json",
                        apiService.getArticlesBySource(sourceNumber),
                        false
                );

                currentArticles = articles != null ? articles : new ArrayList<>();
                mainHandler.post(() -> articleAdapter.notifyDataSetChanged());

            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void onArticleClick(ArticleFull article) {
        loadArticleText(article.название);
    }

    private void onContentClick(ArticleFull article) {
        // Можно добавить дополнительную логику
    }

    private void loadArticleText(String articleName) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(articleName, StandardCharsets.UTF_8.toString());
                TextArticle text = CacheManager.getData(
                        "/api/article/text?article_name=" + encoded,
                        "text_" + encoded + ".cache.json",
                        apiService.getArticleText(articleName),
                        false
                );

                if (text != null) {
                    contentList.clear();

                    //  Создаём ArticleFull для отображения
                    ArticleFull af = new ArticleFull();
                    af.название = text.название;
                    contentList.add(af);

                    mainHandler.post(() -> {
                        contentAdapter.notifyDataSetChanged();

                        // Показать текст в Dialog
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                        builder.setTitle(text.название)
                                .setMessage(text.контент)
                                .setPositiveButton("OK", null)
                                .show();
                    });
                }

            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "Текст не загружен: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void searchByNumber() {
        String text = articleNumberEditText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Введите номер статьи", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int number = Integer.parseInt(text);

            // Локальный поиск если articlesFull загружен
            if (!articlesFull.isEmpty()) {
                List<ArticleFull> results = new ArrayList<>();
                for (ArticleFull a : articlesFull) {
                    if (extractNumberFromTitle(a.название) == number) {
                        results.add(a);
                    }
                }
                currentArticles = results;
                articleAdapter.notifyDataSetChanged();
                return;
            }

            // Поиск на сервере
            executor.execute(() -> {
                try {
                    List<ArticleFull> results = apiService.searchByNumber(number).execute().body();
                    currentArticles = results != null ? results : new ArrayList<>();
                    mainHandler.post(() -> articleAdapter.notifyDataSetChanged());
                } catch (Exception e) {
                    mainHandler.post(() ->
                            Toast.makeText(this, "Ошибка поиска: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            });

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Введите числовой номер", Toast.LENGTH_SHORT).show();
        }
    }

    private void searchByText() {
        String query = searchEditText.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Введите текст для поиска", Toast.LENGTH_SHORT).show();
            return;
        }

        // Локальный поиск
        if (!articlesFull.isEmpty()) {
            String[] words = query.toLowerCase().split("\\s+");
            List<ArticleFull> results = new ArrayList<>();
            for (ArticleFull a : articlesFull) {
                String title = a.название.toLowerCase();
                for (String word : words) {
                    if (title.contains(word)) {
                        results.add(a);
                        break;
                    }
                }
            }
            currentArticles = results;
            articleAdapter.notifyDataSetChanged();
            return;
        }

        // Поиск на сервере
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
                List<ArticleFull> results = apiService.searchByText(query).execute().body();
                currentArticles = results != null ? results : new ArrayList<>();
                mainHandler.post(() -> articleAdapter.notifyDataSetChanged());
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "Ошибка поиска: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void cancelSearch() {
        articleNumberEditText.setText("");
        searchEditText.setText("");
        currentArticles.clear();
        contentList.clear();
        articleAdapter.notifyDataSetChanged();
        contentAdapter.notifyDataSetChanged();
    }

    private int extractNumberFromTitle(String title) {
        StringBuilder digits = new StringBuilder();
        for (char c : title.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}