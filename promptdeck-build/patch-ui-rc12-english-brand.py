from pathlib import Path
import re

p=Path('/tmp/pd/PromptDeck/app/src/main/java/com/kareem/promptdeck/MainActivity.java')
s=p.read_text()

# Approved PromptDeck palette: near-black, crisp white, vivid cobalt blue.
s=s.replace('static final int BG=Color.rgb(13,17,23), SURFACE=Color.rgb(22,27,34), SURFACE2=Color.rgb(33,38,45), BORDER=Color.rgb(48,54,61);',
'''static final int BG=Color.rgb(8,10,14), SURFACE=Color.rgb(18,21,27), SURFACE2=Color.rgb(25,29,37), BORDER=Color.rgb(47,54,66);''')
s=s.replace('static final int TEXT=Color.rgb(230,237,243), MUTED=Color.rgb(125,133,144), ACCENT=Color.rgb(88,166,255), SUCCESS=Color.rgb(63,185,80);',
'''static final int TEXT=Color.rgb(248,249,251), MUTED=Color.rgb(143,152,168), ACCENT=Color.rgb(47,107,255), SUCCESS=Color.rgb(65,200,120);''')
s=s.replace('static final int SATIN_TOP=Color.rgb(30,36,45), SATIN_BOTTOM=Color.rgb(20,25,32), SATIN_EDGE=Color.rgb(56,64,74);',
'''static final int SATIN_TOP=Color.rgb(27,31,39), SATIN_BOTTOM=Color.rgb(16,19,25), SATIN_EDGE=Color.rgb(52,61,75);''')
s=s.replace('Button primary(String s){return styledButton(s,Color.rgb(31,111,235),Color.rgb(56,139,253),Color.WHITE);}',
'''Button primary(String s){return styledButton(s,Color.rgb(47,107,255),Color.rgb(74,128,255),Color.WHITE);}''')
s=s.replace('if(fill==Color.rgb(31,111,235))x.setBackground(satinShape(Color.rgb(55,135,250),Color.rgb(28,103,219),stroke,10));',
'''if(fill==Color.rgb(47,107,255))x.setBackground(satinShape(Color.rgb(67,126,255),Color.rgb(38,91,224),stroke,10));''')
s=s.replace('x.setElevation(dp(fill==Color.rgb(31,111,235)?3:1));','x.setElevation(dp(fill==Color.rgb(47,107,255)?3:1));')

# All category descriptions in English.
subs={
'Writing & Rewriting':'Write, rewrite, polish and improve text',
'Thinking & Ideas':'Generate ideas, challenge assumptions and make decisions',
'Research & Analysis':'Research, verify, compare and understand information',
'Planning & Execution':'Turn goals into priorities, plans and executable steps',
'Learning & Study':'Explain, learn, review and practice effectively',
'Work & Career':'Emails, resumes, interviews and meetings',
'Content Creation':'Hooks, scripts, social content and storytelling',
'Problem Solving & Technical':'Diagnose, fix, test and improve solutions',
'Data & Formatting':'Structure, transform and present information',
'Photo Editing & Image Generation':'Image editing, visual styles and generation presets'
}
for title,sub in subs.items():
    pat=r'(new Group\([^\n]*?"'+re.escape(title)+r'",)"[^"]*"'
    s,n=re.subn(pat,lambda m:m.group(1)+'"'+sub+'"',s,count=1)
    if n!=1: print('warning category subtitle not replaced:',title)

# English-only screen copy.
repls={
'كل الأوامر متقسمة بشكل واضح. افتح أي قسم، اختار الـcommand، واقرأ استخدامه قبل ما تضيفه للـStack.':'Browse prompt tools by category. Open any category, choose a prompt, review what it does, then add it to your Stack.',
'لسه ما اخترتش أي commands.':'No prompts selected yet.',
'رتّب الخطوات. كل command هيبني على نتيجة اللي قبله.':'Arrange your prompts in order. Each step builds on the useful output of the previous one.',
'ابدأ من Categories واختار command أو أكتر.':'Start by browsing Categories and add one or more prompts.',
'اكتب هنا الموضوع أو النص اللي عاوز تطبق عليه الـcommands…':'Enter your request, text or context here…',
'جاهز للنسخ أو الإرسال مباشرة إلى ChatGPT.':'Your composed prompt is ready to copy or send to ChatGPT.',
'ابدأ بالاقتراحات المرتبطة بالـStack الحالي، أو اختار أي command من المكتبة.':'Start with suggestions that work well with your current Stack, or choose any prompt from the library.',
'اقتراحات مبنية على الـcommands الموجودة عندك دلوقتي. دوس على أي واحدة لإضافتها فورًا.':'Suggestions based on your current Stack. Tap any prompt to add it instantly.',
'ستايلات وتوجيهات جاهزة لتعديل الصور وتوليد المشاهد':'Image editing, visual styles and generation presets',
'preset بصري جاهز يوجّه ChatGPT لنفس الستايل مع الحفاظ على تفاصيل طلب الصورة.':'ready-made visual preset that guides ChatGPT toward the intended style while preserving the image request.'
}
for a,b in repls.items(): s=s.replace(a,b)

