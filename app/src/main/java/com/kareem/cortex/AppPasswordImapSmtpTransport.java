package com.kareem.cortex;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Emergency Gmail transport using a Google App Password over implicit TLS. */
public final class AppPasswordImapSmtpTransport implements BridgeMailTransport {
    private static final String SMTP_HOST="smtp.gmail.com";
    private static final int SMTP_PORT=465;
    private static final String IMAP_HOST="imap.gmail.com";
    private static final int IMAP_PORT=993;
    private static final int TIMEOUT_MS=30_000;
    private static final int MAX_VERDICT_SCAN=500;

    private final String email;
    private final String appPassword;

    public AppPasswordImapSmtpTransport(String email,String appPassword){
        if(email==null||email.trim().isEmpty()) throw new IllegalArgumentException("email required");
        String normalized=appPassword==null?"":appPassword.replace(" ","").trim();
        if(normalized.length()!=16) throw new IllegalArgumentException("16-character Google app password required");
        this.email=email.trim();
        this.appPassword=normalized;
    }

    @Override public String name(){return "GMAIL_IMAP_SMTP_APP_PASSWORD";}

    @Override public GmailBridgeTransport.SendResult sendEnvelope(JSONObject envelope){
        ChatGptBridgeProtocol.Validation v=ChatGptBridgeProtocol.validateEnvelope(envelope);
        if(!v.ok) return GmailBridgeTransport.SendResult.fail("INVALID_ENVELOPE: "+v.reason);
        try{
            smtpSend(ChatGptBridgeProtocol.subjectFor(envelope),envelope.toString());
            return GmailBridgeTransport.SendResult.ok("smtp-"+envelope.optString("requestId",""),"");
        }catch(Throwable t){return GmailBridgeTransport.SendResult.fail(classify(t));}
    }

    @Override public List<JSONObject> fetchCandidateVerdicts(long newerThanEpochMs,int maxResults)throws Exception{
        int limit=Math.min(Math.max(1,maxResults),MAX_VERDICT_SCAN);
        ArrayList<JSONObject> out=new ArrayList<>();
        try(SSLSocket socket=open(IMAP_HOST,IMAP_PORT)){
            BufferedInputStream in=new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream os=new BufferedOutputStream(socket.getOutputStream());
            String hello=readLine(in);if(hello==null||!hello.startsWith("* OK"))throw new BridgeAuthException("IMAP_GREETING_FAILED: "+safe(hello));
            command(os,in,"A1 LOGIN "+quote(email)+" "+quote(appPassword),"A1");
            command(os,in,"A2 SELECT INBOX","A2");
            String since=new SimpleDateFormat("dd-MMM-yyyy",Locale.US).format(new Date(Math.max(0,newerThanEpochMs)));
            String search=command(os,in,"A3 UID SEARCH SINCE "+since+" SUBJECT \"CHATGPT_TEST_VERDICT\"","A3");
            long[] uids=parseSearchUids(search);
            int start=Math.max(0,uids.length-limit);
            int tag=4;
            for(int i=uids.length-1;i>=start;i--){
                String t="A"+(tag++);
                try{
                    byte[] literal=fetchSingleLiteral(os,in,t+" UID FETCH "+uids[i]+" (BODY.PEEK[])",t);
                    if(literal.length==0)continue;
                    String rawMime=new String(literal,StandardCharsets.UTF_8);
                    String decodedText=MimeTextExtractor.extractBestText(rawMime);
                    JSONObject env=parseEnvelope(decodedText);
                    if(env!=null&&ChatGptBridgeProtocol.MessageType.CHATGPT_TEST_VERDICT.name().equals(env.optString("messageType"))) out.add(env);
                }catch(Throwable ignored){
                    // One malformed or unreadable message must not abort discovery of later verdicts.
                }
            }
            write(os,"AZ LOGOUT\r\n");
        }catch(Throwable t){if(t instanceof Exception)throw (Exception)t;throw new Exception(t);}
        return out;
    }

    /**
     * Executes one UID FETCH and returns only the IMAP literal bytes declared by {N}.
     * No wrapper stripping or sentinel substring matching is used, so MIME/base64 lines
     * beginning with characters such as 'A' can never truncate the message.
     */
    static byte[] fetchSingleLiteral(BufferedOutputStream out,BufferedInputStream in,String cmd,String tag)throws Exception{
        write(out,cmd+"\r\n");
        byte[] literal=null;
        while(true){
            byte[] line=readLineBytes(in);if(line==null)throw new Exception("IMAP_EOF");
            String text=new String(line,StandardCharsets.US_ASCII);
            int open=text.lastIndexOf('{');
            int close=text.endsWith("}")?text.length()-1:-1;
            if(open>=0&&close>open){
                int n=Integer.parseInt(text.substring(open+1,close));
                byte[] current=readExact(in,n);
                if(literal==null)literal=current;
            }
            if(text.startsWith(tag+" ")){
                if(!text.toUpperCase(Locale.ROOT).startsWith(tag+" OK"))throw new Exception("IMAP_FETCH_FAILED: "+text);
                return literal==null?new byte[0]:literal;
            }
        }
    }

