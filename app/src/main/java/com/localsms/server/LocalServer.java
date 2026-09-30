package com.localsms.server;

import android.content.Context;
import com.localsms.sms.SmsRepository;
import com.localsms.util.NetworkUtils;
import com.localsms.util.Prefs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class LocalServer {
    public static final int PORT = 8080;
    private final Context context;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private long startedAt;
    private volatile long requestCount;
    private volatile long lastRequestAt;

    public LocalServer(Context context) { this.context=context.getApplicationContext(); }
    public synchronized boolean start(){
        if(running)return true;
        try{serverSocket=new ServerSocket(PORT);running=true;startedAt=System.currentTimeMillis();Prefs.setRunning(context,true);acceptThread=new Thread(this::acceptLoop,"LDM-Dashboard");acceptThread.start();return true;}
        catch(IOException e){running=false;Prefs.setRunning(context,false);return false;}
    }
    private void acceptLoop(){while(running){try{Socket socket=serverSocket.accept();new Thread(()->handle(socket),"LDM-DashboardClient").start();}catch(IOException e){if(running){}else break;}}}
    private void handle(Socket socket){
        try(Socket s=socket){s.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));OutputStream out=s.getOutputStream();String request=in.readLine();String line;while((line=in.readLine())!=null&&!line.isEmpty()){}if(request==null)return;requestCount++;lastRequestAt=System.currentTimeMillis();String[] parts=request.split(" ");String path=parts.length>1?parts[1]:"/";
            if("/".equals(path)){send(out,200,"text/html; charset=utf-8",page());return;}
            if(path.startsWith("/api/sms")){if(!authorized(path))send(out,401,"application/json; charset=utf-8","{\"error\":\"unauthorized\"}");else send(out,200,"application/json; charset=utf-8",SmsRepository.toJson(context.getContentResolver(),100));return;}
            if("/api/status".equals(path)){send(out,200,"application/json; charset=utf-8",statusJson());return;}
            send(out,404,"text/plain; charset=utf-8","404");
        }catch(Exception ignored){}
    }
    private boolean authorized(String path){String q=path.contains("?")?path.substring(path.indexOf('?')+1):"";for(String x:q.split("&")){String[] kv=x.split("=",2);if(kv.length==2&&"token".equals(kv[0]))try{return Prefs.getToken(context).equals(URLDecoder.decode(kv[1],"UTF-8"));}catch(Exception ignored){}}return false;}
    private String statusJson(){String ip=NetworkUtils.getLocalIpAddress();return "{\"running\":"+running+",\"ip\":\""+json(ip)+"\",\"port\":"+PORT+",\"startedAt\":"+startedAt+",\"requestCount\":"+requestCount+",\"lastRequestAt\":"+lastRequestAt+"}";}
    private String page(){
        String token=Prefs.getToken(context),ip=NetworkUtils.getLocalIpAddress();StringBuilder h=new StringBuilder(17000);
        h.append("<!doctype html><html lang='fa' dir='rtl'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Local Device Manager • SMS</title><style>");
        h.append("*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,sans-serif;background:#f4f7fb;color:#172033}body.dark{background:#0e1420;color:#eef3fb}.wrap{max-width:1000px;margin:auto;padding:18px}.top{display:flex;justify-content:space-between;align-items:center;gap:12px}.brand{font-size:23px;font-weight:800}.muted{color:#707b8b}.dark .muted{color:#a8b1c0}.panel{background:#fff;border-radius:20px;padding:20px;margin-top:14px;box-shadow:0 10px 30px rgba(23,32,51,.07)}.dark .panel{background:#151d2b;box-shadow:0 10px 30px rgba(0,0,0,.2)}.btn{border:0;border-radius:12px;padding:11px 14px;font-weight:700;cursor:pointer;background:#3046c7;color:white}.btn.ghost{background:#edf1f7;color:#26324a}.dark .btn.ghost{background:#232d3f;color:#e7edf7}.search{width:100%;border:1px solid #dce2eb;border-radius:13px;padding:13px;font-size:15px;outline:none;background:transparent;color:inherit}.dark .search{border-color:#2b3546}.meta{margin-top:10px;font-size:13px;color:#737e8d}.sms{padding:16px 0;border-bottom:1px solid #e7ebf0}.dark .sms{border-color:#2b3546}.num{font-weight:800}.body{white-space:pre-wrap;line-height:1.8;margin-top:6px}.date{font-size:12px;color:#7a8494;margin-top:8px}.empty{text-align:center;padding:50px 10px;color:#7b8697}.actions{display:flex;gap:9px;flex-wrap:wrap}@media(max-width:650px){.wrap{padding:12px}}");
        h.append("</style></head><body><div class='wrap'><div class='top'><div><div class='brand'>💬 SMS Dashboard</div><div class='muted'>Local Device Manager • "+ip+"</div></div><div class='actions'><button class='btn ghost' onclick='theme()'>◐</button><button class='btn' onclick='load()'>↻ بروزرسانی</button></div></div>");
        h.append("<div class='panel'><input id='q' class='search' placeholder='جستجو در شماره یا متن پیام…'><div id='meta' class='meta'>در حال دریافت…</div></div><div id='list' class='panel'></div></div><script>");
        h.append("const T='"+jsQuote(token)+"';let all=[];const $=id=>document.getElementById(id);function api(){return fetch('/api/sms?token='+encodeURIComponent(T),{cache:'no-store'}).then(r=>r.json())}function render(){const q=$('q').value.trim().toLowerCase();const a=all.filter(x=>String(x.address||'').toLowerCase().includes(q)||String(x.body||'').toLowerCase().includes(q));$('meta').textContent=a.length+' پیام';$('list').innerHTML='';if(!a.length){$('list').innerHTML='<div class=empty>پیامی پیدا نشد</div>';return}a.forEach(s=>{const x=document.createElement('div');x.className='sms';x.innerHTML='<div class=num></div><div class=body></div><div class=date></div>';x.querySelector('.num').textContent=s.address||'نامشخص';x.querySelector('.body').textContent=s.body||'';x.querySelector('.date').textContent=new Date(s.date).toLocaleString('fa-IR');$('list').appendChild(x)})}async function load(){try{const d=await api();all=Array.isArray(d)?d:[];render()}catch(e){$('meta').textContent='اتصال برقرار نشد'}}$('q').addEventListener('input',render);function theme(){document.body.classList.toggle('dark');localStorage.ldmDark=document.body.classList.contains('dark')?'1':'0'}if(localStorage.ldmDark==='1')document.body.classList.add('dark');load();setInterval(load,30000);");
        h.append("</script></body></html>");return h.toString();
    }
    private static String jsQuote(String s){return s==null?"":s.replace("\\","\\\\").replace("'","\\'").replace("\r","\\r").replace("\n","\\n");}
    private static String json(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"");}
    private static void send(OutputStream out,int code,String type,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);String phrase=code==200?"OK":code==401?"Unauthorized":"Not Found";String h="HTTP/1.1 "+code+" "+phrase+"\r\nContent-Type: "+type+"\r\nContent-Length: "+b.length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";out.write(h.getBytes(StandardCharsets.UTF_8));out.write(b);out.flush();}
    public synchronized void stop(){running=false;Prefs.setRunning(context,false);try{if(serverSocket!=null)serverSocket.close();}catch(Exception ignored){}serverSocket=null;}
    public boolean isRunning(){return running;}
    public long getStartedAt(){return startedAt;} public long getRequestCount(){return requestCount;} public long getLastRequestAt(){return lastRequestAt;}
}
