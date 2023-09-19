package kr.or.yujin.repairsystem.Activity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import device.common.ScanConst;
import kr.or.yujin.repairsystem.BuildConfig;
import kr.or.yujin.repairsystem.Class.MyFunction;
import kr.or.yujin.repairsystem.R;

public class SMT_Working_Information extends AppCompatActivity {

    //Server 접속주소
    private static String server_ip = MainActivity.server_ip;
    private static int server_port = MainActivity.server_port;

    private TextView etWorker, etLotNo, etWorkMode, etPartNo;
    private Button btnWorkEnd, btnMaterialUse, btnMaterialChange;

    private String activityTag = "SMT 작업정보";

    private ActivityResultLauncher<Intent> resultLauncher;

    private TableLayout tableLayout, tlFeeder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smt_working_information);

        this.InitializeControl();

        etWorker.setText(getIntent().getStringExtra("worker"));
        etLotNo.setText(getIntent().getStringExtra("lot_no"));
        etWorkMode.setText(getIntent().getStringExtra("work_mode"));

        // 자재 투입 내역 및 파트넘버를 불러 온다.
        getData load_part_no = new getData();
        load_part_no.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/load_partno.php",
                "load_part_no",
                etLotNo.getText().toString());

        getData load_part_history = new getData();
        load_part_history.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/load_part_history.php",
                "load_part_history",
                etLotNo.getText().toString(),
                etWorkMode.getText().toString());

        // Dipping Feeder Check 결과를 가져 온다.
        getData load_dipping_feeder_list = new getData();
        load_dipping_feeder_list.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/load_dipping_feeder_used_list.php",
                "load_dipping_feeder_list",
                etLotNo.getText().toString(),
                etWorkMode.getText().toString());

        btnWorkEnd.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WorkingEnd_Question();
            }
        }));

        btnMaterialUse.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //액티비티 호출하는 부분
                Intent intent = new Intent(SMT_Working_Information.this, SMT_Parts_Use.class);
                intent.putExtra("worker", etWorker.getText().toString());
                intent.putExtra("lot_no", etLotNo.getText().toString());
                intent.putExtra("work_mode", etWorkMode.getText().toString());
                intent.putExtra("part_no", etPartNo.getText().toString());
                mStartForResult.launch(intent);
            }
        }));

        btnMaterialChange.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tableLayout.getChildCount() == 1){
                    Toast.makeText(SMT_Working_Information.this,
                            "사용된 자재가 없습니다.\n먼저 자재투입을 하여 주십시오.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                //액티비티 호출하는 부분
                Intent intent = new Intent(SMT_Working_Information.this, SMT_Parts_Change.class);
                intent.putExtra("worker", etWorker.getText().toString());
                intent.putExtra("lot_no", etLotNo.getText().toString());
                intent.putExtra("work_mode", etWorkMode.getText().toString());
                intent.putExtra("part_no", etPartNo.getText().toString());
                mStartForResult.launch(intent);
            }
        }));
    }

    // 넘어온 데이터를 받는 부분
    ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if(result.getResultCode() == 10001 ||
                        result.getResultCode() == 10002) {
                    Intent intent = result.getData();
                    if (intent.getStringExtra("material_use_reg").equals("OK")){
                        //Log.d(activityTag, "자재사용 등록 확인");
                        Toast.makeText(SMT_Working_Information.this,
                                "자재투입(사용) 등록이 완료 되었습니다.",
                                Toast.LENGTH_SHORT).show();

                        // 투입내역을 새로 불러 온다.
                        getData load_part_history = new getData();
                        load_part_history.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/load_part_history.php",
                                "load_part_history",
                                etLotNo.getText().toString(),
                                etWorkMode.getText().toString());

                        // Dipping Feeder Check 결과를 가져 온다.
                        getData load_dipping_feeder_list = new getData();
                        load_dipping_feeder_list.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/load_dipping_feeder_used_list.php",
                                "load_dipping_feeder_list",
                                etLotNo.getText().toString(),
                                etWorkMode.getText().toString());
                    }
                } else if (result.getResultCode() == 10003){
                    Intent intent = new Intent(SMT_Working_Information.this, SMT_Manager.class);
                    intent.putExtra("work_end","End");
                    intent.putExtra("work_mode",etWorkMode.getText().toString());
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
    );

    private void WorkingEnd_Question() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("SMT 작업 종료");
        //타이틀설정
        builder.setMessage("작업 종료 하시겠습니까?\n(작업종료 창으로 전환합니다.)");
        builder.setCancelable(false); // 뒤로가기로 취소
        //내용설정
        builder.setPositiveButton("작업종료",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // 마지막 자재의 사용수량, Loss수량 입력하는 액티비티를 띄운다.
                        Intent intent = new Intent(SMT_Working_Information.this, SMT_Working_End.class);
                        intent.putExtra("worker", etWorker.getText().toString());
                        intent.putExtra("lot_no", etLotNo.getText().toString());
                        intent.putExtra("work_mode", etWorkMode.getText().toString());
                        intent.putExtra("part_no", etPartNo.getText().toString());
                        mStartForResult.launch(intent);
                    }
                });

        builder.setNegativeButton("취소",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {

            }
        });
        builder.show();
    }

    private void InitializeControl() {
        etLotNo = (TextView) findViewById(R.id.etLotNo);
        etWorker = (TextView) findViewById(R.id.etWorker);
        etWorkMode = (TextView) findViewById(R.id.etWorkMode);
        etPartNo = (TextView) findViewById(R.id.etPartNo);
        btnWorkEnd = (Button) findViewById(R.id.btnWorkEnd);
        btnMaterialUse = (Button) findViewById(R.id.btnMaterialUse);
        btnMaterialChange = (Button) findViewById(R.id.btnMaterialChange);

        tableLayout = (TableLayout) findViewById(R.id.tlList);
        tlFeeder = (TableLayout) findViewById(R.id.tlFeeder);
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

            if (ActivityName.equals("Activity.SMT_Manager"))
                progressDialog = ProgressDialog.show(SMT_Working_Information.this,
                        "Connecting to server....\nPlease wait.", null, true, true);
        }

        @Override
        protected String doInBackground(String... params) {

            String serverURL = params[0];

            String secondString = (String) params[1];
            String postParameters = null;

            if (secondString.equals("load_part_no")) {
                postParameters = "lot_no=" + params[2];
            } else if (secondString.equals("load_part_history")) {
                postParameters = "lot_no=" + params[2];
                postParameters += "&work_section=" + params[3];
            } else if (secondString.equals("load_dipping_feeder_list")){
                postParameters = "lot_no=" + params[2];
                postParameters += "&work_section=" + params[3];
            } else if (secondString.equals("ver")) {
                postParameters = "";
            }

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
                Log.d(activityTag, "getData : Error ", e);
                errorString = e.toString();
                return null;
            }
        }

        @Override
        protected void onPostExecute (String result){
            super.onPostExecute(result);

            if (progressDialog != null && progressDialog.isShowing())
                progressDialog.dismiss();

            if (result == null){
                Log.d(activityTag, "서버 접속 Error - " + errorString);
                Toast.makeText(SMT_Working_Information.this,
                        "서버에 접속 할 수 없습니다.\n상세 내용은 로그를 참조 하십시오.",
                        Toast.LENGTH_SHORT).show();
            } else {
                Log.d(activityTag, "서버 응답 내용 - " + result);
                showResult(result);
            }
        }

        private void showResult (String mJsonString){
            try {
                JSONObject jsonObject = new JSONObject(mJsonString);

                String header = jsonObject.names().toString();
                header = header.replace("[", "");
                header = header.replace("\"", "");
                header = header.replace("]", "");
                if (header.equals("part_no")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("part_no");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (etWorkMode.getText().toString().equals("PMIC")) {
                        etPartNo.setText(item.getString("pmic_partno"));
                    } else if (etWorkMode.getText().toString().equals("RCD")) {
                        etPartNo.setText(item.getString("rcd_partno"));
                    }
                } else if (header.equals("part_no")) {
                    Toast.makeText(SMT_Working_Information.this,
                            "Part No.를 확인 할 수 없습니다.",
                            Toast.LENGTH_SHORT).show();
                } else if (header.equals("used_part_list")){
                    //테이블 레이아웃 초기화
                    tableLayout.removeViews(1, tableLayout.getChildCount()-1);
                    JSONArray jsonArray = jsonObject.getJSONArray("used_part_list");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        TableRow tableRow = new TableRow(SMT_Working_Information.this); //tablerow 생성
                        tableRow.setLayoutParams(new TableRow.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));

                        int selColor = Color.WHITE;
                        if (i % 2 == 1) {
                            selColor = Color.parseColor("#00D8FF");
                        }

                        JSONObject item = jsonArray.getJSONObject(i);
                        TextView textView = new TextView(SMT_Working_Information.this);
                        textView.setText(String.valueOf(tableLayout.getChildCount()));
                        textView.setGravity(Gravity.CENTER);
                        textView.setBackgroundColor(selColor);
                        tableRow.addView(textView);
                        TextView textView2 = new TextView(SMT_Working_Information.this);
                        textView2.setText(item.getString("material_part_no"));
                        textView2.setGravity(Gravity.CENTER);
                        textView2.setBackgroundColor(selColor);
                        tableRow.addView(textView2);
                        TextView textView3 = new TextView(SMT_Working_Information.this);
                        textView3.setText(item.getString("material_lot_no"));
                        textView3.setGravity(Gravity.CENTER);
                        textView3.setBackgroundColor(selColor);
                        tableRow.addView(textView3);
                        TextView textView4 = new TextView(SMT_Working_Information.this);
                        textView4.setText(MyFunction.decimalFormat(Integer.parseInt(item.getString("basic_stock_qty"))));
                        textView4.setGravity(Gravity.CENTER);
                        textView4.setBackgroundColor(selColor);
                        tableRow.addView(textView4);
                        String usingCount = null;
                        if (item.getString("material_used_qty").equals("0")){
                            usingCount = "사용중";
                        } else {
                            usingCount = MyFunction.decimalFormat(Integer.parseInt(item.getString("material_used_qty")));
                        }
                        TextView textView5 = new TextView(SMT_Working_Information.this);
                        textView5.setText(usingCount);
                        textView5.setGravity(Gravity.CENTER);
                        textView5.setBackgroundColor(selColor);
                        tableRow.addView(textView5);
                        tableLayout.addView(tableRow);
                    }
                } else if (header.equals("used_part_list!")){
                    //Toast.makeText(SMT_Working_Information.this,
                    //        "자재 사용이력을 확인 할 수 없습니다.",
                    //        Toast.LENGTH_SHORT).show();
                } else if (header.equals("dipping_feeder_list")){
                    //테이블 레이아웃 초기화
                    tlFeeder.removeViews(1, tlFeeder.getChildCount()-1);
                    JSONArray jsonArray = jsonObject.getJSONArray("dipping_feeder_list");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        TableRow tableRow = new TableRow(SMT_Working_Information.this); //tablerow 생성
                        tableRow.setLayoutParams(new TableRow.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));

                        int selColor = Color.WHITE;
                        if (i % 2 == 1) {
                            selColor = Color.parseColor("#00D8FF");
                        }

                        JSONObject item = jsonArray.getJSONObject(i);
                        TextView textView = new TextView(SMT_Working_Information.this);
                        textView.setText(String.valueOf(tlFeeder.getChildCount()));
                        textView.setGravity(Gravity.CENTER);
                        textView.setBackgroundColor(selColor);
                        tableRow.addView(textView);
                        TextView textView2 = new TextView(SMT_Working_Information.this);
                        textView2.setText(item.getString("dipping_feeder_no"));
                        textView2.setGravity(Gravity.CENTER);
                        textView2.setBackgroundColor(selColor);
                        tableRow.addView(textView2);
                        TextView textView3 = new TextView(SMT_Working_Information.this);
                        textView3.setText(item.getString("feeder_for_use"));
                        textView3.setGravity(Gravity.CENTER);
                        textView3.setBackgroundColor(selColor);
                        tableRow.addView(textView3);
                        TextView textView4 = new TextView(SMT_Working_Information.this);
                        textView4.setText(item.getString("dipping_feeder_check"));
                        textView4.setGravity(Gravity.CENTER);
                        textView4.setBackgroundColor(selColor);
                        tableRow.addView(textView4);
                        tlFeeder.addView(tableRow);
                    }
                } else if (header.equals("dipping_feeder_list!")) {

                } else if (header.equals("CheckVer")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("CheckVer");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Ver:"+ BuildConfig.VERSION_NAME)){
                        appVerAlarm();
                    }
                } else {
                    Toast.makeText(SMT_Working_Information.this, mJsonString, Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                Log.d(activityTag, "showResult Error : ", e);
            }
        }
    }

    private void verCheck(){
        getData task_VerLoad = new getData();
        task_VerLoad.execute( "http://" + server_ip + ":" + server_port + "/Repair_System/app_ver_new.php", "ver");
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
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                });
        builder.show();
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        verCheck();
    }
}