    private void smtpSend(String subject,String body)throws Exception{
        try(SSLSocket socket=open(SMTP_HOST,SMTP_PORT)){
            BufferedInputStream in=new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream out=new BufferedOutputStream(socket.getOutputStream());
            expectSmtp(in,220);smtp(out,in,"EHLO cortex.android",250);smtp(out,in,"AUTH LOGIN",334);
            smtp(out,in,java.util.Base64.getEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8)),334);
            smtp(out,in,java.util.Base64.getEncoder().encodeToString(appPassword.getBytes(StandardCharsets.UTF_8)),235);
            smtp(out,in,"MAIL FROM:<"+email+">",250);smtp(out,in,"RCPT TO:<"+email+">",250);smtp(out,in,"DATA",354);
            String mime="From: "+email+"\r\nTo: "+email+"\r\nSubject: "+sanitize(subject)+"\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n"+dotStuff(body)+"\r\n.\r\n";
            write(out,mime);expectSmtp(in,250);try{smtp(out,in,"QUIT",221);}catch(Throwable ignored){}
        }
    }

    private static SSLSocket open(String host,int port)throws Exception{SSLSocket socket=(SSLSocket)SSLSocketFactory.getDefault().createSocket(host,port);socket.setSoTimeout(TIMEOUT_MS);socket.startHandshake();return socket;}
    private static void smtp(BufferedOutputStream out,BufferedInputStream in,String line,int expected)throws Exception{write(out,line+"\r\n");expectSmtp(in,expected);}
    private static String expectSmtp(BufferedInputStream in,int expected)throws Exception{StringBuilder all=new StringBuilder();String line;int code=-1;do{line=readLine(in);if(line==null)throw new Exception("SMTP_EOF");all.append(line).append('\n');if(line.length()>=3)try{code=Integer.parseInt(line.substring(0,3));}catch(Throwable ignored){}}while(line.length()>3&&line.charAt(3)=='-');if(code!=expected){if(code==534||code==535)throw new BridgeAuthException("APP_PASSWORD_REJECTED: "+all.toString().trim());throw new Exception("SMTP_"+code+": "+all.toString().trim());}return all.toString();}
    private static String command(BufferedOutputStream out,BufferedInputStream in,String cmd,String tag)throws Exception{write(out,cmd+"\r\n");StringBuilder s=new StringBuilder();String line;while((line=readLine(in))!=null){s.append(line).append('\n');if(line.startsWith(tag+" ")){if(!line.toUpperCase(Locale.ROOT).startsWith(tag+" OK")){if(line.toUpperCase(Locale.ROOT).contains("AUTHENTICATIONFAILED"))throw new BridgeAuthException("APP_PASSWORD_REJECTED: "+line);throw new Exception("IMAP_COMMAND_FAILED: "+line);}return s.toString();}}throw new Exception("IMAP_EOF");}
    private static long[] parseSearchUids(String response){for(String line:response.split("\\n"))if(line.startsWith("* SEARCH")){String rest=line.substring(8).trim();if(rest.isEmpty())return new long[0];String[] p=rest.split("\\s+");long[] a=new long[p.length];int n=0;for(String x:p)try{a[n++]=Long.parseLong(x);}catch(Throwable ignored){}return Arrays.copyOf(a,n);}return new long[0];}
    static JSONObject parseEnvelope(String body){if(body==null)return null;int first=body.indexOf('{'),last=body.lastIndexOf('}');if(first<0||last<=first)return null;try{JSONObject o=new JSONObject(body.substring(first,last+1));return ChatGptBridgeProtocol.validateEnvelope(o).ok?o:null;}catch(Throwable ignored){return null;}}
    private static void write(BufferedOutputStream out,String s)throws Exception{out.write(s.getBytes(StandardCharsets.UTF_8));out.flush();}
    private static String readLine(BufferedInputStream in)throws Exception{byte[] b=readLineBytes(in);return b==null?null:new String(b,StandardCharsets.UTF_8);}
    private static byte[] readLineBytes(BufferedInputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();int c;boolean any=false;while((c=in.read())!=-1){any=true;if(c=='\n')break;if(c!='\r')b.write(c);}return !any&&b.size()==0?null:b.toByteArray();}
    private static byte[] readExact(BufferedInputStream in,int n)throws Exception{byte[] b=new byte[n];int off=0;while(off<n){int r=in.read(b,off,n-off);if(r<0)throw new Exception("IMAP_LITERAL_EOF");off+=r;}return b;}
    private static String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    private static String sanitize(String s){return safe(s).replace("\r"," ").replace("\n"," ");}
    private static String dotStuff(String s){String x=s==null?"":s.replace("\r\n","\n").replace("\r","\n");StringBuilder b=new StringBuilder();for(String line:x.split("\n",-1)){if(line.startsWith("."))b.append('.');b.append(line).append("\r\n");}return b.toString();}
    private static String safe(String s){return s==null?"":s;}
    private static String classify(Throwable t){String m=t.getMessage()==null?"":t.getMessage();return t.getClass().getSimpleName()+": "+m;}
    public static final class BridgeAuthException extends Exception{public BridgeAuthException(String m){super(m);}}
}