# Replace decision/help copy with English.
use_re=re.compile(r'String useWhen\(Cmd c\)\{.*?\}String example\(Cmd c\)\{.*?\}',re.S)
use_new='''String useWhen(Cmd c){String n=c.command;if(has(n,"compare,proscons,rank,recommend,decision"))return"When you have multiple options and want a clearer comparison or decision.";if(has(n,"research,verify,sources,evidence,facts"))return"When you need reliable information or want to verify a claim before relying on it.";if(has(n,"rewrite,rephrase,polish,proofread,grammar,humanize"))return"When you already have text and want to improve it instead of starting from scratch.";if(has(n,"brainstorm,ideas,angles,alternative"))return"When you want to expand the solution space and generate fresh ideas or directions.";if(has(n,"debug,rootcause,fix,check,tests"))return"When something is wrong and you want to diagnose the cause and reach a testable fix.";if(has(n,"plan,strategy,roadmap,action,priority"))return"When you have a goal and want to turn it into a clear, practical sequence of actions.";return"When you want this capability as a focused step inside a larger request.";}String example(Cmd c){return "Apply /"+c.command+" to the request or material I provide, and give me a clear, useful result.";}'''
s,n=use_re.subn(use_new,s,count=1)
if n!=1: print('warning useWhen/example replacement failed')

# Ensure photo preset descriptions are English-only.
s=s.replace('return shortText+" — ready-made visual preset that guides ChatGPT toward the intended style while preserving the image request.";',
'''return shortText+" — ready-made visual preset for a consistent style while preserving the subject and request.";''')

# Add an English description catalog and apply it after all built-in seeds are loaded.
load_hook='seedPhotoCommands();seedExtraPhotoCommands();}'
if load_hook in s:
    s=s.replace(load_hook,'seedPhotoCommands();seedExtraPhotoCommands();englishizeDescriptions();}',1)
elif 'seedPhotoCommands();}' in s:
    s=s.replace('seedPhotoCommands();}','seedPhotoCommands();englishizeDescriptions();}',1)
else: print('warning load hook not found')

