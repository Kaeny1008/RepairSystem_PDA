package kr.or.yujin.repairsystem.Activity;

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
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
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
import java.util.ArrayList;
import java.util.List;

import device.common.DecodeResult;
import device.common.DecodeStateCallback;
import device.common.ScanConst;
import device.sdk.ScanManager;
import kr.or.yujin.repairsystem.BuildConfig;
import kr.or.yujin.repairsystem.Class.MyFunction;
import kr.or.yujin.repairsystem.R;

public class Module_Ship_Temp extends AppCompatActivity {

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

    private String activityTag = "출고 Lot 확인";

    private TextView tvStatus, tvTotalLot, tvTotalModule;
    private TableLayout tableLayout;
    private Button btnSave;
    private int totalLot, totalModule;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_module_ship_temp);

        server_ip = MainActivity.server_ip;
        server_port = MainActivity.server_port;

        this.InitializeControl();

        //임시 저장된 리스트를 불러 온다.
        getData load_LotList = new getData();
        load_LotList.execute("http://" + server_ip + ":" + server_port + "/Repair_System/Module_Ship_Temp/load_lot_list.php",
                "load_LotList");

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

        btnSave.setOnClickListener((v -> {
            this.TempSave();
        }));
    }

    private void InitializeControl()
    {
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvTotalLot = (TextView) findViewById(R.id.tvTotalLot);
        tvTotalModule = (TextView) findViewById(R.id.tvTotalModule);
        vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        tableLayout = (TableLayout) findViewById(R.id.tlList);

        btnSave = (Button) findViewById(R.id.btnSave);
    }

    private void TempSave(){
        if (tableLayout.getChildCount() == 1){
            String showMessage = "입력된 Lot이 없습니다.";
            Toast.makeText(Module_Ship_Temp.this,
                    showMessage,
                    Toast.LENGTH_SHORT).show();
            tvStatus.setText(showMessage);
            return;
        }

        save_Question();
    }

    private void save_Question() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("출고리스트 저장");
        //타이틀설정
        builder.setMessage("저장 하시겠습니까?");
        builder.setCancelable(false); // 뒤로가기로 취소
        //내용설정
        builder.setPositiveButton("저장",
                (dialog, which) -> {
                    String sendText = "delete from module_ship_information_pda_temp;";

                    TableLayout tableView = (TableLayout)findViewById(R.id.tlList);
                    View myTempView = null;
                    int noOfChild = tableView.getChildCount();
                    for (int i = 1; i < noOfChild; i++) {
                        myTempView = tableView.getChildAt(i);
                        View vv = ((TableRow) myTempView).getChildAt(2);
                        if (vv instanceof TextView) {
                            sendText += "insert into module_ship_information_pda_temp(lot_no) values(";
                            sendText += "'" + ((TextView) vv).getText().toString() + "'";
                            sendText += ");";
                        }
                    }
                    //Log.d(activityTag, "Module Ship Temp 전송 sql : " + sendText);
                    getData tempList_Save = new getData();
                    tempList_Save.execute("http://" + server_ip + ":" + server_port + "/Repair_System/Module_Ship_Temp/save_templist.php",
                            "tempList_Save",
                            sendText);
                });

        builder.setNegativeButton("취소",
                (dialog, which) -> dialog.dismiss());

        builder.setOnDismissListener(dialog -> {

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
                        Log.d(activityTag, "Module Ship Temp Scan Result - " + mDecodeResult.toString());
                        getData taskSave = new getData();
                        taskSave.execute("http://" + server_ip + ":" + server_port + "/Repair_System/Module_Ship_Temp/load_lot_information.php"
                                , "load_lot_information"
                                , mDecodeResult.toString());
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    private void lotExistCheck(String yj_no, String lot_no, Integer chip_qty){
        //TableLayOut을 순차적으로 돌면서 동일한 Lot No.가 존재하는지 찾는다.
        boolean findOK = false;
        TableLayout tableView = (TableLayout)findViewById(R.id.tlList);
        View myTempView=null;
        int noOfChild = tableView.getChildCount();
        for (int i = 0; i < noOfChild; i++) {
            myTempView = tableView.getChildAt(i);

            View vv = ((TableRow) myTempView).getChildAt(2);
            if (vv instanceof TextView) {
                if (((TextView) vv).getText().toString().equals(lot_no)) {
                    //Log.d(activityTag, "Module_Ship_Temp 찾았다 : " + i);
                    long[] pattern = {500,1000,500,1000};
                    vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
                    Toast.makeText(Module_Ship_Temp.this,
                            "이미 등록된 Lot No.입니다.\n확인하여 주십시오.",
                            Toast.LENGTH_SHORT).show();
                    tvStatus.setText("이미된 등록 Lot No.입니다.\n확인하여 주십시오.");
                    findOK = true;
                    break;
                }
            }
        }
        if (!findOK) lotTableWrite(yj_no, lot_no, chip_qty);
    }

    private void lotTableWrite(String yj_no, String lot_no, Integer chip_qty){
        TableRow tableRow = new TableRow(Module_Ship_Temp.this); //tablerow 생성
        tableRow.setLayoutParams(new TableRow.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        int selColor = Color.WHITE;
        if ((tableLayout.getChildCount()-1) % 2 == 1) {
            selColor = Color.parseColor("#00D8FF");
        }

        TextView textView = new TextView(Module_Ship_Temp.this);
        textView.setText(String.valueOf(tableLayout.getChildCount()));
        textView.setGravity(Gravity.CENTER);
        textView.setBackgroundColor(selColor);
        textView.setHeight(100);
        tableRow.addView(textView);
        TextView textView3 = new TextView(Module_Ship_Temp.this);
        textView3.setText(yj_no);
        textView3.setGravity(Gravity.CENTER);
        textView3.setBackgroundColor(selColor);
        textView3.setHeight(100);
        tableRow.addView(textView3);
        TextView textView4 = new TextView(Module_Ship_Temp.this);
        textView4.setText(lot_no);
        textView4.setGravity(Gravity.CENTER);
        textView4.setBackgroundColor(selColor);
        textView4.setHeight(100);
        tableRow.addView(textView4);
        TextView textView5 = new TextView(Module_Ship_Temp.this);
        textView5.setText(MyFunction.decimalFormat(chip_qty));
        textView5.setGravity(Gravity.CENTER);
        textView5.setBackgroundColor(selColor);
        textView5.setHeight(100);
        tableRow.addView(textView5);
        tableLayout.addView(tableRow);

        totalLot += 1;
        totalModule += chip_qty;

        long[] pattern = {100,100,100,100};
        vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동

        Toast.makeText(Module_Ship_Temp.this,
                "등록되었습니다. 출고하려는 다른 Lot의\n바코드를 스캔하여 주십시오.",
                Toast.LENGTH_SHORT).show();
        tvStatus.setText("등록되었습니다. 출고하려는 다른 Lot의\n바코드를 스캔하여 주십시오.");
        tvTotalLot.setText("Total Lot : " + totalLot);
        tvTotalModule.setText("Total Module : " + totalModule);
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
                progressDialog = ProgressDialog.show(Module_Ship_Temp.this,
                        "Connecting to server....\nPlease wait.", null, true, true);
        }

        @Override
        protected String doInBackground(String... params) {

            String serverURL = params[0];

            String secondString = (String) params[1];
            String postParameters = null;

            if (secondString.equals("load_lot_information")) {
                postParameters = "lot_no=" + params[2];
            } else if(secondString.equals("tempList_Save")) {
                postParameters = "sql=" + params[2];
            } else if(secondString.equals("load_LotList")) {
                postParameters = "";
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
                Toast.makeText(Module_Ship_Temp.this
                        , "서버에 접속 할 수 없습니다.\n상세 내용은 로그를 참조 하십시오."
                        , Toast.LENGTH_SHORT).show();
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
                if (header.equals("lot_information")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("lot_information");
                    JSONObject item = jsonArray.getJSONObject(0);
                    String lot_Option = item.getString("lot_option");
                    String lot_Status = item.getString("lot_status");
                    String yj_no = item.getString("yj_no");
                    String lot_no = item.getString("lot_no");
                    String pfq_doe = item.getString("pfq_doe");
                    int chip_qty = Integer.parseInt(item.getString("chip_qty"));

                    if (pfq_doe.equals("False")){
                        if (lot_Option.matches(".*O")){
                            if (!lot_Status.equals("PMIC Working Completed")){
                                Toast.makeText(Module_Ship_Temp.this,
                                        "Lot 상태를 확인하여 주십시오.\nPMIC SMT완료 상태가 아닙니다.",
                                        Toast.LENGTH_SHORT).show();
                                tvStatus.setText("Lot 상태를 확인하여 주십시오.\nPMIC SMT완료 상태가 아닙니다.");
                                long[] pattern = {500,1000,500,1000};
                                vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
                                return;
                            }
                        } else if (lot_Option.matches(".*Q")) {
                            if (!lot_Status.equals("RCD Working Completed")){
                                Toast.makeText(Module_Ship_Temp.this,
                                        "Lot 상태를 확인하여 주십시오.\nRCD SMT완료 상태가 아닙니다.",
                                        Toast.LENGTH_SHORT).show();
                                tvStatus.setText("Lot 상태를 확인하여 주십시오.\nRCD SMT완료 상태가 아닙니다.");
                                long[] pattern = {500,1000,500,1000};
                                vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
                                return;
                            }
                        }
                    }

                    lotExistCheck(yj_no, lot_no, chip_qty);
                } else if (header.equals("lot_information!")) {
                    Toast.makeText(Module_Ship_Temp.this,
                            "Lot 정보를 확인 할 수 없습니다.",
                            Toast.LENGTH_SHORT).show();
                    tvStatus.setText("Lot 정보를 확인 할 수 없습니다.");
                } else if (header.equals("TempListSave")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("TempListSave");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Success")) {
                        Toast.makeText(Module_Ship_Temp.this, mJsonString, Toast.LENGTH_SHORT).show();
                        tvStatus.setText(mJsonString);
                        return;
                    } else {
                        Toast.makeText(Module_Ship_Temp.this,
                                "정상 등록 되었습니다.",
                                Toast.LENGTH_SHORT).show();
                        tvStatus.setText("정상 등록 되었습니다.");
                    }
                } else if (header.equals("lot_list")){
                    totalLot = 0;
                    totalModule = 0;
                    //테이블 레이아웃 초기화
                    tableLayout.removeViews(1, tableLayout.getChildCount()-1);
                    JSONArray jsonArray = jsonObject.getJSONArray("lot_list");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        TableRow tableRow = new TableRow(Module_Ship_Temp.this); //tablerow 생성
                        tableRow.setLayoutParams(new TableRow.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));

                        int selColor = Color.WHITE;
                        if (i % 2 == 1) {
                            selColor = Color.parseColor("#00D8FF");
                        }

                        JSONObject item = jsonArray.getJSONObject(i);
                        TextView textView = new TextView(Module_Ship_Temp.this);
                        textView.setText(String.valueOf(tableLayout.getChildCount()));
                        textView.setGravity(Gravity.CENTER);
                        textView.setBackgroundColor(selColor);
                        textView.setHeight(100);
                        tableRow.addView(textView);
                        TextView textView2 = new TextView(Module_Ship_Temp.this);
                        textView2.setText(item.getString("yj_no"));
                        textView2.setGravity(Gravity.CENTER);
                        textView2.setBackgroundColor(selColor);
                        textView2.setHeight(100);
                        tableRow.addView(textView2);
                        TextView textView3 = new TextView(Module_Ship_Temp.this);
                        textView3.setText(item.getString("lot_no"));
                        textView3.setGravity(Gravity.CENTER);
                        textView3.setBackgroundColor(selColor);
                        textView3.setHeight(100);
                        tableRow.addView(textView3);
                        TextView textView4 = new TextView(Module_Ship_Temp.this);
                        textView4.setText(MyFunction.decimalFormat(Integer.parseInt(item.getString("chip_qty"))));
                        textView4.setGravity(Gravity.CENTER);
                        textView4.setBackgroundColor(selColor);
                        textView4.setHeight(100);
                        tableRow.addView(textView4);
                        tableLayout.addView(tableRow);
                        totalLot += 1;
                        totalModule += Integer.parseInt(item.getString("chip_qty"));
                    }
                    tvStatus.setText("등록되었습니다. 출고하려는 다른 Lot의\n바코드를 스캔하여 주십시오.");
                    tvTotalLot.setText("Total Lot : " + totalLot);
                    tvTotalModule.setText("Total Module : " + MyFunction.decimalFormat(totalModule));
                } else if (header.equals("lot_list!")){
                } else if (header.equals("CheckVer")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("CheckVer");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Ver:"+ BuildConfig.VERSION_NAME)){
                        appVerAlarm();
                    }
                } else {
                    Toast.makeText(Module_Ship_Temp.this, mJsonString, Toast.LENGTH_SHORT).show();
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
                    (dialog12, which) -> finish());
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                    (dialog1, which) -> {
                        Intent intent = new Intent(ScanConst.LAUNCH_SCAN_SETTING_ACITON);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        dialog1.dismiss();
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