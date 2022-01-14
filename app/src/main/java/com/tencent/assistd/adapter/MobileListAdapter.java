package com.tencent.assistd.adapter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.tencent.assistd.R;
import com.tencent.assistd.signal.Mobile;

import java.util.ArrayList;

public class MobileListAdapter extends ArrayAdapter<Mobile> {
    private Activity activity;
    public MobileListAdapter(@NonNull Activity context, int resource, IMobileListListener listener) {
        super(context, resource, new ArrayList<>());
        activity = context;
        this.listener = listener;
    }

    public interface IMobileListListener {
        void onConnect(Mobile mobile);
        void onReset(Mobile mobile);
    }

    private IMobileListListener listener;

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        //获取当前Browser实例
        Mobile mobile = getItem(position);
        //使用LayoutInfater为子项加载传入的布局
        LayoutInflater inflater = activity.getLayoutInflater();
        @SuppressLint({"ViewHolder", "InflateParams"})
        View itemView = inflater.inflate(R.layout.layout_mobile_list_item, null);
        TextView mobileIdView = itemView.findViewById(R.id.mobileId);
        TextView mobileNameView = itemView.findViewById(R.id.mobileName);
        Button connectBtnView = itemView.findViewById(R.id.connectMobileBtn);
        Button resetBtnView = itemView.findViewById(R.id.resetMobileBtn);
        assert mobile != null;
        mobileIdView.setText(mobile.Id);
        mobileNameView.setText(mobile.Name);
        if (mobile.Status == 1) {
            itemView.setBackgroundColor(Color.parseColor("#fef0f0"));
        }else if (mobile.Status == 2) {
            itemView.setBackgroundColor(Color.parseColor("#fdf6ec"));
        }else {
            itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        connectBtnView.setOnClickListener(v -> {
            listener.onConnect(mobile);
        });
        resetBtnView.setOnClickListener(v -> {
            listener.onReset(mobile);
        });
        return itemView;
    }

}