insert='''  void base(String title,String sub,boolean showStack){'''
methods=r'''  void englishizeDescriptions(){
    for(Cmd c:all){
      String d=englishDescription(c.command);
      if(d!=null&&!d.isEmpty())c.description=d;
      else if(containsArabic(c.description))c.description=c.custom?"Custom prompt — imported instruction":"Prompt tool for this task";
    }
  }

  boolean containsArabic(String x){return x!=null&&x.matches(".*[\\u0600-\\u06FF].*");}

  String englishDescription(String command){
    if(command==null)return null;String k=command.toLowerCase(Locale.ROOT);
    HashMap<String,String> m=new HashMap<>();
    String[][] d=new String[][]{
      {"eli5","Explains something in very simple, beginner-friendly terms"},{"summarize","Condenses text into the most important points"},{"rewrite","Rewrites text while preserving its meaning"},{"humanize","Makes AI-sounding text feel natural and human"},{"simplify","Makes complex wording easier to understand"},{"brainstorm","Generates a wide range of useful ideas"},{"ideas","Suggests fresh ideas and directions"},{"hook","Creates a strong opening that captures attention"},{"professional","Makes writing polished and professional"},{"translate","Translates text accurately into another language"},{"explain","Explains a topic clearly and thoroughly"},{"steps","Turns an explanation or task into clear steps"},{"critique","Identifies weaknesses, risks and ways to strengthen an idea"},{"improve","Improves the quality of the current result"},{"compare","Compares options using consistent criteria"},{"proscons","Lists meaningful advantages and disadvantages"},{"examples","Provides practical examples"},{"analogy","Explains an idea through a simple analogy"},{"shorten","Makes text shorter without losing the core meaning"},{"expand","Develops an idea with useful additional detail"},{"caption","Writes an engaging caption"},{"script","Turns an idea into a structured script"},{"carousel","Turns content into a social-media carousel"},{"reel","Turns an idea into a short-form reel concept"},{"viral","Suggests a more shareable, high-engagement version"},{"cta","Creates a clear call to action"},{"story","Turns information into a compelling story"},{"headline","Creates a strong headline"},{"angles","Suggests different creative or strategic angles"},{"alternative","Suggests viable alternatives"},{"plan","Turns a goal into a practical plan"},{"strategy","Builds a strategy around the objective"},{"roadmap","Creates a staged roadmap from now to the goal"},{"checklist","Turns work into a clear checklist"},{"todo","Extracts concrete tasks and to-dos"},{"priority","Ranks what matters most"},{"workflow","Organizes a repeatable workflow"},{"timeline","Arranges events or work in chronological order"},{"action","Turns analysis into executable actions"},{"template","Creates a reusable template"},{"study","Turns material into a study-friendly format"},{"quiz","Creates questions to test understanding"},{"flashcards","Creates concise review flashcards"},{"teach","Teaches the topic progressively"},{"hint","Provides a useful hint without giving away the full answer"},{"test","Creates a practice test"},{"review","Reviews information for understanding and gaps"},{"memorize","Creates techniques to help remember information"},{"mistakes","Finds common or likely mistakes"},{"research","Researches a question systematically"},{"verify","Checks important claims and flags uncertainty"},{"sources","Finds or requests reliable sources"},{"deepdive","Explores a topic in depth"},{"evidence","Looks for evidence supporting or challenging a claim"},{"facts","Extracts the most important factual information"},{"insights","Extracts useful insights and implications"},{"trends","Identifies important patterns and trends"},{"data","Extracts or organizes important data"},{"summary","Produces a focused executive summary"},{"grammar","Corrects grammar and language errors"},{"proofread","Proofreads text for clarity, correctness and consistency"},{"paraphrase","Rephrases text using different wording"},{"rephrase","Rewrites a sentence or passage in a new way"},{"polish","Refines wording, flow and overall quality"},{"clarify","Makes vague or confusing text clearer"},{"tone","Changes the tone while preserving the message"},{"formal","Makes writing more formal"},{"casual","Makes writing more natural and conversational"},{"arabic","Converts or adapts text into Arabic"},{"email","Writes a polished email"},{"reply","Drafts an appropriate reply"},{"followup","Writes a professional follow-up message"},{"request","Writes a clear, respectful request"},{"apology","Writes an appropriate apology"},{"resume","Improves resume content and positioning"},{"coverletter","Writes a targeted cover letter"},{"interview","Prepares interview questions and answers"},{"meeting","Helps prepare for a meeting"},{"minutes","Summarizes meeting minutes and actions"},{"rank","Ranks options by defined criteria"},{"recommend","Recommends the strongest option with trade-offs"},{"challenge","Challenges assumptions and weak reasoning"},{"devilsadvocate","Argues the strongest opposing case"},{"blindspots","Finds important blind spots you may have missed"},{"check","Reviews the final result for issues"},{"debug","Diagnoses errors and likely causes"},{"fix","Proposes a direct fix for the problem"},{"optimize","Improves performance, efficiency or quality"},{"better","Suggests a stronger version of the current result"},{"table","Converts information into a clear table"},{"bullets","Converts content into concise bullet points"},{"outline","Builds a logical outline"},{"format","Reformats content for readability or a target structure"},{"json","Structures information as JSON"},{"csv","Structures tabular information as CSV"},{"schema","Defines a clear data or output schema"},{"acceptance","Turns requirements into testable acceptance criteria"},{"requirements","Extracts functional and non-functional requirements"},{"spec","Writes a concise implementation specification"},{"prompt","Designs a stronger prompt for the same task"},{"assumptions","Surfaces assumptions that should be tested"},{"firstprinciples","Breaks the problem down from first principles"},{"counterexample","Looks for counterexamples that challenge the current idea"},{"socratic","Uses Socratic questions to improve reasoning"},{"tradeoffs","Makes the important trade-offs explicit"},{"decision","Structures a decision using evidence and constraints"},{"extract","Extracts the requested information from content"},{"classify","Classifies information into useful categories"},{"constraints","Identifies constraints that shape the solution"},{"risks","Identifies risks, likelihood and impact"},{"estimate","Produces a reasoned estimate with assumptions"},{"rootcause","Investigates the underlying root cause"},{"edgecases","Finds edge cases and failure scenarios"},{"refactor","Improves structure without changing intended behavior"},{"tests","Designs useful tests and validation cases"},{"security","Reviews security risks and weaknesses"},{"rubric","Creates a scoring rubric"},{"score","Scores a result against defined criteria"},
      {"neoncity","Cyberpunk night portrait preset"},{"goldenhour","Cinematic golden-hour portrait preset"},{"miniworld","Miniature diorama image preset"},{"fog","Atmospheric fog and mystery preset"},{"luxuryad","Premium luxury product-advertising preset"},{"lowanglehero","Powerful low-angle hero photography preset"},{"vintagefilm","Authentic vintage film photography preset"},{"droneview","Dramatic top-down aerial photography preset"},{"magazine","Fashion editorial photography preset"},{"rainynight","Moody rainy-night cinematic preset"},{"proheadshot","Professional headshot preset"},{"snowworld","Winter travel photography preset"},{"doubleexposure","Artistic double-exposure portrait preset"},{"oldmoney","Refined old-money lifestyle portrait preset"},{"studiopro","Professional studio portrait preset"},{"autumn","Warm autumn portrait preset"},{"moviescene","Cinematic movie-still preset"},{"hdreal","High-definition realistic image enhancement preset"},{"cinematicportrait","Cinematic portrait transformation preset"},{"doubleexposureviral","High-impact double-exposure visual preset"},{"travelstory","Cinematic travel-story photography preset"},{"storymytravel","Atmospheric rainy travel-story preset"},{"cinematictravel","Premium cinematic travel photography preset"},{"documentrytravel","Natural documentary travel photography preset"},{"travelvlog","Social-ready travel-vlog photography preset"},{"fixfaceresolution","Restores facial detail and natural photographic texture"}
    };
    for(String[] a:d)m.put(a[0],a[1]);return m.get(k);
  }

'''
if insert not in s: raise SystemExit('base insertion point not found')
s=s.replace(insert,methods+insert,1)

