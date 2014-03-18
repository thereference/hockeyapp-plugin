package be.reference.jenkinsci.plugin.hockeyapp;

import java.io.File;

public class HockeyAppUploaderMain {
    /**
     * Useful for testing
     */
    public static void main(String[] args) {
        try {
            upload(args);
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace(System.err);
        }
    }

    private static void upload(String[] args) throws Exception {
        HockeyAppUploader uploader = new HockeyAppUploader();
        uploader.setLogger(new HockeyAppUploader.Logger() {
            public void logDebug(String message) {
                System.out.println(message);
            }
        });

        HockeyAppUploader.UploadRequest r = new HockeyAppUploader.UploadRequest();
        r.apiToken = args[0];
        r.buildNotes = args[1];
        File file = new File(args[2]);
        r.file = file;
        r.dsymFile = null;
        r.notifyTeam = true;
        r.lists = args[4];

        uploader.upload(r);
    }
}
