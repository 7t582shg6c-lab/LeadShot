package dev.leadshot;

/** Numeric editor metadata. Integers prevent locale dependent parsing and floating-point drift. */
public enum FineTuneParameter {
    SCAN_CONE("索敵角 / SCAN CONE", "CENTERの画面中央からの探索角度。大きいほど候補が増えます。", "°", 4, 85, 24, 1, 5),
    SENSOR_RANGE("索敵距離 / SENSOR RANGE", "常時追跡する距離。遠距離・ALLは処理が重くなります。", "m", 8, 128, 48, 1, 16),
    RETICLE_WEIGHT("照準線の太さ / RETICLE WEIGHT", "LEAD/PREDラインの見やすさ。太くすると画面を覆います。", "px", 1, 8, 2, 1, 2),
    HUD_ALPHA("ガイドの不透明度 / HUD ALPHA", "0は透明、255は不透明です。見失う場合は上げてください。", "", 0, 255, 210, 1, 25),
    LABEL_SCALE("文字の倍率 / LABEL SCALE", "照準ラベルとHUD本文。50〜200%で調整できます。", "%", 50, 200, 100, 5, 25),
    AIM_RATE("照準追従速度 / AIM RATE", "1/10°毎tick単位。1.5°なら15と入力します。", "x0.1°/tick", 1, 120, 20, 1, 10),
    CAMERA_FOV("投影画角 / CAMERA FOV", "Minecraftの画角設定に合わせます。実カメラ画角の自動取得ではありません。", "°", 50, 110, 70, 1, 10);
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
