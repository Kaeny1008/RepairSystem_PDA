package kr.or.yujin.repairsystem.Class;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class ForecdTerminationService extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) { //핸들링 하는 부분
        Log.e("Error","onTaskRemoved(강제 종료) - " + rootIntent);
        Toast.makeText(this, "리페어 시스템이이 종료 되었습니다.", Toast.LENGTH_SHORT).show();
        stopSelf(); //서비스 종료
    }
}
