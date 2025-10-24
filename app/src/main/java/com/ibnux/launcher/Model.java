package com.ibnux.launcher;

import android.graphics.drawable.Drawable;

final class Model {

    final long id;
    final String label;
    final String labelSearch;
    final String packageName;
    final Drawable icon;

    Model(long id, String label, String packageName, Drawable icon) {
        this.id = id;
        this.label = label;
        this.labelSearch = label.toLowerCase();
        this.packageName = packageName;
        this.icon = icon;
    }

}
