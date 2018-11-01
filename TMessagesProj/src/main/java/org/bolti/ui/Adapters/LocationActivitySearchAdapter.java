/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.bolti.ui.Adapters;

import android.content.Context;
import android.view.ViewGroup;

import org.bolti.messenger.support.widget.RecyclerView;
import org.bolti.tgnet.TLRPC;
import org.bolti.ui.Cells.LocationCell;
import org.bolti.ui.Components.RecyclerListView;

public class LocationActivitySearchAdapter extends BaseLocationAdapter {

    private Context mContext;

    public LocationActivitySearchAdapter(Context context) {
        super();
        mContext = context;
    }

    @Override
    public int getItemCount() {
        return places.size();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new RecyclerListView.Holder(new LocationCell(mContext));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ((LocationCell) holder.itemView).setLocation(places.get(position), iconUrls.get(position), position != places.size() - 1);
    }

    public TLRPC.TL_messageMediaVenue getItem(int i) {
        if (i >= 0 && i < places.size()) {
            return places.get(i);
        }
        return null;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return true;
    }
}
