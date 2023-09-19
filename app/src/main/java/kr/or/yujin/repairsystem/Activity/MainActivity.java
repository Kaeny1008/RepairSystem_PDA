package kr.or.yujin.repairsystem.Activity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import kr.or.yujin.repairsystem.BuildConfig;
import kr.or.yujin.repairsystem.Class.ForecdTerminationService;
import kr.or.yujin.repairsystem.R;

public class MainActivity extends AppCompatActivity {

    public static String server_ip;
    public static int server_port; //실제 SQL포트가 아닌 php포트

    private String activityTag = "Repair System Main Activity";
    private TextView loginStatus;
    private ImageButton btnSetting, btnIncomingCheck, btnSMTManager, btnShipManager;

    private ActivityResultLauncher<Intent> resultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startService(new Intent(this, ForecdTerminationService.class));

        appSettingLoad(); //기본 셋팅값을 불러온다.

        verCheck(); // Application Ver. Check

        checkPermission(); // 권한얻기

        btnSetting = (ImageButton) findViewById(R.id.btnSetting);
        btnIncomingCheck = (ImageButton) findViewById(R.id.btnIncomingCheck);
        btnSMTManager = (ImageButton) findViewById(R.id.btnSMTManager);
        btnShipManager = (ImageButton) findViewById(R.id.btnShipManager);

        loginStatus = (TextView) findViewById(R.id.loginStatus);
        loginStatus.setText("Repair System V" + BuildConfig.VERSION_NAME);

        btnIncomingCheck.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, IncomingCheck.class);
            startActivity(intent);//액티비티 띄우기
        });

        btnSMTManager.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SMT_Manager.class);
            startActivity(intent);//액티비티 띄우기
        });

        btnShipManager.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Module_Ship_Temp.class);
            startActivity(intent);//액티비티 띄우기
        });

        btnSetting.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AppSetting.class);
            startActivity(intent);//액티비티 띄우기
        });
    }

    // 넘어온 데이터를 받는 부분
    ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if(result.getResultCode() == RESULT_OK) {

                }
            }
    );

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // 마시멜로우 버전과 같거나 이상이라면
            if(checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
                if(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "인터넷과 진동모터를 사용해야 합니다.", Toast.LENGTH_SHORT).show();
                }

                requestPermissions(new String[]
                                {Manifest.permission.INTERNET,
                                        Manifest.permission.VIBRATE},
                        2);  // 마지막 인자는 체크해야될 권한 갯수

            } else {
                // Toast.makeText(this, "권한이 이미 승인되었음.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void appSettingLoad(){
        SharedPreferences setting = getSharedPreferences("setting", Activity.MODE_PRIVATE);
        server_ip = setting.getString("serverIP","125.137.78.158");
        server_port = Integer.parseInt(setting.getString("serverPort","10520"));
    }

    private void verCheck(){
        getData task_VerLoad = new getData();
        task_VerLoad.execute( "http://" + server_ip + ":" + server_port + "/Repair_System/app_ver_new.php", "ver");
        Log.d(activityTag, "버전체크 주소 : " + "http://" + server_ip + ":" + server_port + "/Repair_System/app_ver_new.php");
    }

    private class getData extends AsyncTask<String, Void, String> {

        ProgressDialog progressDialog;
        String errorString = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> info = manager.getRunningTasks(1);
            ComponentName componentName= info.get(0).topActivity;
            String ActivityName = componentName.getShortClassName().substring(1);

            if (ActivityName.equals(".Activity.MainActivity"))
                progressDialog = ProgressDialog.show(MainActivity.this,
                        "Connecting to server....\nPlease wait.", null, true, true);
        }

        @Override
        protected String doInBackground(String... params) {

            String serverURL = params[0];
            String postParameters = params[1];

            try {
                URL url = new URL(serverURL);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();

                httpURLConnection.setReadTimeout(5000);
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setDoInput(true);
                httpURLConnection.connect();

                OutputStream outputStream = httpURLConnection.getOutputStream();
                outputStream.write(postParameters.getBytes("UTF-8"));
                outputStream.flush();
                outputStream.close();

                int responseStatusCode = httpURLConnection.getResponseCode();
                Log.d(activityTag, "response code - " + responseStatusCode);

                InputStream inputStream;
                if (responseStatusCode == HttpURLConnection.HTTP_OK) {
                    inputStream = httpURLConnection.getInputStream();
                } else {
                    inputStream = httpURLConnection.getErrorStream();
                }

                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                StringBuilder sb = new StringBuilder();
                String line;

                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }
                bufferedReader.close();
                return sb.toString().trim();
            } catch (Exception e) {
                //Log.d(activityTag, "GetData : Error ", e);
                errorString = e.toString();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            if (progressDialog != null && progressDialog.isShowing())
                progressDialog.dismiss();

            if (result != null){
                Log.d(activityTag, "서버 응답 내용 - " + result);
                showResult(result);
            } else {
                Log.d(activityTag, "서버 접속 Error - " + errorString);
                Toast.makeText(MainActivity.this, "서버에 접속 할 수 없습니다.\n상세 내용은 로그를 참조 하십시오.", Toast.LENGTH_SHORT).show();
            }
        }

        private void showResult(String mJsonString){
            try {
                JSONObject jsonObject = new JSONObject(mJsonString);

                String header = jsonObject.names().toString();
                header = header.replace("[", "");
                header = header.replace("\"", "");
                header = header.replace("]", "");

                if (header.equals("CheckVer")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("CheckVer");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Ver:"+ BuildConfig.VERSION_NAME)){
                        appVerAlarm();
                    }
                } else {
                    Toast.makeText(MainActivity.this, mJsonString, Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                Log.d("Main Activity", "showResult Error : ", e);
            }
        }
    }

    private void appVerAlarm() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("사용 경고");
        builder.setMessage("프로그램 업데이트가 필요합니다.\n담당자에게 요청하십시오.");
        builder.setPositiveButton("확인",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //확인 눌렀을때의 이벤트 처리
                        dialog.dismiss();
                    }
                });
        builder.show();
    }
}