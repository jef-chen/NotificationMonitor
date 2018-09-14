package com.tencent.jefchen.notificationmonitor;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity{

    private static final int GET_PACKAGE_LIST_SUCCESS = 1;

    private String TAG = "MainActivity";

    private List<PackageInfo> mPackageList;
    private List<PackageInfo> mTencentPackageList;

    private Spinner installed_packages_spinner;

    private ImageView application_icon;

    private TextView text_show_notification_count;

    private MyHandler mHandler = new MyHandler(this);
    private PackageInfo packageUnderMonitor;
    private int receivedNotificationCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button bt_send_notification = findViewById(R.id.bt_send_notification);
        installed_packages_spinner = findViewById(R.id.installed_applications_spinner);
        application_icon = findViewById(R.id.application_icon);
        text_show_notification_count = findViewById(R.id.text_show_notification_count);

        new ScanPackagesTask().execute();

        bt_send_notification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendNotification();
            }
        });

        if (!isNotificationServiceEnabled()) {
            AlertDialog enableNotificationListenerDialog = buildNotificationServiceAlertDialog();
            enableNotificationListenerDialog.show();
        }

        ensureServiceIsRunning();
        registerBroadcastReceiver();
    }

    /**
     * 判断NotificationListenerServices是否被授予Notification Access权限
     * @return
     */
    private boolean isNotificationServiceEnabled(){
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());
    }

    /**
     * 确保NotificationListenerService在后台运行，所以通过判断服务是否在运行中的服务中来进行触发系统rebind操作
     */
    private void ensureServiceIsRunning(){
        ComponentName serviceComponent = new ComponentName(this, NotificationMonitorService.class);
        Log.d(TAG, "确保服务NotificationListenerExampleService正在运行");
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        boolean isRunning = false;
        List<ActivityManager.RunningServiceInfo> runningServiceInfos = manager.getRunningServices(Integer.MAX_VALUE);
        if (runningServiceInfos == null) {
            Log.w(TAG, "运行中的服务为空");
            return;
        }

        for (ActivityManager.RunningServiceInfo serviceInfo : runningServiceInfos) {
            if (serviceInfo.service.equals(serviceComponent)){
                Log.w(TAG, "ensureServiceRunning service - pid: "+serviceInfo.pid
                        +",currentPID: " + Process.myPid()
                        +", clientPackage: "+serviceInfo.clientPackage
                        +", clientCount: " +serviceInfo.clientCount
                        +", clientLabel: "+((serviceInfo.clientLabel==0)?"0":"(" +getResources().getString(serviceInfo.clientLabel)+")"));
                if (serviceInfo.pid == Process.myPid()) {
                    isRunning = true;
                }
            }
        }

        if (isRunning) {
            Log.d(TAG, "ensureServiceIsRunning: 监听服务正在运行");
            return;
        }

        Log.d(TAG, "ensureServiceIsRunning: 服务没有运行，重启中...");
        toggleNotificationListenerService();
    }


    /**
     * 不调用下面的函数，第一次安装使用app能够正常读取通知栏的通知。但是把app进城杀掉重启发现不能拦截到通知栏消息，
     * 这是因为监听器服务没有开启，更深层的原因是没有bindService。解决方法是把NotificationListenerService的实
     * 现类disable后再enable，这样可以触发系统的rebind操作。
     *
     * 下面的做法会有点小延迟，大约在10s钟左右。
     */
    private void toggleNotificationListenerService(){
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(this, NotificationMonitorService.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(new ComponentName(this, NotificationMonitorService.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    private AlertDialog buildNotificationServiceAlertDialog(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle(R.string.app_name);
        alertDialogBuilder.setMessage(R.string.start_notification_monitor);
        alertDialogBuilder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            }
        });
        alertDialogBuilder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Toast.makeText(MainActivity.this,"不授予通知读取权限Monitor将无法运行！",Toast.LENGTH_SHORT)
                        .show();
            }
        });
        return alertDialogBuilder.create();
    }

    private void sendNotification(){
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "com.tencent.jefchen")
                .setContentTitle("测试通知")
                .setContentText("点我试试~")
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("com.tencent.jefchen",
                    "Notification Monitor", NotificationManager.IMPORTANCE_DEFAULT);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        manager.notify(2,builder.build());
    }

    private void registerBroadcastReceiver(){
        NotificationBroadcastReceiver broadcastReceiver = new NotificationBroadcastReceiver();
        IntentFilter notificationIntentFilter = new IntentFilter(Constants.BROADCAST_NOTIFICATION_POSTED_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,notificationIntentFilter);
    }

    class NotificationBroadcastReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            String packageName = intent.getStringExtra("packageName");
            Log.d(TAG, "收到包："+packageName+" 通知消息的Broadcast");
            if (packageName.equals(packageUnderMonitor.packageName)) {
                receivedNotificationCount++;
                Log.d(TAG, "目前为止，收到包："+packageName+" 的Notification共"+receivedNotificationCount+"条");
                text_show_notification_count.setText(receivedNotificationCount+"");
            }

        }
    }

    private List<PackageInfo> scanLocalInstalledAppList(){
        List<PackageInfo> packageInfos = null;
        try {
            packageInfos = this.getPackageManager().getInstalledPackages(0);
        } catch (Exception e){
            Log.e(TAG, "获取已安装包信息失败");
            e.printStackTrace();
        }
        return packageInfos;
    }

    private static class MyHandler extends Handler{
        private final WeakReference<MainActivity> mActivity;

        MyHandler(MainActivity activity){
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity mainActivity = mActivity.get();
            if (mainActivity != null) {
                mainActivity.handleMessage(msg);
            }
        }
    }

    private void handleMessage(Message msg){

    }

    private void updateSpinner(){
        mTencentPackageList = new ArrayList<>();
        for (PackageInfo p : mPackageList){
            if (p.packageName.contains("tencent")) {
                mTencentPackageList.add(p);
            }
        }
        String[] apps = new String[mTencentPackageList.size()];
        int i = 0;
        for (PackageInfo p : mTencentPackageList) {
            apps[i] = p.packageName;
            i++;
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, apps);
        installed_packages_spinner.setAdapter(spinnerAdapter);
        installed_packages_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                packageUnderMonitor = mTencentPackageList.get(position);
                changeIconImage(position);
                receivedNotificationCount = 0;
                text_show_notification_count.setText(receivedNotificationCount+"");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void changeIconImage(int pos){
        application_icon.setImageDrawable(mTencentPackageList.get(pos).applicationInfo.loadIcon(getPackageManager()));

    }

    class ScanPackagesTask extends AsyncTask<Void,Integer,Boolean>{

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                mPackageList = MainActivity.this.getPackageManager().getInstalledPackages(0);
            } catch (Exception e){
                Log.e(TAG, "获取已安装包信息失败");
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            if (aBoolean) {
                if (mPackageList != null) {
                    updateSpinner();
                }
            } else {
                Toast.makeText(MainActivity.this, "扫描已安装应用失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