# Final sweep of user-facing Arabic literals left by earlier prototypes.
# Do not touch prompt instructions themselves; this only replaces UI/descriptions.
ui_arabic={
'بيعمل إيه؟':'WHAT IT DOES','استخدمه لما':'USE IT WHEN','مثال':'EXAMPLE','التعليمات اللي هتتبعت لـ ChatGPT':'INSTRUCTION SENT TO CHATGPT','يشتغل كويس مع':'WORKS WELL WITH'
}
for a,b in ui_arabic.items():s=s.replace(a,b)

# Approved flat launcher icon: wand + improvement spark + three progress steps.
res=Path('/tmp/pd/PromptDeck/app/src/main/res')
(res/'drawable').mkdir(parents=True,exist_ok=True)
(res/'drawable'/'promptdeck_icon.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n  <path android:fillColor="#080A0E" android:pathData="M0,0h108v108h-108z"/>\n  <path android:fillColor="#F8F9FB" android:pathData="M23,80 L20,77 Q18,75 20,72 L55,37 Q57,35 59,37 L62,40 Q64,42 62,44 L27,79 Q25,81 23,80z"/>\n  <path android:fillColor="#080A0E" android:pathData="M55,37 L59,33 L64,38 L62,40z"/>\n  <path android:fillColor="#F8F9FB" android:pathData="M60,32 L64,28 Q66,26 68,28 L72,32 Q74,34 72,36 L68,40z"/>\n  <path android:fillColor="#2F6BFF" android:pathData="M78,14 C80,24 84,28 94,30 C84,32 80,36 78,47 C76,36 72,32 62,30 C72,28 76,24 78,14z"/>\n  <path android:fillColor="#2F6BFF" android:pathData="M58,79 Q58,77 60,77 H91 Q93,77 93,79 V84 H58z"/>\n  <path android:fillColor="#2F6BFF" android:pathData="M69,70 Q69,68 71,68 H91 Q93,68 93,70 V75 H69z"/>\n  <path android:fillColor="#2F6BFF" android:pathData="M80,61 Q80,59 82,59 H91 Q93,59 93,61 V66 H80z"/>\n</vector>''')
manifest=Path('/tmp/pd/PromptDeck/app/src/main/AndroidManifest.xml')
ms=manifest.read_text()
ms=re.sub(r'android:icon="[^"]+"','android:icon="@drawable/promptdeck_icon"',ms)
if 'android:roundIcon=' in ms:ms=re.sub(r'android:roundIcon="[^"]+"','android:roundIcon="@drawable/promptdeck_icon"',ms)
manifest.write_text(ms)

p.write_text(s)
print('PromptDeck RC12 English-only UI + approved icon/theme applied')
