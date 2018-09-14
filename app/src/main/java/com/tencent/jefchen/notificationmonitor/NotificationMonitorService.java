package com.tencent.jefchen.notificationmonitor;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class NotificationMonitorService extends NotificationListenerService {

    private String TAG = "NotificationMonitorService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "开启服务：NotificationMonitorService");

//        startForeground(1,getNotification());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "通知所属包名："+sbn.getPackageName());

        Notification notification = sbn.getNotification();
        if (notification == null) {
            return;
        }
        PendingIntent pendingIntent = null;
        // 当 API > 18 时，使用 extras 获取通知的详细信息
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Bundle extras = notification.extras;
            if (extras != null) {
                // 获取通知标题
                String title = extras.getString(Notification.EXTRA_TITLE, "");
                Log.d(TAG, "通知的标题为："+title);
                // 获取通知内容
                String content = extras.getString(Notification.EXTRA_TEXT, "");
                Log.d(TAG, "通知的内容为："+content);
                if (!TextUtils.isEmpty(content) && content.contains("[微信红包]")) {
                    pendingIntent = notification.contentIntent;
                }
            }
        } else {
            // 当 API = 18 时，利用反射获取内容字段
            List<String> textList = getText(notification);
            if (textList != null && textList.size() > 0) {
                for (String text : textList) {
                    Log.d(TAG, "通知的内容包含："+text);
                    if (!TextUtils.isEmpty(text) && text.contains("[微信红包]")) {
                        pendingIntent = notification.contentIntent;
                        break;
                    }
                }
            }
        }
        // 发送 pendingIntent 以此打开微信
        try {
            if (pendingIntent != null) {
                pendingIntent.send();
            }
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "监听到包："+sbn.getPackageName()+" 的通知被移除");
        Intent localIntent = new Intent(Constants.BROADCAST_NOTIFICATION_REMOVED_ACTION);
        localIntent.putExtra("packageName",sbn.getPackageName());
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"监听服务被销毁");
//        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    private Notification getNotification(){
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,0,intent,0);
        Notification notification = new Notification.Builder(this)
                .setContentTitle("Notification Monitor Service")
                .setContentText("请不要杀掉Monitor进程，服务正在运行...")
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher))
                .setContentIntent(pendingIntent)
                .build();
        return notification;
    }

    @Override
    public void onListenerDisconnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(new ComponentName(this, NotificationMonitorService.class));
        }
    }

    public List<String> getText(Notification notification) {
        if (null == notification) {
            return null;
        }
        RemoteViews views = notification.bigContentView;
        if (views == null) {
            views = notification.contentView;
        }
        if (views == null) {
            return null;
        }
        // Use reflection to examine the m_actions member of the given RemoteViews object.
        // It's not pretty, but it works.
        List<String> text = new ArrayList<>();
        try {
            Field field = views.getClass().getDeclaredField("mActions");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<Parcelable> actions = (ArrayList<Parcelable>) field.get(views);
            // Find the setText() and setTime() reflection actions
            for (Parcelable p : actions) {
                Parcel parcel = Parcel.obtain();
                p.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                // The tag tells which type of action it is (2 is ReflectionAction, from the source)
                int tag = parcel.readInt();
                if (tag != 2) continue;
                // View ID
                parcel.readInt();
                String methodName = parcel.readString();
                if (null == methodName) {
                    continue;
                } else if (methodName.equals("setText")) {
                    // Parameter type (10 = Character Sequence)
                    parcel.readInt();
                    // Store the actual string
                    String t = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel).toString().trim();
                    text.add(t);
                }
                parcel.recycle();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return text;
    }
}
