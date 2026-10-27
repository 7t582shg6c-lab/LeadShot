package dev.leadshot;

/** Numeric editor metadata. Integers prevent locale dependent parsing and floating-point drift. */
public enum FineTuneParameter {
    SCAN_CONE("索敵角", "CENTERの画面中央からの探索角度。大きいほど候補が増えます。", "°", 4, 85, 24, 1, 5),
    SENSOR_RANGE("索敵距離", "常時追跡する距離。遠距離・ALLは処理が重くなります。", "m", 8, 128, 48, 1, 16),
    RETICLE_WEIGHT("照準線の太さ", "偏差照準・予測位置の見やすさ。太くすると画面を覆います。", "px", 1, 8, 2, 1, 2),
    HUD_ALPHA("ガイドの不透明度", "0は透明、255は不透明です。見失う場合は上げてください。", "", 0, 255, 210, 1, 25),
    LABEL_SCALE("文字の倍率", "照準ラベルとHUD本文。50〜200%で調整できます。", "%", 50, 200, 100, 5, 25),
    AIM_RATE("照準追従速度", "1/10°毎tick単位。例：600と入力すると60°/tickです。", "x0.1°/tick", 1, 1200, 20, 5, 50),
    CAMERA_FOV("投影画角", "Minecraftの画角設定に合わせます。自動取得ではありません。", "°", 50, 110, 70, 1, 10),
    LEAD_LATENCY("通信遅延の先読み", "相手の位置を何tick分先読みするか。0〜4.0tick。高速移動時は0.5〜2.0を試します。", "x0.1tick", 0, 40, 8, 1, 5);
    private final String label,description,unit;
    private final int minimum,maximum,defaults,smallStep,largeStep;
    FineTuneParameter(String label,String description,String unit,int min,int max,int def,int small,int large){
        this.label=label;this.description=description;this.unit=unit;this.minimum=min;this.maximum=max;
        this.defaults=def;this.smallStep=small;this.largeStep=large;
    }
    public String label(){return label;}
    public String description(){return description;}
    public String unit(){return unit;}
    public int minimum(){return minimum;}
    public int maximum(){return maximum;}
    public int defaults(){return defaults;}
    public int smallStep(){return smallStep;}
    public int largeStep(){return largeStep;}
    public int clamp(int value){return Math.max(minimum,Math.min(maximum,value));}
    public int move(int now,int direction,boolean coarse){return clamp(now+(direction>0?1:-1)*(coarse?largeStep:smallStep));}
    public Integer parse(String value){
        if(value==null||!value.trim().matches("[+-]?[0-9]{1,5}"))return null;
        try{return clamp(Integer.parseInt(value.trim()));}catch(NumberFormatException ex){return null;}
    }
    public double ratio(int value){return (clamp(value)-minimum)/(double)(maximum-minimum);}
    public String show(int raw){
        int value=clamp(raw);
        if(this==AIM_RATE)return String.format(java.util.Locale.ROOT,"%.1f°/tick",value/10.0);
        return value+unit;
    }
}
