package com.example.lawapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lawapp.R;
import com.example.lawapp.models.ArticleFull;
import java.util.List;

public class ArticleAdapter extends RecyclerView.Adapter<ArticleAdapter.ViewHolder> {
    private List<ArticleFull> articles;
    private OnArticleClickListener listener;

    public interface OnArticleClickListener {
        void onArticleClick(ArticleFull article);
    }

    public ArticleAdapter(List<ArticleFull> articles, OnArticleClickListener listener) {
        this.articles = articles;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_article, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ArticleFull article = articles.get(position);
        holder.title.setText(article.getНазвание());
        holder.button.setOnClickListener(v -> listener.onArticleClick(article));
        holder.button.setOnLongClickListener(v -> {
            android.widget.Toast.makeText(holder.button.getContext(),
                    article.ссылка, android.widget.Toast.LENGTH_LONG).show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return articles != null ? articles.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        Button button;
        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.articleTitle);
            button = itemView.findViewById(R.id.articleButton);
        }
    }
}