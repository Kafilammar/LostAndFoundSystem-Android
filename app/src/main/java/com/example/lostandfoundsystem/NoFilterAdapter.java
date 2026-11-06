package com.example.lostandfoundsystem;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Adapter لقوائم الاختيار المنسدلة يعرض كل الخيارات دائمًا
 * (ArrayAdapter العادي يُخفي الخيارات بعد استعادة الحالة أو تدوير الشاشة).
 */
public class NoFilterAdapter extends ArrayAdapter<String> {
    private final List<String> items;

    public NoFilterAdapter(Context context, String[] values) {
        super(context, R.layout.view_dropdown_item, values);
        items = Arrays.asList(values);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults r = new FilterResults();
                r.values = items;
                r.count = items.size();
                return r;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };
    }
}
