/*
 * Copyright 2016 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import org.matrix.androidsdk.util.Log;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;

import org.matrix.androidsdk.MXSession;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.Matrix;
import im.vector.activity.CommonActivityUtils;
import im.vector.db.VectorContentProvider;
import im.vector.gcm.GcmRegistrationManager;

import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Pusher;

/**
 * BugReporter creates and sends the bug reports.
 */
public class BugReporter {

    private static final String LOG_TAG = "BugReporter";

    /**
     * @return the bug report body message.
     */
    private static String buildBugReportMessage(Context context, String bugDescription) {
        String message = "Something went wrong on my Vector client : \n";
        message += String.format("%s\n\n\n", bugDescription);


        message += "\n";
        message += "----------------------- Device information -------------------------------------\n\n";
        message += "Phone : " + Build.MODEL.trim() + " (" + Build.VERSION.INCREMENTAL + " " + Build.VERSION.RELEASE + " " + Build.VERSION.CODENAME + ")\n";
        message += "Vector version: " + Matrix.getInstance(context).getVersion(true) + "\n";
        message += "SDK version:  " + Matrix.getInstance(context).getDefaultSession().getVersion(true) + "\n";
        message += "Olm version:  " + Matrix.getInstance(context).getDefaultSession().getCryptoVersion(context, true) + "\n";
        message += "\n";
        message += "------------------------- Memory statuses -------------------------------------\n";
        message += "\n";

        long freeSize = 0L;
        long totalSize = 0L;
        long usedSize = -1L;
        try {
            Runtime info = Runtime.getRuntime();
            freeSize = info.freeMemory();
            totalSize = info.totalMemory();
            usedSize = totalSize - freeSize;
        } catch (Exception e) {
            e.printStackTrace();
        }

        message += "usedSize   " + (usedSize / 1048576L) + " MB\n";
        message += "freeSize   " + (freeSize / 1048576L) + " MB\n";
        message += "totalSize   " + (totalSize / 1048576L) + " MB\n";

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) VectorApp.getCurrentActivity().getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);

        message += "availMem   " + (mi.availMem / 1048576L) + " MB\n";
        message += "totalMem   " + (mi.totalMem / 1048576L) + " MB\n";
        message += "threshold  " + (mi.threshold / 1048576L) + " MB\n";
        message += "lowMemory  " + mi.lowMemory + "\n";

        message += "\n";
        message += "---------------------- Application stats -------------------------------------\n";
        message += "\n";
        message += VectorApp.getGAStats();
        message += "\n";
        message += "---------------------- Sessions information ----------------------------------\n";


        Collection<MXSession> sessions = Matrix.getMXSessions(context);
        int profileIndex = 1;

        for(MXSession session : sessions) {
            message += "\n";

            message += "--> Profile " + profileIndex + " :\n\n";
            profileIndex++;

            message += "----> General\n\n";

            MyUser mMyUser = session.getMyUser();
            message += "userId : "+ mMyUser.user_id + "\n";
            message += "displayname : " + mMyUser.displayname + "\n";
            message += "homeServer :" + session.getCredentials().homeServer + "\n";

            if (null != session.getCrypto()) {
                message += "\n----> Crypto\n\n";

                MXDeviceInfo myDevice = session.getCrypto().getMyDevice();
                message += "Device ID : " + myDevice.deviceId + "\n";
                message += "Device key : " + myDevice.fingerprint() + "\n";
            }

            GcmRegistrationManager registrationManager = Matrix.getInstance(context).getSharedGCMRegistrationManager();
            List<Pusher> pushers = new ArrayList<>(registrationManager.mPushersList);

            message += "\n----> Notification targets\n\n";

            if (pushers.size() == 0) {
                message += "No target\n";
            } else {
                for (Pusher pusher : pushers) {
                    message += " - " + pusher.toString() + "\n";
                }
            }
        }

        message += "\n---------------------------------------------------------------------\n";

        return message;
    }

    /**
     * Build the bug report zip file
     * @param context the application context
     * @param withScreenshot true to include the screenshort
     * @return the zip file
     */
    private static File buildBugZipFile(Context context, boolean withScreenshot, String bugDescription) {
        Bitmap screenShot = takeScreenshot();

        if (null != screenShot) {
            try {
                ArrayList<File> logFiles = new ArrayList<>();

                if (withScreenshot) {
                    File screenFile = new File(VectorApp.mLogsDirectoryFile, "screenshot.jpg");

                    if (screenFile.exists()) {
                        screenFile.delete();
                    }

                    FileOutputStream screenOutputStream = new FileOutputStream(screenFile);
                    screenShot.compress(Bitmap.CompressFormat.PNG, 50, screenOutputStream);
                    screenOutputStream.close();
                    logFiles.add(screenFile);
                }

                {
                    File configLogFile = new File(VectorApp.mLogsDirectoryFile, "config.txt");
                    ByteArrayOutputStream configOutputStream = new ByteArrayOutputStream();
                    configOutputStream.write(buildBugReportMessage(context, bugDescription).getBytes());

                    if (configLogFile.exists()) {
                        configLogFile.delete();
                    }

                    FileOutputStream fos = new FileOutputStream(configLogFile);
                    configOutputStream.writeTo(fos);
                    configOutputStream.flush();
                    configOutputStream.close();

                    logFiles.add(configLogFile);
                }

                {
                    String errorCatLog = getLogCatError();

                    ByteArrayOutputStream logOutputStream = new ByteArrayOutputStream();
                    logOutputStream.write(errorCatLog.getBytes());

                    File debugLogFile = new File(VectorApp.mLogsDirectoryFile, "crashes.txt");

                    if (debugLogFile.exists()) {
                        debugLogFile.delete();
                    }

                    FileOutputStream fos = new FileOutputStream(debugLogFile);
                    logOutputStream.writeTo(fos);
                    logOutputStream.flush();
                    logOutputStream.close();

                    logFiles.add(debugLogFile);
                }


                logFiles.addAll(org.matrix.androidsdk.util.Log.addLogFiles(new ArrayList<File>()));

                MXSession session = Matrix.getInstance(VectorApp.getInstance()).getDefaultSession();
                String userName = session.getMyUser().user_id.replace("@", "").replace(":", "_");

                File compressedFile = new File(VectorApp.mLogsDirectoryFile, "RiotBugReport-" + userName + "-" + System.currentTimeMillis()  + ".zip");

                if (compressedFile.exists()) {
                    compressedFile.delete();
                }

                ZipFile zipFile = new ZipFile(compressedFile);
                ZipParameters parameters = new ZipParameters();

                parameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
                parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_FASTEST);

                /*
                    // to define a password
                    parameters.setEncryptFiles(true);
                    parameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES);
                    parameters.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_256);
                    parameters.setPassword(password);
                */

                // file compressed
                zipFile.addFiles(logFiles, parameters);

                return compressedFile;

            } catch (Exception e) {
                Log.e(LOG_TAG, "" + e);
            }
        }

        return null;
    }


    /**
     * Send the bug report with Vector.
     * @param context the application context
     * @param withScreenshot tru to include the screenshot
     */
    private static void sendBugReportWithVector(Context context, boolean withScreenshot, String bugDescription) {
        File file = buildBugZipFile(context, withScreenshot, bugDescription);

        if (null != file) {
            try {
                final Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.setType("application/zip");
                sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));

                CommonActivityUtils.sendFilesTo(VectorApp.getCurrentActivity(), sendIntent);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## sendBugReportWithVector() : failed " + e.getMessage());
            }
        }
    }

    /**
     * Send the bug report by mail.
     */
    private static void sendBugReportWithMail(Context context, boolean withScreenshot, String bugDescription) {
        Bitmap screenShot = takeScreenshot();

        if (null != screenShot) {
            try {
                String message = buildBugReportMessage(context, bugDescription);
                File file = buildBugZipFile(context, withScreenshot, bugDescription);

                // list the intent which supports email
                // it should avoid having lot of unexpected applications (like bluetooth...)
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "rageshake@riot.im", null));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Mail subject");
                List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentActivities(emailIntent, 0);

                if ((null == resolveInfos) || (0 == resolveInfos.size())) {
                    Log.e(LOG_TAG, "Cannot send bug report because there is no application to send emails");
                    return;
                }

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setType("text/html");
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"rageshake@riot.im"});
                intent.putExtra(Intent.EXTRA_SUBJECT, "[Android] Riot bug report - " + Matrix.getInstance(context).getVersion(true));
                intent.putExtra(Intent.EXTRA_TEXT, message);
                if (null != file) {
                    intent.putExtra(Intent.EXTRA_STREAM, VectorContentProvider.absolutePathToUri(context, file.getAbsolutePath()));
                }

                context.startActivity(intent);

            } catch (Exception e) {
                Log.e(LOG_TAG, "" + e);
            }
        }
    }

    /**
     * Send a bug report either with email or with Vector.
     */
    public static void sendBugReport() {
        final Activity currentActivity = VectorApp.getCurrentActivity();

        // no current activity so cannot display an alert
        if (null == currentActivity) {
            sendBugReportWithMail(VectorApp.getInstance().getApplicationContext(), false, "");
            return;
        }

        LayoutInflater inflater = currentActivity.getLayoutInflater();
        View dialoglayout = inflater.inflate(R.layout.dialog_bug_report, null);

        AlertDialog.Builder dialog = new AlertDialog.Builder(currentActivity);
        dialog.setView(dialoglayout);

        final EditText bugReportText = (EditText)dialoglayout.findViewById(R.id.bug_report_edit_text);
        final Button onSendButton = (Button)dialoglayout.findViewById(R.id.bug_report_button);
        final RadioButton emailNoScreenshotButton = (RadioButton)dialoglayout.findViewById(R.id.bug_report_button_email_no_screenshot);
        final RadioButton emailWithScreenshotButton = (RadioButton)dialoglayout.findViewById(R.id.bug_report_button_email_screenshot);
        final RadioButton riotWithScreenshotButton = (RadioButton)dialoglayout.findViewById(R.id.bug_report_button_riot_screenshot);
        final RadioButton riotNoScreenshotButton = (RadioButton)dialoglayout.findViewById(R.id.bug_report_button_riot_no_screenshot);

        TextView riotWithScreenshotText = (TextView)dialoglayout.findViewById(R.id.bug_report_text_riot_screenshot);
        TextView riotNoScreenshotText = (TextView)dialoglayout.findViewById(R.id.bug_report_text_riot_no_screenshot);

        riotWithScreenshotText.setText(currentActivity.getString(R.string.with_vector_and_screenshot, Matrix.getApplicationName()));
        riotNoScreenshotText.setText(currentActivity.getString(R.string.with_vector_without_screenshot, Matrix.getApplicationName()));

        emailNoScreenshotButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (!isChecked) {
                    if (!emailWithScreenshotButton.isChecked() &&
                            !riotWithScreenshotButton.isChecked() &&
                            !riotNoScreenshotButton.isChecked()) {
                        emailNoScreenshotButton.setChecked(true);
                    }
                } else {
                    emailWithScreenshotButton.setChecked(false);
                    riotNoScreenshotButton.setChecked(false);
                    riotWithScreenshotButton.setChecked(false);
                }
            }
        });

        emailWithScreenshotButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (!isChecked) {
                    if (!emailNoScreenshotButton.isChecked() &&
                            !riotWithScreenshotButton.isChecked() &&
                            !riotNoScreenshotButton.isChecked()) {
                        emailWithScreenshotButton.setChecked(true);
                    }
                } else {
                    emailNoScreenshotButton.setChecked(false);
                    riotNoScreenshotButton.setChecked(false);
                    riotWithScreenshotButton.setChecked(false);
                }
            }
        });

        riotWithScreenshotButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (!isChecked) {
                    if (!emailNoScreenshotButton.isChecked() &&
                            !emailWithScreenshotButton.isChecked() &&
                            !riotNoScreenshotButton.isChecked()) {
                        riotWithScreenshotButton.setChecked(true);
                    }
                } else {
                    emailNoScreenshotButton.setChecked(false);
                    riotNoScreenshotButton.setChecked(false);
                    emailWithScreenshotButton.setChecked(false);
                }
            }
        });

        riotNoScreenshotButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    if (!riotWithScreenshotButton.isChecked() &&
                            !emailNoScreenshotButton.isChecked() &&
                            !emailWithScreenshotButton.isChecked()) {
                        riotNoScreenshotButton.setChecked(true);
                    }
                } else {
                    riotWithScreenshotButton.setChecked(false);
                    emailWithScreenshotButton.setChecked(false);
                    emailNoScreenshotButton.setChecked(false);
                }
            }
        });


        onSendButton.setEnabled(false);
        bugReportText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                onSendButton.setEnabled(bugReportText.getText().toString().length() > 10);
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        final AlertDialog bugReportDialog = dialog.show();

        onSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bugReportDialog.cancel();

                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String bugDescription = bugReportText.getText().toString().trim();

                        if (emailNoScreenshotButton.isChecked()) {
                            sendBugReportWithMail(currentActivity, false, bugDescription);
                        } else if (emailWithScreenshotButton.isChecked()) {
                            sendBugReportWithMail(currentActivity, true, bugDescription);
                        } else if (riotWithScreenshotButton.isChecked()) {
                            sendBugReportWithVector(currentActivity, true, bugDescription);
                        } else {
                            sendBugReportWithVector(currentActivity, false, bugDescription);
                        }
                    }
                }, 300);
            }
        });
    }

    /**
     * Take a screenshot of the display.
     * @return the screenshot
     */
    private static Bitmap takeScreenshot() {
        // sanity check
        if (VectorApp.getCurrentActivity() == null) {
            return null;
        }
        // get content view
        View contentView = VectorApp.getCurrentActivity().findViewById(android.R.id.content);
        if (contentView == null) {
            Log.e(LOG_TAG, "Cannot find content view on " + VectorApp.getCurrentActivity() + ". Cannot take screenshot.");
            return null;
        }

        // get the root view to snapshot
        View rootView = contentView.getRootView();
        if (rootView == null) {
            Log.e(LOG_TAG, "Cannot find root view on " + VectorApp.getCurrentActivity() + ". Cannot take screenshot.");
            return null;
        }
        // refresh it
        rootView.setDrawingCacheEnabled(false);
        rootView.setDrawingCacheEnabled(true);

        try {
            return rootView.getDrawingCache();
        }
        catch (OutOfMemoryError oom) {
            Log.e(LOG_TAG, "Cannot get drawing cache for "+ VectorApp.getCurrentActivity() +" OOM.");
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "Cannot get snapshot of screen: "+e);
        }
        return null;
    }

    private static final int BUFFER_SIZE = 1024 * 1024 * 5;
    private static final String[] LOGCAT_CMD = new String[] {
            "logcat", ///< Run 'logcat' command
            "-d",  ///< Dump the log rather than continue outputting it
            "-v", // formatting
            "threadtime", // include timestamps
            "AndroidRuntime:E " + ///< Pick all AndroidRuntime errors (such as uncaught exceptions)"communicatorjni:V " + ///< All communicatorjni logging
            "libcommunicator:V " + ///< All libcommunicator logging
            "DEBUG:V " + ///< All DEBUG logging - which includes native land crashes (seg faults, etc)
            "*:S" ///< Everything else silent, so don't pick it..
    };


    /**
     * @return the error logcat command line.
     */
    public static String getLogCatError() {
        return getLog(LOGCAT_CMD);
    }

    /**
     * Retrieves the logs from a dedicated command.
     * @param cmd the command to execute.
     * @return the logs.
     */
    private static String getLog(String[] cmd) {
        Process logcatProc;
        try {
            logcatProc = Runtime.getRuntime().exec(cmd);
        }
        catch (IOException e1) {
            return "";
        }

        BufferedReader reader = null;
        String response = "";
        try {
            String separator = System.getProperty("line.separator");
            StringBuilder sb = new StringBuilder();
            reader = new BufferedReader(new InputStreamReader(logcatProc.getInputStream()), BUFFER_SIZE);
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append(separator);
            }
            response = sb.toString();
        }
        catch (IOException e) {
            Log.e(LOG_TAG, "getLog fails with " + e.getLocalizedMessage());
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    Log.e(LOG_TAG, "getLog fails with " + e.getLocalizedMessage());
                }
            }
        }
        return response;
    }

}
