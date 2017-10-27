import groovy.transform.Field
import org.apache.http.*
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

@Grab(group = 'org.jsoup', module = 'jsoup', version = '1.10.3')
@Grab(group = 'org.apache.httpcomponents', module = 'httpclient', version = '4.5.3')

/**
 * Title
 * Author jirenhe@wanshifu.com
 * Time 2017/10/24.
 * Version v1.0
 */

def rootDir = "\\\\192.168.31.1\\XiaoMi-usb0\\下载\\";
println("start");

@Field boolean debugAble = false;
@Field ExecutorService executor = Executors.newCachedThreadPool();
@Field final Semaphore semaphore = new Semaphore(3);
@Field final String videoSuffix = "avi,rmvb,rm,asf,divx,mpg,mpeg,mpe,wmv,mp4,mkv,vob";
@Field final String[] exclusionWords = [".DOCU", ".EXTENDED", ".com"];

File file = new File(rootDir);
recursionDir(file);

def recursionDir(File file) {
    File[] files = file.listFiles();
    files.each {
        item ->
            if (item.isDirectory()) {
                recursionDir(item);
            } else {
                def sourceFileName = item.getName();
                if (isVideoFile(sourceFileName)) {
                    def final fileDir = item.getParentFile().getAbsolutePath() + "\\";
                    def final keyWord = analysisKeyWord(sourceFileName);
                    executor.execute({
                        semaphore.acquire();
                        SubtitlesCrawler crawler = new SubtitlesCrawler(debugAble, fileDir, keyWord);
                        try {
                            crawler?.downloadSubtitles();
                        } catch (any) {
                            any.printStackTrace();
                        } finally {
                            semaphore.release();
                        }
                    });
                }
            }
    }
}

def isVideoFile(String sourceName) {
    int lastIndexOf = sourceName.lastIndexOf(".");
    return lastIndexOf > 0 && videoSuffix.contains(sourceName.substring(sourceName.lastIndexOf(".") + 1, sourceName.length()));
}

def analysisKeyWord(String sourceName) {
    int lastIndexOf = sourceName.lastIndexOf(".");
    assert lastIndexOf > 0;
    String actuallyFileName = sourceName.substring(0, lastIndexOf);
    int index = -1;
    if ((index = actuallyFileName.indexOf(".1080p")) > 0) {
        actuallyFileName = actuallyFileName.substring(0, index);
    }
    if ((index = actuallyFileName.indexOf(".720p")) > 0) {
        actuallyFileName = actuallyFileName.substring(0, index);
    }
    for (String exclusionWord : exclusionWords) {
        actuallyFileName = actuallyFileName.replaceAll("(?i)" + exclusionWord, "");
    }
    return actuallyFileName;
}

class SubtitlesCrawler {

    final String urlRoot = "http://www.zimuku.net";
    boolean debugAble = false;
    final String fileDir;
    final String keyWord;

    SubtitlesCrawler(boolean debugAble, String fileDir, String keyWord) {
        this.debugAble = debugAble
        this.fileDir = fileDir
        this.keyWord = keyWord
    }

    def downloadSubtitles() {
        debugAble && println("start match subtitles for ${fileDir}${keyWord}");
        def subtitlesInfo = matchSubtitlesInfo();
        if (subtitlesInfo) {
            for (int i = 0; i < subtitlesInfo.size(); i++) {
                final def item = subtitlesInfo.get(i);
                final def index = i;
                try {
                    download(item.href, index);
                } catch (any) {
                    println("down load from ${item.href} fail! error:${any.getMessage()}");
                }
            }
        } else {
            println("${fileDir} subtitles not found");
        }
    }

    private def matchSubtitlesInfo() {
        def list = [];
        String url = "${urlRoot}/search?q=${keyWord}";
        try {
            Document document = Jsoup.connect("${urlRoot}/search?q=${keyWord}").get();
            debugAble && (println(document));
            Elements elements = document.getElementsByClass("sublist");
            if (elements) {
                elements.forEach({
                    item ->
                        Elements trs = item.getElementsByTag("tr");
                        if (trs) {
                            Elements temp;
                            Element a;
                            trs.forEach({
                                temp = it.getElementsByClass("rating-star");
                                if (temp && temp.get(0).hasClass("allstar50")) {
                                    temp = it.getElementsByClass("first");
                                    if (temp) {
                                        a = temp.get(0).getElementsByTag("a").get(0);
                                        String href = a.attr("href");
                                        String title = a.attr("title");
                                        list.add(["href": href, "title": title]);
                                    }
                                }
                            });
                        }
                });
            }
        } catch (any) {
            println("${fileDir}${keyWord} match subtitles fail! exception:${any.getMessage()} url:${url}");
        }
        return list;
    }

    private def download(String href, final int index) {
        String url = "${urlRoot}${href}";
        try {
            Document document = Jsoup.connect(url).get();
            debugAble && println(document);
            Elements elements = document.getElementsByClass("dlsub");
            if (elements) {
                Element downloadATag = elements.get(0).getElementById("down1");
                String downloadUrl = downloadATag.attr("href");
                downloadFile("${urlRoot}${downloadUrl}", index);
            }
        } catch (any) {
            throw new Exception("open url fail ! ${url} ${any.getMessage()}");
        }
    }

    private def downloadFile(String url, final int index) {
        InputStream is;
        HttpClient client;
        FileOutputStream fileOut;
        try {
            client = HttpClientBuilder.create().build();
            HttpGet get = new HttpGet(url);
            HttpResponse response = client.execute(get);
            HttpEntity entity = response.getEntity();
            is = entity.getContent();
            String fileName = this.getDownLoadFileName(response, index);
            def filepath = "${fileDir}${fileName}";
            File file = new File(filepath);
            if (file.exists()) {
                println("${filepath} is already exist, so skip it");
                return;
            }
            println("${filepath} downloading ... ");
            fileOut = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int ch = 0;
            while ((ch = is.read(buffer)) != -1) {
                fileOut.write(buffer, 0, ch);
            }
            fileOut.flush();
            println("${filepath} downloading finished! ");
        } catch (Exception e) {
            throw new Exception("url : ${url}, ${e.getMessage()}");
        } finally {
            is && (is.close());
            fileOut && (fileOut.close());
            client && (client.close());
        }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private def getDownLoadFileName(HttpResponse response, final int index) {
        Header contentHeader = response.getFirstHeader("Content-Disposition");
        String remoteFileName = null;
        if (contentHeader) {
            HeaderElement[] values = contentHeader.getElements();
            if (values.length == 1) {
                NameValuePair param = values[0].getParameterByName("filename");
                if (param != null) {
                    remoteFileName = param.getValue();
                }
            }
        }
        return remoteFileName;
    }
}