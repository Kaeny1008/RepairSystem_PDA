package kr.or.yujin.repairsystem.Activity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

public class SMT_Manager extends AppCompatActivity {

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

    private EditText etWorker;
    private TextView etProduct, etLotNo, etLotNo2, etQty, etYJNo, etRepairMode, etWorkStatus,
                     tvLotNo;
    private Button btnReset;

    private final String activityTag = "SMT 작업관리";
    private String workMessage = null;
    private String workMessage2 = null;
    private String nowCustomerCode = "";
    private String beforeCustomerCode = "";
    private String nowProduct = "";
    private String beforeProduct = "";
    private String nowWorkMode = "";
    private String beforeWorkMode = "";

    private ActivityResultLauncher<Intent> resultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smt_manager);

        this.InitializeControl();

        server_ip = MainActivity.server_ip;
        server_port = MainActivity.server_port;

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

            if (!mKeyLock) {
                mKeyLock = true;
                mScanner.aDecodeSymSetLocalPropEnable(symID, propIndex, 0);
            } else {
                mKeyLock = false;
                mScanner.aDecodeSymSetLocalPropEnable(symID, propIndex, 1);
            }
        }

        tvLotNo.setOnClickListener((v -> {
            etLotNo.setText("");
            etProduct.setText("");
            etLotNo2.setText("");
            etQty.setText("");
            etYJNo.setText("");
            etRepairMode.setText("");
            etWorkStatus.setText("");
        }));

        btnReset.setOnClickListener((v -> {
            etLotNo.setText("");
            etProduct.setText("");
            etLotNo2.setText("");
            etQty.setText("");
            etYJNo.setText("");
            etRepairMode.setText("");
            etWorkStatus.setText("");
            btnReset.setEnabled(false);
            nowCustomerCode = "";
            nowProduct = "";
            nowWorkMode = "";
        }));
    }

    // 넘어온 데이터를 받는 부분
    ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if(result.getResultCode() == RESULT_OK) {
                    Intent intent = result.getData();
                    if (intent.getStringExtra("work_end").equals("End")){
                        // 작업 종료내용을 서버에 기록한다.
                        String sendText = "update smt_working_history set end_date = '" + MyFunction.getDateTime() + "'";
                        sendText += ", end_worker = '" + etWorker.getText().toString() + "'";
                        sendText += " where lot_no = '" + etLotNo2.getText().toString() + "'";
                        sendText += " and work_section = '" + workMessage2 + "';";

                        sendText += "update basic_lot_information set lot_status = '" + workMessage2 + " Working Completed'";
                        sendText += " where lot_no = '" + etLotNo2.getText().toString() + "';";
                        Log.d(activityTag, "전송 SQL : " + sendText);
                        getData load_LotList = new getData();
                        load_LotList.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/end_update.php",
                                "endUpdate",
                                sendText);
                        beforeCustomerCode = nowCustomerCode;
                        beforeProduct = nowProduct;
                        beforeWorkMode = nowWorkMode;
                        nowCustomerCode = "";
                        nowProduct = "";
                        nowWorkMode = "";
                    }
                } else if(result.getResultCode() == 10004) {
                    // 프로그램 이름 체크에서 넘어온 데이터
                    // 프로그램 이름 체크가 끝나고 서버에 저장한 결과가 이상 없을때 받았다.
                    Intent intent = result.getData();
                    if (intent.getStringExtra("check_end").equals("End")){
                        run_Working_Information();
                    }
                }
            }
    );

    private void run_Working_Information() {
        Intent intent = new Intent(SMT_Manager.this, SMT_Working_Information.class);
        intent.putExtra("worker", etWorker.getText().toString());
        intent.putExtra("lot_no", etLotNo2.getText().toString());
        intent.putExtra("work_mode", workMessage2);
        mStartForResult.launch(intent);
    }

    private void InitializeControl()
    {
        vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);

        etLotNo = findViewById(R.id.etLotNo);
        etWorker = findViewById(R.id.etWorker);
        etProduct = findViewById(R.id.etProduct);
        etLotNo2 = findViewById(R.id.etLotNo2);
        etQty = findViewById(R.id.etQty);
        etYJNo = findViewById(R.id.etYJNo);
        etRepairMode = findViewById(R.id.etRepairMode);
        etWorkStatus = findViewById(R.id.etWorkStatus);
        tvLotNo = findViewById(R.id.tvLotNo);
        btnReset = findViewById(R.id.btnReset);
        btnReset.setEnabled(false);
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
                        if (etWorker.length() == 0){
                            // ex : AWTXJ00230/Y00134-I01/ZJ/1203-009546/0801-003672
                            String barcodeSplit[] = mDecodeResult.toString().split("/");
                            if (barcodeSplit.length == 5){
                                Toast.makeText(SMT_Manager.this,
                                        "작업자 Barcode를 스캔하여 주십시오.",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            etWorker.setText(mDecodeResult.toString());
                        } else if (etLotNo.length() == 0){
                            // ex : AWTXJ00230/Y00134-I01/ZJ/1203-009546/0801-003672
                            String barcodeSplit[] = mDecodeResult.toString().split("/");
                            if (barcodeSplit.length == 5){
                                String lot_no = barcodeSplit[0];

                                etLotNo.setText(lot_no);
                                etProduct.setText("");
                                etLotNo2.setText("");
                                etQty.setText("");
                                etYJNo.setText("");
                                etRepairMode.setText("");
                                etWorkStatus.setText("");

                                //Log.d(activityTag, "서버 php 위치 : " +
                                //        "http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/load_lot_info.php");
                                getData load_LotList = new getData();
                                load_LotList.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/load_lot_info.php",
                                        "load_lot_information",
                                        lot_no);
                            } else {
                                Toast.makeText(SMT_Manager.this,
                                        "Yujin Datamatrix Barcode를\n 스캔하여 주십시오.",
                                        Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(SMT_Manager.this,
                                    "초기화를 눌러 준 뒤 다시 시도 하십시오.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    private void Question_WorkStart(String nowWorkStatus) {
        if (nowWorkStatus.equals("Moving to WorkSite")){
            workMessage = "PMIC 작업대기 중입니다.\n작업시작 등록을 하시겠습니까?";
            workMessage2 = "PMIC";
        } else if (nowWorkStatus.equals("PMIC Working Completed") &&
                etRepairMode.getText().toString().equals("PMIC+RCD")){
            workMessage = "RCD 작업대기 중입니다.\n작업시작 등록을 하시겠습니까?";
            workMessage2 = "RCD";
        } else if (nowWorkStatus.equals("PMIC Working Completed") &&
                etRepairMode.getText().toString().equals("PMIC")){
            etWorkStatus.setText("SMT 작업 대기중인\nLot이 아닙니다.\n( PMIC 작업완료. )");
            btnReset.setEnabled((true));
            return;
        } else if (nowWorkStatus.equals("RCD Working Completed")){
            etWorkStatus.setText("SMT 작업 대기중인\nLot이 아닙니다.\n( RCD 작업완료. )");
            btnReset.setEnabled((true));
            return;
        } else if (nowWorkStatus.equals("SMT PMIC Working")){
            workMessage2 = "PMIC";
        } else if (nowWorkStatus.equals("SMT RCD Working")){
            workMessage2 = "RCD";
        }
        nowWorkMode = workMessage2;
        if (nowWorkStatus.equals("SMT PMIC Working") || nowWorkStatus.equals("SMT RCD Working")) {
            //작업중이므로 창을 바로 띄운다
            etWorkStatus.setText(workMessage2 + " 작업 중입니다.");

            //액티비티 호출하는 부분
            Intent intent = new Intent(SMT_Manager.this, SMT_Working_Information.class);
            intent.putExtra("worker", etWorker.getText().toString());
            intent.putExtra("lot_no", etLotNo2.getText().toString());
            intent.putExtra("work_mode", workMessage2);
            mStartForResult.launch(intent);
        } else {
            question_WorkStart();
        }
    }

    private void question_WorkStart() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("SMT 작업 시작");
        //타이틀설정
        builder.setMessage(workMessage);
        builder.setCancelable(false); // 뒤로가기로 취소
        //내용설정
        builder.setPositiveButton("작업시작",
                (dialog, which) -> {
                    etWorkStatus.setText(workMessage2 + " 작업 중입니다.");
                    String insertText = "";
                    // 작업시작 등록
                    insertText = "insert into smt_working_history(lot_no, yj_no, work_section, start_date, start_worker) values(";
                    insertText += "'" + etLotNo2.getText().toString() + "'";
                    insertText += ",'" + etYJNo.getText().toString() + "'";
                    insertText += ",'" + workMessage2 + "'";
                    insertText += ",'" + MyFunction.getDateTime() + "'";
                    insertText += ",'" + etWorker.getText().toString() + "');";

                    insertText += "update basic_lot_information set lot_status = 'SMT " + workMessage2 + " Working'";
                    insertText += " where lot_no = '" + etLotNo2.getText().toString() + "';";
                    Log.d(activityTag, "전송 SQL : " + insertText);
                    // 서버로 전송한다.
                    getData taskFeederInit = new getData();
                    taskFeederInit.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/start_update.php"
                            , "startUpdate"
                            , insertText);
                });

        builder.setNegativeButton("취소",
                (dialog, which) -> {
                    btnReset.setEnabled(true);
                    dialog.dismiss();
                });

        builder.setOnDismissListener(dialog -> {

        });
        builder.show();
    }

    private void statusWrite(String lot_status){
        if (lot_status.equals("Ready") ||
                lot_status.equals("Completed") ||
                lot_status.equals("Kitting Completed") ||
                lot_status.equals("Baking End") ||
                lot_status.equals("Incoming Inspection Completed") ||
                lot_status.equals("Baking Start")){
            etWorkStatus.setText("SMT 작업 대기중인\nLot이 아닙니다.");
            btnReset.setEnabled(true);
        } else {
            etWorkStatus.setText("SMT 작업 대기 중");
            Question_WorkStart(lot_status);
        }
    }

    private void workModeWrite(String lot_option){
        if (lot_option.matches("(.*)O")) {
            etRepairMode.setText("PMIC");
        } else if (lot_option.matches("(.*)Q")) {
            etRepairMode.setText("PMIC+RCD");
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
                progressDialog = ProgressDialog.show(SMT_Manager.this,
                        "Connecting to server....\nPlease wait.", null, true, true);
        }

        @Override
        protected String doInBackground(String... params) {

            String serverURL = params[0];

            String secondString = (String) params[1];
            String postParameters = null;

            if (secondString.equals("load_lot_information")) {
                postParameters = "lot_no=" + params[2];
            } else if (secondString.equals("startUpdate")) {
                postParameters = "sql=" + params[2];
            } else if (secondString.equals("endUpdate")){
                postParameters = "sql=" + params[2];
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
                Toast.makeText(SMT_Manager.this, "서버에 접속 할 수 없습니다.\n상세 내용은 로그를 참조 하십시오.", Toast.LENGTH_SHORT).show();
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
                if (header.equals("lot_info")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("lot_info");
                    JSONObject item = jsonArray.getJSONObject(0);
                    etProduct.setText(item.getString("product"));
                    etLotNo2.setText(item.getString("lot_no"));
                    etQty.setText(item.getString("chip_qty"));
                    etYJNo.setText(item.getString("yj_no"));
                    workModeWrite(item.getString("lot_option"));
                    statusWrite(item.getString("lot_status"));
                    nowCustomerCode = item.getString("product").toString().substring(16, 18);
                    nowProduct = item.getString("product").toString().substring(0, 12);
                    //Log.d(activityTag,
                    //        "현재 Customer Code = " + item.getString("product").toString().substring(16, 18));
                } else if (header.equals("lot_info!")){
                    Toast.makeText(SMT_Manager.this,
                            "Lot 정보를 확인 할 수 없습니다.",
                            Toast.LENGTH_SHORT).show();
                } else if (header.equals("StatusUpdateResult")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("StatusUpdateResult");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Success")){
                        Toast.makeText(SMT_Manager.this, mJsonString, Toast.LENGTH_SHORT).show();
                        return;
                    } else {
                        //액티비티 호출하는 부분
                        SharedPreferences setting = getSharedPreferences("setting", Activity.MODE_PRIVATE);
                        if (setting.getString("smt_PGM_Name_Check","Yes").equals("Yes")){
                            Log.d(activityTag, "현재 Bucket : " + nowCustomerCode);
                            Log.d(activityTag, "기존 Bucket : " + beforeCustomerCode);
                            Log.d(activityTag, "현재 Product : " + nowProduct);
                            Log.d(activityTag, "기존 Product : " + beforeProduct);
                            Log.d(activityTag, "현재 작업모드는 : " + nowWorkMode);
                            Log.d(activityTag, "기존 작업모드는 : " + beforeWorkMode);
                            boolean changeProgram = false;
                            if (!beforeCustomerCode.equals(nowCustomerCode)){
                                Log.d(activityTag, "Bucket 변경되었으니 프로그램명 확인창을 띄운다.");
                                changeProgram = true;
                            }
                            if (!beforeProduct.equals(nowProduct)){
                                Log.d(activityTag, "Product 변경되었으니 프로그램명 확인창을 띄운다.");
                                changeProgram = true;
                            }
                            if (!beforeWorkMode.equals(nowWorkMode)){
                                Log.d(activityTag, "작업모드 변경되었으니 프로그램명 확인창을 띄운다.");
                                changeProgram = true;
                            }
                            if (changeProgram){
                                Intent intent = new Intent(SMT_Manager.this, SMT_Program_Name_Check.class);
                                intent.putExtra("lot_no", etLotNo2.getText().toString());
                                intent.putExtra("work_mode", workMessage2);
                                mStartForResult.launch(intent);
                            } else {
                                run_Working_Information();
                            }
                        } else {
                            run_Working_Information();
                        }
                    }
                } else if (header.equals("EndUpdateResult")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("EndUpdateResult");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Success")){
                        Toast.makeText(SMT_Manager.this, mJsonString, Toast.LENGTH_SHORT).show();
                        return;
                    } else {
                        etWorkStatus.setText(workMessage2 + "\n작업 종료 되었습니다." +
                                "\n다음 공정으로 인계하여 주십시오.");
                        btnReset.setEnabled(true);
                    }
                } else if (header.equals("CheckVer")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("CheckVer");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Ver:"+ BuildConfig.VERSION_NAME)){
                        appVerAlarm();
                    }
                } else {
                    Toast.makeText(SMT_Manager.this, mJsonString, Toast.LENGTH_SHORT).show();
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