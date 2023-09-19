package kr.or.yujin.repairsystem.Activity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
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

public class SMT_Parts_Change extends AppCompatActivity {

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

    private TextView etWorker, etLotNo, etWorkMode, etORGPartNo,
            etChangePartNo, etChangeLotNo,etChangeQty, tvStatus,
            tvChangePartNo, tvChangeLotNo, tvChangeQty, etFeederNo;
    private EditText etUseQty, etLossQty;
    private Button btnMaterialChange;
    private Integer beforeUseQty;

    private String beforeMaterial_Lot_no = null; //스캔한 피더에 걸려 있는 기존 자재의 로트넘버

    private ActivityResultLauncher<Intent> resultLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smt_parts_change);

        this.InitializeControl();

        server_ip = MainActivity.server_ip;
        server_port = MainActivity.server_port;

        etWorker.setText(getIntent().getStringExtra("worker"));
        etLotNo.setText(getIntent().getStringExtra("lot_no"));
        etWorkMode.setText(getIntent().getStringExtra("work_mode"));
        etORGPartNo.setText(getIntent().getStringExtra("part_no"));

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

        etUseQty.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 입력난에 변화가 있을 시 조치
            }

            @Override
            public void afterTextChanged(Editable arg0) {
                // 입력이 끝났을 때 조치
                if (etUseQty.getText().length() != 0) {
                    tvStatus.setText("Loss 수량을 입력하여 주십시오.");


                } else {
                    tvStatus.setText("사용 수량을 입력하여 주십시오.");
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 입력하기 전에 조치
            }
        });

        etLossQty.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 입력난에 변화가 있을 시 조치
            }

            @Override
            public void afterTextChanged(Editable arg0) {
                // 입력이 끝났을 때 조치
                if (etLossQty.getText().length() != 0) {
                    tvStatus.setText("Part No, Lot No.를 스캔 하여 주십시오.");

                } else {
                    tvStatus.setText("Loss 수량을 입력하여 주십시오.");

                }
                Integer replaceLossQty = 0 ;
                if (etLossQty.length() !=0){
                    replaceLossQty = Integer.parseInt(etLossQty.getText().toString());
                }
                Integer calQty = beforeUseQty - replaceLossQty;
                etUseQty.setText(String.valueOf(calQty));
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 입력하기 전에 조치
            }
        });

        btnMaterialChange.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                materialUsedReg();
            }
        }));
    }

    private void materialUsedReg(){
        // 모두 입력되었는지 검사
        if (etFeederNo.length() == 0){
            tvStatus.setText("Feeder No.를 스캔하여 주십시오.");
            return;
        }
        if (etUseQty.length() == 0){
            tvStatus.setText("사용수량을 입력하여 주십시오.");
            return;
        }
        if (etLossQty.length() == 0){
            tvStatus.setText("Loss 수량을 입려하여 주십시오.");
            return;
        }
        if (etChangePartNo.length() == 0){
            tvStatus.setText("Part No.를 스캔 하여 주십시오.");
            return;
        }
        if (etChangeLotNo.length() == 0){
            tvStatus.setText("Lot No.를 스캔 하여 주십시오.");
            return;
        }
        if (etChangeQty.length() == 0){
            tvStatus.setText("자재 정보를 불러오지 못했습니다.");
            return;
        }

        // 오삽검사
        if (etORGPartNo.getText().equals(etChangePartNo.getText())){
            // 신규자재 등록부분
            String nowDateTime = MyFunction.getDateTime();
            String sendText = "insert into smt_material_used_history(feeder_no, lot_no, work_section, material_part_no";
            sendText += ", material_lot_no, basic_stock_qty, material_used_qty, material_loss_qty";
            sendText += ", start_date, start_worker, check_result) values(";
            sendText += "'" + etFeederNo.getText() + "'";
            sendText += ",'" + etLotNo.getText() + "'";
            sendText += ",'" + etWorkMode.getText() + "'";
            sendText += ",'" + etChangePartNo.getText() + "'";
            sendText += ",'" + etChangeLotNo.getText() + "'";
            sendText += ",'" + Integer.parseInt(etChangeQty.getText().toString().replace(",", "")) + "'";
            sendText += ",0";
            sendText += ",0";
            sendText += ",'" + nowDateTime + "'";
            sendText += ",'" + etWorker.getText() + "'";
            sendText += ",'OK'";
            sendText += ");";

            sendText += "update smt_material_used_history set";
            sendText += " material_used_qty = " + Integer.parseInt(etUseQty.getText().toString().replace(",", "")) + "";
            sendText += ", material_loss_qty = " + Integer.parseInt(etLossQty.getText().toString().replace(",", "")) + "";
            sendText += ", end_date =  '" + nowDateTime + "'";
            sendText += ", end_worker = '" + etWorker.getText().toString() + "'";
            sendText += " where lot_no = '" + etLotNo.getText().toString() + "'";
            sendText += " and feeder_no = '" + etFeederNo.getText().toString() + "'";
            sendText += " and material_lot_no = '" + beforeMaterial_Lot_no + "';";

            getData load_LotList = new getData();
            load_LotList.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/smt_material_used_register.php",
                    "used_register",
                    sendText);
            //Log.d(activityTag, "오삽검사 결과 이상 무, 서버에 내용 등록");
        } else {
            Log.d(activityTag, "오삽검사 결과 이상 관리자 확인 액티비티 활성화");
            long[] pattern = {500,1000,500,1000,500,1000};
            vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
            Intent intent = new Intent(SMT_Parts_Change.this, SMT_MisCheck.class);
            intent.putExtra("callActivity", "SMT_Parts_Change");
            intent.putExtra("worker", etWorker.getText().toString());
            mStartForResult.launch(intent);
        }
    }

    // 넘어온 데이터를 받는 부분
    ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if(result.getResultCode() == RESULT_OK) {
                    Intent intent = result.getData();
                    String misReason = intent.getStringExtra("misReason");
                    String checkID = intent.getStringExtra("checkID");

                    String sendText = "insert into smt_material_used_history(feeder_no, lot_no, work_section, material_part_no";
                    sendText += ", material_lot_no, basic_stock_qty, material_used_qty, material_loss_qty";
                    sendText += ", start_date, start_worker, check_result, fault_reason, fault_checker) values(";
                    sendText += "'" + etFeederNo.getText() + "'";
                    sendText += ",'" + etLotNo.getText() + "'";
                    sendText += ",'" + etWorkMode.getText() + "'";
                    sendText += ",'" + etChangePartNo.getText() + "'";
                    sendText += ",'" + etChangeLotNo.getText() + "'";
                    sendText += ",0";
                    sendText += ",0";
                    sendText += ",0";
                    sendText += ",'" + MyFunction.getDateTime() + "'";
                    sendText += ",'" + etWorker.getText() + "'";
                    sendText += ",'NG'";
                    sendText += ",'" + misReason + "'";
                    sendText += ",'" + checkID + "'";
                    sendText += ");";

                    getData load_LotList = new getData();
                    load_LotList.execute("http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/smt_material_fault_register.php",
                            "fault_register",
                            sendText);
                    //Log.d(activityTag, "넘어온 오삽 이유 : " + misReason);
                }
            }
    );

    private void InitializeControl()
    {
        etLotNo = (TextView) findViewById(R.id.etLotNo);
        etWorker = (TextView) findViewById(R.id.etWorker);
        etWorkMode = (TextView) findViewById(R.id.etWorkMode);
        etORGPartNo = (TextView) findViewById(R.id.etORGPartNo);
        etChangePartNo = (TextView) findViewById(R.id.etChangePartNo);
        etChangeLotNo = (TextView) findViewById(R.id.etChangeLotNo);
        etChangeQty = (TextView) findViewById(R.id.etChangeQty);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvChangePartNo = (TextView) findViewById(R.id.tvChangePartNo);
        tvChangeLotNo = (TextView) findViewById(R.id.tvChangeLotNo);
        tvChangeQty = (TextView) findViewById(R.id.tvChangeQty);
        etFeederNo = (TextView) findViewById(R.id.etFeederNo);

        etUseQty = (EditText) findViewById(R.id.etUseQty);
        etUseQty.setEnabled(false);
        etLossQty = (EditText) findViewById(R.id.etLossQty);

        btnMaterialChange = (Button) findViewById(R.id.btnMaterialChange);

        vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
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
                        } else if (sResult.charAt(sResult.length() - 1) == '+'){
                            String partNo = sResult.split(" ")[1].replace("+", "");
                            //Log.d(activityTag, "Part No. 스캔하였다. : " + partNo);
                            if (etChangePartNo.length() == 0) {
                                etChangePartNo.setText(partNo);
                                tvStatus.setText("Lot No.를 스캔 하여 주십시오.");
                            } else {
                                tvStatus.setText("Part No.는 입력되어 있습니다.\n재스캔 할 경우 내용을 지우고\n다시 시도 하십시오.");
                            }
                        } else if (sResult.charAt(0) == '+'){
                            String lotNo = sResult.substring(9).replace(" ", "");
                            //Log.d(activityTag, "Lot No. 스캔하였다. : " + lotNo);
                            if (etChangeLotNo.length() == 0){
                                etChangeLotNo.setText(lotNo);
                                tvStatus.setText("Part No.를 스캔 하여 주십시오.");
                            } else {
                                tvStatus.setText("Lot No.는 입력되어 있습니다.\n재스캔 할 경우 내용을 지우고\n다시 시도 하십시오.");
                            }
                        } else {
                            tvStatus.setText("확인 할 수 없는 바코드 입니다.");
                        }

                        //모두 입력 되었을때
                        if (etChangePartNo.length() != 0 &&
                                etChangeLotNo.length() != 0){
                            getData task_VerLoad = new getData();
                            task_VerLoad.execute( "http://" + server_ip + ":" + server_port + "/Repair_System/SMT_Manager/smt_material_used_history.php"
                                    , "part_used_info"
                                    , etChangePartNo.getText().toString()
                                    , etChangeLotNo.getText().toString());
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
                progressDialog = ProgressDialog.show(SMT_Parts_Change.this,
                        "Connecting to server....\nPlease wait.", null, true, true);
        }

        @Override
        protected String doInBackground(String... params) {

            String serverURL = params[0];

            String secondString = (String) params[1];
            String postParameters = null;

            if (secondString.equals("part_used_info")) {
                postParameters = "part_no=" + params[2];
                postParameters += "&lot_no=" + params[3];
            } else if (secondString.equals("load_feeder_status")) {
                postParameters = "lot_no=" + params[2];
                postParameters += "&feeder_no=" + params[3];
            } else if (secondString.equals("used_register")) {
                postParameters = "sql=" + params[2];
            } else if (secondString.equals("fault_register")){
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
                Toast.makeText(SMT_Parts_Change.this, "서버에 접속 할 수 없습니다.\n상세 내용은 로그를 참조 하십시오.", Toast.LENGTH_SHORT).show();
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
                if (header.equals("part_used_info")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("part_used_info");
                    JSONObject item = jsonArray.getJSONObject(0);
                    int orgQty = Integer.parseInt(item.getString("material_qty"));
                    int usedQty = Integer.parseInt(item.getString("material_used_qty"));
                    int lossQty = Integer.parseInt(item.getString("material_loss_qty"));
                    if ((orgQty-usedQty-lossQty) == 0 ) {
                        long[] pattern = {100,100,100,100,100,100};
                        vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
                        etChangeQty.setText("");
                        tvStatus.setText("재고가 0인 자재는 사용할 수 없습니다.");
                    } else {
                        etChangeQty.setText(MyFunction.decimalFormat(orgQty-usedQty-lossQty));
                        if (etUseQty.length() == 0){
                            tvStatus.setText("사용 수량을 입력하여 주십시오.");
                            return;
                        }
                        if (etLossQty.length() == 0){
                            tvStatus.setText("Loss 수량을 입력하여 주십시오.");
                            return;
                        }
                        materialUsedReg();
                    }
                } else if (header.equals("part_used_info!")){
                    //etChangeQty.setText("");
                    etChangePartNo.setText("");
                    etChangeLotNo.setText("");
                    etChangeQty.setText("");
                    tvStatus.setText("자재 정보를 불러 올 수 없습니다.\n확인하여 주십시오.\n(이미 사용 등록된 자재일 수도 있습니다)");
                } else if (header.equals("feeder_status")){
                    JSONArray jsonArray = jsonObject.getJSONArray("feeder_status");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (item.getString("feeder_used").equals("1")){
                        etFeederNo.setText(item.getString("feeder_no"));
                        tvStatus.setText("사용 수량을 입력하여 주십시오.");
                        beforeMaterial_Lot_no = item.getString("material_lot_no");
                        etUseQty.setText(item.getString("basic_stock_qty"));
                        beforeUseQty = Integer.parseInt(item.getString("basic_stock_qty"));
                    } else {
                        tvStatus.setText("Feeder No.를 확인 하지 못하였습니다.\n(사용등록 되지 않은 Feeder No.)");
                    }
                } else if (header.equals("Register_Result")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("Register_Result");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Success")){
                        Toast.makeText(SMT_Parts_Change.this, mJsonString, Toast.LENGTH_SHORT).show();
                        return;
                    } else {
                        long[] pattern = {100,500,100,500,100,500};
                        vibrator.vibrate(pattern, -1); // miliSecond, 지정한 시간동안 진동
                        Intent intent = new Intent(SMT_Parts_Change.this, SMT_Working_Information.class);
                        intent.putExtra("material_use_reg","OK");
                        setResult(10002, intent);
                        finish();
                    }
                } else if (header.equals("Fault_Register")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("Fault_Register");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Success")){
                        Toast.makeText(SMT_Parts_Change.this, mJsonString, Toast.LENGTH_SHORT).show();
                        return;
                    } else {
                        etChangePartNo.setText("");
                        etChangeLotNo.setText("");
                        etChangeQty.setText("");
                        tvStatus.setText("Part No, Lot No.를 스캔 하여 주십시오.");
                    }
                } else if (header.equals("CheckVer")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("CheckVer");
                    JSONObject item = jsonArray.getJSONObject(0);
                    if (!item.getString("Result").equals("Ver:"+ BuildConfig.VERSION_NAME)){
                        appVerAlarm();
                    }
                } else {
                    Toast.makeText(SMT_Parts_Change.this, mJsonString, Toast.LENGTH_SHORT).show();
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