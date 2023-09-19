package kr.or.yujin.repairsystem.Activity;

import androidx.annotation.Dimension;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Switch;
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
import java.util.List;

import kr.or.yujin.repairsystem.BuildConfig;
import kr.or.yujin.repairsystem.Class.MyFunction;
import kr.or.yujin.repairsystem.R;

public class SMT_Program_Name_Check extends AppCompatActivity {

    private String activityTag = "공정별 프로그램 확인";
    //Server 접속주소
    private static String server_ip = MainActivity.server_ip;
    private static int server_port = MainActivity.server_port;

    private TextView etWorkMode, etPartNo, etLotNo;
    private Button btnCheckEnd;

    private TableLayout tableLayout;
    private String checkResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smt_program_name_check);

        this.InitializeControl();

        getData load_dipping_feeder_list = new getData();
        load_dipping_feeder_list.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/load_product.php",
                "load_product",
                getIntent().getStringExtra("lot_no"));

        btnCheckEnd.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tableRow_AllOK_Check();
            }
        }));
    }

    private void tableRow_AllOK_Check(){
        int checkCount = 0;
        TableLayout tableView = (TableLayout)findViewById(R.id.tlProcess);
        View myTempView=null;
        int noOfChild = tableView.getChildCount();
        for (int i = 0; i <noOfChild; i++) {
            myTempView = tableView.getChildAt(i);
            View vv = ((TableRow) myTempView).getChildAt(1);
            if (vv instanceof CheckBox) {
                if (((CheckBox) vv).isChecked()) {
                    checkCount+=1;
                }
            }
        }
        Log.d(activityTag, "Table Row Count : " + (noOfChild-1) + ",   확인 Count : " + checkCount);
        if ((noOfChild-1)==checkCount){
            // 서버에 저장하는 부분을 작성하고 저장이 이상없으면 Manager로 전송하는걸 해야한다.
            // 서버에 Table 작성필요
            insertCheckResult();
        } else {
            Toast.makeText(SMT_Program_Name_Check.this,
                    "확인되지 않은 항목이 존재합니다.\n 재 확인하여 주십시오.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void insertCheckResult() {
        String nowDateTime = MyFunction.getDateTime();
        String sendText = "";

        TableLayout tableView = (TableLayout)findViewById(R.id.tlProcess);
        View myTempView=null;
        int noOfChild = tableView.getChildCount();
        for (int i = 1; i < noOfChild; i++) {
            sendText += "insert into smt_process_pgm_name_check(lot_no, work_section, process_name, process_pgm_name, check_date) values(";
            sendText += "'" + etLotNo.getText() + "'";
            sendText += ",'" + etWorkMode.getText() + "'";
            myTempView = tableView.getChildAt(i);
            for (int j = 1; j < 3; j++){
                View vv = ((TableRow) myTempView).getChildAt(j);
                if (vv instanceof TextView) {
                    sendText += ",'" + ((TextView) vv).getText().toString() + "'";
                }
            }
            sendText += ",'" + nowDateTime + "');";
        }
        Log.d(activityTag, "Program Name Check 전송 sql : " + sendText);
        getData update_parts_history = new getData();
        update_parts_history.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/insert_pgm_name_check.php",
                "insert_CheckResult",
                sendText);
    }

    private void InitializeControl() {
        etLotNo = (TextView) findViewById(R.id.etLotNo);
        etWorkMode = (TextView) findViewById(R.id.etWorkMode);
        etPartNo = (TextView) findViewById(R.id.etPartNo);
        tableLayout = (TableLayout) findViewById(R.id.tlProcess);
        btnCheckEnd = (Button) findViewById(R.id.btnCheckEnd);
    }

    private void making_ProgramName(String lotStatus, String temp_PMIC_PartNo, String temp_RCD_PartNo) {
        String density = (Integer.parseInt(etPartNo.getText().toString().substring(5, 6)) * 8) + "GB";
        String name_JetPrinter = "";
        if (etWorkMode.getText().equals("PMIC")) {
            name_JetPrinter = "PMIC_" +
                    temp_PMIC_PartNo;
        }
        Log.d(activityTag, "현재 제품의 Jet Printer Program명 :" + name_JetPrinter);
        String name_Mounter = "DDR5_" +
                density +
                "_" +
                etWorkMode.getText();
        Log.d(activityTag, "현재 제품의 Mounter Program명 :" + name_Mounter);
        String name_Reflow = "DDR5_" +
                density +
                "(" +
                etPartNo.getText().toString().substring(0, 12) +
                ")_" +
                etWorkMode.getText();
        Log.d(activityTag, "현재 제품의 Reflow Program명 :" + name_Reflow);
        String name_AOI = "DDR5_" +
                density +
                "(" +
                etPartNo.getText().toString().substring(0, 12) +
                ")_" +
                etPartNo.getText().toString().substring(16, 18) +
                "_" +
                etWorkMode.getText();
        Log.d(activityTag, "현재 제품의 S-AOI Program명 :" + name_AOI);

        if (etWorkMode.getText().toString().equals("PMIC") &&
                lotStatus.equals("SMT PMIC Working")) {
            add_TableLayOut("Jet Printer", name_JetPrinter, 1);
            add_TableLayOut("Mounter 1", name_Mounter, 0);
            add_TableLayOut("Mounter 2", name_Mounter, 1);

        } else if (etWorkMode.getText().toString().equals("PMIC") &&
                    lotStatus.equals("PMIC Working Completed")){
            add_TableLayOut("Reflow", name_Reflow, 1);
            add_TableLayOut("AOI", name_AOI, 0);
        } else if (etWorkMode.getText().toString().equals("RCD")) {
            add_TableLayOut("Mounter 1", name_Mounter, 1);
            add_TableLayOut("Mounter 2", name_Mounter, 0);
            add_TableLayOut("Reflow", name_Reflow, 1);
            add_TableLayOut("AOI", name_AOI, 0);
        }
    }

    private void add_TableLayOut(String nameProcess, String NamePGM, Integer colorNo){
        //tableLayout.removeViews(1, tableLayout.getChildCount()-1);
        TableRow tableRow = new TableRow(SMT_Program_Name_Check.this); //tablerow 생성
        tableRow.setLayoutParams(new TableRow.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        int selColor = Color.WHITE;
        if (colorNo == 1) {
            selColor = Color.parseColor("#00D8FF");
        }

        TextView textView = new TextView(SMT_Program_Name_Check.this);
        textView.setText(String.valueOf(tableLayout.getChildCount()));
        textView.setGravity(Gravity.CENTER);
        textView.setBackgroundColor(selColor);
        textView.setHeight(100);
        textView.setTextSize(Dimension.SP, 12);
        textView.setTextColor(Color.BLACK);
        tableRow.addView(textView);
        CheckBox textView2 = new CheckBox(SMT_Program_Name_Check.this);
        textView2.setText(nameProcess);
        textView2.setGravity(Gravity.CENTER);
        textView2.setBackgroundColor(selColor);
        textView2.setHeight(100);
        textView2.setTextSize(Dimension.SP, 12);
        tableRow.addView(textView2);
        TextView textView3 = new TextView(SMT_Program_Name_Check.this);
        textView3.setText(NamePGM);
        textView3.setGravity(Gravity.CENTER);
        textView3.setBackgroundColor(selColor);
        textView3.setHeight(100);
        textView3.setTextSize(Dimension.SP, 12);
        textView3.setTextColor(Color.BLACK);
        tableRow.addView(textView3);
        tableLayout.addView(tableRow);
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
                progressDialog = ProgressDialog.show(SMT_Program_Name_Check.this,
                        "Connecting to server....\nPlease wait.", null, true, true);
        }

        @Override
        protected String doInBackground(String... params) {

            String serverURL = params[0];

            String secondString = (String) params[1];
            String postParameters = null;

            if (secondString.equals("load_product")) {
                postParameters = "lot_no=" + params[2];
            } else if (secondString.equals("insert_CheckResult")) {
                postParameters = "sql=" + params[2];
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
                Toast.makeText(SMT_Program_Name_Check.this,
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
                if (header.equals("product")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("product");
                    JSONObject item = jsonArray.getJSONObject(0);
                    etPartNo.setText(item.getString("product"));
                    etLotNo.setText(getIntent().getStringExtra("lot_no"));
                    etWorkMode.setText(getIntent().getStringExtra("work_mode"));
                    making_ProgramName(item.getString("lot_status"),
                            item.getString("pmic_partno"),
                            item.getString("rcd_partno"));
                } else if (header.equals("product!")){
                } else if (header.equals("insertCheckResult")){
                    JSONArray jsonArray = jsonObject.getJSONArray("insertCheckResult");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Success")){
                        if (item.getString("Result").contains("SQLSTATE[23000]")){
                            Toast.makeText(SMT_Program_Name_Check.this,
                                    "이미 저장(입력)된 데이터 입니다.\n확인하여 주십시오.",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SMT_Program_Name_Check.this,
                                    mJsonString,
                                    Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Intent intent = new Intent(SMT_Program_Name_Check.this, SMT_Manager.class);
                        intent.putExtra("check_end","End");
                        setResult(10004, intent);
                        finish();
                    }
                } else if (header.equals("CheckVer")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("CheckVer");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Ver:"+ BuildConfig.VERSION_NAME)){
                        appVerAlarm();
                    }
                } else {
                    Toast.makeText(SMT_Program_Name_Check.this, mJsonString, Toast.LENGTH_SHORT).show();
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
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        verCheck();
    }
}