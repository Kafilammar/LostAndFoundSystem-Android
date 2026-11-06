package com.example.lostandfoundsystem;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Adapter عام لعرض البلاغات في RecyclerView. */
public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.Holder> {

    public interface OnClick { void onClick(Report r); }

    /** زر إجراء اختياري أسفل البطاقة. يرجع null لإخفاء الزر. */
    public interface ActionProvider {
        Action actionFor(Report r);
    }

    public static class Action {
        final String label; final int colorRes; final OnClick onClick;
        public Action(String label, int colorRes, OnClick onClick) {
            this.label = label; this.colorRes = colorRes; this.onClick = onClick;
        }
    }

    private final List<Report> items = new ArrayList<>();
    private final boolean showType;
    private final OnClick onClick;
    private ActionProvider actionProvider;

    public ReportAdapter(boolean showType, OnClick onClick) {
        this.showType = showType;
        this.onClick = onClick;
    }

    public void setActionProvider(ActionProvider p) { this.actionProvider = p; }

    public void submit(List<Report> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ReportCard.inflate(parent));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Report r = items.get(position);
        ReportCard.bind(h.itemView, r, showType);
        h.itemView.setOnClickListener(v -> onClick.onClick(r));
        if (actionProvider != null) {
            Action a = actionProvider.actionFor(r);
            if (a != null) ReportCard.setAction(h.itemView, a.label, a.colorRes, v -> a.onClick.onClick(r));
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) { super(itemView); }
    }
}
