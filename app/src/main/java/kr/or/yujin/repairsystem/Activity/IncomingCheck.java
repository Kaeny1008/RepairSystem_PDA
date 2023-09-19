package kr.or.yujin.repairsystem.Activity;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;


import device.common.DecodeResult;
import device.common.DecodeStateCallback;
import device.common.ScanConst;
import device.sdk.ScanManager;
import kr.or.yujin.repairsystem.BuildConfig;
import kr.or.yujin.repairsystem.Class.MyFunction;
import kr.or.yujin.repairsystem.R;

public class IncomingCheck extends AppCompatActivity {

    //Server 접속주소
    private static String server_ip = MainActivity.server_ip;
    private static int server_port = MainActivity.server_port;

    // Scanner Setting
    private static ScanManager mScanner;
    private static DecodeResult mDecodeResult;
    private boolean mKeyLock = false;

    private AlertDialog mDialog = null;
    private int mBackupResultType = ScanConst.ResultType.DCD_RESULT_COPYPASTE;
    private Context mContext;
    private ProgressDialog mWaitDialog = null;
    private final Handler mHandler = new Handler();
    private static ScanResultReceiver mScanResultReceiver = null;
    private Vibrator vibrator;
    // Scanner Setting

    private String activityTag = "입고Lot 확인";
    private DatePickerDialog.OnDateSetListener callbackMethod;
    private TextView tvSelIncomingDate,tvIncomingDate,tvStatus;
    private Spinner spnSlipNo;
    private Button btnResultSave;

    private ArrayList<String> list_SlipNo; // 스피너의 네임 리스트
    private ArrayAdapter<String> adt_SlipNo; // 스피너에 사용되는 ArrayAdapter
    private int firstRun_spnSlipNo = 0;

