package kr.or.yujin.repairsystem.Activity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.app.AlertDialog;
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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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
import java.util.List;

import device.common.DecodeResult;
import device.common.DecodeStateCallback;
import device.common.ScanConst;
import device.sdk.ScanManager;
import kr.or.yujin.repairsystem.BuildConfig;
import kr.or.yujin.repairsystem.Class.MyFunction;
import kr.or.yujin.repairsystem.R;

public class SMT_Working_End extends AppCompatActivity {

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

    private String activityTag = "자재교환";

    private TextView etWorker, etLotNo, etWorkMode, etPartNo, etOrgPartNo,
            etFeederPartNo, etFeederLotNo, tvFeederNo, tvFeederPartNo,
            tvFeederLotNo, tvLossQty, tvUseQty, etFeederNo;
    private EditText etUseQty, etLossQty;
    private Button btnPartCheck, btnWorkEnd;
    private TableLayout tableLayout;

    private String beforeMaterial_Lot_no = null; //스캔한 피더에 걸려 있는 기존 자재의 로트넘버

    private ActivityResultLauncher<Intent> resultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smt_working_end);

        this.InitializeControl();

        server_ip = MainActivity.server_ip;
        server_port = MainActivity.server_port;

        etWorker.setText(getIntent().getStringExtra("worker"));
        etLotNo.setText(getIntent().getStringExtra("lot_no"));
        etWorkMode.setText(getIntent().getStringExtra("work_mode"));
        etOrgPartNo.setText(getIntent().getStringExtra("part_no"));

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

        getData load_part_history = new getData();
        load_part_history.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/load_part_history_working_end.php",
                "load_part_history",
                etLotNo.getText().toString());

        btnWorkEnd.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tableRow_AllOK_Check();
            }
        }));

        btnPartCheck.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (etFeederNo.length() == 0 || etFeederLotNo.length() == 0){
                    Toast.makeText(SMT_Working_End.this,
                            "Feeder No.가 확인되지 않았습니다.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                if (etUseQty.length() == 0){
                    Toast.makeText(SMT_Working_End.this,
                            "사용 수량이 입력되지 않았습니다.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                if (etLossQty.length() == 0){
                    Toast.makeText(SMT_Working_End.this,
                            "Loss 수량이 입력되지 않았습니다.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                //TableLayOut을 순차적으로 돌면서 동일한 Lot No.를 찾는다.
                Boolean findOK = false;
                TableLayout tableView = (TableLayout)findViewById(R.id.tlList);
                View myTempView=null;
                int noOfChild = tableView.getChildCount();
                for (int i = 0; i < noOfChild; i++) {
                    myTempView = tableView.getChildAt(i);

                    View vv = ((TableRow) myTempView).getChildAt(1);
                    if (vv instanceof TextView) {
                        if (((TextView) vv).getText().toString().equals(etFeederPartNo.getText().toString())) {
                            View vv2 = ((TableRow) myTempView).getChildAt(2);
                            if (((TextView) vv2).getText().toString().equals(etFeederLotNo.getText().toString())) {
                                View vv3 = ((TableRow) myTempView).getChildAt(0);
                                if (((TextView) vv3).getText().toString().equals("OK")) {
                                    Toast.makeText(SMT_Working_End.this,
                                            "이미 확인된 Lot No.입니다.\n확인하여 주십시오.",
                                            Toast.LENGTH_SHORT).show();
                                    findOK = true;
                                    long[] pattern = {500,1000,500,1000,500,1000};
                                    vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
                                    break;
                                } else {
                                    findOK = true;
                                }
                            }
                        }
                    }
                    if (findOK == true) {
                        long[] pattern = {100,500,100,500,100,500};
                        vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
                        View vv2 = ((TableRow) myTempView).getChildAt(3);
                        ((TextView) vv2).setText(etUseQty.getText().toString());
                        View vv3 = ((TableRow) myTempView).getChildAt(4);
                        ((TextView) vv3).setText(etLossQty.getText().toString());
                        View vv4 = ((TableRow) myTempView).getChildAt(0);
                        ((TextView) vv4).setText("OK");
                        Toast.makeText(SMT_Working_End.this,
                                "확인 되었습니다.",
                                Toast.LENGTH_SHORT).show();
                        etFeederNo.setText("");
                        etFeederPartNo.setText("");
                        etFeederLotNo.setText("");
                        etUseQty.setText("");
                        etLossQty.setText("");
                        break;
                    }
                }
                if (findOK == false) {
                    long[] pattern = {500,1000,500,1000,500,1000};
                    vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
                    Toast.makeText(SMT_Working_End.this,
                            "Lot No.가 확인되지 않았습니다.\n확인하여 주십시오.",
                            Toast.LENGTH_LONG).show();
                }
            }
        }));

        tvFeederNo.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etFeederNo.setText("");
                etFeederPartNo.setText("");
                etFeederLotNo.setText("");
                etUseQty.setText("");
                etLossQty.setText("");
            }
        }));

        tvFeederPartNo.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etFeederNo.setText("");
                etFeederPartNo.setText("");
                etFeederLotNo.setText("");
                etUseQty.setText("");
                etLossQty.setText("");
            }
        }));

        tvFeederLotNo.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etFeederNo.setText("");
                etFeederPartNo.setText("");
                etFeederLotNo.setText("");
                etUseQty.setText("");
                etLossQty.setText("");
            }
        }));

        tvUseQty.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etFeederNo.setText("");
                etFeederPartNo.setText("");
                etFeederLotNo.setText("");
                etUseQty.setText("");
                etLossQty.setText("");
            }
        }));

        tvLossQty.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etFeederNo.setText("");
                etFeederPartNo.setText("");
                etFeederLotNo.setText("");
                etUseQty.setText("");
                etLossQty.setText("");
            }
        }));
    }

    private void tableRow_AllOK_Check(){
        int checkCount = 0;
        TableLayout tableView = (TableLayout)findViewById(R.id.tlList);
        View myTempView=null;
        int noOfChild = tableView.getChildCount();
        for (int i = 0; i <noOfChild; i++) {
            myTempView = tableView.getChildAt(i);
            View vv = ((TableRow) myTempView).getChildAt(0);
            if (vv instanceof TextView) {
                if (((TextView) vv).getText().toString().equals("OK")) {
                    checkCount+=1;
                }
            }
        }
        Log.d(activityTag, "Table Row Count : " + (noOfChild-1) + ",   확인 Count : " + checkCount);
        if ((noOfChild-1)==checkCount){
            sqlWrite();
        } else {
            Toast.makeText(SMT_Working_End.this,
                    "사용, Loss 수량이 확인되지 않은 자재가 있습니다.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void sqlWrite(){
        String nowDateTime = MyFunction.getDateTime();
        String sendText = "";

        TableLayout tableView = (TableLayout)findViewById(R.id.tlList);
        View myTempView=null;
        int noOfChild = tableView.getChildCount();
        for (int i = 1; i < noOfChild; i++) {
            sendText += "update smt_material_used_history set";
            myTempView = tableView.getChildAt(i);
            //Log.d(activityTag, "행 i : " + i);
            for (int j = 4; j > 0; j--){
                //Log.d(activityTag, "행 j : " + j);
                View vv = ((TableRow) myTempView).getChildAt(j);
                if (vv instanceof TextView) {
                    switch (j){
                        case 1:
                            sendText += " and material_part_no = '" + ((TextView) vv).getText().toString() + "';";
                            break;
                        case 2:
                            sendText += " where lot_no = '" + etLotNo.getText().toString() + "'";
                            sendText += " and material_lot_no = '" + ((TextView) vv).getText().toString()+ "'";
                            break;
                        case 3:
                            sendText += ", material_used_qty = " + ((TextView) vv).getText().toString() + "";
                            sendText += ", end_date =  '" + nowDateTime + "'";
                            sendText += ", end_worker = '" + etWorker.getText().toString() + "'";
                            break;
                        case 4:
                            sendText += " material_loss_qty = " + ((TextView) vv).getText().toString() + "";
                            break;
                    }
                }
            }
        }
        Log.d(activityTag, "전송 sql : " + sendText);
        getData update_parts_history = new getData();
        update_parts_history.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/smt_parts_end_update.php",
                "update_parts_history",
                sendText);
    }

    private void InitializeControl()
    {
        etLotNo = (TextView) findViewById(R.id.etLotNo);
        etWorker = (TextView) findViewById(R.id.etWorker);
        etWorkMode = (TextView) findViewById(R.id.etWorkMode);
        etOrgPartNo = (TextView) findViewById(R.id.etOrgPartNo);

        tvFeederNo = (TextView) findViewById(R.id.tvFeederNo);
        tvFeederPartNo = (TextView) findViewById(R.id.tvFeederPartNo);
        tvFeederLotNo = (TextView) findViewById(R.id.tvFeederLotNo);
        tvLossQty = (TextView) findViewById(R.id.tvLossQty);
        tvUseQty = (TextView) findViewById(R.id.tvUseQty);

        etFeederNo = (TextView) findViewById(R.id.etFeederNo);
        etFeederPartNo = (TextView) findViewById(R.id.etFeederPartNo);
        etFeederLotNo = (TextView) findViewById(R.id.etFeederLotNo);
        etUseQty = (EditText) findViewById(R.id.etUseQty);
        etLossQty = (EditText) findViewById(R.id.etLossQty);

        btnPartCheck = (Button) findViewById(R.id.btnPartCheck);
        btnWorkEnd = (Button) findViewById(R.id.btnWorkEnd);

        tableLayout = (TableLayout) findViewById(R.id.tlList);

        vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void WorkingEnd_Question() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("SMT 작업 종료");
        //타이틀설정
        builder.setMessage("작업 종료 하시겠습니까?\n(기록한 내용은 되돌릴 수 없습니다.)");
        builder.setCancelable(false); // 뒤로가기로 취소
        //내용설정
        builder.setPositiveButton("작업종료",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(SMT_Working_End.this, SMT_Working_Information.class);
                        setResult(10003, intent);
                        finish();
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

    public class ScanResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mScanner != null) {
                try {
                    if (ScanConst.INTENT_USERMSG.equals(intent.getAction())) {
                        mScanner.aDecodeGetResult(mDecodeResult.recycle());
                        Log.d(activityTag, "Scan Result - " + mDecodeResult.toString());
                        if (mDecodeResult.toString().equals("READ_FAIL")) {
                            return;
                        }
                        String sResult = mDecodeResult.toString();
                        if (sResult.substring(0, 3).equals("FN-")){
                            if (etFeederNo.length() == 0){
                                getData load_feeder_status = new getData();
                                load_feeder_status.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/load_feeder_used.php",
                                        "load_feeder_status",
                                        etLotNo.getText().toString(),
                                        sResult);
                                //etFeederNo.setText(sResult);
                                //tvStatus.setText("Part No, Lot No.를 스캔 하여 주십시오.");
                            }
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
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

            if (ActivityName.equals("Activity.SMT_Manager"))
                progressDialog = ProgressDialog.show(SMT_Working_End.this,
                        "Connecting to server....\nPlease wait.", null, true, true);
        }

        @Override
        protected String doInBackground(String... params) {

            String serverURL = params[0];

            String secondString = (String) params[1];
            String postParameters = null;

            if (secondString.equals("load_feeder_status")) {
            postParameters = "lot_no=" + params[2];
            postParameters += "&feeder_no=" + params[3];
            } else if (secondString.equals("load_part_history")) {
                postParameters = "lot_no=" + params[2];
            } else if (secondString.equals("update_parts_history")) {
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
                Toast.makeText(SMT_Working_End.this, "서버에 접속 할 수 없습니다.\n상세 내용은 로그를 참조 하십시오.", Toast.LENGTH_SHORT).show();
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

                if (header.equals("feeder_status")){
                    JSONArray jsonArray = jsonObject.getJSONArray("feeder_status");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (item.getString("feeder_used").equals("1")){
                        etFeederNo.setText(item.getString("feeder_no"));
                        etFeederPartNo.setText(item.getString("material_part_no"));
                        etFeederLotNo.setText(item.getString("material_lot_no"));

                        etUseQty.requestFocus();
                        InputMethodManager manager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                        manager.showSoftInput(etUseQty, InputMethodManager.SHOW_IMPLICIT);
                        Toast.makeText(SMT_Working_End.this,
                                "사용수량, Loss 수량을 입력하여 주십시오.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(SMT_Working_End.this,
                                "Feeder No.를 확인 하지 못하였습니다.\n(사용등록 되지 않은 Feeder No.)",
                                Toast.LENGTH_SHORT).show();
                    }
                } else if (header.equals("partsEndUpdateResult")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("partsEndUpdateResult");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Success")){
                        Toast.makeText(SMT_Working_End.this, mJsonString, Toast.LENGTH_SHORT).show();
                        return;
                    } else {
                        WorkingEnd_Question();
                    }
                } else if (header.equals("used_part_list")){
                    //테이블 레이아웃 초기화
                    tableLayout.removeViews(1, tableLayout.getChildCount()-1);
                    JSONArray jsonArray = jsonObject.getJSONArray("used_part_list");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        TableRow tableRow = new TableRow(SMT_Working_End.this); //tablerow 생성
                        tableRow.setLayoutParams(new TableRow.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));

                        int selColor = Color.WHITE;
                        if (i % 2 == 1) {
                            selColor = Color.parseColor("#00D8FF");
                        }

                        JSONObject item = jsonArray.getJSONObject(i);
                        TextView textView = new TextView(SMT_Working_End.this);
                        textView.setText(String.valueOf(tableLayout.getChildCount()));
                        textView.setGravity(Gravity.CENTER);
                        textView.setBackgroundColor(selColor);
                        tableRow.addView(textView);
                        TextView textView2 = new TextView(SMT_Working_End.this);
                        textView2.setText(item.getString("material_part_no"));
                        textView2.setGravity(Gravity.CENTER);
                        textView2.setBackgroundColor(selColor);
                        tableRow.addView(textView2);
                        TextView textView3 = new TextView(SMT_Working_End.this);
                        textView3.setText(item.getString("material_lot_no"));
                        textView3.setGravity(Gravity.CENTER);
                        textView3.setBackgroundColor(selColor);
                        tableRow.addView(textView3);
                        TextView textView4 = new TextView(SMT_Working_End.this);
                        textView4.setText(MyFunction.decimalFormat(Integer.parseInt(item.getString("basic_stock_qty"))));
                        textView4.setGravity(Gravity.CENTER);
                        textView4.setBackgroundColor(selColor);
                        tableRow.addView(textView4);
                        TextView textView5 = new TextView(SMT_Working_End.this);
                        textView5.setText("0");
                        textView5.setGravity(Gravity.CENTER);
                        textView5.setBackgroundColor(selColor);
                        tableRow.addView(textView5);
                        tableLayout.addView(tableRow);
                    }
                } else if (header.equals("used_part_list!")){
                    //Toast.makeText(SMT_Working_Information.this,
                    //        "자재 사용이력을 확인 할 수 없습니다.",
                    //        Toast.LENGTH_SHORT).show();
                } else if (header.equals("CheckVer")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("CheckVer");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Ver:"+ BuildConfig.VERSION_NAME)){
                        appVerAlarm();
                    }
                } else {
                    Toast.makeText(SMT_Working_End.this, mJsonString, Toast.LENGTH_SHORT).show();
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