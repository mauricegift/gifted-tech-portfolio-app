package co.median.android;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import co.median.android.icons.Icon;
import co.median.median_core.GNLog;

/**
 * Created by weiyin on 4/14/14.
 */
public class JsonMenuAdapter extends BaseExpandableListAdapter
        implements ExpandableListView.OnGroupClickListener, ExpandableListView.OnChildClickListener {
    private static final String TAG = JsonMenuAdapter.class.getName();

    private final MainActivity mainActivity;
    private final ExpandableListView expandableListView;
    private final int highlightColor;
    private final int foregroundColor;
    private final int backgroudColor;
    private final int sidebar_icon_size;
    private final int sidebar_expand_indicator_size;
    private JSONArray menuItems;
    private boolean groupsHaveIcons = false;
    private boolean childrenHaveIcons = false;
    private int selectedIndex;

    JsonMenuAdapter(MainActivity activity, ExpandableListView expandableListView) {
        this.mainActivity = activity;
        this.menuItems = new JSONArray();
        this.sidebar_icon_size = mainActivity.getResources().getInteger(R.integer.sidebar_icon_size);
        this.sidebar_expand_indicator_size = mainActivity.getResources().getInteger(R.integer.sidebar_expand_indicator_size);
        this.expandableListView = expandableListView;
        this.foregroundColor = ContextCompat.getColor(activity, R.color.sidebarForeground);
        this.backgroudColor = ContextCompat.getColor(activity, R.color.sidebarBackground);
        this.highlightColor = ContextCompat.getColor(activity, R.color.sidebarHighlight);

        expandableListView.setAdapter(this);
        expandableListView.setOnGroupClickListener(this);
        expandableListView.setOnChildClickListener(this);

        expandableListView.setDividerHeight(5);
    }

    public synchronized void update(JSONArray menuItems) {
        this.menuItems = menuItems;
        if (this.menuItems == null) this.menuItems = new JSONArray();

        // figure out groupsHaveIcons and childrenHaveIcons (for layout alignment)
        groupsHaveIcons = false;
        childrenHaveIcons = false;
        for (int i = 0; i < this.menuItems.length(); i++) {
            JSONObject item = this.menuItems.optJSONObject(i);
            if (item == null) continue;

            String icon = item.optString("icon");
            String activeIcon = item.optString("activeIcon");

            if (!icon.isEmpty() || !activeIcon.isEmpty()) {
                groupsHaveIcons = true;
            }

            if (item.optBoolean("isGrouping", false)) {
                JSONArray sublinks = item.optJSONArray("subLinks");
                if (sublinks != null) {
                    for (int j = 0; j < sublinks.length(); j++) {
                        JSONObject sublink = sublinks.optJSONObject(j);
                        String sublinkIcon = sublink.optString("icon");
                        String sublinkActiveIcon = sublink.optString("activeIcon");
                        if (!sublinkIcon.isEmpty() || !sublinkActiveIcon.isEmpty()) {
                            childrenHaveIcons = true;
                            break;
                        }
                    }
                }

            }
        }

        notifyDataSetChanged();
    }


    private String itemString(String s, int groupPosition) {
        String value = null;
        try {
            JSONObject section = (JSONObject) menuItems.get(groupPosition);
            if (!section.isNull(s))
                value = section.getString(s).trim();
        } catch (Exception e) {
            GNLog.getInstance().logError(TAG, e.getMessage(), e);
        }

        return value;
    }

    private String itemString(String s, int groupPosition, int childPosition) {
        String value = null;
        try {
            JSONObject section = (JSONObject) menuItems.get(groupPosition);
            JSONObject sublink = section.getJSONArray("subLinks").getJSONObject(childPosition);
            if (!sublink.isNull(s))
                value = sublink.getString(s).trim();
        } catch (Exception e) {
            GNLog.getInstance().logError(TAG, e.getMessage(), e);
        }

        return value;
    }

    private String getTitle(int groupPosition) {
        return itemString("label", groupPosition);
    }

    private String getTitle(int groupPosition, int childPosition) {
        return itemString("label", groupPosition, childPosition);
    }

    private Pair<String, String> getUrlAndJavascript(int groupPosition) {
        String url = itemString("url", groupPosition);
        String js = itemString("javascript", groupPosition);
        return new Pair<>(url, js);
    }

    private Pair<String, String> getUrlAndJavascript(int groupPosition, int childPosition) {
        String url = itemString("url", groupPosition, childPosition);
        String js = itemString("javascript", groupPosition, childPosition);
        return new Pair<>(url, js);
    }

    private boolean isGrouping(int groupPosition) {
        try {
            JSONObject section = (JSONObject) menuItems.get(groupPosition);
            return section.optBoolean("isGrouping", false);
        } catch (Exception e) {
            GNLog.getInstance().logError(TAG, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public int getGroupCount() {
        return menuItems.length();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        int count = 0;
        try {
            JSONObject section = (JSONObject) menuItems.get(groupPosition);
            if (section.optBoolean("isGrouping", false)) {
                count = section.getJSONArray("subLinks").length();
            }
        } catch (Exception e) {
            GNLog.getInstance().logError(TAG, e.getMessage(), e);
        }
        return count;
    }

    @Override
    public Object getGroup(int i) {
        return null;
    }

    @Override
    public Object getChild(int i, int i2) {
        return null;
    }

    @Override
    public long getGroupId(int i) {
        return 0;
    }

    @Override
    public long getChildId(int i, int i2) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = mainActivity.getLayoutInflater();

            convertView = inflater.inflate(groupsHaveIcons ?
                    R.layout.menu_group_icon : R.layout.menu_group_noicon, null);
        }

        RelativeLayout menuItem = convertView.findViewById(R.id.menu_item);
        menuItem.setBackground(generateRippleHighlightBackground());

        boolean isSelected = this.selectedIndex == groupPosition;

        // initialize color and icon
        int color;
        String icon;
        if (isSelected) {
            color = this.highlightColor;
            icon = itemString("activeIcon", groupPosition);
        } else {
            color = this.foregroundColor;
            icon = itemString("inactiveIcon", groupPosition);
        }

        if (icon == null || icon.isEmpty()) {
            icon = itemString("icon", groupPosition);
        }

        // expand/collapse indicator
        ImageView indicator = convertView.findViewById(R.id.menu_group_indicator);
        if (isGrouping(groupPosition)) {
            String iconName;
            if (isExpanded) {
                iconName = "fas fa-angle-up";
            } else {
                iconName = "fas fa-angle-down";
            }
            indicator.setImageDrawable(new Icon(mainActivity, iconName, sidebar_expand_indicator_size, color).getDrawable());
            indicator.setVisibility(View.VISIBLE);
        } else {
            indicator.setVisibility(View.GONE);
        }

        // set title
        TextView title = convertView.findViewById(R.id.menu_item_title);
        title.setText(getTitle(groupPosition));
        title.setTextColor(color);

        int typefaceStyle = isSelected ? Typeface.BOLD : Typeface.NORMAL;
        Typeface typeface = Typeface.create("sans-serif-medium", typefaceStyle);

        title.setTypeface(typeface);

        // set icon
        ImageView imageView = convertView.findViewById(R.id.menu_item_icon);

        if (imageView != null) {
            if (icon != null && !icon.isEmpty()) {
                Drawable iconDrawable = new Icon(mainActivity, icon, sidebar_icon_size, color).getDrawable();
                imageView.setImageDrawable(iconDrawable);
                imageView.setVisibility(View.VISIBLE);

            } else {
                imageView.setVisibility(View.INVISIBLE);
            }
        }

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                             View convertView, ViewGroup parent) {

        if (convertView == null) {
            LayoutInflater inflater = mainActivity.getLayoutInflater();

            if (groupsHaveIcons || childrenHaveIcons)
                convertView = inflater.inflate(R.layout.menu_child_icon, parent, false);
            else
                convertView = inflater.inflate(R.layout.menu_child_noicon, parent, false);
        }

        RelativeLayout menuItem = convertView.findViewById(R.id.menu_item);
        menuItem.setBackground(generateRippleHighlightBackground());

        int itemIndex = expandableListView.getFlatListPosition(ExpandableListView.getPackedPositionForChild(groupPosition, childPosition));
        boolean isSelected = this.selectedIndex == itemIndex;

        // initialize color and icon
        int color;
        String icon;
        if (isSelected) {
            color = this.highlightColor;
            icon = itemString("activeIcon", groupPosition, childPosition);
        } else {
            color = this.foregroundColor;
            icon = itemString("inactiveIcon", groupPosition, childPosition);
        }

        if (icon == null || icon.isEmpty()) {
            icon = itemString("icon", groupPosition, childPosition);
        }

        // set title
        TextView title = convertView.findViewById(R.id.menu_item_title);
        title.setText(getTitle(groupPosition, childPosition));
        title.setTextColor(color);

        int typefaceStyle = isSelected ? Typeface.BOLD : Typeface.NORMAL;
        Typeface typeface = Typeface.create("sans-serif-medium", typefaceStyle);

        title.setTypeface(typeface);

        // set icon
        ImageView imageView = convertView.findViewById(R.id.menu_item_icon);
        if (imageView != null) {
            if (icon != null && !icon.isEmpty()) {
                Drawable iconDrawable = new Icon(mainActivity, icon, sidebar_icon_size, color).getDrawable();
                imageView.setImageDrawable(iconDrawable);
                imageView.setVisibility(View.VISIBLE);
            } else {
                imageView.setVisibility(View.INVISIBLE);
            }
        }

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
        try {
            if (isGrouping(groupPosition)) {
                // return false for default handling behavior
                return false;
            } else {
                Pair<String,String> urlAndJavascript = getUrlAndJavascript(groupPosition);
                loadUrlAndJavascript(urlAndJavascript.first, urlAndJavascript.second);
                return true; // tell android that we have handled it
            }
        } catch (Exception e) {
            GNLog.getInstance().logError(TAG, e.getMessage(), e);
        }

        return false;
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
        int index = parent.getFlatListPosition(ExpandableListView.getPackedPositionForChild(groupPosition, childPosition));
        parent.setItemChecked(index, true);
        this.selectedIndex = index;
        Pair<String, String> urlAndJavascript = getUrlAndJavascript(groupPosition, childPosition);
        loadUrlAndJavascript(urlAndJavascript.first, urlAndJavascript.second);
        return true;
    }

    private void loadUrlAndJavascript(String url, String javascript) {
        // check for GONATIVE_USERID
        if (UrlInspector.getInstance().getUserId() != null) {
            url = url.replaceAll("GONATIVE_USERID", UrlInspector.getInstance().getUserId());
        }

        if (javascript == null) mainActivity.getUrlLoader().loadUrl(url, true);
        else mainActivity.getUrlLoader().loadUrlAndJavascript(url, javascript, true, false);

        mainActivity.closeDrawers();
    }

    public void autoSelectItem(String url) {
        String formattedUrl = url.replaceAll("/$", "");
        if (menuItems == null) return;

        for (int i = 0; i < menuItems.length(); i++) {
            if (formattedUrl.equals(menuItems.optJSONObject(i).optString("url").replaceAll("/$", ""))) {
                expandableListView.setItemChecked(i, true);
                selectedIndex = i;
                return;
            }
        }
    }

    private RippleDrawable generateRippleHighlightBackground() {
        GradientDrawable selectedDrawable = getHighlightDrawable();

        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{android.R.attr.state_activated}, selectedDrawable);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, selectedDrawable);

        ColorStateList rippleColor = ColorStateList.valueOf(getRippleColorFromBackground());

        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(100f);
        mask.setColor(rippleColor);

        return new RippleDrawable(rippleColor, stateListDrawable, mask);
    }

    private GradientDrawable getHighlightDrawable() {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(100f);
        shape.setColor(this.highlightColor);
        shape.setAlpha(100);
        return shape;
    }

    private int getRippleColorFromBackground() {
        float factor;

        if (ThemeUtils.isDarkThemeEnabled(mainActivity)) {
            factor = 0.2f; // Lighten by 20%
        } else {
            factor = -0.2f; // Darken by 20%
        }

        int alpha = Color.alpha(backgroudColor);
        int r = Color.red(backgroudColor);
        int g = Color.green(backgroudColor);
        int b = Color.blue(backgroudColor);

        int newR, newG, newB;

        if (factor > 0) {
            // Lighten the color
            newR = Math.min((int) (r + (255 - r) * factor), 255);
            newG = Math.min((int) (g + (255 - g) * factor), 255);
            newB = Math.min((int) (b + (255 - b) * factor), 255);
        } else {
            // Darken the color
            newR = Math.max((int) (r * (1 + factor)), 0);
            newG = Math.max((int) (g * (1 + factor)), 0);
            newB = Math.max((int) (b * (1 + factor)), 0);
        }

        return Color.argb(alpha, newR, newG, newB);
    }

    @Override
    public int getChildType(int groupPosition, int childPosition) {
        if (groupsHaveIcons || childrenHaveIcons) return 0;
        else return 1;
    }

    @Override
    public int getChildTypeCount() {
        return 2;
    }

    @Override
    public int getGroupType(int groupPosition) {
        if (groupsHaveIcons) return 0;
        else return 1;
    }

    @Override
    public int getGroupTypeCount() {
        return 2;
    }
}
