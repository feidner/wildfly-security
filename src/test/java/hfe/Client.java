package hfe;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.nio.file.WatchService;
import java.util.logging.Logger;

public class Client {


    public static void main(String[] args) throws IOException, AuthenticationException {

        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("feidner", "10Hendi!"));
        //provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("feidner", "10Hendi!"));
        CloseableHttpClient httpclient = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
        CloseableHttpResponse response = httpclient.execute(new HttpPost("http://localhost:8080/hfe/secure/init.html"));

        //CloseableHttpResponse response = httpclient.execute(new HttpPost("http://localhost:8080/hfe/secure/init.html"));
        Logger.getLogger("Client").info("" + response.getStatusLine());
        Logger.getLogger("Client").info(EntityUtils.toString(response.getEntity()));
        EntityUtils.consume(response.getEntity());
        response.close();

        WatchService watchService = FileSystems.getDefault().newWatchService();
        Paths.get("test");
    }
}
