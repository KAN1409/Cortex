package com.kareem.cortex;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Entry point for creating work documents from grounded archive evidence. */
public final class WorkCreateDocumentActivity extends Activity {
    private VaultDb db;
    private EditText project;

    int dp(int v){return CortexUi.dp(this,v);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());build();
    }

    @Override protected void onDestroy(){if(db!=null)try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(16),dp(20),dp(28));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView title=CortexUi.plain(this,"Create from Archive",30,CortexUi.TEXT);CortexUi.medium(title);content.addView(title);
        TextView sub=CortexUi.text(this,"Cortex builds a grounded package from Work Vault. If local generation is not reliable, the package is sent to ChatGPT with exact instructions and source evidence.",11,CortexUi.MUTED);sub.setPadding(0,dp(5),0,dp(14));content.addView(sub);

        project=new EditText(this);project.setHint("Project filter — optional");project.setTextColor(CortexUi.TEXT);project.setHintTextColor(CortexUi.MUTED);project.setSingleLine(true);project.setBackgroundColor(0x00000000);project.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout field=CortexUi.card(this,16);field.addView(project,new LinearLayout.LayoutParams(-1,dp(48)));content.addView(field);

        content.addView(CortexUi.section(this,"Orders"));
        addChoice(content,WorkDocumentRecipe.Kind.ASSIGNMENT_ORDER_MANUFACTURING_ONLY);
        addChoice(content,WorkDocumentRecipe.Kind.ASSIGNMENT_ORDER_MANUFACTURING_AND_SUPPLY);
        addChoice(content,WorkDocumentRecipe.Kind.SUPPLY_ORDER_SUPPLY_ONLY);

        content.addView(CortexUi.section(this,"Reports and comparisons"));
        addChoice(content,WorkDocumentRecipe.Kind.COMMERCIAL_COMPARISON);
        addChoice(content,WorkDocumentRecipe.Kind.FOLLOW_UP_REPORT);
        addChoice(content,WorkDocumentRecipe.Kind.PRICE_COMPARISON);
        addChoice(content,WorkDocumentRecipe.Kind.PROJECT_STATUS_REPORT);
        addChoice(content,WorkDocumentRecipe.Kind.OWNER_PRESENTATION);

        TextView note=CortexUi.text(this,"Generated files remain derived outputs. Cortex must never treat them as original archive evidence unless they are later received from an external authoritative source.",10,CortexUi.MUTED);note.setPadding(dp(2),dp(16),dp(2),0);content.addView(note);
        CortexUi.addBottomNav(this,root,"work",null);setContentView(root);
    }

    private void addChoice(LinearLayout parent,WorkDocumentRecipe.Kind kind){
        WorkDocumentRecipe.Recipe recipe=WorkDocumentRecipe.forKind(kind);
        LinearLayout card=CortexUi.card(this,18);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(9));
        TextView name=CortexUi.plain(this,WorkDocumentRecipe.displayName(kind),14,CortexUi.TEXT);CortexUi.medium(name);card.addView(name);
        String meta=recipe.outputFormat+"  •  "+recipe.family+(recipe.scope.isEmpty()?"":"  •  "+recipe.scope);
        TextView m=CortexUi.text(this,meta,10,CortexUi.MUTED);m.setPadding(0,dp(4),0,dp(7));card.addView(m);
        TextView action=CortexUi.action(this,"BUILD PACKAGE + OPEN CHATGPT",CortexUi.ACCENT,false);card.addView(action,new LinearLayout.LayoutParams(-1,dp(42)));
        action.setOnClickListener(v->send(kind,action));parent.addView(card,cp);
    }

    private void send(WorkDocumentRecipe.Kind kind,TextView button){
        button.setEnabled(false);button.setText("PREPARING GROUNDED PACKAGE…");
        new Thread(()->{
            try{
                WorkDocumentBuilderBridge.Prepared prepared=WorkDocumentBuilderBridge.prepare(this,db,kind,project.getText()==null?"":project.getText().toString());
                runOnUiThread(()->{
                    boolean opened=WorkDocumentBuilderBridge.openChatGpt(this,prepared);
                    button.setEnabled(true);button.setText("BUILD PACKAGE + OPEN CHATGPT");
                    android.widget.Toast.makeText(this,opened?"Build package sent":"Could not open a share target",opened?android.widget.Toast.LENGTH_SHORT:android.widget.Toast.LENGTH_LONG).show();
                });
            }catch(Throwable e){runOnUiThread(()->{button.setEnabled(true);button.setText("BUILD PACKAGE + OPEN CHATGPT");android.widget.Toast.makeText(this,"Could not prepare document package",android.widget.Toast.LENGTH_LONG).show();});}
        },"work-document-builder").start();
    }
}
