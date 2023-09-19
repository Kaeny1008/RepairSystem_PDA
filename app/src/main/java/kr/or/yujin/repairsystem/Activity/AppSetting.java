package kr.or.yujin.repairsystem.Activity;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import kr.or.yujin.repairsystem.R;

public class AppSetting extends AppCompatActivity{

    private Button btnSave;
    private EditText serverIP, serverPort;
    private CheckBox cbPGMNameCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_setting);

        btnSave = (Button) findViewById(R.id.btnSave);
        serverIP = (EditText) findViewById(R.id.serverIP);
        serverPort = (EditText) findViewById(R.id.serverPort);
        cbPGMNameCheck = (CheckBox) findViewById(R.id.cbPGMNameCheck);

        SharedPreferences setting = getSharedPreferences("setting", Activity.MODE_PRIVATE);
        serverIP.setText(setting.getString("serverIP","125.137.78.158"));
        serverPort.setText(setting.getString("serverPort","10520"));

        cbPGMNameCheck.setChecked(setting.getString("smt_PGM_Name_Check", "Yes").equals("Yes"));

        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences setting = getSharedPreferences("setting", Activity.MODE_PRIVATE);
                SharedPreferences.Editor appsetting = setting.edit();
                appsetting.putString("serverIP", serverIP.getText().toString());
                MainActivity.server_ip = serverIP.getText().toString();
                appsetting.putString("serverPort", serverPort.getText().toString());
                MainActivity.server_port = Integer.parseInt(serverPort.getText().toString());
                if (cbPGMNameCheck.isChecked()) {
                    appsetting.putString("smt_PGM_Name_Check", "Yes");
                } else {
                    appsetting.putString("smt_PGM_Name_Check", "No");
                }
                //꼭 commit()을 해줘야 값이 저장됩니다
                appsetting.commit();

                finish();
            }
        });
    }
}
