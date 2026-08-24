package com.kareem.cortex;

import android.content.*;
import android.database.Cursor;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import java.text.SimpleDateFormat;
import java.util.*;

public final class IntegrationEngine {
    private IntegrationEngine(){}

    public static int importCalendar(Context ctx,VaultDb db)throws Exception{
        long now=System.currentTimeMillis(),from=now-7L*86400000L,to=now+45L*86400000L;int n=0;
        String[] cols={CalendarContract.Events.TITLE,CalendarContract.Events.DESCRIPTION,CalendarContract.Events.DTSTART,CalendarContract.Events.DTEND,CalendarContract.Events.EVENT_LOCATION,CalendarContract.Events.CALENDAR_DISPLAY_NAME};
        Cursor c=ctx.getContentResolver().query(CalendarContract.Events.CONTENT_URI,cols,CalendarContract.Events.DTSTART+">=? AND "+CalendarContract.Events.DTSTART+"<=?",new String[]{String.valueOf(from),String.valueOf(to)},CalendarContract.Events.DTSTART+" ASC");
        if(c!=null){SimpleDateFormat f=new SimpleDateFormat("EEE dd MMM yyyy HH:mm",Locale.getDefault());while(c.moveToNext()){String title=nz(c.getString(0)),desc=nz(c.getString(1)),loc=nz(c.getString(4)),cal=nz(c.getString(5));long start=c.getLong(2),end=c.getLong(3);StringBuilder body=new StringBuilder();body.append(title).append("\nWhen: ").append(f.format(new Date(start)));if(end>start)body.append(" → ").append(f.format(new Date(end)));if(!loc.isEmpty())body.append("\nLocation: ").append(loc);if(!desc.isEmpty())body.append("\n").append(desc);if(!cal.isEmpty())body.append("\nCalendar: ").append(cal);long id=db.insert("CALENDAR_EVENT","calendar_sync",title.isEmpty()?"Calendar event":title,body.toString(),"Calendar","calendar,event,date,follow-up","",Fingerprint.text("cal|"+title+"|"+start+"|"+loc),"{\"start\":"+start+",\"end\":"+end+"}");if(id>0)n++;}c.close();}
        FeatureStore.logIntegration(db,"calendar","ok",n+" events imported");if(n>0)AnalysisQueue.kick(ctx,db,null);return n;
    }

    public static int importContacts(Context ctx,VaultDb db)throws Exception{
        int n=0;HashSet<String> seen=new HashSet<>();preloadExistingContactKeys(db,seen);
        String[] cols={ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.CONTACT_ID};
        Cursor c=ctx.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,cols,null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC");
        if(c!=null){while(c.moveToNext()&&n<1000){String name=nz(c.getString(0)),phone=nz(c.getString(1));if(name.isEmpty()||phone.isEmpty())continue;String canonical=PhoneNumberNormalizer.canonical(phone);String key=contactKey(name,canonical.isEmpty()?phone:canonical);if(!seen.add(key))continue;String body=name+"\nPhone: "+phone;long id=db.insert("CONTACT","contacts_sync",name,body,"People","person,contact","",Fingerprint.text("contact|"+key),"{\"phone_identity\":\""+json(canonical)+"\"}");if(id>0)n++;}c.close();}
        ContactSafetyMaintenance.run(db);
        EntityGraphMaintenance.run(db);
        FeatureStore.logIntegration(db,"contacts","ok",n+" contacts imported");if(n>0)AnalysisQueue.kick(ctx,db,null);return n;
    }

    private static void preloadExistingContactKeys(VaultDb db,Set<String> seen){
        Cursor c=db.getReadableDatabase().rawQuery("SELECT title,raw_text FROM knowledge_items WHERE type='CONTACT' AND source='contacts_sync'",null);
        while(c.moveToNext()){
            String name=nz(c.getString(0)),raw=nz(c.getString(1)),phone=extractPhone(raw);if(name.isEmpty()||phone.isEmpty())continue;
            String canonical=PhoneNumberNormalizer.canonical(phone);seen.add(contactKey(name,canonical.isEmpty()?phone:canonical));
        }c.close();
    }
    private static String extractPhone(String raw){int i=raw.indexOf("Phone:");return i<0?"":raw.substring(i+6).trim().split("\\n",2)[0].trim();}
    private static String contactKey(String name,String phoneIdentity){return name.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT)+"|"+phoneIdentity;}
    private static String json(String s){return (s==null?"":s).replace("\\","\\\\").replace("\"","\\\"");}
    private static String nz(String s){return s==null?"":s.trim();}
}
