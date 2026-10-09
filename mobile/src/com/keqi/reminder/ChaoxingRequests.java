package com.keqi.reminder;

import java.io.IOException;

/** Native transport with one bounded browser continuation for protected GET pages. */
public final class ChaoxingRequests {
    public interface Reader {Http.Page read(String url,ChaoxingReadPolicy.Stage stage) throws Exception;}
    public static Http.Page get(String url,ChaoxingReadPolicy.Stage stage,Reader nativeReader,Reader browserReader) throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        if(!ChaoxingReadPolicy.allowed(url))throw new IOException("平台返回了不支持的读取页面");
        try{
            Http.Page page=nativeReader.read(url,stage);
            if(!Http.trusted(page.url,"chaoxing"))throw new IOException("平台返回了不支持的读取页面");
            if(ChaoxingReadPolicy.ready(page.url,page.html,stage))return page;
        }catch(Http.BrowserRequired e){/* Continue in the logged-in browser; never count 202 as data. */}
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        Http.Page page=browserReader.read(url,stage);
        if(page==null||!ChaoxingReadPolicy.ready(page.url,page.html,stage))throw new ChaoxingReadPolicy.InteractionRequired();
        return page;
    }
}
