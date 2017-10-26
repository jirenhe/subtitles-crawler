import groovy.transform.Field
import org.apache.http.*
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

@Grab(group = 'org.jsoup', module = 'jsoup', version = '1.10.3')
@Grab(group = 'org.apache.httpcomponents', module = 'httpclient', version = '4.5.3')

/**
 * Title
 * Author jirenhe@wanshifu.com
 * Time 2017/10/24.
 * Version v1.0
 */

def rootDir = "E://temp/";
println("start");

@Field boolean debugAble = false;
@Field boolean isNameEqualFile = true;


File file = new File(rootDir);
recursionDir(file);

def recursionDir(File file) {
    File[] files = file.listFiles();
    files.each {
        item ->
            if (item.isDirectory()) {
                recursionDir(item);
            } else {
                new Thread({
                    try {
                        new SubtitlesCrawler.Builder(item).debugAble(debugAble).
                                isNameEqualFile(isNameEqualFile).build().downloadSubtitles();
                    } catch (ignore) {
                    }
                }).start();
            }
    }
}

class SubtitlesCrawler {

    final String urlRoot = "http://www.zimuku.net";
    boolean debugAble = false;
    boolean isNameEqualFile = true;
    String fileDir;
    String actuallyFileName;
    String fullFileName;
    File targetFile;

    private SubtitlesCrawler() {}

    static class Builder {

        final String videoSuffix = "mkv,flv,mp4,rmvb";

        private SubtitlesCrawler subtitlesCrawler;

        Builder(String fileDir, String fullFileName) {
            subtitlesCrawler = new SubtitlesCrawler();
            subtitlesCrawler.fileDir = fileDir;
            subtitlesCrawler.fullFileName = fullFileName;
        }

        Builder(File targetFile) {
            subtitlesCrawler = new SubtitlesCrawler();
            subtitlesCrawler.targetFile = targetFile;
        }

        Builder debugAble(boolean debugAble) {
            subtitlesCrawler.debugAble = debugAble;
            return this;
        }

        Builder isNameEqualFile(boolean isNameEqualFile) {
            subtitlesCrawler.isNameEqualFile = isNameEqualFile;
            return this;
        }

        SubtitlesCrawler build() {
            if (subtitlesCrawler.targetFile) {
                if (!subtitlesCrawler.targetFile.exists()) throw new FileNotFoundException();
                subtitlesCrawler.fullFileName = subtitlesCrawler.targetFile.getName();
                subtitlesCrawler.fileDir = subtitlesCrawler.targetFile.getParentFile().getAbsolutePath() + "\\";
            } else {
                subtitlesCrawler.targetFile = new File(subtitlesCrawler.fileDir + subtitlesCrawler.fullFileName);
                if (!subtitlesCrawler.targetFile.exists()) throw new FileNotFoundException();
            }
            int lastIndexOf = subtitlesCrawler.fullFileName.lastIndexOf(".");
            assert lastIndexOf != -1;
            subtitlesCrawler.actuallyFileName = subtitlesCrawler.fullFileName.substring(0, lastIndexOf);
            String suffix = subtitlesCrawler.fullFileName.substring(lastIndexOf + 1, subtitlesCrawler.fullFileName.length());
            assert videoSuffix.contains(suffix);
            return subtitlesCrawler;
        }
    }

    def downloadSubtitles() {
        println("start match subtitles for ${fileDir}${actuallyFileName}");
        def subtitlesInfo = matchSubtitlesInfo();
        if (subtitlesInfo) {
            for (int i = 0; i < subtitlesInfo.size(); i++) {
                final def item = subtitlesInfo.get(i);
                final def index = i;
                new Thread({
                    download(item.href, index);
                }).start();
            }
        }
    }

    private def matchSubtitlesInfo() {
        def list = [];
        Document document = Jsoup.connect("${urlRoot}/search?q=${actuallyFileName}").get();
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
        return list;
    }

    private def download(String href, final int index) {
        Document document = Jsoup.connect("${urlRoot}${href}").get();
        debugAble && println(document);
        Elements elements = document.getElementsByClass("dlsub");
        if (elements) {
            Element downloadATag = elements.get(0).getElementById("down1");
            String downloadUrl = downloadATag.attr("href");
            downloadFile("${urlRoot}${downloadUrl}", index);
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

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            is && (is.close());
            fileOut && (fileOut.close());
            client && (client.close());
        }
    }

    private def getDownLoadFileName(HttpResponse response, final int index) {
        Header contentHeader = response.getFirstHeader("Content-Disposition");
        String singleFileName = null;
        if (contentHeader) {
            HeaderElement[] values = contentHeader.getElements();
            if (values.length == 1) {
                NameValuePair param = values[0].getParameterByName("filename");
                if (param != null) {
                    String remoteFileName = param.getValue();
                    singleFileName = remoteFileName;
                    if (isNameEqualFile) {
                        def indexOf = singleFileName.lastIndexOf(".");
                        if (singleFileName.contains(".")) {
                            def indexName = "";
                            if (index) {
                                indexName = "(${index})";
                            }
                            String suffix = singleFileName.substring(indexOf, singleFileName.length());
                            singleFileName = this.actuallyFileName + indexName + suffix;
                        }
                    }
                    println("index : ${index} remote file is : ${remoteFileName} actuallyFileName is : ${singleFileName}")
                }
            }
        }
        return singleFileName;
    }
}