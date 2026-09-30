package com.localsms.server;

import android.content.Context;
import android.content.Intent;

import com.localsms.service.LocalSmsService;
import com.localsms.sms.SmsRepository;
import com.localsms.util.DeviceInfo;
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

/** LAN-only management panel. It remains alive while the main SMS dashboard is stopped. */
public final class RemoteControlServer {
    public static final int PORT = 8081;
    private final Context context;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public RemoteControlServer(Context context) { this.context = context.getApplicationContext(); }

    public synchronized boolean start() {
        if (running) return true;
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;
            acceptThread = new Thread(this::acceptLoop, "LDM-Remote");
            acceptThread.start();
            return true;
        } catch (IOException e) { running = false; return false; }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                new Thread(() -> handle(socket), "LDM-RemoteClient").start();
            } catch (IOException e) { if (running) break; }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = s.getOutputStream();
            String request = in.readLine();
            String line; while ((line = in.readLine()) != null && !line.isEmpty()) {}
            if (request == null) return;
            String[] parts = request.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String path = parts.length > 1 ? parts[1] : "/";
            if (!"GET".equals(method)) { send(out,405,"application/json; charset=utf-8","{\"error\":\"method_not_allowed\"}"); return; }
            if (!authorized(path)) { send(out,401,"application/json; charset=utf-8","{\"error\":\"unauthorized\"}"); return; }
            String route = path.contains("?") ? path.substring(0,path.indexOf('?')) : path;
            switch (route) {
                case "/": send(out,200,"text/html; charset=utf-8",page()); break;
                case "/api/status": send(out,200,"application/json; charset=utf-8",statusJson()); break;
                case "/api/device": send(out,200,"application/json; charset=utf-8",deviceJson()); break;
                case "/api/start": startMainServer(); send(out,200,"application/json; charset=utf-8",statusJson()); break;
                case "/api/stop": stopMainServer(); send(out,200,"application/json; charset=utf-8",statusJson()); break;
                case "/api/sms/latest": send(out,200,"application/json; charset=utf-8",SmsRepository.toJson(context.getContentResolver(),1)); break;
                case "/api/sms": send(out,200,"application/json; charset=utf-8",SmsRepository.toJson(context.getContentResolver(),100)); break;
                default: send(out,404,"application/json; charset=utf-8","{\"error\":\"not_found\"}");
            }
        } catch (Exception ignored) {}
    }

    private void startMainServer() { context.startService(new Intent(context, LocalSmsService.class).setAction(LocalSmsService.ACTION_REMOTE_START)); }
    private void stopMainServer() { context.startService(new Intent(context, LocalSmsService.class).setAction(LocalSmsService.ACTION_REMOTE_STOP)); }

    private boolean authorized(String path) {
        String q = path.contains("?") ? path.substring(path.indexOf('?')+1) : "";
        for (String x : q.split("&")) {
            String[] kv=x.split("=",2);
            if(kv.length==2 && "token".equals(kv[0])) try { return Prefs.getToken(context).equals(URLDecoder.decode(kv[1],"UTF-8")); } catch(Exception ignored) {}
        }
        return false;
    }

    private String statusJson() {
        String ip=NetworkUtils.getLocalIpAddress();
        return "{\"remoteRunning\":true,\"serverRunning\":"+Prefs.running(context)+",\"ip\":\""+json(ip)+"\",\"remotePort\":"+PORT+",\"serverPort\":"+LocalServer.PORT+"}";
    }

    private String deviceJson() {
        return "{\"model\":\""+json(DeviceInfo.model())+"\",\"android\":\""+json(DeviceInfo.androidVersion())+"\",\"battery\":"+DeviceInfo.battery(context)+",\"storage\":"+DeviceInfo.storage(context)+",\"memory\":"+DeviceInfo.memory(context)+"}";
    }

    private String page() {
        String token=Prefs.getToken(context); String ip=NetworkUtils.getLocalIpAddress();
        StringBuilder h=new StringBuilder(22000);
        h.append("<!doctype html><html lang='fa' dir='rtl'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Local Device Manager</title>");
        h.append("<style>");
        h.append("")
          .append("*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,sans-serif;background:#f4f7fb;color:#172033}body.dark{background:#0e1420;color:#eef3fb}.app{max-width:1180px;margin:auto;padding:18px}.top{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:16px}.brand{font-size:22px;font-weight:800}.muted{color:#6d7787}.dark .muted{color:#a8b1c0}.pill{padding:7px 11px;border-radius:999px;background:#e8f7ee;color:#13763c;font-weight:700;font-size:13px}.dark .pill{background:#153724;color:#76e0a0}.layout{display:grid;grid-template-columns:220px 1fr;gap:16px}.side,.panel,.card{background:#fff;border-radius:20px;box-shadow:0 10px 30px rgba(23,32,51,.07)}.dark .side,.dark .panel,.dark .card{background:#151d2b;box-shadow:0 10px 30px rgba(0,0,0,.2)}.side{padding:12px;height:max-content;position:sticky;top:12px}.nav{width:100%;border:0;background:transparent;text-align:right;padding:12px 13px;border-radius:12px;font-size:14px;color:inherit;cursor:pointer}.nav.active{background:#edf1ff;color:#2d42b8;font-weight:800}.dark .nav.active{background:#202b49;color:#aab8ff}.main{min-width:0}.hero{padding:22px}.hero h1{margin:0 0 7px;font-size:25px}.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:14px}.stat{padding:17px}.label{font-size:12px;color:#727d8d}.value{font-size:22px;font-weight:800;margin-top:5px}.section{display:none}.section.active{display:block}.card{padding:18px;margin-top:14px}.actions{display:flex;flex-wrap:wrap;gap:9px}.btn{border:0;border-radius:12px;padding:12px 15px;font-weight:700;cursor:pointer;background:#3046c7;color:white}.btn.stop{background:#c23b4b}.btn.ghost{background:#edf1f7;color:#253047}.dark .btn.ghost{background:#232d3f;color:#e7edf7}.sms{border-top:1px solid #e6eaf0;padding:14px 0}.dark .sms{border-color:#2b3546}.num{font-weight:800}.body{white-space:pre-wrap;line-height:1.75;margin-top:5px}.date{font-size:12px;color:#7a8494;margin-top:8px}.row{display:flex;justify-content:space-between;gap:10px;padding:10px 0;border-bottom:1px solid #e6eaf0}.dark .row{border-color:#2b3546}.bar{height:9px;background:#e9edf4;border-radius:9px;overflow:hidden;margin-top:8px}.fill{height:100%;background:#4b61d1;border-radius:9px}.tools{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.tool{padding:15px;border:1px solid #e3e8f0;border-radius:14px}.dark .tool{border-color:#2b3546}.mono{font-family:ui-monospace,monospace;font-size:12px;word-break:break-all}@media(max-width:800px){.layout{grid-template-columns:1fr}.side{position:static;display:flex;overflow:auto;gap:5px}.nav{white-space:nowrap}.grid{grid-template-columns:repeat(2,1fr)}.tools{grid-template-columns:1fr}.app{padding:12px}}");
        h.append("</style></head><body><div class='app'>");
        h.append("<div class='top'><div><div class='brand'>📱 Local Device Manager</div><div class='muted'>کنترل و پایش دستگاه در شبکه محلی</div></div><div><span id='online' class='pill'>● Online</span> <button class='btn ghost' onclick='theme()'>◐</button></div></div>");
        h.append("<div class='layout'><aside class='side'>");
        h.append("<button class='nav active' data-tab='dashboard'>🏠 داشبورد</button><button class='nav' data-tab='sms'>💬 پیامک‌ها</button><button class='nav' data-tab='device'>📱 دستگاه</button><button class='nav' data-tab='network'>📶 شبکه</button><button class='nav' data-tab='tools'>🛠 ابزارها</button>");
        h.append("</aside><main class='main'>");
        h.append("<section id='dashboard' class='section active'><div class='panel hero'><h1 id='model'>دستگاه</h1><div class='muted' id='android'>در حال دریافت اطلاعات…</div><div class='grid'><div class='card stat'><div class='label'>باتری</div><div class='value' id='battery'>—</div></div><div class='card stat'><div class='label'>حافظه</div><div class='value' id='storage'>—</div></div><div class='card stat'><div class='label'>RAM</div><div class='value' id='memory'>—</div></div><div class='card stat'><div class='label'>سرور</div><div class='value' id='server'>—</div></div></div></div>");
        h.append("<div class='card'><h3>کنترل سریع</h3><div class='actions'><button class='btn' onclick='cmd("start")'>▶ فعال‌سازی داشبورد</button><button class='btn stop' onclick='cmd("stop")'>■ توقف داشبورد</button><button class='btn ghost' onclick='loadAll()'>↻ بروزرسانی</button></div></div>");
        h.append("<div class='card'><h3>آخرین فعالیت</h3><div id='latest'>در حال دریافت…</div></div></section>");
        h.append("<section id='sms' class='section'><div class='panel hero'><h1>💬 پیامک‌ها</h1><div class='muted'>آخرین پیام‌های دریافتی</div><div class='actions' style='margin-top:12px'><button class='btn' onclick='loadSms()'>↻ بروزرسانی</button></div><div id='smsList'></div></div></section>");
        h.append("<section id='device' class='section'><div class='panel hero'><h1>📱 اطلاعات دستگاه</h1><div id='deviceRows'></div></div></section>");
        h.append("<section id='network' class='section'><div class='panel hero'><h1>📶 شبکه</h1><div class='row'><span>IP محلی</span><b class='mono'>"+ip+"</b></div><div class='row'><span>Remote</span><b>8081</b></div><div class='row'><span>Dashboard</span><b>8080</b></div><div class='row'><span>محدوده</span><b>LAN</b></div></div></section>");
        h.append("<section id='tools' class='section'><div class='panel hero'><h1>🛠 ابزارها</h1><div class='tools'><div class='tool'><b>سرویس Remote</b><div class='muted'>پورت 8081 و کنترل مستقل</div></div><div class='tool'><b>داشبورد SMS</b><div class='muted'>پورت 8080 و مشاهده پیام‌ها</div></div><div class='tool'><b>Token</b><div class='mono'>"+token+"</div></div><div class='tool'><b>امنیت</b><div class='muted'>APIهای مشخص و بدون shell آزاد</div></div></div></div></section>");
        h.append("</main></div></div><script>");
        h.append("const T='"+jsQuote(token)+"';const $=id=>document.getElementById(id);function api(p){return fetch(p+(p.includes('?')?'&':'?')+'token='+encodeURIComponent(T),{cache:'no-store'}).then(r=>{if(!r.ok)throw new Error(r.status);return r.json()})}");
        h.append("document.querySelectorAll('.nav').forEach(b=>b.onclick=()=>{document.querySelectorAll('.nav').forEach(x=>x.classList.remove('active'));document.querySelectorAll('.section').forEach(x=>x.classList.remove('active'));b.classList.add('active');$(b.dataset.tab).classList.add('active');if(b.dataset.tab==='sms')loadSms();if(b.dataset.tab==='device')loadDevice()});");
        h.append("function theme(){document.body.classList.toggle('dark');localStorage.ldmDark=document.body.classList.contains('dark')?'1':'0'}if(localStorage.ldmDark==='1')document.body.classList.add('dark');");
        h.append("async function loadStatus(){try{const s=await api('/api/status');$('server').textContent=s.serverRunning?'Online':'Stopped';$('online').textContent='● Online'}catch(e){$('online').textContent='● Offline';$('server').textContent='—'}}");
        h.append("function human(n){if(!n)return '0 B';const u=['B','KB','MB','GB','TB'];let i=0,x=n;while(x>=1024&&i<u.length-1){x/=1024;i++}return x.toFixed(i?1:0)+' '+u[i]}");
        h.append("async function loadDevice(){try{const d=await api('/api/device');$('model').textContent=d.model;$('android').textContent=d.android;$('battery').textContent=d.battery+'%';const su=d.storage.used,st=d.storage.total;$('storage').textContent=human(su);$('memory').textContent=human(d.memory.available)+' free';$('deviceRows').innerHTML='<div class=row><span>مدل</span><b>'+d.model+'</b></div><div class=row><span>سیستم‌عامل</span><b>'+d.android+'</b></div><div class=row><span>باتری</span><b>'+d.battery+'%</b></div><div class=row><span>حافظه استفاده‌شده</span><b>'+human(su)+' / '+human(st)+'</b></div><div class=row><span>RAM در دسترس</span><b>'+human(d.memory.available)+' / '+human(d.memory.total)+'</b></div>'}catch(e){$('model').textContent='دریافت اطلاعات ناموفق بود'}}");
        h.append("async function loadSms(){try{const d=await api('/api/sms');const box=$('smsList');box.innerHTML='';if(!Array.isArray(d)||!d.length){box.textContent='پیامی وجود ندارد';return}d.forEach(s=>{const x=document.createElement('div');x.className='sms';x.innerHTML='<div class=num></div><div class=body></div><div class=date></div>';x.querySelector('.num').textContent=s.address||'نامشخص';x.querySelector('.body').textContent=s.body||'';x.querySelector('.date').textContent=new Date(s.date).toLocaleString('fa-IR');box.appendChild(x)})}catch(e){$('smsList').textContent='خطا در دریافت پیام‌ها'}}");
        h.append("async function loadLatest(){try{const d=await api('/api/sms/latest');const s=d&&d[0];$('latest').textContent=s?(s.address+' — '+s.body):'پیامی وجود ندارد'}catch(e){$('latest').textContent='خطا'}}");
        h.append("async function cmd(x){try{await api('/api/'+x);loadStatus()}catch(e){alert('عملیات انجام نشد')}}async function loadAll(){await Promise.all([loadStatus(),loadDevice(),loadLatest()])}loadAll();setInterval(loadAll,10000);");
        h.append("</script></body></html>");
        return h.toString();
    }

    private static String jsQuote(String s){return s==null?"":s.replace("\\","\\\\").replace("'","\\'").replace("\r","\\r").replace("\n","\\n");}
    private static String json(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"");}
    private static void send(OutputStream out,int code,String type,String body)throws IOException{
        byte[] data=body.getBytes(StandardCharsets.UTF_8);String phrase=code==200?"OK":code==401?"Unauthorized":code==404?"Not Found":"Method Not Allowed";
        String head="HTTP/1.1 "+code+" "+phrase+"\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));out.write(data);out.flush();
    }
    public synchronized void stop(){running=false;try{if(serverSocket!=null)serverSocket.close();}catch(Exception ignored){}serverSocket=null;}
    public boolean isRunning(){return running;}
}
