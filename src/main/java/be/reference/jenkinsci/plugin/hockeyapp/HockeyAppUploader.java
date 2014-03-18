package be.reference.jenkinsci.plugin.hockeyapp;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectHandler;
import org.json.simple.parser.JSONParser;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * A HockeyApp uploader
 */
public class HockeyAppUploader implements Serializable {
    static interface Logger {
        void logDebug(String message);
    }

    static class UploadRequest implements Serializable {
        String filePaths;
        String dsymPath;
        String apiToken;
        Integer releaseType;
        Boolean privateBuild;
        Boolean notifyTeam;
        String buildNotes;
        Boolean notesInMarkdown;
        File file;
        File dsymFile;
        String lists;
        String proxyHost;
        String proxyUser;
        String proxyPass;
        int proxyPort;
        Boolean debug;

        public String toString() {
            return new ToStringBuilder(this)
                    .append("ipaPaths", filePaths)
                    .append("dsymPath", dsymPath)
                    .append("releaseType", releaseType)
                    .append("privateBuild", privateBuild)
                    .append("apiToken", "********")
                    .append("notifyTeam", notifyTeam)
                    .append("buildNotes", buildNotes)
                    .append("notes_type", notesInMarkdown)
                    .append("ipa", file)
                    .append("dsym", dsymFile)
                    .append("tags", lists)
                    .append("proxyHost", proxyHost)
                    .append("proxyUser", proxyUser)
                    .append("proxyPass", "********")
                    .append("proxyPort", proxyPort)
                    .append("debug", debug)
                    .toString();
        }

        static UploadRequest copy(UploadRequest r) {
            UploadRequest r2 = new UploadRequest();
            r2.filePaths = r.filePaths;
            r2.dsymPath = r.dsymPath;
            r2.releaseType = r.releaseType;
            r2.privateBuild = r.privateBuild;
            r2.apiToken = r.apiToken;
            r2.notifyTeam = r.notifyTeam;
            r2.buildNotes = r.buildNotes;
            r2.notesInMarkdown = r.notesInMarkdown;
            r2.file = r.file;
            r2.dsymFile = r.dsymFile;
            r2.lists = r.lists;
            r2.proxyHost = r.proxyHost;
            r2.proxyUser = r.proxyUser;
            r2.proxyPort = r.proxyPort;
            r2.proxyPass = r.proxyPass;
            r2.debug = r.debug;

            return r2;
        }
    }

    private Logger logger = null;

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public Map upload(UploadRequest ur) throws IOException, org.json.simple.parser.ParseException {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        
        httpClient.setRedirectHandler(new DefaultRedirectHandler() {                
            @Override
            public boolean isRedirectRequested(HttpResponse response, HttpContext context) {
                boolean isRedirect = super.isRedirectRequested(response, context);
                if (!isRedirect) {
                    int responseCode = response.getStatusLine().getStatusCode();
                    if (responseCode == 301 || responseCode == 302) {
                        return true;
                    }
                }
                return isRedirect;
            }
        });
        
        
        // Configure the proxy if necessary
        if (ur.proxyHost != null && !ur.proxyHost.isEmpty() && ur.proxyPort > 0) {
            Credentials cred = null;
            if (ur.proxyUser != null && !ur.proxyUser.isEmpty())
                cred = new UsernamePasswordCredentials(ur.proxyUser, ur.proxyPass);

            httpClient.getCredentialsProvider().setCredentials(new AuthScope(ur.proxyHost, ur.proxyPort), cred);
            HttpHost proxy = new HttpHost(ur.proxyHost, ur.proxyPort);
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        HttpHost targetHost = new HttpHost("rink.hockeyapp.net");
        HttpPost httpPost = new HttpPost("/api/2/apps/upload");
        FileBody fileBody = new FileBody(ur.file);

        httpPost.setHeader("X-HockeyAppToken", ur.apiToken);
        
        MultipartEntity entity = new MultipartEntity();

        entity.addPart("release_type", new StringBody(ur.releaseType.toString()));        
        entity.addPart("notes", new StringBody(ur.buildNotes, "text/plain", Charset.forName("UTF-8")));
        entity.addPart("notes_type", new StringBody(ur.notesInMarkdown ? "1" : "0"));
        entity.addPart("ipa", fileBody);

        entity.addPart("private", new StringBody(ur.privateBuild ? "1" : "0"));
        
        if (ur.dsymFile != null) {
            FileBody dsymFileBody = new FileBody(ur.dsymFile);
            entity.addPart("dsym", dsymFileBody);
        }

        if (ur.lists.length() > 0)
            entity.addPart("tags", new StringBody(ur.lists));
        entity.addPart("notify", new StringBody(ur.notifyTeam ? "1" : "0"));
        
        httpPost.setEntity(entity);

        logDebug("POST Request: " + ur);

        HttpResponse response = httpClient.execute(targetHost, httpPost);
        HttpEntity resEntity = response.getEntity();

        InputStream is = resEntity.getContent();

        // Improved error handling.
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            String responseBody = new Scanner(is).useDelimiter("\\A").next();
            throw new UploadException(statusCode, responseBody, response);
        }

        StringWriter writer = new StringWriter();
        IOUtils.copy(is, writer, "UTF-8");
        String json = writer.toString();

        logDebug("POST Answer: " + json);

        JSONParser parser = new JSONParser();

        return (Map) parser.parse(json);
    }

    private void logDebug(String message) {
        if (logger != null) {
            logger.logDebug(message);
        }
    }
}
