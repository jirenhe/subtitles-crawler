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
def keyWord = "明日边缘";
println("start");

def subtitlesCrawler = new SubtitlesCrawler();
subtitlesCrawler.with {
    debugAble = false
}
subtitlesCrawler.downloadSubtitles(keyWord, "E://temp/");

class SubtitlesCrawler {

    def urlRoot = "http://www.zimuku.net";
    def debugAble = true;


    def downloadSubtitles(String keyWord, String fileDir) {
        def subtitlesInfo = this.matchSubtitlesInfo(keyWord);
        if (subtitlesInfo) {
            subtitlesInfo.forEach({
                item ->
                    new Thread({ download(item.href, item.title, fileDir); }).start();
            });
        }
    }

    def matchSubtitlesInfo(keyWord) {
        def list = [];
        Document document = Jsoup.connect("${urlRoot}/search?q=${keyWord}").get();
        debugAble && (println(document));
        Elements elements = document.getElementsByClass("sublist");
        if (elements) {
            Elements trs = elements.get(0).getElementsByTag("tr");
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
                            println("title : ${title} href : ${href}");
                        }
                    }
                });
            }
        }
        return list;
    }

    def download(String href, String title, String fileDir) {
        Document document = Jsoup.connect("${urlRoot}${href}").get();
        debugAble && println(document);
        Elements elements = document.getElementsByClass("dlsub");
        if (elements) {
            Element downloadATag = elements.get(0).getElementById("down1");
            String downloadUrl = downloadATag.attr("href");
            this.download("${urlRoot}${downloadUrl}", fileDir);
            try {
                HttpClient client = HttpClientBuilder.newInstance().build();
                HttpGet get = new HttpGet("${urlRoot}${downloadUrl}");
                HttpResponse response = client.execute(get);

                HttpEntity entity = response.getEntity();
                InputStream is = entity.getContent();
                Header contentHeader = response.getFirstHeader("Content-Disposition");
                String filename = null;
                if (contentHeader) {
                    HeaderElement[] values = contentHeader.getElements();
                    if (values.length == 1) {
                        NameValuePair param = values[0].getParameterByName("filename");
                        if (param != null) {
                            filename = param.getValue();
                        }
                    }
                }
                def filepath = "${fileDir}${filename}";
                println("${filepath} downloading ... ");
                File file = new File(filepath);
                file.getParentFile().mkdirs();
                FileOutputStream fileOut = new FileOutputStream(file);
                /**
                 * 根据实际运行效果 设置缓冲区大小
                 */
                byte[] buffer = new byte[1024];
                int ch = 0;
                while ((ch = is.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, ch);
                }
                is.close();
                fileOut.flush();
                fileOut.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}