package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Locale;

/** Reversible graph hygiene: bad inferred labels are hidden, never deleted with their evidence. */
public final class EntityQualityMaintenance {
    private EntityQualityMaintenance(){}

    public static int run(VaultDb db){
        if(db==null)return 0;SQLiteDatabase s=db.getWritableDatabase();ArrayList<Long> hide=new ArrayList<>();
        Cursor c=s.rawQuery("SELECT id,kind,canonical_name FROM entity_nodes WHERE status='active'",null);
        while(c.moveToNext()){
            long id=c.getLong(0);String kind=n(c.getString(1)).toUpperCase(Locale.ROOT),name=n(c.getString(2));
            boolean identity="PERSON".equals(kind)||"PROJECT".equals(kind)||"ORGANIZATION".equals(kind)||"ORG".equals(kind)||"PRODUCT".equals(kind)||"PLACE".equals(kind);
            if(identity&&!EntityQualityPolicy.plausibleEntity(kind,name))hide.add(id);
        }
        c.close();
        ContentValues v=new ContentValues();v.put("status","filtered");v.put("updated_at",System.currentTimeMillis());
        int changed=0;for(long id:hide)changed+=s.update("entity_nodes",v,"id=?",new String[]{String.valueOf(id)});
        return changed;
    }

    private static String n(String s){return s==null?"":s.trim();}
}