    private TableLayout tableLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_check);

        this.InitializeControl();
        this.InitializeListener();

        server_ip = MainActivity.server_ip;
        server_port = MainActivity.server_port;

        tvSelIncomingDate.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Calendar c = Calendar.getInstance();
                int year = c.get(Calendar.YEAR);
                int month = c.get(Calendar.MONTH);
                int day = c.get(Calendar.DAY_OF_MONTH);

                DatePickerDialog dialog = new DatePickerDialog(IncomingCheck.this, callbackMethod, year, month, day);
                dialog.show();
            }
        }));

        mContext = this;
        mScanner = new ScanManager();
        mDecodeResult = new DecodeResult();
        mScanResultReceiver = new ScanResultReceiver();

        if (mScanner != null) {
            mScanner.aDecodeSetTriggerMode(ScanConst.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
            mScanner.aDecodeSetResultType(ScanConst.ResultType.DCD_RESULT_USERMSG);
            mScanner.aDecodeSetBeepEnable(1);

            int symID = ScanConst.SymbologyID.DCD_SYM_UPCA;
            int propCnt = mScanner.aDecodeSymGetLocalPropCount(symID);
            int propIndex = 0;

            for (int i = 0; i < propCnt; i++) {
                String propName = mScanner.aDecodeSymGetLocalPropName(symID, i);
                if (propName.equals("Send Check Character")) {
                    propIndex = i;
                    break;
                }
            }

            if (mKeyLock == false) {
                mKeyLock = true;
                mScanner.aDecodeSymSetLocalPropEnable(symID, propIndex, 0);
            } else {
                mKeyLock = false;
                mScanner.aDecodeSymSetLocalPropEnable(symID, propIndex, 1);
            }
        }

        spnSlipNo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (firstRun_spnSlipNo == 0) { //자동실행 방지
                    firstRun_spnSlipNo += 1;
                } else {
                    //Toast.makeText(IncomingCheck.this,"선택 된 Slip No. : " + list_SlipNo.get(position),Toast.LENGTH_SHORT).show();
                    //Log.d(activityTag, "선택 된 고객사 코드 : " + customerCodeList.get(position));
                    if (spnSlipNo.getSelectedItemPosition() != 0) {
                        getData load_LotList = new getData();
                        load_LotList.execute("http://" + server_ip + ":" + server_port + "/Repair_System/Incoming_Check/load_lotlist.php",
                                "load_LotList",
                                list_SlipNo.get(position));
                    } else {
                        tableLayout.removeViews(1, tableLayout.getChildCount()-1);
                    }
                    tvStatus.setText("진행사항 안내");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Dummy
            }
        });

        btnResultSave.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tvStatus.getText().equals("모든 항목이 확인되었습니다.")) {
                    String insertText = "insert into module_in_check_history(slip_no, check_date) values(";
                    insertText += "'" + spnSlipNo.getSelectedItem().toString() + "'";
                    insertText += ",'" + MyFunction.getDateTime() + "');";
                    //Log.d(activityTag, "Insert Text : "+insertText);
                    getData taskSave = new getData();
                    taskSave.execute("http://" + server_ip + ":" + server_port + "/Repair_System/Incoming_Check/check_insert.php"
                            , "insertCheck"
                            , insertText);
                } else {
                    Toast.makeText(IncomingCheck.this,
                            "모든 항목이 확인되지 않았습니다.",
                            Toast.LENGTH_SHORT).show();
                    tvStatus.setText("모든 항목이 확인되지 않았습니다.");
                }
            }
        }));
    }

    private void InitializeControl()
    {
        tvSelIncomingDate = (TextView) findViewById(R.id.tvSelIncomingDate);
        tvStatus = (TextView) findViewById(R.id.tvStatus);

        vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);

        list_SlipNo = new ArrayList<String>();
        adt_SlipNo = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, list_SlipNo);
        spnSlipNo = (Spinner) findViewById(R.id.spnSlipNo);
        spnSlipNo.setAdapter(adt_SlipNo);

        tableLayout = (TableLayout) findViewById(R.id.tlList);

        btnResultSave = (Button) findViewById(R.id.btnResultSave);
    }

    private void InitializeListener()
    {
        callbackMethod = new DatePickerDialog.OnDateSetListener()
        {
            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth)
            {
                //날짜가 선택되었다면 텍스트뷰에 표시
                String replaceMonth = String.format("%02d", (monthOfYear + 1));
                String replaceDay = String.format("%02d", (dayOfMonth));
                tvSelIncomingDate.setText(year + "년 " + replaceMonth + "월 " + replaceDay + "일");
                //표시된 날짜의 Slip No. 목록을 불러 온다.
                getData taskSave = new getData();
                taskSave.execute("http://" + server_ip + ":" + server_port + "/Repair_System/Incoming_Check/load_slipno.php"
                        , "loadSlipNo"
                        , year + "-" + replaceMonth + "-" + replaceDay);
                tvStatus.setText("진행사항 안내");
            }
        };
    }

    public class ScanResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mScanner != null) {
                try {
                    if (ScanConst.INTENT_USERMSG.equals(intent.getAction())) {
                        mScanner.aDecodeGetResult(mDecodeResult.recycle());
                        Log.d(activityTag, "IncomingCheck Scan Result - " + mDecodeResult.toString());

                        //TableLayOut을 순차적으로 돌면서 동일한 Lot No.를 찾는다.
                        Boolean findOK = false;
                        TableLayout tableView = (TableLayout)findViewById(R.id.tlList);
                        View myTempView=null;
                        int noOfChild = tableView.getChildCount();
                        for (int i = 0; i < noOfChild; i++) {
                            myTempView = tableView.getChildAt(i);

                            View vv = ((TableRow) myTempView).getChildAt(1);
                            if (vv instanceof TextView) {
                                if (((TextView) vv).getText().toString().equals(mDecodeResult.toString())) {
                                    Log.d(activityTag, "찾았다 : " + i);
                                    View vv2 = ((TableRow) myTempView).getChildAt(3);
                                    if (((TextView) vv2).getText().toString().equals("Checked")) {
                                        long[] pattern = {500,1000,500,1000};
                                        vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
                                        Toast.makeText(IncomingCheck.this,
                                                "이미 확인된 Lot No.입니다.\n확인하여 주십시오.",
                                                Toast.LENGTH_SHORT).show();
                                        tvStatus.setText("이미 확인된 Lot No.입니다.\n확인하여 주십시오.");
                                        findOK = true;
                                        break;
                                    } else {
                                        findOK = true;
                                    }
                                }
                            }
                            if (findOK == true) {
                                View vv2 = ((TableRow) myTempView).getChildAt(3);
                                ((TextView) vv2).setText("Checked"); //체크표시
                                long[] pattern = {100,100,100,100};
                                vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
                                Toast.makeText(IncomingCheck.this,
                                        "확인 되었습니다.",
                                        Toast.LENGTH_SHORT).show();
                                tvStatus.setText("확인 되었습니다.");
                                break;
                            }
                        }
                        if (findOK == false) {
                            long[] pattern = {500,1000,500,1000};
                            vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
                            Toast.makeText(IncomingCheck.this,
                                    "Lot No.가 확인되지 않았습니다.\n확인하여 주십시오.",
                                    Toast.LENGTH_LONG).show();
                            tvStatus.setText("Lot No.가 확인되지 않았습니다.\n확인하여 주십시오.");
                        }
                        tableRow_AllOK_Check();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    private void tableRow_AllOK_Check(){
        int checkCount = 0;
        TableLayout tableView = (TableLayout)findViewById(R.id.tlList);
        View myTempView=null;
        int noOfChild = tableView.getChildCount();
        for (int i = 0; i <noOfChild; i++) {
            myTempView = tableView.getChildAt(i);
            View vv = ((TableRow) myTempView).getChildAt(3);
            if (vv instanceof TextView) {
                if (((TextView) vv).getText().toString().equals("Checked")) {
                    checkCount+=1;
                }
            }
        }
        Log.d(activityTag, "Table Row Count : " + (noOfChild-1) + ",   확인 Count : " + checkCount);
        if ((noOfChild-1)==checkCount){
            long[] pattern = {100,100,100,100,100,100,100,100};
            vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
            Toast.makeText(IncomingCheck.this,
                    "모든 항목이 확인되었습니다.",
                    Toast.LENGTH_LONG).show();
            tvStatus.setText("모든 항목이 확인되었습니다.");
        }
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

            if (ActivityName.equals("Activity.IncomingCheck"))
                progressDialog = ProgressDialog.show(IncomingCheck.this,
                        "Connecting to server....\nPlease wait.", null, true, true);
        }

        @Override
        protected String doInBackground(String... params) {

            String serverURL = params[0];

            String secondString = (String) params[1];
            String postParameters = null;

            if (secondString.equals("loadSlipNo")) {
                postParameters = "find_Date=" + params[2];
            } else if (secondString.equals("load_LotList")){
                postParameters = "slipNo=" + params[2];
            } else if (secondString.equals("insertCheck")){
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
                Toast.makeText(IncomingCheck.this, "서버에 접속 할 수 없습니다.\n상세 내용은 로그를 참조 하십시오.", Toast.LENGTH_SHORT).show();
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
                if (header.equals("slip_no_list")) {
                    adt_SlipNo.clear();
                    adt_SlipNo.notifyDataSetChanged();
                    adt_SlipNo.add("선택");

                    JSONArray jsonArray = jsonObject.getJSONArray("slip_no_list");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject item = jsonArray.getJSONObject(i);
                        adt_SlipNo.add(item.getString("slip_no"));
                        //Log.d(activityTag, "입력 slip_no : " + item.getString("slip_no"));
                    }
                    adt_SlipNo.notifyDataSetChanged();
                    spnSlipNo.setSelection(0);
                    tableLayout.removeViews(1, tableLayout.getChildCount()-1);
                } else if (header.equals("slip_no_list!")) {
                    adt_SlipNo.clear();
                    adt_SlipNo.add("선택");
                    spnSlipNo.setSelection(0);
                    adt_SlipNo.notifyDataSetChanged();
                    tableLayout.removeViews(1, tableLayout.getChildCount()-1);
                    Toast.makeText(IncomingCheck.this, "확인되지 않은 Lot이 없습니다.", Toast.LENGTH_SHORT).show();
                    tvStatus.setText("확인되지 않은 Lot이 없습니다.");
                } else if (header.equals("lot_list")) {
                    //테이블 레이아웃 초기화
                    tableLayout.removeViews(1, tableLayout.getChildCount()-1);

                    JSONArray jsonArray = jsonObject.getJSONArray("lot_list");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        TableRow tableRow = new TableRow(IncomingCheck.this); //tablerow 생성
                        tableRow.setLayoutParams(new TableRow.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));

                        int selColor = Color.WHITE;
                        if (i % 2 == 1) {
                            selColor = Color.parseColor("#00D8FF");
                        }

                        JSONObject item = jsonArray.getJSONObject(i);
                        TextView textView = new TextView(IncomingCheck.this);
                        textView.setText(String.valueOf(tableLayout.getChildCount()));
                        textView.setGravity(Gravity.CENTER);
                        textView.setBackgroundColor(selColor);
                        tableRow.addView(textView);
                        //TextView textView2 = new TextView(IncomingCheck.this);
                        //textView2.setText(item.getString("product"));
                        //textView2.setGravity(Gravity.CENTER);
                        //tableRow.addView(textView2);
                        TextView textView3 = new TextView(IncomingCheck.this);
                        textView3.setText(item.getString("lot_no"));
                        textView3.setGravity(Gravity.CENTER);
                        textView3.setBackgroundColor(selColor);
                        tableRow.addView(textView3);
                        TextView textView4 = new TextView(IncomingCheck.this);
                        textView4.setText(item.getString("chip_qty"));
                        textView4.setGravity(Gravity.CENTER);
                        textView4.setBackgroundColor(selColor);
                        tableRow.addView(textView4);
                        TextView textView5 = new TextView(IncomingCheck.this);
                        textView5.setText("");
                        textView5.setGravity(Gravity.CENTER);
                        textView5.setBackgroundColor(selColor);
                        tableRow.addView(textView5);
                        // textView4.setGravity(Gravity.CENTER);
                        //textView.setTextSize(36);
                        tableLayout.addView(tableRow);
                    }
                } else if (header.equals("lot_list!")) {
                    tableLayout.removeViews(1, tableLayout.getChildCount()-1);
                    Toast.makeText(IncomingCheck.this, "Lot List를 불러 올 수 없습니다.", Toast.LENGTH_SHORT).show();
                    tvStatus.setText("Lot List를 불러 올 수 없습니다.");
                } else if (header.equals("insertResult")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("insertResult");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Success")){
                        Toast.makeText(IncomingCheck.this, mJsonString, Toast.LENGTH_SHORT).show();
                        tvStatus.setText(mJsonString);
                        return;
                    } else {
                        tvSelIncomingDate.setText("");
                        adt_SlipNo.clear();
                        adt_SlipNo.add("선택");
                        spnSlipNo.setSelection(0);
                        adt_SlipNo.notifyDataSetChanged();
                        Toast.makeText(IncomingCheck.this, "정상 등록 되었습니다.", Toast.LENGTH_SHORT).show();
                        tvStatus.setText("정상 등록 되었습니다.");
                    }
                } else if (header.equals("CheckVer")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("CheckVer");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Ver:"+ BuildConfig.VERSION_NAME)){
                        appVerAlarm();
                    }
                } else {
                    Toast.makeText(IncomingCheck.this, mJsonString, Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                Log.d(activityTag, "showResult Error : ", e);
            }
        }
    }

    private DecodeStateCallback mStateCallback = new DecodeStateCallback(mHandler) {
        public void onChangedState(int state) {
            switch (state) {
                case ScanConst.STATE_ON:
                case ScanConst.STATE_TURNING_ON:
                    if (getEnableDialog().isShowing()) {
                        getEnableDialog().dismiss();
                    }
                    break;
                case ScanConst.STATE_OFF:
                case ScanConst.STATE_TURNING_OFF:
                    if (!getEnableDialog().isShowing()) {
                        getEnableDialog().show();
                    }
                    break;
            }
        }
    };

    private void initScanner() {
        if (mScanner != null) {
            mScanner.aRegisterDecodeStateCallback(mStateCallback);
            mBackupResultType = mScanner.aDecodeGetResultType();
            mScanner.aDecodeSetResultType(ScanConst.ResultType.DCD_RESULT_USERMSG);
        }
    }

    private Runnable mStartOnResume = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    initScanner();
                    if (mWaitDialog != null && mWaitDialog.isShowing()) {
                        mWaitDialog.dismiss();
                    }
                }
            });
        }
    };

    private AlertDialog getEnableDialog() {
        if (mDialog == null) {
            AlertDialog dialog = new AlertDialog.Builder(this).create();
            dialog.setTitle(R.string.app_name);
            dialog.setMessage("Your scanner is disabled. Do you want to enable it?");

            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(ScanConst.LAUNCH_SCAN_SETTING_ACITON);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            dialog.dismiss();
                        }
                    });
            dialog.setCancelable(false);
            mDialog = dialog;
        }
        return mDialog;
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
    protected void onResume() {
        super.onResume();
        verCheck();
        mWaitDialog = ProgressDialog.show(mContext, "", "Scanner Running...", true);
        mHandler.postDelayed(mStartOnResume, 1000);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ScanConst.INTENT_USERMSG);
        filter.addAction(ScanConst.INTENT_EVENT);
        mContext.registerReceiver(mScanResultReceiver, filter);
    }

    @Override
    protected void onPause() {
        if (mScanner != null) {
            mScanner.aDecodeSetResultType(mBackupResultType);
            mScanner.aUnregisterDecodeStateCallback(mStateCallback);
        }
        mContext.unregisterReceiver(mScanResultReceiver);

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mScanner != null) {
            mScanner.aDecodeSetResultType(mBackupResultType);
        }
        mScanner = null;

        super.onDestroy();
    }
}