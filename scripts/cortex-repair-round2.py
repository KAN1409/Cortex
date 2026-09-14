#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
changed = []

def read(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

def write(rel, text):
    p = ROOT / rel
    old = p.read_text(encoding='utf-8')
    if old != text:
        p.write_text(text, encoding='utf-8')
        changed.append(rel)

# 1) Locale-neutral normalization for internal identifiers/classifiers.
for p in (ROOT / 'app/src/main/java').rglob('*.java'):
    s = p.read_text(encoding='utf-8')
    n = s.replace('.toLowerCase()', '.toLowerCase(java.util.Locale.ROOT)')
    n = n.replace('.toUpperCase()', '.toUpperCase(java.util.Locale.ROOT)')
    if n != s:
        p.write_text(n, encoding='utf-8')
        changed.append(str(p.relative_to(ROOT)))

# 2) Bound the exported Android Share surface: count, text size, and streamed bytes.
share = 'app/src/main/java/com/kareem/cortex/ShareImporter.java'
s = read(share)
if 'MAX_SHARED_BYTES' not in s:
    s = s.replace(
        'public class ShareImporter {\n    private final Context ctx;',
        'public class ShareImporter {\n'
        '    static final int MAX_SHARED_ITEMS=32;\n'
        '    static final int MAX_SHARED_TEXT_CHARS=1_000_000;\n'
        '    static final long MAX_SHARED_BYTES=512L*1024L*1024L;\n'
        '    private final Context ctx;'
    )

s = s.replace(
    'String text=i.getStringExtra(Intent.EXTRA_TEXT);return text!=null&&!text.trim().isEmpty()?saveText(text,mime):0;',
    'String text=i.getStringExtra(Intent.EXTRA_TEXT);if(text!=null&&text.length()>MAX_SHARED_TEXT_CHARS)return 0;return text!=null&&!text.trim().isEmpty()?saveText(text,mime):0;'
)
s = s.replace(
    'if(us!=null)for(Uri u:us){long id=(mime!=null&&mime.startsWith("image/"))?saveImage(u,mime):(mime!=null&&mime.startsWith("audio/"))?saveAudio(u,mime):saveFile(u,mime);if(id>0)n++;}',
    'if(us!=null)for(int idx=0;idx<us.size()&&idx<MAX_SHARED_ITEMS;idx++){Uri u=us.get(idx);long id=(mime!=null&&mime.startsWith("image/"))?saveImage(u,mime):(mime!=null&&mime.startsWith("audio/"))?saveAudio(u,mime):saveFile(u,mime);if(id>0)n++;}'
)
start = s.find('    private String copy(Uri uri,String name,String folder){')
if start >= 0 and 'Shared item exceeds Cortex import limit' not in s:
    end = s.find('\n}', start)
    if end < 0:
        raise SystemExit('ShareImporter copy method boundary not found')
    replacement = '''    private String copy(Uri uri,String name,String folder){
        File out=null;
        try{
            try(android.content.res.AssetFileDescriptor afd=ctx.getContentResolver().openAssetFileDescriptor(uri,"r")){
                if(afd!=null){long declared=afd.getLength();if(declared>MAX_SHARED_BYTES)return"";}
            }catch(FileNotFoundException ignored){}
            File dir=new File(ctx.getFilesDir(),folder);
            if(!dir.exists()&&!dir.mkdirs())return"";
            String safe=name.replaceAll("[^A-Za-z0-9._-]","_");
            if(safe.length()>80)safe=safe.substring(safe.length()-80);
            out=new File(dir,UUID.randomUUID()+"_"+safe);
            try(InputStream in=ctx.getContentResolver().openInputStream(uri);OutputStream os=new BufferedOutputStream(new FileOutputStream(out))){
                if(in==null)return"";
                byte[] b=new byte[64*1024];int r;long total=0;
                while((r=in.read(b))!=-1){total+=r;if(total>MAX_SHARED_BYTES)throw new IOException("Shared item exceeds Cortex import limit");os.write(b,0,r);}
            }
            return out.getAbsolutePath();
        }catch(Exception e){
            if(out!=null&&out.exists())try{out.delete();}catch(Throwable ignored){}
            return"";
        }
    }'''
    s = s[:start] + replacement + s[end:]
write(share, s)

# 3) Bound the exported ChatGPT Teacher text import before JSON parsing/shadow evaluation.
teacher = 'app/src/main/java/com/kareem/cortex/CortexChatGptAppTeacher.java'
s = read(teacher)
if 'MAX_POLICY_TEXT_CHARS' not in s:
    s = s.replace(
        '    private static final String KEY_LAUNCHED_AT="launched_at";\n',
        '    private static final String KEY_LAUNCHED_AT="launched_at";\n'
        '    static final int MAX_POLICY_TEXT_CHARS=262_144;\n'
    )
s = s.replace(
    '    public static ImportResult importText(Context context,String raw){\n        try{',
    '    public static ImportResult importText(Context context,String raw){\n'
    '        if(raw==null||raw.length()>MAX_POLICY_TEXT_CHARS)return new ImportResult(false,"","Policy text outside bounds");\n'
    '        try{'
)
s = s.replace(
    '        String x=safe(raw).trim();\n',
    '        String x=safe(raw).trim();\n'
    '        if(x.length()>MAX_POLICY_TEXT_CHARS)throw new IllegalArgumentException("Policy text outside bounds");\n'
)
write(teacher, s)

# 4) Accessibility-safe press animation: consume animated gestures and route ACTION_UP through performClick exactly once.
ui = 'app/src/main/java/com/kareem/cortex/CortexUi.java'
s = read(ui)
old_touch = 'v.setOnTouchListener((x,e)->{if(!CortexMotion.allowed(a))return false;int act=e.getActionMasked();if(act==MotionEvent.ACTION_DOWN){x.animate().cancel();x.animate().scaleX(.985f).scaleY(.985f).translationZ(-dp(a,1)).setDuration(90).start();}else if(act==MotionEvent.ACTION_UP||act==MotionEvent.ACTION_CANCEL){x.animate().cancel();x.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(150).start();}return false;});return v;'
new_touch = 'v.setOnTouchListener((x,e)->{if(!CortexMotion.allowed(a))return false;int act=e.getActionMasked();if(act==MotionEvent.ACTION_DOWN){x.animate().cancel();x.animate().scaleX(.985f).scaleY(.985f).translationZ(-dp(a,1)).setDuration(90).start();return true;}if(act==MotionEvent.ACTION_UP){x.animate().cancel();x.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(150).start();x.performClick();return true;}if(act==MotionEvent.ACTION_CANCEL){x.animate().cancel();x.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(150).start();return true;}return true;});return v;'
if old_touch in s:
    s = s.replace(old_touch, new_touch, 1)
elif new_touch not in s:
    raise SystemExit('CortexUi pressable touch contract not found')
write(ui, s)

# 5) Eliminate per-frame allocations from hot custom views.
energy = 'app/src/main/java/com/kareem/cortex/CortexEnergyRibbonView.java'
s = read(energy)
if 'private final Paint haze=' not in s:
    s = s.replace(
        '    private final Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG),core=new Paint(Paint.ANTI_ALIAS_FLAG),gold=new Paint(Paint.ANTI_ALIAS_FLAG);',
        '    private final Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG),core=new Paint(Paint.ANTI_ALIAS_FLAG),gold=new Paint(Paint.ANTI_ALIAS_FLAG),haze=new Paint(Paint.ANTI_ALIAS_FLAG);'
    )
if 'onSizeChanged(int w,int h,int oldw,int oldh)' not in s:
    marker = '    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();animator=null;super.onDetachedFromWindow();}\n'
    addition = marker + '    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w>0&&h>0)haze.setShader(new RadialGradient(w*.82f,h*.35f,Math.max(w,h)*.28f,new int[]{Color.argb(30,190,221,82),Color.argb(8,240,184,56),Color.TRANSPARENT},new float[]{0,.46f,1f},Shader.TileMode.CLAMP));}\n'
    if marker not in s: raise SystemExit('EnergyRibbon detach marker missing')
    s = s.replace(marker, addition, 1)
s = s.replace('        Paint haze=new Paint(Paint.ANTI_ALIAS_FLAG);haze.setShader(new RadialGradient(w*.82f,h*.35f,Math.max(w,h)*.28f,new int[]{Color.argb(30,190,221,82),Color.argb(8,240,184,56),Color.TRANSPARENT},new float[]{0,.46f,1f},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,haze);', '        c.drawRect(0,0,w,h,haze);')
write(energy, s)

glyph = 'app/src/main/java/com/kareem/cortex/CortexGlyphView.java'
s = read(glyph)
if 'private final Path glyphPath=' not in s:
    s = s.replace('    private final Paint accentPaint=new Paint(Paint.ANTI_ALIAS_FLAG);', '    private final Paint accentPaint=new Paint(Paint.ANTI_ALIAS_FLAG);\n    private final Path glyphPath=new Path();\n    private final RectF glyphRect=new RectF();')
s = s.replace('        Path p=new Path();RectF r;', '        Path p=glyphPath;p.reset();RectF r=glyphRect;')
s = re.sub(r'r=new RectF\(([^;]+)\);', r'r.set(\1);', s)
write(glyph, s)

ring = 'app/src/main/java/com/kareem/cortex/CortexRingButton.java'
s = read(ring)
if 'idleArc' not in s:
    s = s.replace('private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),inner=new Paint(Paint.ANTI_ALIAS_FLAG),border=new Paint(Paint.ANTI_ALIAS_FLAG),track=new Paint(Paint.ANTI_ALIAS_FLAG),glow=new Paint(Paint.ANTI_ALIAS_FLAG),arc=new Paint(Paint.ANTI_ALIAS_FLAG),icon=new Paint(Paint.ANTI_ALIAS_FLAG);', 'private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),inner=new Paint(Paint.ANTI_ALIAS_FLAG),border=new Paint(Paint.ANTI_ALIAS_FLAG),track=new Paint(Paint.ANTI_ALIAS_FLAG),glow=new Paint(Paint.ANTI_ALIAS_FLAG),arc=new Paint(Paint.ANTI_ALIAS_FLAG),idleArc=new Paint(Paint.ANTI_ALIAS_FLAG),icon=new Paint(Paint.ANTI_ALIAS_FLAG);')
    s = s.replace('    private final RectF ring=new RectF();', '    private final RectF ring=new RectF(),glyphRect=new RectF();private final Path glyphPath=new Path();')
    s = s.replace('        arc.setColor(accent);arc.setStyle(Paint.Style.STROKE);arc.setStrokeCap(Paint.Cap.ROUND);arc.setStrokeWidth(2.2f*density);', '        arc.setColor(accent);arc.setStyle(Paint.Style.STROKE);arc.setStrokeCap(Paint.Cap.ROUND);arc.setStrokeWidth(2.2f*density);idleArc.setStyle(Paint.Style.STROKE);idleArc.setStrokeCap(Paint.Cap.ROUND);idleArc.setStrokeWidth(2.2f*density);')
s = s.replace('Paint idle=new Paint(arc);idle.setColor(accent);idle.setAlpha(190);c.drawArc(ring,-90,359.9f,false,idle);', 'idleArc.setColor(accent);idleArc.setAlpha(190);c.drawArc(ring,-90,359.9f,false,idleArc);')
s = s.replace('    private void drawGlyph(Canvas c,float cx,float cy,float s){Path p=new Path();switch(glyph){', '    private void drawGlyph(Canvas c,float cx,float cy,float s){Path p=glyphPath;p.reset();switch(glyph){')
s = s.replace('RectF stop=new RectF(cx-q,cy-q,cx+q,cy+q);', 'RectF stop=glyphRect;stop.set(cx-q,cy-q,cx+q,cy+q);')
s = s.replace('RectF mic=new RectF(cx-bodyW,cy-bodyH,cx+bodyW,cy+s*.08f);', 'RectF mic=glyphRect;mic.set(cx-bodyW,cy-bodyH,cx+bodyW,cy+s*.08f);')
write(ring, s)

scrub = 'app/src/main/java/com/kareem/cortex/CortexScrubberView.java'
s = read(scrub)
if 'progressTrack' not in s:
    s = s.replace('private final Paint bars=new Paint(Paint.ANTI_ALIAS_FLAG),active=new Paint(Paint.ANTI_ALIAS_FLAG),head=new Paint(Paint.ANTI_ALIAS_FLAG),track=new Paint(Paint.ANTI_ALIAS_FLAG),thumb=new Paint(Paint.ANTI_ALIAS_FLAG);', 'private final Paint bars=new Paint(Paint.ANTI_ALIAS_FLAG),active=new Paint(Paint.ANTI_ALIAS_FLAG),head=new Paint(Paint.ANTI_ALIAS_FLAG),track=new Paint(Paint.ANTI_ALIAS_FLAG),progressTrack=new Paint(Paint.ANTI_ALIAS_FLAG),thumb=new Paint(Paint.ANTI_ALIAS_FLAG);')
    s = s.replace('        track.setColor(Color.rgb(44,44,47));track.setStrokeWidth(3*d);track.setStrokeCap(Paint.Cap.ROUND);', '        track.setColor(Color.rgb(44,44,47));track.setStrokeWidth(3*d);track.setStrokeCap(Paint.Cap.ROUND);progressTrack.setColor(CortexUi.RED);progressTrack.setStrokeWidth(3*d);progressTrack.setStrokeCap(Paint.Cap.ROUND);')
s = s.replace('if(progress>0){Paint pr=new Paint(track);pr.setColor(CortexUi.RED);c.drawLine(pad,y,xp,y,pr);}', 'if(progress>0)c.drawLine(pad,y,xp,y,progressTrack);')
write(scrub, s)

brief = 'app/src/main/java/com/kareem/cortex/SatinBriefActivity.java'
s = read(brief)
old_fields = '        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),dot=new Paint(Paint.ANTI_ALIAS_FLAG);final String kind;final float d;final int accent;'
new_fields = '        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),dot=new Paint(Paint.ANTI_ALIAS_FLAG),accentStroke=new Paint(Paint.ANTI_ALIAS_FLAG);final Path glyphPath=new Path();final RectF glyphRect=new RectF();final String kind;final float d;final int accent;'
if old_fields in s:
    s = s.replace(old_fields, new_fields, 1)
old_ctor = 'p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);dot.setColor(color);dot.setStyle(Paint.Style.FILL);setClickable(true);}'
new_ctor = 'p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);dot.setColor(color);dot.setStyle(Paint.Style.FILL);accentStroke.setStyle(Paint.Style.STROKE);accentStroke.setStrokeCap(Paint.Cap.ROUND);setClickable(true);}'
if old_ctor in s:
    s = s.replace(old_ctor, new_ctor, 1)
s = s.replace('Path q=new Path();', 'Path q=glyphPath;q.reset();')
s = re.sub(r'RectF r=new RectF\(([^;]+)\);', r'RectF r=glyphRect;r.set(\1);', s)
s = s.replace('Paint a=new Paint(p);a.setColor(accent);a.setStrokeWidth(2*d);', 'Paint a=accentStroke;a.setColor(accent);a.setStrokeWidth(2*d);')
write(brief, s)

print(f'round2_changed_files={len(set(changed))}')
for rel in sorted(set(changed)):
    print(rel)
