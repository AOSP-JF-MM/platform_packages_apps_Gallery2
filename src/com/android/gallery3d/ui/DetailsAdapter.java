/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.ui.DetailsAddressResolver.AddressResolvingListener;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.DetailsHelper.DetailsSource;
import com.android.gallery3d.ui.DetailsHelper.DetailsViewContainer;
import com.android.gallery3d.ui.DetailsHelper.ResolutionResolvingListener;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map.Entry;


public class DetailsAdapter extends BaseAdapter
        implements AddressResolvingListener, ResolutionResolvingListener {
    private final AbstractGalleryActivity mActivity;
    private final ArrayList<String> mItems;
    private int mLocationIndex;
    private final Locale mDefaultLocale = Locale.getDefault();
    private final DecimalFormat mDecimalFormat = new DecimalFormat(".####");
    private int mWidthIndex = -1;
    private int mHeightIndex = -1;
    private MediaDetails mDetails;

    public DetailsAdapter(MediaDetails details, AbstractGalleryActivity activity) {
        mActivity = activity;
        mDetails = details;
        Context context = mActivity.getAndroidContext();
        mItems = new ArrayList<String>(details.size());
        mLocationIndex = -1;
        setDetails(context, details);
    }

    private void setDetails(Context context, MediaDetails details) {
        boolean resolutionIsValid = true;
        String path = null;
        for (Entry<Integer, Object> detail : details) {
            String value;
            switch (detail.getKey()) {
                case MediaDetails.INDEX_LOCATION: {
                    double[] latlng = (double[]) detail.getValue();
                    mLocationIndex = mItems.size();
                    value = DetailsHelper.resolveAddress(mActivity, latlng, this);
                    break;
                }
                case MediaDetails.INDEX_SIZE: {
                    value = Formatter.formatFileSize(
                            context, (Long) detail.getValue());
                    break;
                }
                case MediaDetails.INDEX_WHITE_BALANCE: {
                    value = "1".equals(detail.getValue())
                            ? context.getString(R.string.manual)
                            : context.getString(R.string.auto);
                    break;
                }
                case MediaDetails.INDEX_FLASH: {
                    MediaDetails.FlashState flash =
                            (MediaDetails.FlashState) detail.getValue();
                    // TODO: camera doesn't fill in the complete values, show more information
                    // when it is fixed.
                    if (flash.isFlashFired()) {
                        value = context.getString(R.string.flash_on);
                    } else {
                        value = context.getString(R.string.flash_off);
                    }
                    break;
                }
                case MediaDetails.INDEX_EXPOSURE_TIME: {
                    value = (String) detail.getValue();
                    double time = Double.valueOf(value);
                    if (time < 1.0f) {
                        value = String.format(mDefaultLocale, "%d/%d", 1,
                                (int) (0.5f + 1 / time));
                    } else {
                        int integer = (int) time;
                        time -= integer;
                        value = String.valueOf(integer) + "''";
                        if (time > 0.0001) {
                            value += String.format(mDefaultLocale, " %d/%d", 1,
                                    (int) (0.5f + 1 / time));
                        }
                    }
                    break;
                }
                case MediaDetails.INDEX_WIDTH:
                    mWidthIndex = mItems.size();
                    if (detail.getValue().toString().equalsIgnoreCase("0")) {
                        value = context.getString(R.string.unknown);
                        resolutionIsValid = false;
                    } else {
                        value = toLocalInteger(detail.getValue());
                    }
                    break;
                case MediaDetails.INDEX_HEIGHT: {
                    mHeightIndex = mItems.size();
                    if (detail.getValue().toString().equalsIgnoreCase("0")) {
                        value = context.getString(R.string.unknown);
                        resolutionIsValid = false;
                    } else {
                        value = toLocalInteger(detail.getValue());
                    }
                    break;
                }
                case MediaDetails.INDEX_PATH:
                    // Prepend the new-line as a) paths are usually long, so
                    // the formatting is better and b) an RTL UI will see it
                    // as a separate section and interpret it for what it
                    // is, rather than trying to make it RTL (which messes
                    // up the path).
                    value = "\n" + detail.getValue().toString();
                    path = detail.getValue().toString();
                    break;
                case MediaDetails.INDEX_ISO:
                    value = toLocalNumber(Integer.parseInt((String) detail.getValue()));
                    break;
                case MediaDetails.INDEX_FOCAL_LENGTH:
                    double focalLength = Double.parseDouble(detail.getValue().toString());
                    value = toLocalNumber(focalLength);
                    break;
                case MediaDetails.INDEX_ORIENTATION:
                    value = toLocalInteger(detail.getValue());
                    break;
                default: {
                    Object valueObj = detail.getValue();
                    // This shouldn't happen, log its key to help us diagnose the problem.
                    if (valueObj == null) {
                        Utils.debug("%s's value is Null",
                                DetailsHelper.getDetailsName(context, detail.getKey()));
                        // ignore this detail
                        continue;
                    }
                    value = valueObj.toString();
                }
            }
            int key = detail.getKey();
            if (details.hasUnit(key)) {
                value = String.format("%s: %s %s", DetailsHelper.getDetailsName(
                        context, key), value, context.getString(details.getUnit(key)));
            } else {
                if (View.LAYOUT_DIRECTION_RTL == TextUtils
                        .getLayoutDirectionFromLocale(Locale.getDefault())
                        && (key == MediaDetails.INDEX_PATH)) {
                    value = String.format("%s : \n%s",
                            DetailsHelper.getDetailsName(context, key), value);
                } else {
                    value = String.format("%s: %s", DetailsHelper.getDetailsName(context, key),
                            value);
                }
            }
            mItems.add(value);
            if (!resolutionIsValid) {
                DetailsHelper.resolveResolution(path, this);
            }
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return false;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mDetails.getDetail(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView tv;
        if (convertView == null) {
            tv = (TextView) LayoutInflater.from(mActivity.getAndroidContext()).inflate(
                    R.layout.details_item, parent, false);
        } else {
            tv = (TextView) convertView;
        }
        tv.setText(mItems.get(position));
        return tv;
    }

    @Override
    public void onAddressAvailable(String address) {
        mItems.set(mLocationIndex, address);
        notifyDataSetChanged();
    }

    @Override
    public void onAddressAvailableNew(String key, String address) {
    }

    @Override
    public void onResolutionAvailable(int width, int height) {
        if (width == 0 || height == 0) return;
        // Update the resolution with the new width and height
        Context context = mActivity.getAndroidContext();
        String widthString = String.format(mDefaultLocale, "%s: %d",
                DetailsHelper.getDetailsName(
                        context, MediaDetails.INDEX_WIDTH), width);
        String heightString = String.format(mDefaultLocale, "%s: %d",
                DetailsHelper.getDetailsName(
                        context, MediaDetails.INDEX_HEIGHT), height);
        mItems.set(mWidthIndex, String.valueOf(widthString));
        mItems.set(mHeightIndex, String.valueOf(heightString));
        notifyDataSetChanged();
    }

    /**
     * Converts the given integer (given as String or Integer object) to a
     * localized String version.
     */
    private String toLocalInteger(Object valueObj) {
        if (valueObj instanceof Integer) {
            return toLocalNumber((Integer) valueObj);
        } else {
            String value = valueObj.toString();
            try {
                value = toLocalNumber(Integer.parseInt(value));
            } catch (NumberFormatException ex) {
                // Just keep the current "value" if we cannot
                // parse it as a fallback.
            }
            return value;
        }
    }

    /**
     * Converts the given integer to a localized String version.
     */
    private String toLocalNumber(int n) {
        return String.format(mDefaultLocale, "%d", n);
    }

    /**
     * Converts the given double to a localized String version.
     */
    private String toLocalNumber(double n) {
        return mDecimalFormat.format(n);
    }
}
