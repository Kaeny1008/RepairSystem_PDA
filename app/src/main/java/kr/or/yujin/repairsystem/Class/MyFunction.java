package kr.or.yujin.repairsystem.Class;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MyFunction {
    // 현재 날짜, 시간을 가져온다.
    public static String getDateTime(){
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String getDateTime = df.format(date);

        return getDateTime;
    }

    // 일반적으로 사용하는 숫자 포맷
    public static String decimalFormat(int data){
        DecimalFormat formatter = new DecimalFormat("#,##0");
        String ret = formatter.format(data);

        return ret;
    }

    public static String repalce_MySQL_Error(String data){
        String replace_String  = "";

        switch (data){
            case "SQLSTATE[23000]":
                replace_String = "이미 저장(입력)된 데이터 입니다.\n확인하여 주십시오.";
            case "SQLSTATE[42S02]":
                replace_String = "Database에서 해당 Table을 찾을 수 없습니다.";
            default:
                replace_String = data;
        }

        return replace_String;
    }
}
